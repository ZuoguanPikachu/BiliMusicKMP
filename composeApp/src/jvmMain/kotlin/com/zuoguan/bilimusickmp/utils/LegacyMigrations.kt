package com.zuoguan.bilimusickmp.utils

import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.services.PrefsFileContent
import com.zuoguan.bilimusickmp.services.syncJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * 迁移 v1 桌面的偏好数据（llm_config.json → preferences.json）。
 * 仅在 preferences.json 尚不存在时执行一次；旧文件保留为 .bak。
 */
fun migrateLegacyJvmPreferences() {
    val cfgDir = getAppConfigDir()
    val legacy = File(cfgDir, "llm_config.json")
    val target = File(cfgDir, "preferences.json")
    if (target.exists() || !legacy.exists()) return

    val text = legacy.readText(Charsets.UTF_8)
    val config = runCatching {
        syncJson.decodeFromString<LLMConfig>(text)
    }.getOrNull() ?: return

    val content = syncJson.encodeToString(
        PrefsFileContent(
            values = mapOf(
                "llm.apiKey" to config.apiKey,
                "llm.baseUrl" to config.baseUrl,
                "llm.modelName" to config.modelName
            ),
            syncUpdatedAt = 0L
        )
    )
    writeTextFileAtomic(target.absolutePath, content)
    legacy.renameTo(File(cfgDir, "llm_config.json.bak"))
}