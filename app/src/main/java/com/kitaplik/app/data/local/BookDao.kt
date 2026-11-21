package com.kitaplik.app.data.local

import kotlinx.coroutines.flow.Flow

interface BookDao {

    /**
     * Tüm listeleri reaktif olarak gözlemler.
     */
    fun observeAllLists(): Flow<List<BookListEntity>>

    suspend fun getAllListsSnapshot(): List<BookListEntity>

    suspend fun getListById(listId: Long): BookListEntity?

    suspend fun insertBookList(bookList: BookListEntity): Long

    suspend fun updateBookList(bookList: BookListEntity)

    suspend fun deleteBookList(listId: Long)

    /**
     * Uygulama açıldığında yeniden tarama yapmadan mevcut belgeleri
     * gecikmesiz olarak (Flow ile reaktif biçimde) sunar.
     */
    fun observeAllBooks(): Flow<List<BookEntity>>

    suspend fun getAllBooksSnapshot(): List<BookEntity>

    suspend fun getBooksByListIdSnapshot(listId: Long): List<BookEntity>

    suspend fun getBookById(bookId: Long): BookEntity?

    suspend fun getBookByPath(path: String): BookEntity?

    suspend fun insertBook(book: BookEntity): Long

    suspend fun insertBooks(books: List<BookEntity>)

    /**
     * Okuyucu açıkken veya belge kapatıldığında kalınan sayfayı günceller.
     */
    suspend fun updateReadingProgress(
        bookId: Long,
        pageIndex: Int,
        totalPages: Int = 0,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Belgeler kopyalanmadan bir klasörde toparlandığında (move)
     * veritabanındaki dosya yolunu günceller; okuma geçmişi ve kaldığı sayfa korunur.
     */
    suspend fun updateBookFilePath(bookId: Long, newPath: String)

    suspend fun deleteBookById(bookId: Long)

    suspend fun insertIgnoredFile(ignoredFile: IgnoredFileEntity)

    suspend fun removeIgnoredFile(path: String)

    suspend fun getAllIgnoredFilePaths(): List<String>

    /**
     * Filtrelerden kaçan alakasız bir belgeyi tek tıkla listeden kaldırır
     * ve sonraki taramalarda tekrar çıkmaması için hariç tutulanlara (ignored_files) ekler.
     */
    suspend fun removeAndIgnoreBook(book: BookEntity) {
        deleteBookById(book.id)
        insertIgnoredFile(IgnoredFileEntity(filePath = book.filePath))
    }
}
