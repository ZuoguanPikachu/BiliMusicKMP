package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.models.Song
import com.zuoguan.bilimusickmp.models.SongDto
import com.zuoguan.bilimusickmp.models.SyncOrder
import com.zuoguan.bilimusickmp.models.SyncSnapshot
import com.zuoguan.bilimusickmp.utils.DatabaseHelper
import com.zuoguan.bilimusickmp.utils.currentTimeMillis
import kotbase.Collection
import kotbase.DataSource
import kotbase.Document
import kotbase.MutableArray
import kotbase.MutableDocument
import kotbase.QueryBuilder
import kotbase.Result
import kotbase.SelectResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一次脏数据收集结果。 */
data class DirtySync(
    val songs: List<SongDto> = emptyList(),
    val order: SyncOrder? = null
)

/**
 * 歌曲仓库（本地数据入口）：
 *
 * - 每个歌曲文档带 [SongDto.updatedAt] 内容版本（LWW 依据），升级前存量数据为 0（基线）；
 * - 顺序存于 `~order` 元文档（有序 id 列表 + 版本），歌曲的 [Song.ts] 由顺序索引推导，
 *   不再写回文档 —— 排序只改一个文档；
 * - 删除写 `~del:<id>` 墓碑文档，防止合并时已删除的歌曲被复活；
 * - 用户变更通过 [userChanges] 通知云同步服务做防抖推送。
 */
class SongRepositoryService {

    companion object {
        // Couchbase Lite 文档 ID 不允许以 "_" 开头，元文档统一用 "~" 前缀
        const val ORDER_DOC_ID = "~order"
        const val TOMBSTONE_PREFIX = "~del:"
        const val NO_APPLY = Long.MIN_VALUE

        fun tombstoneId(songId: String) = TOMBSTONE_PREFIX + songId
        private fun isMetaId(id: String) = id.startsWith("~")
    }

    private val coll: Collection = DatabaseHelper.songCollection

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags.asStateFlow()

    private val _userChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val userChanges: SharedFlow<Unit> = _userChanges.asSharedFlow()

    private var clock = 0L

    init {
        loadSongs()
    }

    // ---------- 版本时钟 ----------

    /** 本地单调版本：取当前时间与本地时钟 +1 的较大者，保证单调递增。 */
    private fun nextClock(): Long {
        clock = maxOf(currentTimeMillis(), clock + 1)
        return clock
    }

    // ---------- 本地 CRUD ----------

    fun loadSongs() {
        ensureOrderIndex()
        val rows = queryAllRows()
        val orderIds = getOrderIds()
        val songList = rows.mapNotNull { row ->
            val id = row.getString("id") ?: return@mapNotNull null
            if (isMetaId(id)) return@mapNotNull null
            rowToSong(row, id, orderIds)
        }
        val maxUpdated = rows.maxOfOrNull { it.getLong("updatedAt") } ?: 0L
        clock = maxOf(clock, maxUpdated + 1)

        _songs.value = songList
        _allTags.value = songList
            .flatMap { it.tags }
            .distinct()
            .sorted()
    }

    suspend fun saveSong(song: Song) {
        val existed = coll.getDocument(song.id) != null
        coll.save(songToDocument(song, nextClock()))
        if (!existed) {
            appendToOrder(song.id)
        }
        loadSongs()
        _userChanges.tryEmit(Unit)
    }

    suspend fun removeSong(id: String) {
        val doc = coll.getDocument(id) ?: return
        coll.delete(doc)
        coll.save(MutableDocument(tombstoneId(id)).setLong("updatedAt", nextClock()))
        removeFromOrder(id)
        loadSongs()
        _userChanges.tryEmit(Unit)
    }

    fun getSongById(id: String): Song? {
        val doc = coll.getDocument(id) ?: return null
        return docToSong(doc, getOrderIds())
    }

    /** 重新排序：只调整传入歌曲彼此的相对位次，未传入的歌曲保持原位。 */
    suspend fun persistOrder(songs: List<Song>) {
        applyOrderIds(songs.map { it.id }, bump = true)
        loadSongs()
        _userChanges.tryEmit(Unit)
    }

    // ---------- 顺序（~order 元文档） ----------

    private fun ensureOrderIndex() {
        if (coll.getDocument(ORDER_DOC_ID) != null) return
        // 从存量文档构建初始顺序：优先保留 v1 时代的 ts 位次，其次按文档 id。
        val rows = queryAllRows().filter { row ->
            val id = row.getString("id") ?: return@filter false
            !isMetaId(id)
        }
        val sorted = rows.sortedWith(
            compareBy({ it.getLong("ts") }, { it.getString("id") ?: "" })
        )
        coll.save(
            MutableDocument(ORDER_DOC_ID)
                .setArray("ids", MutableArray(sorted.map { it.getString("id")!! }))
                .setLong("updatedAt", 0L)
        )
    }

    private fun getOrderIds(): List<String> {
        val doc = coll.getDocument(ORDER_DOC_ID) ?: return emptyList()
        return doc.getArray("ids")?.toList()?.mapNotNull { it.toString() } ?: emptyList()
    }

    private fun getOrderUpdatedAt(): Long =
        coll.getDocument(ORDER_DOC_ID)?.getLong("updatedAt") ?: -1L

    private fun appendToOrder(id: String) {
        val ids = getOrderIds().toMutableList()
        if (id in ids) return
        ids.add(id)
        coll.save(orderDocument(ids, nextClock()))
    }

    private fun removeFromOrder(id: String) {
        val ids = getOrderIds().toMutableList()
        if (ids.remove(id)) {
            coll.save(orderDocument(ids, nextClock()))
        }
    }

    private fun applyOrderIds(ids: List<String>, bump: Boolean) {
        val merged = mergeSubsetOrder(getOrderIds(), ids)
        coll.save(orderDocument(merged, if (bump) nextClock() else getOrderUpdatedAt()))
    }

    private fun mergeSubsetOrder(existing: List<String>, subsetIds: List<String>): List<String> {
        if (subsetIds.isEmpty()) return existing
        val subsetSet = subsetIds.toSet()
        val pending = ArrayDeque(subsetIds)
        val result = ArrayList<String>(existing.size + subsetIds.size)
        for (id in existing) {
            result += if (id in subsetSet) pending.removeFirst() else id
        }
        result += pending
        return result
    }

    private fun orderDocument(ids: List<String>, updatedAt: Long): MutableDocument =
        MutableDocument(ORDER_DOC_ID)
            .setArray("ids", MutableArray(ids))
            .setLong("updatedAt", updatedAt)

    // ---------- 云同步：脏数据收集 / 快照 / 远端合并 ----------

    /**
     * 收集相对 [watermark] 的脏数据。
     * @param forceFull 首次推送时传 true：无论 watermark 高低都全量收集（含基线歌曲与墓碑）。
     */
    suspend fun collectDirty(watermark: Long, forceFull: Boolean): DirtySync {
        val rows = queryAllRows()
        val songs = mutableListOf<SongDto>()
        for (row in rows) {
            val id = row.getString("id") ?: continue
            if (id == ORDER_DOC_ID) continue
            val updatedAt = row.getLong("updatedAt")
            if (!forceFull && updatedAt <= watermark) continue
            if (id.startsWith(TOMBSTONE_PREFIX)) {
                songs += SongDto(
                    id = id.removePrefix(TOMBSTONE_PREFIX),
                    updatedAt = updatedAt,
                    deleted = true
                )
            } else if (!isMetaId(id)) {
                songs += rowToDto(row, id, updatedAt)
            }
        }
        val orderUpdatedAt = getOrderUpdatedAt()
        val order = if (forceFull || orderUpdatedAt > watermark) {
            SyncOrder(updatedAt = orderUpdatedAt.coerceAtLeast(0), ids = getOrderIds())
        } else null
        return DirtySync(songs, order)
    }

    /** 构建全量快照（含歌曲、顺序、墓碑；偏好由偏好存储单独提供）。 */
    suspend fun buildSnapshot(deviceId: String, v: Long, ts: Long): SyncSnapshot {
        val rows = queryAllRows()
        val songs = mutableListOf<SongDto>()
        for (row in rows) {
            val id = row.getString("id") ?: continue
            if (id == ORDER_DOC_ID) continue
            val updatedAt = row.getLong("updatedAt")
            if (id.startsWith(TOMBSTONE_PREFIX)) {
                songs += SongDto(
                    id = id.removePrefix(TOMBSTONE_PREFIX),
                    updatedAt = updatedAt,
                    deleted = true
                )
            } else if (!isMetaId(id)) {
                songs += rowToDto(row, id, updatedAt)
            }
        }
        return SyncSnapshot(
            v = v,
            device = deviceId,
            ts = ts,
            songs = songs,
            order = SyncOrder(updatedAt = getOrderUpdatedAt().coerceAtLeast(0), ids = getOrderIds())
        )
    }

    /**
     * 应用远端歌曲列表（LWW：incoming.updatedAt >= 本地则生效，"相等且分歧时远端胜"）。
     * @return 实际应用到的最大 updatedAt；未应用任何内容返回 [NO_APPLY]。
     */
    suspend fun applyRemoteSongs(songs: List<SongDto>): Long {
        var applied = NO_APPLY
        for (dto in songs) {
            applied = maxOf(applied, applySongDto(dto))
        }
        loadSongs()
        return applied
    }

    private fun applySongDto(dto: SongDto): Long {
        val songDoc = coll.getDocument(dto.id)
        val tombDoc = coll.getDocument(tombstoneId(dto.id))
        val songUpdated = songDoc?.getLong("updatedAt") ?: NO_APPLY
        val tombUpdated = tombDoc?.getLong("updatedAt") ?: NO_APPLY

        return if (dto.deleted) {
            if (dto.updatedAt >= songUpdated) {
                songDoc?.let { coll.delete(it) }
                if (dto.updatedAt >= tombUpdated) {
                    tombDoc?.let { coll.delete(it) }
                    coll.save(MutableDocument(tombstoneId(dto.id)).setLong("updatedAt", dto.updatedAt))
                }
                dto.updatedAt
            } else {
                NO_APPLY // 本地歌曲更新，保留（稍后推送）
            }
        } else {
            if (songDoc == null) {
                if (dto.updatedAt >= tombUpdated) {
                    tombDoc?.let { coll.delete(it) }
                    coll.save(dto.toMutableDocument())
                    dto.updatedAt
                } else {
                    NO_APPLY // 本地墓碑更新 → 保持删除
                }
            } else if (dto.updatedAt >= songUpdated) {
                coll.save(dto.toMutableDocument())
                dto.updatedAt
            } else {
                NO_APPLY // 本地更新，保留
            }
        }
    }

    /**
     * 应用远端顺序（LWW）。
     * 若本地存在远端未收录的歌曲，追加到末尾并把本地顺序版本抬到 [nextClock]（保持脏状态以便推送）。
     * @return remote 原始 order.updatedAt；未应用返回 [NO_APPLY]。
     */
    suspend fun applyRemoteOrder(order: SyncOrder?): Long {
        if (order == null) return NO_APPLY
        val localUpdated = getOrderUpdatedAt()
        if (order.updatedAt < localUpdated) return NO_APPLY

        val localSongIds = queryAllRows().mapNotNull { row ->
            val id = row.getString("id") ?: return@mapNotNull null
            if (isMetaId(id)) null else id
        }
        val remoteIds = order.ids
        val merged = remoteIds + localSongIds.filterNot { it in remoteIds }
        val updatedAt = if (merged.size == remoteIds.size) {
            order.updatedAt
        } else {
            maxOf(order.updatedAt, nextClock()) // 追加了本地歌曲 → 保持脏，等待推送
        }
        coll.save(orderDocument(merged, updatedAt))
        loadSongs()
        return order.updatedAt
    }

    // ---------- 查询与映射 ----------

    private fun queryAllRows(): List<Result> {
        val query = QueryBuilder
            .select(
                SelectResult.expression(kotbase.Meta.id).`as`("id"),
                SelectResult.property("updatedAt"),
                SelectResult.property("ts"),
                SelectResult.property("cid"),
                SelectResult.property("audioSource"),
                SelectResult.property("title"),
                SelectResult.property("author"),
                SelectResult.property("tags"),
                SelectResult.property("lyricSource"),
                SelectResult.property("lyricId"),
                SelectResult.property("lyricBias"),
                SelectResult.property("coverSource"),
                SelectResult.property("coverId"),
                SelectResult.property("pic")
            )
            .from(DataSource.collection(coll))
        return query.execute().toList()
    }

    private fun rowToDto(row: Result, id: String, updatedAt: Long): SongDto = SongDto(
        id = id,
        updatedAt = updatedAt,
        cid = row.getString("cid") ?: "",
        audioSource = row.getString("audioSource") ?: "",
        title = row.getString("title") ?: "",
        author = row.getString("author") ?: "",
        tags = row.getArray("tags")?.toList()?.mapNotNull { it.toString() } ?: emptyList(),
        lyricSource = row.getString("lyricSource") ?: "",
        lyricId = row.getString("lyricId") ?: "",
        lyricBias = row.getInt("lyricBias"),
        coverSource = row.getString("coverSource") ?: "",
        coverId = row.getString("coverId") ?: "",
        pic = row.getString("pic") ?: ""
    )

    private fun rowToSong(row: Result, id: String, orderIds: List<String>): Song = Song(
        id = id,
        cid = row.getString("cid") ?: "",
        audioSource = parseAudioSource(row.getString("audioSource")),
        title = row.getString("title") ?: "",
        author = row.getString("author") ?: "",
        tags = row.getArray("tags")?.toList()?.mapNotNull { it.toString() } ?: emptyList(),
        lyricSource = parseLyricSource(row.getString("lyricSource")),
        lyricId = row.getString("lyricId") ?: "",
        lyricBias = row.getInt("lyricBias"),
        coverSource = parseCoverSource(row.getString("coverSource")),
        coverId = row.getString("coverId") ?: "",
        pic = row.getString("pic") ?: "",
        ts = orderIndex(orderIds, id)
    )

    private fun docToSong(doc: Document, orderIds: List<String>): Song = Song(
        id = doc.id,
        cid = doc.getString("cid") ?: "",
        audioSource = parseAudioSource(doc.getString("audioSource")),
        title = doc.getString("title") ?: "",
        author = doc.getString("author") ?: "",
        tags = doc.getArray("tags")?.toList()?.mapNotNull { it.toString() } ?: emptyList(),
        lyricSource = parseLyricSource(doc.getString("lyricSource")),
        lyricId = doc.getString("lyricId") ?: "",
        lyricBias = doc.getInt("lyricBias"),
        coverSource = parseCoverSource(doc.getString("coverSource")),
        coverId = doc.getString("coverId") ?: "",
        pic = doc.getString("pic") ?: "",
        ts = orderIndex(orderIds, doc.id)
    )

    private fun orderIndex(orderIds: List<String>, id: String): Long {
        val idx = orderIds.indexOf(id)
        return if (idx >= 0) idx.toLong() else orderIds.size.toLong()
    }

    private fun songToDocument(song: Song, updatedAt: Long): MutableDocument =
        MutableDocument(song.id).apply {
            setLong("updatedAt", updatedAt)
            setString("cid", song.cid)
            setString("audioSource", song.audioSource.name)
            setString("title", song.title)
            setString("author", song.author)
            setArray("tags", MutableArray(song.tags))
            setString("lyricSource", song.lyricSource.name)
            setString("lyricId", song.lyricId)
            setInt("lyricBias", song.lyricBias)
            setString("coverSource", song.coverSource.name)
            setString("coverId", song.coverId)
            setString("pic", song.pic)
        }

    private fun SongDto.toMutableDocument(): MutableDocument =
        MutableDocument(id).apply {
            setLong("updatedAt", updatedAt)
            setString("cid", cid)
            setString("audioSource", audioSource)
            setString("title", title)
            setString("author", author)
            setArray("tags", MutableArray(tags))
            setString("lyricSource", lyricSource)
            setString("lyricId", lyricId)
            setInt("lyricBias", lyricBias)
            setString("coverSource", coverSource)
            setString("coverId", coverId)
            setString("pic", pic)
        }

    private fun parseAudioSource(name: String?): AudioSource =
        name?.let { runCatching { AudioSource.valueOf(it) }.getOrNull() } ?: AudioSource.BILI_BILI

    private fun parseLyricSource(name: String?): LyricSource =
        name?.let { runCatching { LyricSource.valueOf(it) }.getOrNull() } ?: LyricSource.NONE

    private fun parseCoverSource(name: String?): CoverSource =
        name?.let { runCatching { CoverSource.valueOf(it) }.getOrNull() } ?: CoverSource.BILI_BILI
}