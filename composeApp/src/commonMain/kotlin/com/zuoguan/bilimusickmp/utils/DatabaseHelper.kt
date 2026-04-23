package com.zuoguan.bilimusickmp.utils

import kotbase.Collection
import kotbase.Database
import kotbase.DatabaseConfiguration

object DatabaseHelper {

    private const val DB_NAME = "songs-db"
    private const val SONG_SCOPE = "music"
    private const val SONG_COLLECTION = "songs"

    val database: Database by lazy {
        Database(DB_NAME, DatabaseConfiguration())
    }

    val songCollection: Collection by lazy {
        database.getCollection(SONG_COLLECTION, SONG_SCOPE) ?: database.createCollection(SONG_COLLECTION, SONG_SCOPE)
    }
}