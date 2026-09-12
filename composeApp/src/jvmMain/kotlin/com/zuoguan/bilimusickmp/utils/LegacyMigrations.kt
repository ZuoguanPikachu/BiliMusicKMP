package com.zuoguan.bilimusickmp.utils

import com.zuoguan.bilimusickmp.models.LLMConfig
import com.zuoguan.bilimusickmp.services.PrefsFileContent
import com.zuoguan.bilimusickmp.services.syncJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * 把 v1 桌面版的 LLM 配置从 llm_config.json 迁移到统一偏好存储 preferences.json。
 *
 * 一次性迁移：只在 preferences.json 不存在且 llm_config.json 存在时执行，因此最多生效一次。
 *
 * - 读出源文件里的 apiKey / baseUrl / modelName，写成新的偏好格式
 * - 源文件重命名为 llm_config.json.bak 保留
 *
 * 整个过程被 try/catch 包住：这个函数在 Koin 的 single 工厂里执行，
 * 任何异常都会导致依赖注入失败、应用起不来，而迁移失败本身并不致命。
 */
fun migrateLegacyJvmPreferences() {
    try {
        val cfgDir = getAppConfigDir()
        val legacy = File(cfgDir, "llm_config.json")
        val target = File(cfgDir, "preferences.json")
        if (target.exists() || !legacy.exists()) return

        val text = legacy.readText(Charsets.UTF_8)
        val config = syncJson.decodeFromString<LLMConfig>(text)

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

        // 旧文件改名保留为 .bak；改名失败只提示，新偏好已写好，不影响使用
        if (!legacy.renameTo(File(cfgDir, "llm_config.json.bak"))) {
            println("旧配置重命名失败（不影响使用）: ${legacy.absolutePath}")
        }
    } catch (e: Exception) {
        println("旧版偏好迁移失败，已跳过: ${e.message}")
    }
}