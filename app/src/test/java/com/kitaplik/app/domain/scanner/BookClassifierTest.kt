package com.kitaplik.app.domain.scanner

import com.kitaplik.app.data.local.BookListEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookClassifierTest {

    @Test
    fun `blacklist keywords in filename are strictly rejected`() {
        val blacklistedSamples = listOf(
            "Diferansiyel_Ders_Notu.pdf",
            "Fizik101_Vize_Sorulari.pdf",
            "Algoritma_Final_2025.pdf",
            "CS102_HW3_Solutions.pdf",
            "Yillik_Faaliyet_Raporu.pdf",
            "Elektrik_Fatura_Kasim.pdf",
            "Kimya_Lab_Deney2.pdf",
            "Bitirme_Projesi_Sunum.pdf"
        )

        for (fileName in blacklistedSamples) {
            val result = BookClassifier.evaluate(
                DocumentEvaluationInput(
                    fileName = fileName,
                    pageCount = 180,
                    author = "Prof. Dr. Ahmet Yılmaz",
                    firstPagesText = "İçindekiler Bölüm 1"
                )
            )
            assertFalse("$fileName kara listeden elenmeliydi", result.isBook)
        }
    }

    @Test
    fun `documents with fewer than 45 pages are strictly rejected in default book list`() {
        val result = BookClassifier.evaluate(
            DocumentEvaluationInput(
                fileName = "Kisa_Kitapcik.pdf",
                pageCount = 42,
                author = "Sabahattin Ali",
                publisher = "Yapı Kredi Yayınları",
                isbn = "978-975-08-0663-6",
                firstPagesText = "İçindekiler Yayınevi Bölüm 1"
            )
        )
        assertFalse("45 sayfanın altındaki belgeler elenmeli", result.isBook)
    }

    @Test
    fun `valid book with metadata and first page keywords is accepted`() {
        val result = BookClassifier.evaluate(
            DocumentEvaluationInput(
                fileName = "Ince_Memed_1.pdf",
                pageCount = 436,
                author = "Yaşar Kemal",
                publisher = "Yapı Kredi Yayınları",
                isbn = "978-975-08-0714-5",
                firstPagesText = "Yapı Kredi Yayınları İçindekiler Birinci Bölüm"
            )
        )
        assertTrue(result.isBook)
        assertNotNull(result.detectedIsbn)
    }

    @Test
    fun `custom lecture notes list accepts lecture notes and rejects invoices`() {
        val lectureNotesList = BookListEntity(
            id = 2L,
            name = "Ders Notlarım",
            minPages = 2,
            maxPages = 120,
            filenameWhitelist = "not, vize, final, ders, ödev, hw, lab",
            filenameBlacklist = "fatura, dekont",
            firstPagesKeywords = "hafta, ders, soru, çözüm",
            useDefaultBookHeuristics = false,
            matchBothFilenameAndFirstPages = false
        )

        val acceptedNote = BookClassifier.evaluateForList(
            input = DocumentEvaluationInput(
                fileName = "EMT2_Hafta3_Ders_Notu.pdf",
                pageCount = 18,
                firstPagesText = "3. Hafta Elektromanyetik Dalgalar"
            ),
            listConfig = lectureNotesList
        )
        assertTrue("Ders notu özel ders notları listesine kabul edilmeli", acceptedNote.isBook)

        val rejectedInvoice = BookClassifier.evaluateForList(
            input = DocumentEvaluationInput(
                fileName = "Elektrik_Fatura_2026.pdf",
                pageCount = 2,
                firstPagesText = "Fatura Tutarı"
            ),
            listConfig = lectureNotesList
        )
        assertFalse("Fatura kara listeden dolayı reddedilmeli", rejectedInvoice.isBook)
    }

    @Test
    fun `normal book titles containing substring not inside English words are not falsely blacklisted`() {
        assertNull(BookClassifier.findBlacklistedKeywordInFileName("Another_Country_Baldwin.epub"))
        assertNull(BookClassifier.findBlacklistedKeywordInFileName("Hypnotic_Writing.pdf"))
    }
}
