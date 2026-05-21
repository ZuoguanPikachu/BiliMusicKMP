package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SongEditService {
    private val _editingSongInfo = MutableStateFlow<EditingSongInfo?>(null)
    val editingSongInfo: StateFlow<EditingSongInfo?> = _editingSongInfo

    fun editSong(song: Song, from: String) {
        _editingSongInfo.value = EditingSongInfo(song = song, from = from)
    }
}

data class EditingSongInfo(
    val song: Song,
    val from: String
)