package com.zuoguan.bilimusickmp.services

import com.zuoguan.bilimusickmp.models.AudioSource
import com.zuoguan.bilimusickmp.models.CoverSource
import com.zuoguan.bilimusickmp.models.LyricSource
import com.zuoguan.bilimusickmp.utils.DatabaseHelper
import com.zuoguan.bilimusickmp.models.Song
import kotbase.DataSource
import kotbase.Meta
import kotbase.MutableArray
import kotbase.MutableDocument
import kotbase.QueryBuilder
import kotbase.SelectResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

class SongRepositoryService(
    private val databaseDir: File,
    private val engine: JsEngineService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coll by lazy { DatabaseHelper.songCollection }

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags.asStateFlow()

    init {
        scope.launch {
            if (engine.isScriptLoaded) {
                val resp = engine.download("songs-db.md5")
                if (resp.status == 200) {
                    val remoteSongsDBMD5 = String(resp.body, Charsets.UTF_8)
                    val songsDBMD5 = getSongsDBMD5()

                    if (songsDBMD5 != remoteSongsDBMD5) {
                        downloadDBFiles()
                    }
                }
            }

            loadSongs()

            engine.scriptAddedEvent.collect {
                val resp = engine.download("songs-db.md5")
                if (resp.status == 404) {
                    uploadDBFiles()
                }
            }
        }
    }

    fun getSongsDBMD5(): String {
        val dbFile = File(databaseDir, "db.sqlite3-wal")
        val digest = MessageDigest.getInstance("MD5").digest(dbFile.readBytes())

        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun downloadDBFiles() {
        var resp = engine.download("db.sqlite3")
        File(databaseDir, "db.sqlite3").writeBytes(resp.body)

        resp = engine.download("db.sqlite3-shm")
        File(databaseDir, "db.sqlite3-shm").writeBytes(resp.body)

        resp = engine.download("db.sqlite3-wal")
        File(databaseDir, "db.sqlite3-wal").writeBytes(resp.body)
    }

    suspend fun uploadDBFiles() {
        engine.uploadFile("db.sqlite3", File(databaseDir, "db.sqlite3"))
        engine.uploadFile("db.sqlite3-shm", File(databaseDir, "db.sqlite3-shm"))
        engine.uploadFile("db.sqlite3-wal", File(databaseDir, "db.sqlite3-wal"))

        engine.uploadString("songs-db.md5", getSongsDBMD5())
    }


    fun loadSongs() {
        _songs.value = querySongs()
        _allTags.value = songs.value
            .flatMap { it.tags }
            .distinct()
            .sorted()
    }

    suspend fun saveSong(song: Song, refresh: Boolean = true) {
        val doc = MutableDocument(song.id)
            .apply {
                setString("cid", song.cid)
                setString("audioSource", song.audioSource.name)
                setString("title", song.title)
                setString("author", song.author)
                setArray("tags", MutableArray(song.tags))
                setString("lyricSource", song.lyricSource.name)
                setString("lyricId", song.lyricId)
                setInt("lyricBias", song.lyricBias)
                setString("coverSource", song.coverSource.name)
                setString("coverId", song.coverId)
                setString("pic", song.pic)
                setLong("ts", song.ts)
            }

        coll.save(doc)
        if (refresh) {
            loadSongs()
            uploadDBFiles()
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
                SelectResult.property("cid"),
                SelectResult.property("audioSource"),
                SelectResult.property("title"),
                SelectResult.property("author"),
                SelectResult.property("tags"),
                SelectResult.property("lyricSource"),
                SelectResult.property("lyricId"),
                SelectResult.property("lyricBias"),
                SelectResult.property("coverSource"),
                SelectResult.property("coverId"),
                SelectResult.property("pic"),
                SelectResult.property("ts")
            )
            .from(DataSource.collection(coll))

        return query.execute().mapNotNull { row ->
            Song().apply {
                id = row.getString("id") ?: return@mapNotNull null
                cid = row.getString("cid") ?: ""
                audioSource = row.getString("audioSource")
                    ?.let { runCatching { AudioSource.valueOf(it) }.getOrNull() }
                    ?: AudioSource.BILI_BILI
                title = row.getString("title") ?: ""
                author = row.getString("author") ?: ""
                tags = row.getArray("tags")
                    ?.toList()
                    ?.mapNotNull { it.toString() }
                    ?: emptyList()
                lyricSource = row.getString("lyricSource")
                    ?.let { runCatching { LyricSource.valueOf(it) }.getOrNull() }
                    ?: LyricSource.NONE
                lyricId = row.getString("lyricId") ?: ""
                lyricBias = row.getInt("lyricBias")
                coverSource = row.getString("coverSource")
                    ?.let { runCatching { CoverSource.valueOf(it) }.getOrNull() }
                    ?: CoverSource.NONE
                coverId = row.getString("coverId") ?: ""
                pic = row.getString("pic") ?: ""
                ts = row.getLong("ts")
            }
        }
    }
}