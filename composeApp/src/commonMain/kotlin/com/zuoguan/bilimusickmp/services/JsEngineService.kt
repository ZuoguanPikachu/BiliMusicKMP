package com.zuoguan.bilimusickmp.services

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.define
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


class JsEngineService(
    private val scriptFile: File,
) {
    private var engine = QuickJs.create(Dispatchers.Default)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = HttpClient()
    var isScriptLoaded = false
    val scriptAddedEvent = MutableSharedFlow<Boolean>()
    private val _scriptFlow = MutableStateFlow("")

    fun getScript(): Flow<String> = _scriptFlow.asStateFlow()

    init {
        defineFunctions()

        if (scriptFile.exists()) {
            val script = scriptFile.readText(Charsets.UTF_8)
            loadScript(script)
        }
    }

    fun defineFunctions() {
        engine.define("console") {
            function("log"){ args ->
                println(args.joinToString(" "))
            }
        }

        engine.define("http") {
            asyncFunction("get") { args ->
                val url = args[0].toString()
                val options : JsObject? = args.getOrNull(1) as JsObject?
                val headers = parseHeaders(options?.get("headers"))

                val result = runBlocking {
                    httpClient.request("GET", url, headers = headers)
                }

                JsObject(mapOf(
                    "status" to result.status,
                    "body" to result.body,
                    "headers" to result.headers
                ))
            }

            asyncFunction("post") { args ->
                val url = args[0].toString()
                val body = args[1]
                val options : JsObject? = args.getOrNull(2) as JsObject?
                val contentType: String = options?.get("contentType")?.toString() ?: ""
                val headers = parseHeaders(options?.get("headers"))

                val result = runBlocking {
                    httpClient.request("POST", url, body, contentType = contentType, headers = headers)
                }

                JsObject(mapOf(
                    "status" to result.status,
                    "body" to result.body,
                    "headers" to result.headers
                ))
            }

            asyncFunction("put") { args ->
                val url = args[0].toString()
                val body = args[1]
                val options : JsObject? = args.getOrNull(2) as JsObject?
                val contentType: String = options?.get("contentType")?.toString() ?: ""
                val headers = parseHeaders(options?.get("headers"))

                val result = runBlocking {
                    httpClient.request("PUT", url, body, contentType = contentType, headers = headers)
                }

                JsObject(mapOf(
                    "status" to result.status,
                    "body" to result.body,
                    "headers" to result.headers
                ))
            }
        }

        engine.define("crypto") {
            // SHA1
            function("sha1") { args ->
                digest("SHA-1", args[0].toString())
            }

            // SHA256
            function("sha256") { args ->
                digest("SHA-256", args[0].toString())
            }

            // MD5
            function("md5") { args ->
                digest("MD5", args[0].toString())
            }

            // HMAC-SHA1
            function("hmacSha1") { args ->
                hmac("HmacSHA1", args[0].toString(), args[1].toString())
            }

            // HMAC-SHA256
            function("hmacSha256") { args ->
                hmac("HmacSHA256", args[0].toString(), args[1].toString())
            }
        }

        engine.define("file"){
            function("readBytes"){ args ->
                val path = args[0].toString()
                File(path).readBytes()
            }
        }

        engine.define("time") {
            function("now"){
                System.currentTimeMillis() / 1000
            }
        }

        engine.define("url") {
            function("encode") { args ->
                URLEncoder
                    .encode(args[0].toString(), "UTF-8")
                    .replace("+", "%20")
            }

            function("decode") { args ->
                URLDecoder.decode(args[0].toString(), "UTF-8")
            }
        }

        engine.define("str") {
            function("encode") { args ->
                args[0].toString().toByteArray(Charsets.UTF_8)
            }
        }
    }

    fun loadScript(script: String) {
        _scriptFlow.value = script
        scope.launch {
            engine.evaluate<Any?>(script)
        }
        isScriptLoaded = true
    }

    suspend fun saveScript(script: String) {
        scriptFile.writeText(script)

        engine.close()
        engine = QuickJs.create(Dispatchers.Default)
        defineFunctions()
        loadScript(script)

        scriptAddedEvent.emit(true)
    }

    suspend fun execute(code: String) {
        engine.evaluate<Any?>(
            code
        )
    }

    suspend fun uploadFile(key: String, file: File): HttpResponse {
        val resp = engine.evaluate<JsObject>(
            """
                await upload("$key", file.readBytes("${file.toString().replace('\\', '/')}"))
            """.trimIndent()
        )

        return HttpResponse(
            (resp["status"] as Long).toInt(),
            resp["body"] as ByteArray,
            resp["headers"] as Map<String,String>
        )
    }

    suspend fun uploadString(key: String, content: String): HttpResponse {
        return uploadJson(key, content)
    }

    /** 上传 JSON/文本：内容经 JS 字符串转义后交给用户脚本的 upload(key, bytes)。 */
    suspend fun uploadJson(key: String, content: String): HttpResponse {
        val resp = engine.evaluate<JsObject>(
            """
                await upload("${jsEscape(key)}", str.encode("${jsEscape(content)}"))
            """.trimIndent()
        )

        return HttpResponse(
            (resp["status"] as Long).toInt(),
            resp["body"] as ByteArray,
            resp["headers"] as Map<String,String>
        )
    }

    /** 下载并解码为 UTF-8 文本；对象不存在（404）返回 null，其它错误抛异常。 */
    suspend fun downloadText(key: String): String? {
        val resp = download(key)
        return when (resp.status) {
            200 -> String(resp.body, Charsets.UTF_8)
            404 -> null
            else -> throw IllegalStateException("下载 $key 失败：HTTP ${resp.status}")
        }
    }

    suspend fun download(key: String): HttpResponse {
        val resp = engine.evaluate<JsObject>(
            """
                await download("$key")
            """.trimIndent()
        )

        return HttpResponse(
            (resp["status"] as Long).toInt(),
            resp["body"] as ByteArray,
            resp["headers"] as Map<String,String>
        )
    }

    fun close(){
        engine.close()
    }
}

fun parseHeaders(obj: Any?): Map<String,String>{
    if(obj == null) {
        return emptyMap()
    }

    return (obj as Map<*,*>).mapKeys { it.key.toString() }.mapValues { it.value.toString() }
}

class HttpClient {
    private val client = OkHttpClient()

    suspend fun request(
        method: String,
        url: String,
        body: Any? = null,
        contentType: String = "",
        headers: Map<String,String> = emptyMap()
    ): HttpResponse {
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)

            headers.forEach{(k,v) ->
                builder.addHeader(k, v)
            }

            when(method.uppercase()) {
                "GET" -> {
                    builder.get()
                }

                "POST" -> {
                    builder.post(body?.toRequestBody(contentType) ?: "".toRequestBody())
                }


                "PUT" -> {
                    builder.put(body?.toRequestBody(contentType) ?: "".toRequestBody())
                }
            }

            client
                .newCall(builder.build())
                .execute()
                .use { response ->
                    HttpResponse(
                        status = response.code,
                        body = response.body.bytes(),
                        headers = response.headers.toMap()
                    )
                }
        }

    }
}

data class HttpResponse(
    val status: Int,
    val body: ByteArray,
    val headers: Map<String,String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpResponse

        if (status != other.status) return false
        if (!body.contentEquals(other.body)) return false
        if (headers != other.headers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status
        result = 31 * result + body.contentHashCode()
        result = 31 * result + headers.hashCode()
        return result
    }
}

private fun Any.toRequestBody(contentType: String): RequestBody {
    var mediaType: MediaType

    return when (this) {
        is String -> {
            mediaType = if (contentType.isEmpty()) {
                "application/json".toMediaType()
            } else {
                contentType.toMediaType()
            }
            this.toRequestBody(mediaType)
        }

        is ByteArray -> {
            mediaType = if (contentType.isEmpty()) {
                "application/octet-stream".toMediaType()
            } else {
                contentType.toMediaType()
            }
            this.toRequestBody(mediaType)
        }

        else -> {
            throw IllegalArgumentException(
                "Unsupported body type: ${this::class}"
            )
        }
    }
}

private fun digest(algorithm: String, value: String): String {
    val bytes = value.encodeToByteArray()

    return MessageDigest
        .getInstance(algorithm)
        .digest(bytes)
        .joinToString("") {
            "%02x".format(it)
        }
}

private fun hmac(algorithm: String, key: String, data: String): String {
    val mac = Mac.getInstance(algorithm)
    mac.init(SecretKeySpec(key.encodeToByteArray(), algorithm))

    return mac.doFinal(data.encodeToByteArray()).joinToString(""){
        "%02x".format(it)
    }

}

/** 转义字符串，使其可作为 JS 双引号字符串字面量嵌入 evaluate 代码。 */
private fun jsEscape(value: String): String = buildString {
    for (c in value) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u0000' -> append("\\u0000")
            else -> append(c)
        }
    }
}