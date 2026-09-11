package com.zuoguan.bilimusickmp.utils

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zuoguan.bilimusickmp.services.PrefsFileContent
import com.zuoguan.bilimusickmp.services.syncJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import java.io.File

private val Context.legacyDataStore by preferencesDataStore(name = "settings")

/**
 * 迁移 v1 Android 的偏好数据（DataStore "settings" → preferences.json）。
 * 仅在 preferences.json 尚不存在时执行一次；迁移后 DataStore 原样保留不再读取。
 */
fun migrateLegacyAndroidPreferences(context: Context) {
    val target = File(context.filesDir, "preferences.json")
    if (target.exists()) return

    runBlocking {
        val legacy = context.legacyDataStore.data.first()
        val apiKey = legacy[stringPreferencesKey("llm_api_key")]
        val baseUrl = legacy[stringPreferencesKey("llm_base_url")]
        val modelName = legacy[stringPreferencesKey("llm_model_name")]
        if (apiKey == null && baseUrl == null && modelName == null) return@runBlocking

        val content = syncJson.encodeToString(
            PrefsFileContent(
                values = buildMap {
                    if (apiKey != null) put("llm.apiKey", apiKey)
                    if (baseUrl != null) put("llm.baseUrl", baseUrl)
                    if (modelName != null) put("llm.modelName", modelName)
                },
                syncUpdatedAt = 0L
            )
        )
        writeTextFileAtomic(target.absolutePath, content)
    }
}