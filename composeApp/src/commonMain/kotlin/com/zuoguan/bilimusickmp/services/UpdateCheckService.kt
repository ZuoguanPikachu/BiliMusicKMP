package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.AppVersion
import com.zuoguan.bilimusickmp.utils.isNewerVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 一个可下载的新版本。 */
data class ReleaseInfo(
    /** 版本号，已去掉 tag 里可能存在的 `v` 前缀。 */
    val version: String,
    /** Release 页面地址，交给系统浏览器打开。 */
    val pageUrl: String,
    /** 更新说明，已截断，可能为 null。 */
    val notes: String?
)

/** 一次「检查更新」的结果。 */
sealed interface UpdateCheckResult {
    /** 有比当前更新的版本。 */
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult

    /** 当前已是最新版本。 */
    data object UpToDate : UpdateCheckResult

    /** 检查失败；[message] 可直接展示给用户。 */
    data class Failed(val message: String) : UpdateCheckResult
}

/**
 * 检查 GitHub Release 是否有新版本。
 *
 * 只查 `releases/latest` 这一个接口（不鉴权、不含草稿与预发布），因此每小时有次数上限；
 * 由用户手动点击触发，所以只发一次请求，不做重试与轮询：网络与限流等失败都收敛成
 * [UpdateCheckResult.Failed] 交给界面展示，不向上抛异常。
 *
 * 版本判定见 [isNewerVersion]，兼容 `v1.4.0` 这类 tag 写法。
 */
class UpdateCheckService {
    companion object {
        /** 仓库坐标，与项目远程仓库一致。 */
        const val REPO_SLUG = "ZuoguanPikachu/BiliMusicKMP"

        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$REPO_SLUG/releases/latest"

        /** 更新说明过长会把设置页撑得很长，只保留开头这么多字符。 */
        private const val NOTES_MAX_LENGTH = 500
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 查询最新 Release 并与 [currentVersion] 比较。
     *
     * @param currentVersion 当前版本号，默认取构建时由 Gradle 注入的 [AppVersion.NAME]。
     */
    suspend fun check(currentVersion: String = AppVersion.NAME): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                queryLatestRelease(currentVersion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                UpdateCheckResult.Failed("网络连接失败，请检查网络后重试")
            } catch (e: Exception) {
                UpdateCheckResult.Failed("更新信息解析失败：${e.message ?: "未知错误"}")
            }
        }

    private fun queryLatestRelease(currentVersion: String): UpdateCheckResult {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            // GitHub 要求请求必须带 User-Agent，否则一律返回 403
            .header("User-Agent", "BiliMusicKMP/$currentVersion")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return UpdateCheckResult.Failed(httpErrorMessage(response.code))
            }

            val release = json.decodeFromString<GitHubRelease>(response.body.string())
            val tag = release.tagName.trim()
            if (tag.isEmpty()) {
                return UpdateCheckResult.Failed("远端 Release 缺少版本号")
            }

            if (!isNewerVersion(tag, currentVersion)) return UpdateCheckResult.UpToDate

            return UpdateCheckResult.UpdateAvailable(
                ReleaseInfo(
                    version = tag.removePrefix("v").removePrefix("V"),
                    pageUrl = release.htmlUrl,
                    notes = formatNotes(release.body)
                )
            )
        }
    }

    /** 截断更新说明；内容为空时返回 null，界面据此不渲染说明区。 */
    private fun formatNotes(body: String?): String? {
        val text = body?.trim().orEmpty()
        if (text.isEmpty()) return null
        return if (text.length > NOTES_MAX_LENGTH) text.take(NOTES_MAX_LENGTH) + "…" else text
    }

    /** 把 HTTP 状态码翻译成用户能看懂的一句话。 */
    private fun httpErrorMessage(code: Int): String = when (code) {
        403, 429 -> "GitHub 接口访问过于频繁，请稍后再试"
        404 -> "仓库暂无 Release 记录"
        else -> "检查更新失败（HTTP $code）"
    }
}

/** `releases/latest` 响应中本项目用到的字段。 */
@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String? = null
)
