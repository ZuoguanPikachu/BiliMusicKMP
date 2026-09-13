package com.zuoguan.bilimusickmp.models

import kotlinx.serialization.Serializable

/**
 * 单首歌曲的同步载荷，是 [Song] 的可序列化镜像。
 *
 * - 不含 [Song.ts]：ts 是本地排序位次，顺序由 [SyncOrder] 单独同步。
 * - [updatedAt] 为内容版本号（LWW 依据）：无版本信息的历史数据记为 0（基线），
 *   任何远端版本都会覆盖它。
 * - [deleted] 为 true 时表示墓碑（删除标记），其余字段无效；墓碑必须保留，
 *   否则删除过的歌曲会在与他人合并时被复活。
 * - 来源字段以枚举名（字符串）保存，避免序列化时耦合枚举定义。
 *
 * @property updatedAt 内容版本号，取值越大越新。
 * @property deleted 是否为墓碑（删除标记）。
 */
@Serializable
data class SongDto(
    val id: String = "",
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,

    val cid: String = "",
    val audioSource: String = "",
    val title: String = "",
    val author: String = "",
    val tags: List<String> = emptyList(),
    val lyricSource: String = "",
    val lyricId: String = "",
    val lyricBias: Int = 0,
    val coverSource: String = "",
    val coverId: String = "",
    val pic: String = ""
)

/**
 * 歌单顺序：有序歌曲 id 列表 + 顺序版本号。
 *
 * 顺序是整体覆盖式同步（LWW 比较的是 [updatedAt]，不做逐项合并），
 * [ids] 中不应包含已删除歌曲。
 *
 * @property updatedAt 顺序的版本号，LWW 依据。
 * @property ids 歌曲 id 的有序列表，顺序即歌单展示顺序。
 */
@Serializable
data class SyncOrder(
    val updatedAt: Long = 0L,
    val ids: List<String> = emptyList()
)

/**
 * 偏好设置同步载荷：仅含可同步键。
 *
 * @property updatedAt 偏好整体版本号，任一项变更都应提升它。
 * @property values 可同步偏好键到值的映射。
 */
@Serializable
data class SyncPrefs(
    val updatedAt: Long = 0L,
    val values: Map<String, String> = emptyMap()
)

/**
 * 增量版本中的头部指针文件（每次同步只拉这一个对象）。
 *
 * [snapshot] 记录最近一次写入全量快照([SyncKeys.SNAPSHOT_KEY])时的版本号，
 * 仅作为压缩节奏的元数据；快照对象本身固定 key 覆盖写，拉取端以快照内容里的
 * [SyncSnapshot.v] 为准补拉增量。
 *
 * @property schema 本文件的 schema 版本，与 [v] 的取值无关。
 * @property v 当前最新版本号，每次提交在 [prev] 基础上递增。
 * @property prev 上一版本号，用于判断增量链是否连续。
 * @property snapshot 最近一次写入全量快照时的版本号，0 表示尚无快照。
 * @property device 写入该版本的设备 id。
 * @property ts 写入时刻的时间戳。
 */
@Serializable
data class SyncHead(
    val schema: Int = 2,
    val v: Long = 0L,
    val prev: Long = 0L,
    val snapshot: Long = 0L,
    val device: String = "",
    val ts: Long = 0L
)

/**
 * 一次提交产生的增量：只包含相对上一版本变更的歌曲 / 顺序 / 偏好。
 *
 * @property v 本次提交产生的新版本号。
 * @property prev 提交时所见的上一版本号，即增量链在 [v] 之前的位置。
 * @property songs 本次变更的歌曲，含墓碑。
 */
@Serializable
data class SyncDelta(
    val v: Long = 0L,
    val prev: Long = 0L,
    val device: String = "",
    val ts: Long = 0L,
    val songs: List<SongDto> = emptyList(),
    val order: SyncOrder? = null,
    val prefs: SyncPrefs? = null
)

/**
 * 全量快照：引导新设备 / 增量链过长时兜底。
 *
 * 存储 key 固定，每次覆盖写；[v] 是它覆盖到的版本号，拉取端据此继续补拉之后的增量。
 */
@Serializable
data class SyncSnapshot(
    val v: Long = 0L,
    val device: String = "",
    val ts: Long = 0L,
    val songs: List<SongDto> = emptyList(),
    val order: SyncOrder = SyncOrder(),
    val prefs: SyncPrefs = SyncPrefs()
)

/**
 * 云同步涉及的常量（键前缀等）。
 *
 * key 里的 `v2` 是协议存储布局版本：布局不兼容变更时才提升，
 * 与 [SyncHead.schema]、内容版本号都是各自独立的编号。
 */
object SyncKeys {
    /** 头部指针文件的 key，每次同步只拉取这一个对象。 */
    const val HEAD_KEY = "sync/v2/head.json"
    /** 增量文件的前缀，完整 key 为「前缀 + 版本号 + .json」。 */
    const val DELTA_PREFIX = "sync/v2/deltas/"
    /** 全量快照的固定 key，覆盖写，不随版本号变化。 */
    const val SNAPSHOT_KEY = "sync/v2/snapshots/current.json"

    /**
     * 拼接某个版本增量的存储 key。
     *
     * @param v 增量版本号。
     */
    fun deltaKey(v: Long) = "$DELTA_PREFIX$v.json"

    /** 参与云同步的偏好键（未来新增可同步项只需在此追加）。 */
    val PREF_KEYS: Set<String> = setOf(
        "llm.apiKey",
        "llm.baseUrl",
        "llm.modelName",
        // 与 services.ThemePreferences 中的常量保持一致
        "theme.color",
        "theme.darkMode"
    )

    /** 仅存本地的键前缀，绝不参与同步。 */
    const val LOCAL_KEY_PREFIX = "local."

    /** 本机设备 id，首次同步时生成后持久化。 */
    const val LOCAL_DEVICE_ID = "local.deviceId"
    /** 本机已拉取到的云端版本号，用于判断还需补拉哪些增量。 */
    const val LOCAL_HEAD_V = "local.headV"
    /** 本机已应用的最大内容版本号，决定下次推送要收集哪些脏数据。 */
    const val LOCAL_WATERMARK = "local.watermark"
    /** 是否成功推送过；为 false 时下次推送强制全量。 */
    const val LOCAL_EVER_PUSHED = "local.everPushed"
}