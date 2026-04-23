package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.utils.DatabaseHelper
import com.zuoguan.bilimusickmp.models.Song
import kotbase.DataSource
import kotbase.Meta
import kotbase.MutableArray
import kotbase.MutableDocument
import kotbase.QueryBuilder
import kotbase.SelectResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SongRepositoryService {
    private val coll by lazy { DatabaseHelper.songCollection }

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags.asStateFlow()

    init {
        loadSongs()
    }

    fun loadSongs() {
        _songs.value = querySongs()
        _allTags.value = songs.value
            .flatMap { it.tags }
            .distinct()
            .sorted()
    }

    fun saveSong(song: Song, refresh: Boolean = true) {
        val doc = MutableDocument(song.id)
            .apply {
                setString("audioSource", song.audioSource.name)
                setString("title", song.title)
                setString("author", song.author)
                setString("pic", song.pic)
                setArray("tags", MutableArray(song.tags))
                setString("cid", song.cid)
                setString("neteaseId", song.neteaseId)
                setInt("lyricBias", song.lyricBias)
                setLong("ts", song.ts)
            }

        coll.save(doc)
        if (refresh) {
            loadSongs()
        }
    }

    fun removeSong(id: String) {
        val doc = coll.getDocument(id)
        doc?.let {
            coll.delete(it)
        }
        loadSongs()
    }

    private fun querySongs(): List<Song> {
        val query = QueryBuilder
            .select(
                SelectResult.expression(Meta.id).`as`("id"),
                SelectResult.property("audioSource"),
                SelectResult.property("title"),
                SelectResult.property("author"),
                SelectResult.property("pic"),
                SelectResult.property("tags"),
                SelectResult.property("cid"),
                SelectResult.property("neteaseId"),
                SelectResult.property("lyricBias"),
                SelectResult.property("ts")
            )
            .from(DataSource.collection(coll))

        return query.execute().mapNotNull { row ->
            Song().apply {
                id = row.getString("id") ?: return@mapNotNull null
                audioSource = row.getString("audioSource")
                    ?.let { runCatching { AudioSource.valueOf(it) }.getOrNull() }
                    ?: AudioSource.BILI_BILI
                title = row.getString("title") ?: ""
                author = row.getString("author") ?: ""
                pic = row.getString("pic") ?: ""
                tags = row.getArray("tags")
                    ?.toList()
                    ?.mapNotNull { it.toString() }
                    ?: emptyList()
                cid = row.getString("cid") ?: ""
                neteaseId = row.getString("neteaseId") ?: ""
                lyricBias = row.getInt("lyricBias")
                ts = row.getLong("ts")
            }
        }
    }
}