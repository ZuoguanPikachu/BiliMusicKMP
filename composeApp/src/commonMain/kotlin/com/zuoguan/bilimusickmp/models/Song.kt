package com.zuoguan.bilimusickmp.models

class Song {
    var id: String = ""
    var cid: String = ""
    var audioSource: AudioSource = AudioSource.BILI_BILI
    var title: String = ""
    var author: String = ""
    var tags: List<String> = emptyList()

    var lyricSource: LyricSource = LyricSource.NONE
    var lyricId: String = ""
    var lyricBias: Int = 0

    var coverSource: CoverSource = CoverSource.BILI_BILI
    var coverId: String = ""
    var pic: String = ""

    var ts: Long = 0
}