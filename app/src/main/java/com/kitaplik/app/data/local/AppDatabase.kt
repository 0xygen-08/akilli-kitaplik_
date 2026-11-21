package com.kitaplik.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Yerel SQLite veritabanı yöneticisi.
 * Hem kullanıcı tanımlı listeleri (book_lists) hem de belgelere ait kayıtları (books)
 * gecikmesiz olarak sunar.
 * "filePath TEXT NOT NULL UNIQUE" kısıtlaması sayesinde aynı belge asla iki farklı listede bulunamaz.
 */
class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "akilli_kitaplik.db", null, 2),
    BookDao {

    private val listsFlow = MutableStateFlow<List<BookListEntity>>(emptyList())
    private val booksFlow = MutableStateFlow<List<BookEntity>>(emptyList())

    init {
        setWriteAheadLoggingEnabled(true)
        ensureDefaultListExists()
        listsFlow.value = queryAllListsInternal()
        booksFlow.value = queryAllBooksInternal()
    }

    override fun onCreate(db: SQLiteDatabase) {
        createListsTable(db)
        insertDefaultBooksList(db)

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                listId INTEGER NOT NULL DEFAULT 1,
                title TEXT NOT NULL,
                author TEXT,
                publisher TEXT,
                isbn TEXT,
                filePath TEXT NOT NULL UNIQUE,
                format TEXT NOT NULL,
                totalPages INTEGER NOT NULL,
                lastReadPage INTEGER NOT NULL DEFAULT 0,
                lastReadTimestamp INTEGER NOT NULL DEFAULT 0,
                addedTimestamp INTEGER NOT NULL DEFAULT 0,
                fileSizeBytes INTEGER NOT NULL DEFAULT 0,
                isManuallyAdded INTEGER NOT NULL DEFAULT 0,
                detectionScore INTEGER NOT NULL DEFAULT 0,
                detectionReason TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ignored_files (
                filePath TEXT PRIMARY KEY NOT NULL,
                removedAtTimestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createListsTable(db)
            insertDefaultBooksList(db)
            runCatching {
                db.execSQL("ALTER TABLE books ADD COLUMN listId INTEGER NOT NULL DEFAULT 1")
            }
        }
    }

    private fun createListsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS book_lists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                minPages INTEGER NOT NULL DEFAULT 1,
                maxPages INTEGER NOT NULL DEFAULT 0,
                filenameWhitelist TEXT NOT NULL DEFAULT '',
                filenameBlacklist TEXT NOT NULL DEFAULT '',
                firstPagesKeywords TEXT NOT NULL DEFAULT '',
                useDefaultBookHeuristics INTEGER NOT NULL DEFAULT 0,
                matchBothFilenameAndFirstPages INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    private fun insertDefaultBooksList(db: SQLiteDatabase) {
        val defaultList = BookListEntity.createDefaultBooksList()
        db.insertWithOnConflict(
            "book_lists",
            null,
            defaultList.toContentValues(includeId = true),
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    private fun ensureDefaultListExists() {
        val currentLists = queryAllListsInternal()
        if (currentLists.isEmpty()) {
            insertDefaultBooksList(writableDatabase)
        }
    }

    fun bookDao(): BookDao = this

    override fun observeAllLists(): Flow<List<BookListEntity>> = listsFlow.asStateFlow()

    override suspend fun getAllListsSnapshot(): List<BookListEntity> = withContext(Dispatchers.IO) {
        queryAllListsInternal()
    }

    override suspend fun getListById(listId: Long): BookListEntity? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT * FROM book_lists WHERE id = ? LIMIT 1",
            arrayOf(listId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBookListEntity() else null
        }
    }

    override suspend fun insertBookList(bookList: BookListEntity): Long = withContext(Dispatchers.IO) {
        val id = writableDatabase.insertWithOnConflict(
            "book_lists",
            null,
            bookList.toContentValues(includeId = bookList.id > 0),
            SQLiteDatabase.CONFLICT_REPLACE
        )
        refreshListsFlow()
        id
    }

    override suspend fun updateBookList(bookList: BookListEntity) = withContext(Dispatchers.IO) {
        writableDatabase.update(
            "book_lists",
            bookList.toContentValues(includeId = false),
            "id = ?",
            arrayOf(bookList.id.toString())
        )
        refreshListsFlow()
    }

    override suspend fun deleteBookList(listId: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("books", "listId = ?", arrayOf(listId.toString()))
            db.delete("book_lists", "id = ?", arrayOf(listId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        ensureDefaultListExists()
        refreshListsFlow()
        refreshBooksFlow()
    }

    override fun observeAllBooks(): Flow<List<BookEntity>> = booksFlow.asStateFlow()

    override suspend fun getAllBooksSnapshot(): List<BookEntity> = withContext(Dispatchers.IO) {
        queryAllBooksInternal()
    }

    override suspend fun getBooksByListIdSnapshot(listId: Long): List<BookEntity> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<BookEntity>()
            readableDatabase.rawQuery(
                "SELECT * FROM books WHERE listId = ? ORDER BY lastReadTimestamp DESC, title COLLATE NOCASE ASC",
                arrayOf(listId.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursor.toBookEntity())
                }
            }
            list
        }

    override suspend fun getBookById(bookId: Long): BookEntity? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT * FROM books WHERE id = ? LIMIT 1",
            arrayOf(bookId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBookEntity() else null
        }
    }

    override suspend fun getBookByPath(path: String): BookEntity? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT * FROM books WHERE filePath = ? LIMIT 1",
            arrayOf(path)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBookEntity() else null
        }
    }

    override suspend fun insertBook(book: BookEntity): Long = withContext(Dispatchers.IO) {
        val id = writableDatabase.insertWithOnConflict(
            "books",
            null,
            book.toContentValues(),
            SQLiteDatabase.CONFLICT_IGNORE
        )
        refreshBooksFlow()
        id
    }

    override suspend fun insertBooks(books: List<BookEntity>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (book in books) {
                db.insertWithOnConflict(
                    "books",
                    null,
                    book.toContentValues(),
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshBooksFlow()
    }

    override suspend fun updateReadingProgress(
        bookId: Long,
        pageIndex: Int,
        totalPages: Int,
        timestamp: Long
    ) = withContext(Dispatchers.IO) {
        val sql = if (totalPages > 0) {
            "UPDATE books SET lastReadPage = ?, totalPages = ?, lastReadTimestamp = ? WHERE id = ?"
        } else {
            "UPDATE books SET lastReadPage = ?, lastReadTimestamp = ? WHERE id = ?"
        }
        val args: Array<Any> = if (totalPages > 0) {
            arrayOf(pageIndex, totalPages, timestamp, bookId)
        } else {
            arrayOf(pageIndex, timestamp, bookId)
        }
        writableDatabase.execSQL(sql, args)
        refreshBooksFlow()
    }

    override suspend fun updateBookFilePath(bookId: Long, newPath: String) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("filePath", newPath)
            }
            writableDatabase.update("books", values, "id = ?", arrayOf(bookId.toString()))
            refreshBooksFlow()
        }

    override suspend fun deleteBookById(bookId: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete("books", "id = ?", arrayOf(bookId.toString()))
        refreshBooksFlow()
    }

    override suspend fun insertIgnoredFile(ignoredFile: IgnoredFileEntity) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("filePath", ignoredFile.filePath)
                put("removedAtTimestamp", ignoredFile.removedAtTimestamp)
            }
            writableDatabase.insertWithOnConflict(
                "ignored_files",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            Unit
        }

    override suspend fun removeIgnoredFile(path: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("ignored_files", "filePath = ?", arrayOf(path))
        Unit
    }

    override suspend fun getAllIgnoredFilePaths(): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT filePath FROM ignored_files", null).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
        }
        result
    }

    private fun refreshListsFlow() {
        listsFlow.value = queryAllListsInternal()
    }

    private fun refreshBooksFlow() {
        booksFlow.value = queryAllBooksInternal()
    }

    private fun queryAllListsInternal(): List<BookListEntity> {
        val list = mutableListOf<BookListEntity>()
        readableDatabase.rawQuery(
            "SELECT * FROM book_lists ORDER BY createdAt ASC, id ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toBookListEntity())
            }
        }
        return list
    }

    private fun queryAllBooksInternal(): List<BookEntity> {
        val list = mutableListOf<BookEntity>()
        readableDatabase.rawQuery(
            "SELECT * FROM books ORDER BY lastReadTimestamp DESC, title COLLATE NOCASE ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toBookEntity())
            }
        }
        return list
    }

    private fun BookListEntity.toContentValues(includeId: Boolean): ContentValues {
        return ContentValues().apply {
            if (includeId && id > 0) put("id", id)
            put("name", name)
            put("minPages", minPages)
            put("maxPages", maxPages)
            put("filenameWhitelist", filenameWhitelist)
            put("filenameBlacklist", filenameBlacklist)
            put("firstPagesKeywords", firstPagesKeywords)
            put("useDefaultBookHeuristics", if (useDefaultBookHeuristics) 1 else 0)
            put("matchBothFilenameAndFirstPages", if (matchBothFilenameAndFirstPages) 1 else 0)
            put("createdAt", createdAt)
        }
    }

    private fun Cursor.toBookListEntity(): BookListEntity {
        return BookListEntity(
            id = getLong(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            minPages = getInt(getColumnIndexOrThrow("minPages")),
            maxPages = getInt(getColumnIndexOrThrow("maxPages")),
            filenameWhitelist = getString(getColumnIndexOrThrow("filenameWhitelist")),
            filenameBlacklist = getString(getColumnIndexOrThrow("filenameBlacklist")),
            firstPagesKeywords = getString(getColumnIndexOrThrow("firstPagesKeywords")),
            useDefaultBookHeuristics = getInt(getColumnIndexOrThrow("useDefaultBookHeuristics")) != 0,
            matchBothFilenameAndFirstPages = getInt(getColumnIndexOrThrow("matchBothFilenameAndFirstPages")) != 0,
            createdAt = getLong(getColumnIndexOrThrow("createdAt"))
        )
    }

    private fun BookEntity.toContentValues(): ContentValues {
        return ContentValues().apply {
            if (id > 0) put("id", id)
            put("listId", listId)
            put("title", title)
            put("author", author)
            put("publisher", publisher)
            put("isbn", isbn)
            put("filePath", filePath)
            put("format", format.name)
            put("totalPages", totalPages)
            put("lastReadPage", lastReadPage)
            put("lastReadTimestamp", lastReadTimestamp)
            put("addedTimestamp", addedTimestamp)
            put("fileSizeBytes", fileSizeBytes)
            put("isManuallyAdded", if (isManuallyAdded) 1 else 0)
            put("detectionScore", detectionScore)
            put("detectionReason", detectionReason)
        }
    }

    private fun Cursor.toBookEntity(): BookEntity {
        val formatStr = getString(getColumnIndexOrThrow("format"))
        val format = runCatching { BookFormat.valueOf(formatStr) }.getOrDefault(BookFormat.PDF)
        val listIdCol = getColumnIndex("listId")
        val resolvedListId = if (listIdCol >= 0) getLong(listIdCol) else BookListEntity.DEFAULT_BOOKS_LIST_ID

        return BookEntity(
            id = getLong(getColumnIndexOrThrow("id")),
            listId = resolvedListId,
            title = getString(getColumnIndexOrThrow("title")),
            author = getStringOrNull("author"),
            publisher = getStringOrNull("publisher"),
            isbn = getStringOrNull("isbn"),
            filePath = getString(getColumnIndexOrThrow("filePath")),
            format = format,
            totalPages = getInt(getColumnIndexOrThrow("totalPages")),
            lastReadPage = getInt(getColumnIndexOrThrow("lastReadPage")),
            lastReadTimestamp = getLong(getColumnIndexOrThrow("lastReadTimestamp")),
            addedTimestamp = getLong(getColumnIndexOrThrow("addedTimestamp")),
            fileSizeBytes = getLong(getColumnIndexOrThrow("fileSizeBytes")),
            isManuallyAdded = getInt(getColumnIndexOrThrow("isManuallyAdded")) != 0,
            detectionScore = getInt(getColumnIndexOrThrow("detectionScore")),
            detectionReason = getString(getColumnIndexOrThrow("detectionReason"))
        )
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val idx = getColumnIndex(columnName)
        if (idx < 0 || isNull(idx)) return null
        return getString(idx)
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
