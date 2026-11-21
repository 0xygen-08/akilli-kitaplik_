package com.kitaplik.app.ui.reader

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitaplik.app.data.local.AppDatabase
import com.kitaplik.app.data.local.BookEntity
import com.kitaplik.app.data.local.BookFormat
import com.kitaplik.app.domain.reader.EpubPage
import com.kitaplik.app.domain.reader.EpubParser
import com.kitaplik.app.domain.reader.PdfPageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Ready(
        val book: BookEntity,
        val initialPage: Int,
        val totalPages: Int,
        val epubPages: List<EpubPage> = emptyList()
    ) : ReaderUiState
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val bookDao = AppDatabase.getInstance(application).bookDao()

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private var pdfPageLoader: PdfPageLoader? = null
    private var activeBookId: Long = -1L
    private var activeTotalPages: Int = 0

    /**
     * Kitap seçildiğinde hiçbir soru sormadan doğrudan kitabı ve en son kalınan sayfayı yükler.
     */
    fun openBook(bookId: Long) {
        if (activeBookId == bookId && _uiState.value is ReaderUiState.Ready) return
        activeBookId = bookId

        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            val book = bookDao.getBookById(bookId)
            if (book == null) {
                _uiState.value = ReaderUiState.Error("Kitap kaydı bulunamadı.")
                return@launch
            }

            val file = File(book.filePath)
            if (!file.exists() || !file.canRead()) {
                _uiState.value = ReaderUiState.Error("Dosya bulunamadı veya okunamıyor:\n${book.filePath}")
                return@launch
            }

            when (book.format) {
                BookFormat.PDF -> openPdfBook(book, file)
                BookFormat.EPUB -> openEpubBook(book, file)
            }
        }
    }

    private suspend fun openPdfBook(book: BookEntity, file: File) {
        withContext(Dispatchers.IO) {
            runCatching {
                pdfPageLoader?.close()
                val loader = PdfPageLoader(file)
                pdfPageLoader = loader
                val total = loader.pageCount.coerceAtLeast(1)
                val startPage = book.lastReadPage.coerceIn(0, total - 1)

                activeTotalPages = total
                _currentPage.value = startPage

                // Açılış zamanını ve güncel sayfa sayısını kaydet
                bookDao.updateReadingProgress(
                    bookId = book.id,
                    pageIndex = startPage,
                    totalPages = total
                )

                _uiState.value = ReaderUiState.Ready(
                    book = book.copy(totalPages = total, lastReadPage = startPage),
                    initialPage = startPage,
                    totalPages = total
                )
            }.onFailure { e ->
                _uiState.value = ReaderUiState.Error("PDF açılamadı: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun openEpubBook(book: BookEntity, file: File) {
        withContext(Dispatchers.IO) {
            val pages = EpubParser.loadPaginatedBook(file)
            if (pages.isEmpty()) {
                _uiState.value = ReaderUiState.Error("EPUB içeriği ayrıştırılamadı.")
                return@withContext
            }

            val total = pages.size
            val startPage = book.lastReadPage.coerceIn(0, total - 1)
            activeTotalPages = total
            _currentPage.value = startPage

            bookDao.updateReadingProgress(
                bookId = book.id,
                pageIndex = startPage,
                totalPages = total
            )

            _uiState.value = ReaderUiState.Ready(
                book = book.copy(totalPages = total, lastReadPage = startPage),
                initialPage = startPage,
                totalPages = total,
                epubPages = pages
            )
        }
    }

    suspend fun renderPdfPage(pageIndex: Int): Bitmap? {
        return pdfPageLoader?.renderPage(pageIndex)
    }

    /**
     * Sayfa her değiştiğinde ve belge kapatıldığında açık olan sayfayı veritabanına kaydeder.
     */
    fun onPageChanged(newPageIndex: Int) {
        if (activeBookId <= 0) return
        _currentPage.value = newPageIndex
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateReadingProgress(
                bookId = activeBookId,
                pageIndex = newPageIndex,
                totalPages = activeTotalPages
            )
        }
    }

    fun saveProgressOnClose() {
        if (activeBookId <= 0) return
        val lastPage = _currentPage.value
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateReadingProgress(
                bookId = activeBookId,
                pageIndex = lastPage,
                totalPages = activeTotalPages
            )
        }
    }

    override fun onCleared() {
        saveProgressOnClose()
        pdfPageLoader?.close()
        pdfPageLoader = null
        super.onCleared()
    }
}
