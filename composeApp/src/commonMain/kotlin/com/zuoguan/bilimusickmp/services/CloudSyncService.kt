package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.SyncDelta
import com.zuoguan.bilimusickmp.models.SyncHead
import com.zuoguan.bilimusickmp.models.SyncKeys
import com.zuoguan.bilimusickmp.models.SyncPrefs
import com.zuoguan.bilimusickmp.models.SyncSnapshot
import com.zuoguan.bilimusickmp.utils.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

enum class SyncPhase { IDLE, SYNCING, SUCCESS, ERROR, NO_SCRIPT }

data class SyncUiState(
    val phase: SyncPhase = SyncPhase.IDLE,
    val message: String = ""
) {
    val label: String
        get() = when (phase) {
            SyncPhase.IDLE -> "等待同步"
            SyncPhase.SYNCING -> "同步中…"
            SyncPhase.SUCCESS -> message
            SyncPhase.ERROR -> message
            SyncPhase.NO_SCRIPT -> message
        }
}

/**
 * v2 云同步（仅依赖 COS 对象存储 + 用户脚本）：
 *
 * 协议：
 * - `sync/v2/head.json`：头部指针（每次同步只拉 ~200B）；
 * - `sync/v2/deltas/{v}.json`：增量（仅含变更歌曲/顺序/偏好，内嵌完整载荷）；
 * - `sync/v2/snapshots/current.json`：全量快照（固定 key 覆盖写，只保留一份；引导与增量链过长时兜底）。
 *
 * 语义：
 * - 歌曲按 updatedAt LWW；"相等且分歧时远端胜"（升级基线统一给 0，首轮不会互相覆盖）；
 * - 首次推送全量（含基线歌曲与墓碑），之后只推 dirty 增量；
 * - 推送前后自校验 head，发现并发提交则拉取合并后重推（自愈，最多 3 轮）；
 * - 每 [SNAPSHOT_EVERY] 个版本生成一次全量快照，拉取端增量链超过 [CHAIN_LIMIT] 时走快照路径。
 */
class CloudSyncService(
    private val engine: JsEngineService,
    private val repository: SongRepositoryService,
    private val prefs: PreferencesStorageService
) {
    companion object {
        const val SNAPSHOT_EVERY = 100L
        const val CHAIN_LIMIT = 50L
        const val DEBOUNCE_MS = 5000L
        const val MAX_HEAL_ROUNDS = 3

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _status = MutableStateFlow(SyncUiState())
    val status: StateFlow<SyncUiState> = _status.asStateFlow()

    private var debounceJob: Job? = null

    init {
        scope.launch {
            repository.userChanges.collect {
                scheduleSync()
            }
        }
        scope.launch {
            engine.scriptAddedEvent.collect {
                syncNow()
            }
        }
        // 启动后稍等脚本求值完成再同步
        scope.launch {
            delay(500.milliseconds)
            if (engine.isScriptLoaded) {
                syncNow()
            } else {
                _status.value = SyncUiState(SyncPhase.NO_SCRIPT, "未配置云同步脚本")
            }
        }
    }

    /** 本地变更 → 防抖后推送。 */
    private fun scheduleSync() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS.milliseconds)
            syncNow()
        }
    }

    /** 手动触发同步（设置页"立即同步"）。 */
    suspend fun syncNow() {
        syncMutex.withLock {
            try {
                if (!engine.isScriptLoaded) {
                    _status.value = SyncUiState(SyncPhase.NO_SCRIPT, "未配置云同步脚本")
                    return@withLock
                }
                _status.value = SyncUiState(SyncPhase.SYNCING, "同步中…")
                val device = deviceId()

                val head = fetchHead()
                if (head == null) {
                    seed(device)
                    _status.value = SyncUiState(SyncPhase.SUCCESS, "已初始化云同步（上传全量数据）")
                    return@withLock
                }

                val localHeadV = prefs.getLong(SyncKeys.LOCAL_HEAD_V)
                if (localHeadV < head.v) {
                    pull(head, localHeadV)
                    prefs.putLong(SyncKeys.LOCAL_HEAD_V, head.v)
                }

                // 推送脏数据（自愈：若推送后 head 被他人推进，则拉取合并后重推）
                for (round in 0 until MAX_HEAL_ROUNDS) {
                    val pushedV = push(device) ?: break
                    val verify = fetchHead() ?: break
                    if (verify.v == pushedV) break // 推送成功且未被人抢先，结束
                    pull(verify, prefs.getLong(SyncKeys.LOCAL_HEAD_V))
                    prefs.putLong(SyncKeys.LOCAL_HEAD_V, verify.v)
                }

                _status.value = SyncUiState(SyncPhase.SUCCESS, "同步完成")
            } catch (e: Exception) {
                _status.value = SyncUiState(
                    SyncPhase.ERROR,
                    "同步失败：${e.message ?: e::class.simpleName}"
                )
            }
        }
    }

    // ---------- 设备标识 ----------

    private suspend fun deviceId(): String {
        prefs.getString(SyncKeys.LOCAL_DEVICE_ID)?.let {
            if (it.isNotEmpty()) return it
        }
        val id = buildString {
            repeat(16) { append(Random.nextInt(16).toString(16)) }
        }
        prefs.putString(SyncKeys.LOCAL_DEVICE_ID, id)
        return id
    }

    // ---------- 协议：种子 / 拉取 / 推送 ----------

    /** 云端无 head：以本设备全量数据初始化。 */
    private suspend fun seed(device: String) {
        val v = nextVersion(0)
        val snapshot = repository.buildSnapshot(device, v, currentTimeMillis())
            .copy(prefs = prefs.syncablePayload())
        engine.uploadJson(SyncKeys.SNAPSHOT_KEY, json.encodeToString(snapshot))
        engine.uploadJson(
            SyncKeys.HEAD_KEY,
            json.encodeToString(
                SyncHead(v = v, prev = 0, snapshot = v, device = device, ts = currentTimeMillis())
            )
        )
        prefs.putLong(SyncKeys.LOCAL_HEAD_V, v)
        bumpWatermark(
            maxOf(
                snapshot.songs.maxOfOrNull { it.updatedAt } ?: 0L,
                snapshot.order.updatedAt,
                prefs.syncUpdatedAt()
            )
        )
        prefs.putBoolean(SyncKeys.LOCAL_EVER_PUSHED, true)
    }

    /** 拉取并应用远端增量链（必要时回退到固定 key 的全量快照）。 */
    private suspend fun pull(head: SyncHead, localHeadV: Long) {
        val gap = head.v - localHeadV
        var appliedFrom = localHeadV

        if (localHeadV == 0L || gap > CHAIN_LIMIT) {
            val snapshotV = applyCurrentSnapshot()
            if (snapshotV != SongRepositoryService.NO_APPLY) {
                appliedFrom = snapshotV
            }
        }

        for (v in (appliedFrom + 1)..head.v) {
            applyDelta(v)
        }
    }

    /**
     * 下载并应用固定 key 的全量快照。
     * @return 快照内容里的版本号（用于决定补拉哪些增量）；快照不存在返回 [SongRepositoryService.NO_APPLY]。
     */
    private suspend fun applyCurrentSnapshot(): Long {
        val raw = engine.downloadText(SyncKeys.SNAPSHOT_KEY) ?: return SongRepositoryService.NO_APPLY
        val snapshot = json.decodeFromString<SyncSnapshot>(raw)
        var applied = repository.applyRemoteSongs(snapshot.songs)
        applied = maxOf(applied, repository.applyRemoteOrder(snapshot.order))
        applied = maxOf(applied, prefs.applySyncable(snapshot.prefs))
        bumpWatermark(applied)
        return snapshot.v
    }

    private suspend fun applyDelta(v: Long) {
        val raw = engine.downloadText(SyncKeys.deltaKey(v))
            ?: throw IllegalStateException("增量 $v 不存在")
        val delta = json.decodeFromString<SyncDelta>(raw)
        var applied = repository.applyRemoteSongs(delta.songs)
        applied = maxOf(applied, repository.applyRemoteOrder(delta.order))
        applied = maxOf(applied, delta.prefs?.let { prefs.applySyncable(it) } ?: SongRepositoryService.NO_APPLY)
        bumpWatermark(applied)
    }

    /** 推送本地脏数据；无脏数据返回 null，否则返回新版本号。 */
    private suspend fun push(device: String): Long? {
        val head = fetchHead() ?: return null
        val watermark = prefs.getLong(SyncKeys.LOCAL_WATERMARK)
        val forceFull = !prefs.getBoolean(SyncKeys.LOCAL_EVER_PUSHED, false)

        val dirty = repository.collectDirty(watermark, forceFull)
        val prefsPayload: SyncPrefs? = prefs.syncablePayload()
            .takeIf { forceFull || it.updatedAt > watermark }

        if (dirty.songs.isEmpty() && dirty.order == null && prefsPayload == null) {
            return null
        }

        val newV = nextVersion(head.v)
        val delta = SyncDelta(
            v = newV,
            prev = head.v,
            device = device,
            ts = currentTimeMillis(),
            songs = dirty.songs,
            order = dirty.order,
            prefs = prefsPayload
        )
        engine.uploadJson(SyncKeys.deltaKey(newV), json.encodeToString(delta))

        // 定期压缩：覆盖写固定 key 的全量快照并记录版本
        val snapshot = if (newV - head.snapshot >= SNAPSHOT_EVERY) {
            val snap = repository.buildSnapshot(device, newV, currentTimeMillis())
                .copy(prefs = prefs.syncablePayload())
            engine.uploadJson(SyncKeys.SNAPSHOT_KEY, json.encodeToString(snap))
            newV
        } else {
            head.snapshot
        }

        engine.uploadJson(
            SyncKeys.HEAD_KEY,
            json.encodeToString(
                SyncHead(
                    v = newV,
                    prev = head.v,
                    snapshot = snapshot,
                    device = device,
                    ts = currentTimeMillis()
                )
            )
        )

        prefs.putLong(SyncKeys.LOCAL_HEAD_V, newV)
        var maxPushed = watermark
        dirty.songs.forEach { maxPushed = maxOf(maxPushed, it.updatedAt) }
        dirty.order?.let { maxPushed = maxOf(maxPushed, it.updatedAt) }
        prefsPayload?.let { maxPushed = maxOf(maxPushed, it.updatedAt) }
        prefs.putLong(SyncKeys.LOCAL_WATERMARK, maxPushed)
        prefs.putBoolean(SyncKeys.LOCAL_EVER_PUSHED, true)
        return newV
    }

    // ---------- 工具 ----------

    private suspend fun fetchHead(): SyncHead? {
        val raw = engine.downloadText(SyncKeys.HEAD_KEY) ?: return null
        return json.decodeFromString<SyncHead>(raw)
    }

    private fun nextVersion(prev: Long): Long =
        maxOf(currentTimeMillis(), prev + 1)

    private suspend fun bumpWatermark(applied: Long) {
        if (applied == SongRepositoryService.NO_APPLY) return
        val current = prefs.getLong(SyncKeys.LOCAL_WATERMARK)
        if (applied > current) {
            prefs.putLong(SyncKeys.LOCAL_WATERMARK, applied)
        }
    }
}