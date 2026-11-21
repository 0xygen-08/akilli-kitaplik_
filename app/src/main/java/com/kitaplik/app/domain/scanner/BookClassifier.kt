package com.kitaplik.app.domain.scanner

import com.kitaplik.app.data.local.BookListEntity
import java.util.Locale

data class DocumentEvaluationInput(
    val fileName: String,
    val pageCount: Int,
    val author: String? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val firstPagesText: String = "",
    val isEpub: Boolean = false
)

data class BookClassificationResult(
    val isBook: Boolean,
    val score: Int,
    val rejectionReason: String? = null,
    val matchedReasons: List<String> = emptyList(),
    val detectedIsbn: String? = null
)

/**
 * Hem varsayılan Kitap kriterlerini hem de kullanıcının her liste için ayrı ayrı
 * belirlediği özel kriterleri (kelime kara listesi, sayfa sayısı alt/üst sınırı,
 * ilk sayfalarda aranan kelimeler, belge isminde aranan kelimeler) değerlendiren sınıflandırıcı.
 */
object BookClassifier {

    fun evaluate(input: DocumentEvaluationInput): BookClassificationResult {
        return evaluateForList(input, BookListEntity.createDefaultBooksList())
    }

    fun evaluateForList(
        input: DocumentEvaluationInput,
        listConfig: BookListEntity
    ): BookClassificationResult {
        val blacklistKeywords = if (listConfig.useDefaultBookHeuristics && listConfig.filenameBlacklist.isBlank()) {
            BookHeuristicsConfig.BLACKLIST_FILENAME_KEYWORDS
        } else {
            listConfig.parsedBlacklist()
        }

        // 1. Kelime Kara Listesi Kontrolü (Belge isminde geçmemesi gereken kelimeler)
        if (blacklistKeywords.isNotEmpty()) {
            val matchedBlacklistWord = findKeywordInFileName(input.fileName, blacklistKeywords)
            if (matchedBlacklistWord != null) {
                return BookClassificationResult(
                    isBook = false,
                    score = 0,
                    rejectionReason = "Dosya adında kara liste kelimesi bulundu: '$matchedBlacklistWord'"
                )
            }
        }

        // 2. Minimum ve Maksimum Sayfa Sayısı Kontrolü
        val minPages = listConfig.minPages.coerceAtLeast(1)
        if (input.pageCount < minPages) {
            return BookClassificationResult(
                isBook = false,
                score = 0,
                rejectionReason = "Sayfa sayısı alt sınırın altında (${input.pageCount} < $minPages)"
            )
        }

        if (listConfig.maxPages > 0 && input.pageCount > listConfig.maxPages) {
            return BookClassificationResult(
                isBook = false,
                score = 0,
                rejectionReason = "Sayfa sayısı üst sınırın üzerinde (${input.pageCount} > ${listConfig.maxPages})"
            )
        }

        val whitelistKeywords = listConfig.parsedWhitelist()
        val customFirstPageKeywords = listConfig.parsedFirstPagesKeywords()
        val normalizedFirstPages = normalizeText(input.firstPagesText)
        val extractedIsbn = extractIsbn(input.isbn) ?: extractIsbn(input.firstPagesText)

        // Özel liste modu (kullanıcı özel kelime kriterleri girdiyse veya varsayılan kitap heuristiği kapalıysa)
        if (!listConfig.useDefaultBookHeuristics || whitelistKeywords.isNotEmpty()) {
            val reasons = mutableListOf("${input.pageCount} sayfa")
            var score = 50

            val matchedFilenameKeyword = if (whitelistKeywords.isNotEmpty()) {
                findKeywordInFileName(input.fileName, whitelistKeywords)
            } else null

            val matchedFirstPageKeywords = if (customFirstPageKeywords.isNotEmpty()) {
                customFirstPageKeywords.filter { kw ->
                    normalizedFirstPages.contains(normalizeText(kw))
                }
            } else emptyList()

            if (matchedFilenameKeyword != null) {
                score += 30
                reasons.add("İsim eşleşmesi: '$matchedFilenameKeyword'")
            }

            if (matchedFirstPageKeywords.isNotEmpty()) {
                score += 30
                reasons.add("İlk sayfalarda: ${matchedFirstPageKeywords.take(2).joinToString(", ")}")
            }

            val isAccepted = when {
                whitelistKeywords.isNotEmpty() && customFirstPageKeywords.isNotEmpty() -> {
                    if (listConfig.matchBothFilenameAndFirstPages) {
                        matchedFilenameKeyword != null && matchedFirstPageKeywords.isNotEmpty()
                    } else {
                        matchedFilenameKeyword != null || matchedFirstPageKeywords.isNotEmpty()
                    }
                }
                whitelistKeywords.isNotEmpty() -> matchedFilenameKeyword != null
                customFirstPageKeywords.isNotEmpty() -> matchedFirstPageKeywords.isNotEmpty()
                else -> true // Sadece sayfa sayısı ve kara liste kriteri verilmişse kabul et
            }

            return BookClassificationResult(
                isBook = isAccepted,
                score = if (isAccepted) score else 0,
                rejectionReason = if (isAccepted) null else "Liste kelime kriterleriyle eşleşmedi",
                matchedReasons = reasons,
                detectedIsbn = extractedIsbn
            )
        }

        // Varsayılan Kitap Heuristiği Modu
        var score = 40
        val reasons = mutableListOf("${input.pageCount} sayfa")

        if (input.isEpub) {
            score += 20
            reasons.add("EPUB e-kitap")
        }

        if (input.pageCount >= 100) {
            score += 25
        } else if (input.pageCount >= 60) {
            score += 15
        }

        val cleanAuthor = sanitizeMetadataValue(input.author)
        if (cleanAuthor != null) {
            score += 25
            reasons.add("Yazar: $cleanAuthor")
        }

        val cleanPublisher = sanitizeMetadataValue(input.publisher)
        if (cleanPublisher != null) {
            score += 20
            reasons.add("Yayıncı: $cleanPublisher")
        }

        if (extractedIsbn != null) {
            score += 35
            reasons.add("ISBN: $extractedIsbn")
        }

        val primaryKeywordsToUse = if (customFirstPageKeywords.isNotEmpty()) {
            customFirstPageKeywords
        } else {
            BookHeuristicsConfig.PRIMARY_FIRST_PAGE_KEYWORDS
        }

        val matchedPrimaryKeywords = primaryKeywordsToUse.filter { kw ->
            normalizedFirstPages.contains(normalizeText(kw))
        }
        if (matchedPrimaryKeywords.isNotEmpty()) {
            score += 30
            val preview = matchedPrimaryKeywords.take(2).joinToString(", ")
            reasons.add("İlk sayfalarda: $preview")
        }

        val matchedSecondaryKeywords = BookHeuristicsConfig.SECONDARY_FIRST_PAGE_KEYWORDS.filter { kw ->
            normalizedFirstPages.contains(normalizeText(kw))
        }
        if (matchedSecondaryKeywords.isNotEmpty()) {
            score += 15
            if (matchedPrimaryKeywords.isEmpty()) {
                val preview = matchedSecondaryKeywords.take(2).joinToString(", ")
                reasons.add("Künye: $preview")
            }
        }

        val isAccepted = score >= BookHeuristicsConfig.MIN_BOOK_SCORE
        return BookClassificationResult(
            isBook = isAccepted,
            score = score,
            rejectionReason = if (isAccepted) null else "Kitap puanı yetersiz ($score < ${BookHeuristicsConfig.MIN_BOOK_SCORE})",
            matchedReasons = reasons,
            detectedIsbn = extractedIsbn
        )
    }

    fun findBlacklistedKeywordInFileName(fileName: String): String? {
        return findKeywordInFileName(fileName, BookHeuristicsConfig.BLACKLIST_FILENAME_KEYWORDS)
    }

    /**
     * Dosya adını kelimelere/tokenlara ayırarak verilen anahtar kelime listesini kontrol eder.
     * Hem Türkçe/İngilizce küçük harf dönüşümünü hem de token/alt-dizi eşleşmesini destekler.
     */
    fun findKeywordInFileName(fileName: String, keywords: List<String>): String? {
        if (keywords.isEmpty()) return null
        val nameWithoutExt = fileName.substringBeforeLast('.')
        val lowerTr = nameWithoutExt.lowercase(BookHeuristicsConfig.TURKISH_LOCALE)
        val lowerEn = nameWithoutExt.lowercase(Locale.ROOT)
        val normalizedFull = normalizeText(nameWithoutExt)

        val tokensTr = splitIntoWordTokens(lowerTr)
        val tokensEn = splitIntoWordTokens(lowerEn)
        val tokensNormalized = splitIntoWordTokens(normalizedFull)
        val allTokens = (tokensTr + tokensEn + tokensNormalized).toSet()

        for (keyword in keywords) {
            val cleanKw = keyword.trim()
            if (cleanKw.isEmpty()) continue

            val kwTr = cleanKw.lowercase(BookHeuristicsConfig.TURKISH_LOCALE)
            val kwEn = cleanKw.lowercase(Locale.ROOT)
            val kwNorm = normalizeText(cleanKw)

            if (allTokens.contains(kwTr) || allTokens.contains(kwEn) || allTokens.contains(kwNorm)) {
                return cleanKw
            }

            // 4 veya daha uzun kelimeler ya da boşluklu ifadeler bitişik yazılmışsa da yakala
            if (kwNorm.length >= 4 || kwNorm.contains(' ')) {
                if (lowerTr.contains(kwTr) || lowerEn.contains(kwEn) || normalizedFull.contains(kwNorm)) {
                    return cleanKw
                }
            }
        }
        return null
    }

    private fun splitIntoWordTokens(text: String): List<String> {
        val separated = text.replace(Regex("(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})"), " ")
        return separated
            .split(Regex("[^\\p{L}]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun sanitizeMetadataValue(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.length < 2) return null

        val lower = trimmed.lowercase(Locale.ROOT)
        val isGenericTool = BookHeuristicsConfig.IGNORED_METADATA_CREATORS.any { tool ->
            lower.contains(tool)
        }
        return if (isGenericTool) null else trimmed
    }

    fun extractIsbn(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val explicitIsbnRegex = Regex(
            pattern = """(?i)ISBN(?:-1[03])?:?\s*([0-9X][0-9X\-\s]{8,16}[0-9X])"""
        )
        explicitIsbnRegex.find(text)?.groupValues?.getOrNull(1)?.let { match ->
            val digitsOnly = match.replace(Regex("[^0-9Xx]"), "")
            if (digitsOnly.length == 10 || digitsOnly.length == 13) {
                return match.trim()
            }
        }

        val isbn13Regex = Regex("""\b97[89][-\s]?(?:[0-9][-\s]?){9}[0-9]\b""")
        isbn13Regex.find(text)?.value?.let { match ->
            val digitsOnly = match.replace(Regex("[^0-9]"), "")
            if (digitsOnly.length == 13) {
                return match.trim()
            }
        }

        return null
    }

    private fun normalizeText(text: String): String {
        return text
            .lowercase(BookHeuristicsConfig.TURKISH_LOCALE)
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
    }
}
