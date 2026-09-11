package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.SyncPrefs
import kotlinx.coroutines.flow.Flow

/**
 * 通用偏好存储：按键存取字符串 / 数字 / 布尔，支持订阅变化。
 *
 * 设计目标：
 * - 双平台共用同一套实现（[JsonPreferencesStorageService]），消除平台重复代码；
 * - 未来新增偏好项只需存取新 key，无需改动接口与实现；
 * - 需要云同步的键写入时自动推进 [syncUpdatedAt]，参与 v2 增量同步。
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

    /** 当前可同步键的载荷。 */
    suspend fun syncablePayload(): SyncPrefs

    /**
     * 应用云端偏好（LWW：仅当 remote.updatedAt 不小于本地时生效）。
     * @return 实际应用到的 updatedAt；未应用返回 [Long.MIN_VALUE]。
     */
    suspend fun applySyncable(prefs: SyncPrefs): Long
}