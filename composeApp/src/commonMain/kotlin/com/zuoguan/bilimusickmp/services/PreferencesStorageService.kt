package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.SyncPrefs
import kotlinx.coroutines.flow.Flow

/**
 * 跨平台共用的偏好存储抽象：按键读写字符串 / 数字 / 布尔，并支持订阅变化。
 *
 * 实现为 [JsonPreferencesStorageService]，所有值统一以字符串形式落盘。
 *
 * - 需要云同步的键在写入时推进 [syncUpdatedAt]，作为增量同步的版本依据；
 * - 键名以 "local." 开头的偏好只保存在本地，不参与云同步。
 */
interface PreferencesStorageService {

    // ---------- 字符串 ----------

    fun observeString(key: String): Flow<String?>

    suspend fun getString(key: String): String?

    suspend fun putString(key: String, value: String?)

    // ---------- 数字 ----------

    fun observeLong(key: String): Flow<Long>

    suspend fun getLong(key: String, default: Long = 0L): Long

    suspend fun putLong(key: String, value: Long)

    // ---------- 布尔 ----------

    fun observeBoolean(key: String): Flow<Boolean>

    suspend fun getBoolean(key: String, default: Boolean = false): Boolean

    suspend fun putBoolean(key: String, value: Boolean)

    // ---------- 云同步支持 ----------

    /** 可同步键的最新版本号（本地变更时递增）。 */
    suspend fun syncUpdatedAt(): Long

    /**
     * 可同步键版本号的变化流。
     *
     * 本地修改任一可同步键、或应用了云端偏好时都会发射；云同步服务据此在偏好变更后
     * 及时推送，而不是等下一次歌曲变更顺带带上。
     */
    fun observeSyncUpdatedAt(): Flow<Long>

    /** 当前可同步键的载荷。 */
    suspend fun syncablePayload(): SyncPrefs

    /**
     * 应用云端偏好（LWW：仅当 remote.updatedAt 不小于本地时生效）。
     * @return 实际应用到的 updatedAt；未应用返回 [Long.MIN_VALUE]。
     */
    suspend fun applySyncable(prefs: SyncPrefs): Long
}