package com.kitaplik.app.domain.reader

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipFile

data class EpubMetadataPreview(
    val title: String?,
    val author: String?,
    val publisher: String?,
    val isbn: String?,
    val estimatedPageCount: Int,
    val firstPagesText: String
)

data class EpubPage(
    val pageIndex: Int,
    val chapterTitle: String,
    val content: String
)

/**
 * Harici ağır kütüphanelere ihtiyaç duymadan standart EPUB (ZIP + OPF + XHTML)
 * dosyalarının meta verilerini (Yazar, Yayıncı, ISBN), tahmini sayfa sayısını,
 * ilk 3-5 bölüm metnini ve yerleşik okuyucu için sayfalanmış içeriğini ayrıştırır.
 */
object EpubParser {

    private const val CHARS_PER_STANDARD_PAGE = 1450

    /**
     * Tarama sırasında EPUB dosyasını hızlıca inceleyerek meta verileri,
     * tahmini sayfa sayısını ve ilk 5 bölümün metin analizini döndürür.
     */
    fun extractMetadataAndPreview(file: File): EpubMetadataPreview? {
        return runCatching {
            ZipFile(file).use { zip ->
                val opfEntryPath = findOpfPath(zip) ?: return null
                val opfEntry = zip.getEntry(opfEntryPath) ?: return null
                val opfBaseDir = opfEntryPath.substringBeforeLast('/', missingDelimiterValue = "")

                val opfData = zip.getInputStream(opfEntry).use { parseOpf(it) }

                val spineHrefs = opfData.spineItemRefs.mapNotNull { idref ->
                    opfData.manifestIdToHref[idref]
                }

                // İlk 5 spine öğesinden metin çıkar (ilk 3-5 sayfa analizi için)
                val firstPagesBuilder = StringBuilder()
                var totalContentBytes = 0L

                spineHrefs.forEachIndexed { index, href ->
                    val entryPath = resolveRelativePath(opfBaseDir, href)
                    val entry = zip.getEntry(entryPath) ?: zip.getEntry(href)
                    if (entry != null) {
                        val size = entry.size.takeIf { it > 0 } ?: 2048L
                        totalContentBytes += size

                        if (index < 5 && firstPagesBuilder.length < 8000) {
                            val rawHtml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                            val plain = htmlToPlainText(rawHtml)
                            firstPagesBuilder.append(plain).append("\n")
                        }
                    }
                }

                // XHTML etiket yükünü (~%40) düşerek net metin üzerinden sayfa tahmini yap
                val estimatedTextChars = (totalContentBytes * 0.6).toLong()
                val estimatedPages = (estimatedTextChars / CHARS_PER_STANDARD_PAGE).toInt()
                    .coerceAtLeast(spineHrefs.size)

                EpubMetadataPreview(
                    title = opfData.title,
                    author = opfData.author,
                    publisher = opfData.publisher,
                    isbn = opfData.isbn,
                    estimatedPageCount = estimatedPages,
                    firstPagesText = firstPagesBuilder.toString().take(8000)
                )
            }
        }.getOrNull()
    }

    /**
     * Yerleşik okuyucu için EPUB dosyasını okunabilir sayfalara böler.
     */
    fun loadPaginatedBook(file: File): List<EpubPage> {
        return runCatching {
            ZipFile(file).use { zip ->
                val opfEntryPath = findOpfPath(zip) ?: return emptyList()
                val opfEntry = zip.getEntry(opfEntryPath) ?: return emptyList()
                val opfBaseDir = opfEntryPath.substringBeforeLast('/', missingDelimiterValue = "")
                val opfData = zip.getInputStream(opfEntry).use { parseOpf(it) }

                val spineHrefs = opfData.spineItemRefs.mapNotNull { idref ->
                    opfData.manifestIdToHref[idref]
                }

                val pages = mutableListOf<EpubPage>()
                var globalPageIndex = 0

                spineHrefs.forEachIndexed { chapterIndex, href ->
                    val cleanHref = href.substringBefore('#')
                    val entryPath = resolveRelativePath(opfBaseDir, cleanHref)
                    val entry = zip.getEntry(entryPath) ?: zip.getEntry(cleanHref)
                    if (entry != null) {
                        val rawHtml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        val chapterTitle = extractHeadingFromHtml(rawHtml) ?: "Bölüm ${chapterIndex + 1}"
                        val plainText = htmlToPlainText(rawHtml).trim()

                        if (plainText.isNotEmpty()) {
                            val chunks = paginateText(plainText, CHARS_PER_STANDARD_PAGE)
                            for (chunk in chunks) {
                                pages.add(
                                    EpubPage(
                                        pageIndex = globalPageIndex++,
                                        chapterTitle = chapterTitle,
                                        content = chunk
                                    )
                                )
                            }
                        }
                    }
                }

                pages
            }
        }.getOrDefault(emptyList())
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml")
        if (containerEntry != null) {
            runCatching {
                zip.getInputStream(containerEntry).use { stream ->
                    val parser = createXmlParser(stream)
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG &&
                            parser.name.equals("rootfile", ignoreCase = true)
                        ) {
                            val fullPath = parser.getAttributeValue(null, "full-path")
                            if (!fullPath.isNullOrBlank()) return fullPath
                        }
                        event = parser.next()
                    }
                }
            }
        }

        // Yedek: ZIP içindeki herhangi bir .opf dosyasını bul
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.isDirectory && entry.name.endsWith(".opf", ignoreCase = true)) {
                return entry.name
            }
        }
        return null
    }

    private data class OpfData(
        val title: String?,
        val author: String?,
        val publisher: String?,
        val isbn: String?,
        val manifestIdToHref: Map<String, String>,
        val spineItemRefs: List<String>
    )

    private fun parseOpf(inputStream: InputStream): OpfData {
        val parser = createXmlParser(inputStream)
        var title: String? = null
        var author: String? = null
        var publisher: String? = null
        var isbn: String? = null
        val manifest = LinkedHashMap<String, String>()
        val spine = mutableListOf<String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val tagName = parser.name.substringAfter(':').lowercase()
                when (tagName) {
                    "title" -> if (title.isNullOrBlank()) {
                        title = runCatching { parser.nextText().trim() }.getOrNull()
                    }
                    "creator" -> if (author.isNullOrBlank()) {
                        author = runCatching { parser.nextText().trim() }.getOrNull()
                    }
                    "publisher" -> if (publisher.isNullOrBlank()) {
                        publisher = runCatching { parser.nextText().trim() }.getOrNull()
                    }
                    "identifier" -> {
                        val idText = runCatching { parser.nextText().trim() }.getOrNull()
                        if (!idText.isNullOrBlank() &&
                            (idText.contains("isbn", ignoreCase = true) ||
                                idText.replace("-", "").matches(Regex("97[89]\\d{10}|\\d{9}[0-9Xx]")))
                        ) {
                            isbn = idText
                        }
                    }
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                        if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                            if (mediaType.contains("html", ignoreCase = true) ||
                                href.endsWith(".xhtml", ignoreCase = true) ||
                                href.endsWith(".html", ignoreCase = true) ||
                                href.endsWith(".htm", ignoreCase = true)
                            ) {
                                manifest[id] = href
                            }
                        }
                    }
                    "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref")
                        if (!idref.isNullOrBlank()) {
                            spine.add(idref)
                        }
                    }
                }
            }
            event = parser.next()
        }

        return OpfData(
            title = title,
            author = author,
            publisher = publisher,
            isbn = isbn,
            manifestIdToHref = manifest,
            spineItemRefs = spine
        )
    }

    private fun createXmlParser(inputStream: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        return factory.newPullParser().apply {
            setInput(inputStream, "UTF-8")
        }
    }

    private fun resolveRelativePath(baseDir: String, relativeHref: String): String {
        val cleanHref = relativeHref.removePrefix("/")
        if (baseDir.isEmpty()) return cleanHref
        val combined = "$baseDir/$cleanHref"
        val segments = mutableListOf<String>()
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(part)
            }
        }
        return segments.joinToString("/")
    }

    private fun extractHeadingFromHtml(html: String): String? {
        val headingRegex = Regex("""(?is)<h[1-3][^>]*>(.*?)</h[1-3]>""")
        val rawHeading = headingRegex.find(html)?.groupValues?.getOrNull(1) ?: return null
        val clean = rawHeading.replace(Regex("<[^>]+>"), "").trim()
        return clean.takeIf { it.isNotBlank() && it.length <= 80 }
    }

    fun htmlToPlainText(html: String): String {
        // Script ve style bloklarını kaldır
        val withoutScripts = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
            .replace(Regex("(?is)<head[^>]*>.*?</head>"), "")

        // Paragraf ve başlık sonlarını satır sonuna dönüştür
        val withLineBreaks = withoutScripts
            .replace(Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote)>"), "\n\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")

        // Tüm kalan HTML etiketlerini temizle
        val stripped = withLineBreaks.replace(Regex("<[^>]+>"), "")

        // Temel HTML entity'lerini çöz
        return stripped
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun paginateText(text: String, maxCharsPerPage: Int): List<String> {
        if (text.length <= maxCharsPerPage) return listOf(text)
        val pages = mutableListOf<String>()
        val paragraphs = text.split("\n\n")
        val currentBuilder = StringBuilder()

        for (para in paragraphs) {
            val trimmedPara = para.trim()
            if (trimmedPara.isEmpty()) continue

            if (currentBuilder.length + trimmedPara.length + 2 <= maxCharsPerPage) {
                if (currentBuilder.isNotEmpty()) currentBuilder.append("\n\n")
                currentBuilder.append(trimmedPara)
            } else {
                if (currentBuilder.isNotEmpty()) {
                    pages.add(currentBuilder.toString())
                    currentBuilder.clear()
                }
                // Tek bir paragraf sayfa sınırından uzunsa cümle/kelime sınırından böl
                if (trimmedPara.length > maxCharsPerPage) {
                    var start = 0
                    while (start < trimmedPara.length) {
                        val end = (start + maxCharsPerPage).coerceAtMost(trimmedPara.length)
                        val splitAt = if (end < trimmedPara.length) {
                            val lastSpace = trimmedPara.lastIndexOf(' ', end)
                            if (lastSpace > start + maxCharsPerPage / 2) lastSpace else end
                        } else end
                        pages.add(trimmedPara.substring(start, splitAt).trim())
                        start = splitAt + 1
                    }
                } else {
                    currentBuilder.append(trimmedPara)
                }
            }
        }
        if (currentBuilder.isNotEmpty()) {
            pages.add(currentBuilder.toString())
        }
        return pages
    }
}
