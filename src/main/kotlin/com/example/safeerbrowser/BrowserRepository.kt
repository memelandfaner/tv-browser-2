package com.example.safeerbrowser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Bookmark(
    val id: Long = 0,
    val title: String,
    val url: String,
    val icon: String = "⭐"
)

data class HistoryItem(
    val id: Long = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

class BrowserRepository(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "safeer_mobile.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_BOOKMARKS = "bookmarks"
        private const val COL_BM_ID = "id"
        private const val COL_BM_TITLE = "title"
        private const val COL_BM_URL = "url"
        private const val COL_BM_ICON = "icon"

        private const val TABLE_HISTORY = "history"
        private const val COL_HIST_ID = "id"
        private const val COL_HIST_TITLE = "title"
        private const val COL_HIST_URL = "url"
        private const val COL_HIST_TIME = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_BOOKMARKS (
                $COL_BM_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_BM_TITLE TEXT NOT NULL,
                $COL_BM_URL TEXT NOT NULL UNIQUE,
                $COL_BM_ICON TEXT DEFAULT '⭐'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_HISTORY (
                $COL_HIST_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_HIST_TITLE TEXT NOT NULL,
                $COL_HIST_URL TEXT NOT NULL,
                $COL_HIST_TIME INTEGER NOT NULL
            )
        """.trimIndent())

        insertDefaultBookmarks(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BOOKMARKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    private fun insertDefaultBookmarks(db: SQLiteDatabase) {
        val defaults = listOf(
            Bookmark(0, "Google Iskalnik", "https://www.google.com", "🔍"),
            Bookmark(0, "Xplore TV", "https://www.xploretv.si/home", "📡"),
            Bookmark(0, "YouTube", "https://www.youtube.com/tv", "📺"),
            Bookmark(0, "StreamNexus Filmi", "https://hydrahd.ws/", "🎬"),
            Bookmark(0, "ChatGPT AI", "https://chatgpt.com", "💬"),
            Bookmark(0, "RTV Slovenija", "https://www.rtvslo.si", "📰"),
            Bookmark(0, "Wikipedia", "https://sl.wikipedia.org", "📖"),
            Bookmark(0, "Reddit", "https://www.reddit.com", "📱"),
            Bookmark(0, "Speedtest", "https://www.speedtest.net", "⚡")
        )

        for (bm in defaults) {
            val cv = ContentValues().apply {
                put(COL_BM_TITLE, bm.title)
                put(COL_BM_URL, bm.url)
                put(COL_BM_ICON, bm.icon)
            }
            db.insertWithOnConflict(TABLE_BOOKMARKS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun getBookmarks(): List<Bookmark> {
        val list = mutableListOf<Bookmark>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_BOOKMARKS ORDER BY $COL_BM_ID ASC", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_BM_ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(COL_BM_TITLE))
                val url = cursor.getString(cursor.getColumnIndexOrThrow(COL_BM_URL))
                val icon = cursor.getString(cursor.getColumnIndexOrThrow(COL_BM_ICON))
                list.add(Bookmark(id, title, url, icon))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun addBookmark(title: String, url: String, icon: String = "⭐"): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_BM_TITLE, title)
            put(COL_BM_URL, url)
            put(COL_BM_ICON, icon)
        }
        val res = db.insertWithOnConflict(TABLE_BOOKMARKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return res != -1L
    }

    fun removeBookmark(url: String): Boolean {
        val db = writableDatabase
        val res = db.delete(TABLE_BOOKMARKS, "$COL_BM_URL = ?", arrayOf(url))
        return res > 0
    }

    fun isBookmarked(url: String): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM $TABLE_BOOKMARKS WHERE $COL_BM_URL = ?", arrayOf(url))
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun addHistory(title: String, url: String) {
        if (url.startsWith("about:") || url.isEmpty()) return
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_HIST_TITLE, title.ifEmpty { url })
            put(COL_HIST_URL, url)
            put(COL_HIST_TIME, System.currentTimeMillis())
        }
        db.insert(TABLE_HISTORY, null, cv)
    }

    fun getHistory(limit: Int = 100): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_HISTORY ORDER BY $COL_HIST_TIME DESC LIMIT $limit", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_HIST_ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(COL_HIST_TITLE))
                val url = cursor.getString(cursor.getColumnIndexOrThrow(COL_HIST_URL))
                val time = cursor.getLong(cursor.getColumnIndexOrThrow(COL_HIST_TIME))
                list.add(HistoryItem(id, title, url, time))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun clearHistory() {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, null, null)
    }
}
