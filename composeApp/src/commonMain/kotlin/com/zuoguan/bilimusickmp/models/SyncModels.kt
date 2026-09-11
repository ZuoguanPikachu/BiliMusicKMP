package com.zuoguan.bilimusickmp.models

import kotlinx.serialization.Serializable

/**
 * 单首歌曲的同步载荷。
 *
 * - 不含 [Song.ts]：ts 是本地排序位次，顺序由 [SyncOrder] 单独同步。
 * - [updatedAt] 为内容版本号（LWW 依据），升级前的存量数据统一为 0（基线）。
 * - [deleted] 为 true 时表示墓碑（删除标记），其余字段无效。
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

/** 歌单顺序：有序歌曲 id 列表 + 顺序版本号。 */
@Serializable
data class SyncOrder(
    val updatedAt: Long = 0L,
    val ids: List<String> = emptyList()
)

/** 偏好设置同步载荷：仅含可同步键。 */
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

/** 一次提交产生的增量：只包含相对上一版本变更的歌曲 / 顺序 / 偏好。 */
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

/** 全量快照：引导新设备 / 增量链过长时兜底。 */
@Serializable
data class SyncSnapshot(
    val v: Long = 0L,
    val device: String = "",
    val ts: Long = 0L,
    val songs: List<SongDto> = emptyList(),
    val order: SyncOrder = SyncOrder(),
    val prefs: SyncPrefs = SyncPrefs()
)

/** 云同步涉及的常量（键前缀等）。 */
object SyncKeys {
    const val HEAD_KEY = "sync/v2/head.json"
    const val DELTA_PREFIX = "sync/v2/deltas/"
    const val SNAPSHOT_KEY = "sync/v2/snapshots/current.json"

    fun deltaKey(v: Long) = "$DELTA_PREFIX$v.json"

    /** 参与云同步的偏好键（未来新增可同步项只需在此追加）。 */
    val PREF_KEYS: Set<String> = setOf(
        "llm.apiKey",
        "llm.baseUrl",
        "llm.modelName"
    )

    /** 仅存本地的键前缀，绝不参与同步。 */
    const val LOCAL_KEY_PREFIX = "local."

    const val LOCAL_DEVICE_ID = "local.deviceId"
    const val LOCAL_HEAD_V = "local.headV"
    const val LOCAL_WATERMARK = "local.watermark"
    const val LOCAL_EVER_PUSHED = "local.everPushed"
}