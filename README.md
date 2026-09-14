# BiliMusic KMP

使用 Kotlin Multiplatform 开发的，以 BiliBili、酷狗音乐、网易云音乐 为音频源的音乐播放器。支持Android、Windows。

![screenshot](doc/screenshot.png)

## 依赖

Windows端的音乐播放依赖于VLC，所以需要在默认路径安装[VLC media player](https://www.videolan.org/vlc/)

## 功能

- [x] 媒体搜索

  除常规搜索外，还支持直接输入ID（B站：bv_id；网易云：song_id）、URL（原链接、短链接（可含其他文字））

- [x] 音乐播放

- [x] LLM提取歌曲信息（需符合OpenAI接口）：歌曲名称、歌手

  非常建议使用！便于从网易云获取歌曲ID、歌词、封面

  免费的大模型API：

  * [智谱GLM（链接含邀请码）](https://www.bigmodel.cn/login?icode=Pe+h7w7M3Gx9gG4r3CNzLEjPr3uHog9F4g5tjuOUqno=&from=invite&redirect=/)的glm-4.7-flash

  * [GPT_API_free](https://github.com/chatanywhere/GPT_API_free)

- [x] 歌词、封面

  从网易云音乐获取歌词、封面

- [x] Tag

  可以为每一首歌曲添加Tag，替代了歌单功能

- [x] 云同步（待优化）

  用自己的对象存储同步歌曲、顺序与偏好，需要写一段 JS 脚本。接口说明与示例见[云同步脚本](#云同步脚本)。

  js runtime、编写调试工具：[JSK](https://github.com/ZuoguanPikachu/JSK)

## 云同步脚本

在「设置 → 云同步脚本」里填入一段 JavaScript，用你自己的存储（对象存储、自建 HTTP 服务都行）保存同步数据。
应用会把歌曲、顺序与偏好打包成 JSON 写进这份存储，脚本只负责「按 key 存取字节」，不关心内容。

### 必须实现的两个函数

```javascript
// key 是带目录的路径，例如 "sync/v2/head.json"
function upload(key, bytes) {}   // 返回 { status, body, headers }

// 云端还没有这个对象时，返回的 status 要取 404
function download(key) {}        // 返回 { status, body, headers }
```

脚本里直接 `return http.put(...)` / `return http.get(...)` 就行，它们返回的正是这个结构。
返回值必须带 `status`（数字）、`body`（字节）、`headers`（对象），返回 `undefined` 会让同步直接报错。

### 容易踩的坑

* **`key` 是路径，不是文件名。**
  应用会用到 `sync/v2/head.json`、`sync/v2/snapshots/current.json`、`sync/v2/deltas/{版本}.json`。
  自建服务请把 `key` 当成不透明字符串按原样存取；只支持 `?filename=xxx` 这类扁平接口，或者会把目录层级丢掉再落盘的实现，
  轻则上传失败，重则不同对象互相覆盖。若要做文件落盘，记得校验解析后的路径仍在允许的目录内。
* **同一个 `key` 会被反复覆盖写。**
  `head.json` 与快照都是覆盖写，存储必须允许覆盖：不能返回 409，也不能配成不可变（immutable / WORM）存储。
* **下载未上传的对象要返回 404。**
  应用把 404 当成「云端还没有这份数据」，属正常情况（首次同步就靠它判断）；返回 200 + 空 body 会被当成解析失败，
  其它非 200 状态一律算同步失败。
* **上传失败的返回码应用暂不检查。**
  签名错误之类的上传失败不会当场报错，而是拖到之后——别的设备（或本地状态被清掉后）拉取时才会报「增量 N 不存在」。
  排错时可以先在脚本里 `console.log` 出返回码，确认上传真的成功了。
* **密钥都留在了存储里。**
  脚本本身只存在本地、不参与同步；但偏好里的 LLM 配置（含 API Key）与主题设置会随云同步一起上传，
  也就是这些东西会出现在你所配置的存储/服务里，别把这个 bucket 分享给别人。

### 宿主函数

<details>
<summary>引擎注入的可用函数</summary>

| 函数 | 说明 |
| --- | --- |
| `console.log(...)` | 打印日志（桌面端输出到控制台） |
| `http.get(url, { headers })` | GET 请求，返回 `{ status, body, headers }` |
| `http.post(url, body, { contentType, headers })` | POST 请求，返回同上 |
| `http.put(url, body, { contentType, headers })` | PUT 请求，返回同上 |
| `crypto.sha1 / sha256 / md5(text)` | 摘要，返回十六进制小写字符串 |
| `crypto.hmacSha1 / hmacSha256(key, text)` | HMAC，返回十六进制小写字符串 |
| `time.now()` | 当前 Unix 时间戳（秒） |
| `url.encode(text) / url.decode(text)` | URL 编解码 |
| `str.encode(text)` | 字符串转字节数组 |
| `file.readBytes(path)` | 读取本地文件 |

保存脚本后会重建 JS 引擎并立即触发一次同步。

</details>

### 示例：腾讯云 COS

<details>
<summary>COS 签名与读写（可直接改用）</summary>

填上自己的 `secretId` / `secretKey` / `region` / `bucket` 即可。

```javascript
const secretId = ""
const secretKey = ""
const region = ""
const bucket = ""
const host = `${bucket}.cos.${region}.myqcloud.com`
const baseUrl = `https://${host}`

function getKeyTime(expireSeconds=3600) {
    let start = time.now()
    let end = start + expireSeconds

    return `${start};${end}`
}

function buildAuthorization(
    method,
    pathname,
    headers,
    params={},
    expireSeconds=3600
) {
    method = method.toLowerCase()
    if (!pathname.startsWith("/")) {
        pathname = "/" + pathname
    }

    // 1. KeyTime
    let keyTime = getKeyTime(expireSeconds)

    // 2. SignKey
    let signKey = crypto.hmacSha1(secretKey, keyTime)

    // 3. Url Params
    let sortedParams = Object.entries(params)
        .map(([k,v]) => {
            return [
                url.encode(k.toLowerCase()),
                url.encode(String(v))
            ]
        })
        .sort((a,b) => a[0].localeCompare(b[0]))

    let urlParamList = sortedParams
        .map(item=>item[0])
        .join(";")

    let httpParameters = sortedParams
        .map(item => `${item[0]}=${item[1]}`)
        .join("&")

    // 4. Headers
    let headerMap = {}
    for(let [k,v] of Object.entries(headers)){
        headerMap[url.encode(k.toLowerCase())] = url.encode(String(v))
    }

    if(!headerMap["host"]){
        headerMap["host"] = url.encode(host)
    }

    let sortedHeaders = Object
        .entries(headerMap)
        .sort((a,b) => a[0].localeCompare(b[0]))

    let headerList = sortedHeaders
        .map(item=>item[0])
        .join(";")

    let httpHeaders = sortedHeaders
        .map(item => `${item[0]}=${item[1]}`)
        .join("&")

    // 5. HttpString
    let httpString = `${method}\n${pathname}\n${httpParameters}\n${httpHeaders}\n`

    // 6. StringToSign
    let stringToSign = `sha1\n${keyTime}\n${crypto.sha1(httpString)}\n`

    // 7. Signature
    let signature = crypto.hmacSha1(signKey, stringToSign)

    // 8. Authorization
    return (
        `q-sign-algorithm=sha1` +
        `&q-ak=${secretId}` +
        `&q-sign-time=${keyTime}` +
        `&q-key-time=${keyTime}` +
        `&q-header-list=${headerList}` +
        `&q-url-param-list=${urlParamList}` +
        `&q-signature=${signature}`
    )
}

function upload(
    key,
    data,
    contentType="application/octet-stream",
    storageClass="STANDARD",
    expireSeconds=3600
){
    let pathname = key.startsWith("/") ? key : "/" + key
    key = key.replace(/^\//, "")

    let contentLength = data.length
    let headers = {
        "Host": host,
        "Content-Type": contentType,
        "Content-Length": String(contentLength),
        "x-cos-storage-class": storageClass
    }

    let signHeaders = {
        "host":host,
        "content-type": contentType,
        "content-length": String(contentLength)
    }
    if(storageClass !== "STANDARD"){
        signHeaders["x-cos-storage-class"] = storageClass
    }

    headers.Authorization = buildAuthorization("PUT", pathname, signHeaders, {}, expireSeconds)

    return http.put(`${baseUrl}/${key}`, data, { headers: headers})
}


function download(key, expireSeconds=3600) {
    let pathname = key.startsWith("/") ? key : "/" + key
    key = key.replace(/^\//, "")

    let headers = {"Host": host}
    headers.Authorization = buildAuthorization("GET", pathname, headers, {}, expireSeconds)

    return http.get(`${baseUrl}/${key}`, { headers: headers})
}
```

</details>
