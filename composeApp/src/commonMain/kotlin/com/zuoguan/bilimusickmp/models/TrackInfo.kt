package com.zuoguan.bilimusickmp.models

import androidx.compose.runtime.Immutable


/**
 * 正在播放的曲目在运行时的视图。
 *
 * 音频地址与歌词都通过挂起函数按需获取：播放地址会过期，歌词体积较大，
 * 都不适合在构造时就取好。
 *
 * 相等性覆盖「用于展示的字段」（id、歌名、歌手、封面、音源、来源页面、歌词偏移）
 * 以及**歌词身份**（[lyricSource] + [lyricId]），不含两个 provider 闭包：闭包只能按
 * 引用比较，而同一首歌换了闭包实例并不代表内容有变化。
 *
 * 展示字段必须参与比较，否则同一首歌以不同元数据重新播放时（例如在搜索页试听后编辑
 * 信息再加入歌单，然后从歌单播放），`MutableStateFlow` 会因为「与旧值相等」丢掉这次赋值，
 * 播放条的歌名/歌手/封面就停在旧值上。
 *
 * 歌词身份也必须参与比较：只把歌词源从酷狗换成网易云时歌名/封面都没动、闭包又比不了，
 * 不带上这两个字段这次变更就完全不外发，歌词页会一直显示旧来源的歌词。
 *
 * @property urlProvider 拉取音频播放地址。
 * @property playSource 决定本曲播完后从哪里取下一首。
 * @property lyricSource 歌词来自哪个平台；[LyricSource.NONE] 表示没有歌词。
 * @property lyricId 歌词在 [lyricSource] 平台内的歌曲 id，没有歌词时为空串。
 * @property lyricsProvider 拉取歌词；使用方会按 [LyricLine.timeMs] 排序后再展示。
 * @property lyricBias 歌词时间偏移，单位毫秒。
 */
@Immutable
class TrackInfo(
    val id: String,
    val title: String,
    val author: String,
    val pic: String,
    val audioSource: AudioSource,
    val urlProvider: suspend () -> String,
    val playSource: PlaySource,
    val lyricSource: LyricSource,
    val lyricId: String,
    val lyricsProvider: suspend () -> List<LyricLine>,
    val lyricBias: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackInfo) return false
        return id == other.id &&
                title == other.title &&
                author == other.author &&
                pic == other.pic &&
                audioSource == other.audioSource &&
                playSource == other.playSource &&
                lyricSource == other.lyricSource &&
                lyricId == other.lyricId &&
                lyricBias == other.lyricBias
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + pic.hashCode()
        result = 31 * result + audioSource.hashCode()
        result = 31 * result + playSource.hashCode()
        result = 31 * result + lyricSource.hashCode()
        result = 31 * result + lyricId.hashCode()
        result = 31 * result + lyricBias
        return result
    }

    override fun toString(): String = "TrackInfo(id=$id, title=$title)"
}
