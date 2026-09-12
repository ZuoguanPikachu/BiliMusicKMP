package com.zuoguan.bilimusickmp.models

/**
 * 播放模式，决定一首歌播完后如何选择下一首。
 *
 * - [SEQUENTIAL]：按列表顺序；
 * - [SHUFFLE]：随机；
 * - [SINGLE_LOOP]：单曲循环。
 */
enum class PlayMode {
    SEQUENTIAL,
    SHUFFLE,
    SINGLE_LOOP
}
