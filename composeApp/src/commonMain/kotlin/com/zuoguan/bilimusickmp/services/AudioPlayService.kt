package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.PlayMode
import com.zuoguan.bilimusickmp.models.PlaybackState
import com.zuoguan.bilimusickmp.models.TrackInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * 平台无关的播放接口：Android 用 ExoPlayer、桌面端用 VLC 各自实现它，
 * 上层 ViewModel 只依赖本接口，切换平台不需要改动调用方。
 *
 * 约定：
 * - 状态流与 [playlist] 都是只读快照，UI 订阅后自行决定如何呈现；
 * - 挂起方法不阻塞调用方线程，且可被取消；
 * - 队列为空、没有当前曲目或时长未知时，切歌与跳转静默忽略，不抛异常。
 */
interface AudioPlayService {

    /** 当前播放状态，UI 据此切换播放/暂停按钮与进度条可用性。 */
    val state: StateFlow<PlaybackState>
    /** 当前曲目；停止播放后为 null。 */
    val currentTrack: StateFlow<TrackInfo?>
    /** 当前播放模式，默认顺序播放。 */
    val playMode: StateFlow<PlayMode>
    /** 播放队列快照；切歌顺序由它和 [playMode] 共同决定。 */
    val playlist: List<TrackInfo>

    /** 整体替换播放队列；只影响后续切歌，不打断正在播放的曲目。 */
    suspend fun updatePlaylist(list: List<TrackInfo>)

    /** 按 顺序 → 随机 → 单曲循环 循环切换。 */
    fun togglePlayMode()

    /** 当前播放进度，取值 0f..1f，供进度条使用。 */
    val position: StateFlow<Float>
    /** 当前播放位置，单位毫秒。 */
    val time: StateFlow<Long>
    /** 当前曲目总时长，单位毫秒；未知时为 0。 */
    val duration: StateFlow<Long>

    /** 播放指定曲目：会调用 [TrackInfo.urlProvider] 取地址，并复位进度、更新 [currentTrack]。 */
    suspend fun play(track: TrackInfo)
    /** 暂停并保留播放位置。 */
    suspend fun pause()
    /** 从暂停处继续播放。 */
    suspend fun resume()
    /** 停止播放，复位进度并清空当前曲目。 */
    suspend fun stop()
    /** 按比例跳转，[position] 超出 0f..1f 时裁剪到边界。 */
    suspend fun seek(position: Float)
    /** 按绝对时间跳转，[time] 单位为毫秒；总时长未知时不生效。 */
    suspend fun seekMs(time: Long)

    /**
     * 切到下一首：顺序模式取队列下一项（末尾回到开头），随机模式从其余曲目里挑一首，
     * 单曲循环则重播当前曲目；没有当前曲目或队列为空时不做任何事。
     */
    suspend fun playNext()
    /** 切到上一首：顺序模式取队列前一项（开头回到末尾）；无当前曲目或队列为空时不做任何事。 */
    suspend fun playPrevious()

    /** 释放底层播放器资源；默认空实现，供无需释放的平台沿用。 */
    fun close() {}
}