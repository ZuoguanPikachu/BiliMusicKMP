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
 * - 每个歌曲文档带 [SongDto.updatedAt] 内容版本（LWW 依据）；无版本信息的历史数据记为 0（基线），
 *   会被任意远端版本覆盖；
 * - 顺序存于 `~order` 元文档（有序 id 列表 + 版本），歌曲的 [Song.ts] 由顺序索引推导、不落库，
 *   因此排序只需更新一个文档；
 * - 删除写 `~del:<id>` 墓碑文档，防止合并时已删除的歌曲被复活；
 * - 用户变更通过 [userChanges] 通知云同步服务做防抖推送。
 */
class SongRepositoryService {

    companion object {
        // Couchbase Lite 文档 ID 不允许以 "_" 开头，元文档统一用 "~" 前缀
        const val ORDER_DOC_ID = "~order"
        const val TOMBSTONE_PREFIX = "~del:"
        // 哨兵值：表示未应用任何内容，低于一切真实 updatedAt（真实值均 >= 0）
        const val NO_APPLY = Long.MIN_VALUE

        fun tombstoneId(songId: String) = TOMBSTONE_PREFIX + songId
        private fun isMetaId(id: String) = id.startsWith("~")
    }

    private val coll: Collection = DatabaseHelper.songCollection

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    /** 当前歌曲列表，按 `~order` 元文档中的位次排列。 */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags.asStateFlow()

    private val _userChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    /** 本地用户变更信号（远端合并不触发，避免回推），云同步据此做防抖推送。 */
    val userChanges: SharedFlow<Unit> = _userChanges.asSharedFlow()

    private var clock = 0L

    init {
        loadSongs()
    }

    // ---------- 版本时钟 ----------

    /** 本地单调版本：取当前时间与本地时钟 +1 的较大者，保证单调递增、避免同毫秒冲突。 */
    private fun nextClock(): Long {
        clock = maxOf(currentTimeMillis(), clock + 1)
        return clock
    }

    // ---------- 本地 CRUD ----------

    fun loadSongs() {
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
        // 删除后重新添加同一首歌：清掉旧墓碑，否则同 id 的「墓碑 + 歌曲」会一起被
        // 同步出去，且墓碑会随每次快照反复外发（其版本必然低于 nextClock()，删除是安全的）。
        coll.getDocument(tombstoneId(song.id))?.let { coll.delete(it) }
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

    /** 按 id 取歌曲：命中的一定在 [songs] 里，直接查已加载的列表，无需再读数据库。 */
    fun getSongById(id: String): Song? = _songs.value.firstOrNull { it.id == id }

    /** 重新排序：只调整传入歌曲彼此的相对位次，未传入的歌曲保持原位。 */
    suspend fun persistOrder(songs: List<Song>) {
        applyOrderIds(songs.map { it.id })
        loadSongs()
        _userChanges.tryEmit(Unit)
    }

    // ---------- 顺序（~order 元文档） ----------

    private fun getOrderIds(): List<String> {
        val doc = coll.getDocument(ORDER_DOC_ID) ?: return emptyList()
        return doc.getArray("ids")?.toList()?.mapNotNull { it.toString() } ?: emptyList()
    }

    /** 顺序版本；尚无 `~order` 文档时按基线 0 处理，任何远端版本都能覆盖它。 */
    private fun getOrderUpdatedAt(): Long =
        coll.getDocument(ORDER_DOC_ID)?.getLong("updatedAt") ?: 0L

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

    /** 覆盖这些歌曲的相对位次；版本抬到 [nextClock] 以标记顺序为待推送的脏数据。 */
    private fun applyOrderIds(ids: List<String>) {
        coll.save(orderDocument(mergeSubsetOrder(getOrderIds(), ids), nextClock()))
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

    /** 读取全部歌曲（已删除的以墓碑形式返回），供脏数据收集与全量快照共用。 */
    private fun readAllSongs(): List<SongDto> {
        val songs = mutableListOf<SongDto>()
        for (row in queryAllRows()) {
            val id = row.getString("id") ?: continue
            val updatedAt = row.getLong("updatedAt")
            // 墓碑必须先于 isMetaId 判断：墓碑 id 以 "~del:" 开头，同样满足 "~" 前缀，
            // 若先被当作元文档跳过，删除标记就永远推不出去，其他设备上的歌曲会被复活。
            if (id.startsWith(TOMBSTONE_PREFIX)) {
                songs += SongDto(
                    id = id.removePrefix(TOMBSTONE_PREFIX),
                    updatedAt = updatedAt,
                    deleted = true
                )
                continue
            }
            if (isMetaId(id)) continue
            songs += rowToDto(row, id, updatedAt)
        }
        return songs
    }

    /** 读取当前顺序。 */
    private fun readOrder(): SyncOrder = SyncOrder(updatedAt = getOrderUpdatedAt(), ids = getOrderIds())

    /**
     * 收集相对 [watermark] 的脏数据（已删除歌曲以墓碑形式返回）。
     *
     * @param forceFull 首次推送时传 true：无论 watermark 高低都全量收集，保证对端拿到基线数据。
     */
    suspend fun collectDirty(watermark: Long, forceFull: Boolean): DirtySync {
        val songs = readAllSongs()
        val order = readOrder()
        return DirtySync(
            songs = if (forceFull) songs else songs.filter { it.updatedAt > watermark },
            order = order.takeIf { forceFull || it.updatedAt > watermark }
        )
    }

    /** 构建全量快照（含歌曲、顺序、墓碑；偏好由偏好存储单独提供）。 */
    suspend fun buildSnapshot(deviceId: String, v: Long, ts: Long): SyncSnapshot = SyncSnapshot(
        v = v,
        device = deviceId,
        ts = ts,
        songs = readAllSongs(),
        order = readOrder()
    )

    /**
     * 应用远端歌曲列表（LWW：`incoming.updatedAt >= 本地` 则生效，相等且分歧时远端胜）。
     *
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

    /** 单曲 LWW：歌曲文档与墓碑各自持有版本，比较后决定删除、复活还是保留本地。 */
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
     * 应用远端顺序（LWW：`order.updatedAt >= 本地` 则生效）。
     *
     * 远端未收录的本地歌曲追加到末尾，并把本地顺序版本抬到 [nextClock]，保持 dirty 状态等待推送。
     *
     * @return 远端原始 order.updatedAt；未应用返回 [NO_APPLY]。
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

    private fun rowToSong(row: Result, id: String, orderIds: List<String>): Song =
        rowToDto(row, id, row.getLong("updatedAt")).toSong(orderIds)

    private fun orderIndex(orderIds: List<String>, id: String): Long {
        val idx = orderIds.indexOf(id)
        return if (idx >= 0) idx.toLong() else orderIds.size.toLong()
    }

    /** 仓库实体 → 同步载荷（来源枚举按名字落库，避免耦合枚举定义）。 */
    private fun Song.toDto(updatedAt: Long): SongDto = SongDto(
        id = id,
        updatedAt = updatedAt,
        cid = cid,
        audioSource = audioSource.name,
        title = title,
        author = author,
        tags = tags,
        lyricSource = lyricSource.name,
        lyricId = lyricId,
        lyricBias = lyricBias,
        coverSource = coverSource.name,
        coverId = coverId,
        pic = pic
    )

    /** 同步载荷 → 仓库实体，[Song.ts] 由顺序位次推导。 */
    private fun SongDto.toSong(orderIds: List<String>): Song = Song(
        id = id,
        cid = cid,
        audioSource = parseAudioSource(audioSource),
        title = title,
        author = author,
        tags = tags,
        lyricSource = parseLyricSource(lyricSource),
        lyricId = lyricId,
        lyricBias = lyricBias,
        coverSource = parseCoverSource(coverSource),
        coverId = coverId,
        pic = pic,
        ts = orderIndex(orderIds, id)
    )

    private fun songToDocument(song: Song, updatedAt: Long): MutableDocument =
        song.toDto(updatedAt).toMutableDocument()

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
