package com.zuoguan.bilimusickmp.models

/** 播放器的状态；[Error] 表示播放失败，需要外部重新取地址或提示用户。 */
enum class PlaybackState {
    Playing,
    Paused,
    Stopped,
    Ended,
    Error
}