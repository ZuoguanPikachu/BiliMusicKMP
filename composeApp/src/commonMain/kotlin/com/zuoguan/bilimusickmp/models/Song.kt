package com.zuoguan.bilimusickmp.models

class Song {
    var id: String = ""

    var audioSource: AudioSource = AudioSource.BILI_BILI
    var title: String = ""
    var author: String = ""
    var pic: String = ""
    var tags: List<String> = emptyList()
    var cid: String = ""

    var lyricSource: LyricSource = LyricSource.NONE
    var lyricId: String = ""
    var lyricBias: Int = 0
    var ts: Long = 0
}