package com.kitaplik.app.data.local

import com.kitaplik.app.domain.scanner.BookHeuristicsConfig

/**
 * Kullanıcının kendi belirlediği tarama kriterlerine göre oluşturduğu
 * ve birbirine karışmayan özel belge/kitap listesi.
 */
data class BookListEntity(
    val id: Long = 0,
    val name: String,
    val minPages: Int = 1,
    val maxPages: Int = 0, // 0 = Üst sınır yok
    val filenameWhitelist: String = "", // Virgülle ayrılmış, dosya adında aranan kelimeler
    val filenameBlacklist: String = "", // Virgülle ayrılmış, dosya adında dışlanan kelimeler
    val firstPagesKeywords: String = "", // Virgülle ayrılmış, ilk sayfalarda aranan kelimeler
    val useDefaultBookHeuristics: Boolean = false,
    val matchBothFilenameAndFirstPages: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun parsedWhitelist(): List<String> = parseCommaSeparated(filenameWhitelist)
    fun parsedBlacklist(): List<String> = parseCommaSeparated(filenameBlacklist)
    fun parsedFirstPagesKeywords(): List<String> = parseCommaSeparated(firstPagesKeywords)

    companion object {
        const val DEFAULT_BOOKS_LIST_ID = 1L

        fun createDefaultBooksList(): BookListEntity {
            return BookListEntity(
                id = DEFAULT_BOOKS_LIST_ID,
                name = "Kitaplar",
                minPages = BookHeuristicsConfig.MIN_BOOK_PAGE_COUNT,
                maxPages = 0,
                filenameWhitelist = "",
                filenameBlacklist = "not, notu, notlar, vize, final, bütünleme, çıkmış, hw, ödev, rapor, fatura, dekont, makbuz, lab, sunum, slayt, syllabus, quiz",
                firstPagesKeywords = "içindekiler, contents, yayınevi, yayınları, chapter, bölüm, isbn, önsöz, preface, baskı",
                useDefaultBookHeuristics = true,
                matchBothFilenameAndFirstPages = false
            )
        }

        private fun parseCommaSeparated(raw: String): List<String> {
            if (raw.isBlank()) return emptyList()
            return raw.split(',', ';', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}
