package com.zuoguan.bilimusickmp.utils

import kotbase.Collection
import kotbase.Database
import kotbase.DatabaseConfiguration

/** 应用内共享的 Kotbase 数据库访问入口，实例均为懒初始化。 */
object DatabaseHelper {

    private const val DB_NAME = "songs-db"
    private const val SONG_SCOPE = "music"
    private const val SONG_COLLECTION = "songs"

    /** 歌曲库所在的数据库。 */
    val database: Database by lazy {
        Database(DB_NAME, DatabaseConfiguration())
    }

    /** 歌曲集合；集合尚不存在时自动创建。 */
    val songCollection: Collection by lazy {
        database.getCollection(SONG_COLLECTION, SONG_SCOPE) ?: database.createCollection(SONG_COLLECTION, SONG_SCOPE)
    }
}