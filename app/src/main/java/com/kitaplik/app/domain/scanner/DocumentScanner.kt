package com.kitaplik.app.domain.scanner

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.kitaplik.app.data.local.BookDao
import com.kitaplik.app.data.local.BookEntity
import com.kitaplik.app.data.local.BookFormat
import com.kitaplik.app.data.local.BookListEntity
import com.kitaplik.app.domain.reader.EpubParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ScanSummary(
    val listName: String,
    val scannedFilesCount: Int,
    val newlyAddedBooksCount: Int,
    val skippedCount: Int
)

sealed interface ManualAddResult {
    data class Added(val book: BookEntity) : ManualAddResult
    data class AlreadyInCurrentList(val book: BookEntity) : ManualAddResult
    data class AlreadyInAnotherList(val book: BookEntity, val otherListName: String) : ManualAddResult
    data object Failed : ManualAddResult
}

class DocumentScanner(
    private val context: Context,
    private val bookDao: BookDao
) {

    init {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    /**
     * Haftalık arka plan taraması için tüm listeleri sırasıyla tarar.
     * Aynı belge asla iki farklı listeye eklenmez.
     */
    suspend fun scanDocumentsDirectory(): ScanSummary = withContext(Dispatchers.IO) {
        val lists = bookDao.getAllListsSnapshot().ifEmpty {
            listOf(BookListEntity.createDefaultBooksList())
        }
        var totalScanned = 0
        var totalAdded = 0
        var totalSkipped = 0
        for (list in lists) {
            val summary = scanDocumentsForList(list)
            totalScanned = maxOf(totalScanned, summary.scannedFilesCount)
            totalAdded += summary.newlyAddedBooksCount
            totalSkipped += summary.skippedCount
        }
        ScanSummary(
            listName = lists.first().name,
            scannedFilesCount = totalScanned,
            newlyAddedBooksCount = totalAdded,
            skippedCount = totalSkipped
        )
    }

    /**
     * Belirli bir listenin kendine özel kriterlerine (kelime kara listesi, sayfa sayısı,
     * ilk sayfalarda aranan kelimeler, belge isminde aranan kelimeler) göre cihazdaki
     * tüm PDF ve EPUB dosyalarını tarar.
     *
     * KURAL: Herhangi bir listede zaten bulunan bir belge (existingPaths),
     * başka bir listeye ASLA tekrar eklenmez.
     */
    suspend fun scanDocumentsForList(targetList: BookListEntity): ScanSummary =
        withContext(Dispatchers.IO) {
            // Tüm listelerdeki mevcut belgelerin yollarını al (aynı belge iki listede bulunamaz!)
            val existingPaths = bookDao.getAllBooksSnapshot().map { it.filePath }.toHashSet()
            val ignoredPaths = bookDao.getAllIgnoredFilePaths().toHashSet()

            val candidateFilesMap = LinkedHashMap<String, File>()

            val rootsToScan = getAllStorageRoots()
            val visitedDirs = HashSet<String>()
            for (root in rootsToScan) {
                collectDocumentFilesRecursively(root, candidateFilesMap, visitedDirs)
            }

            collectFromMediaStore(candidateFilesMap)

            var scannedCount = 0
            var addedCount = 0
            var rejectedCount = 0
            val newBooks = mutableListOf<BookEntity>()

            val blacklistKeywords = if (targetList.useDefaultBookHeuristics && targetList.filenameBlacklist.isBlank()) {
                BookHeuristicsConfig.BLACKLIST_FILENAME_KEYWORDS
            } else {
                targetList.parsedBlacklist()
            }
            val whitelistKeywords = targetList.parsedWhitelist()
            val firstPageKeywords = targetList.parsedFirstPagesKeywords()

            for ((absolutePath, file) in candidateFilesMap) {
                // Zaten bu listede veya BAŞKA bir listede kayıtlıysa kesinlikle atla
                if (absolutePath in existingPaths || absolutePath in ignoredPaths) {
                    continue
                }

                scannedCount++

                // 1. Dosya adı kara liste kontrolünü dosyayı hiç açmadan önce çalıştır
                if (blacklistKeywords.isNotEmpty() &&
                    BookClassifier.findKeywordInFileName(file.name, blacklistKeywords) != null
                ) {
                    rejectedCount++
                    continue
                }

                // 2. Eğer yalnızca dosya adında aranan kelime kriteri varsa (ilk sayfa kriteri yoksa
                // veya her ikisi de zorunluysa) ve dosya adı eşleşmiyorsa dosyayı hiç açmadan eler
                if (whitelistKeywords.isNotEmpty() &&
                    (firstPageKeywords.isEmpty() || targetList.matchBothFilenameAndFirstPages)
                ) {
                    if (BookClassifier.findKeywordInFileName(file.name, whitelistKeywords) == null) {
                        rejectedCount++
                        continue
                    }
                }

                val ext = file.extension.lowercase()
                val detectedBook = when (ext) {
                    "pdf" -> inspectPdfCandidate(file, targetList)
                    "epub" -> inspectEpubCandidate(file, targetList)
                    else -> null
                }

                if (detectedBook != null) {
                    newBooks.add(detectedBook)
                    existingPaths.add(absolutePath)
                    addedCount++
                } else {
                    rejectedCount++
                }
            }

            if (newBooks.isNotEmpty()) {
                bookDao.insertBooks(newBooks)
            }

            ScanSummary(
                listName = targetList.name,
                scannedFilesCount = scannedCount,
                newlyAddedBooksCount = addedCount,
                skippedCount = rejectedCount
            )
        }

    /**
     * Kullanıcının elle seçtiği belgeyi aktif listeye ekler.
     * Eğer belge zaten başka bir listede varsa "aynı belge iki farklı listede bulunamaz"
     * kuralı gereği eklemez ve hangi listede olduğunu bildirir.
     */
    suspend fun addBookManually(
        uri: Uri,
        targetListId: Long = BookListEntity.DEFAULT_BOOKS_LIST_ID
    ): ManualAddResult = withContext(Dispatchers.IO) {
        val resolvedFile = resolveFileFromUri(uri) ?: return@withContext ManualAddResult.Failed
        val ext = resolvedFile.extension.lowercase()
        val format = when (ext) {
            "epub" -> BookFormat.EPUB
            else -> BookFormat.PDF
        }

        bookDao.removeIgnoredFile(resolvedFile.absolutePath)

        // Zaten herhangi bir listede kayıtlı mı kontrol et
        bookDao.getBookByPath(resolvedFile.absolutePath)?.let { existing ->
            return@withContext if (existing.listId == targetListId) {
                ManualAddResult.AlreadyInCurrentList(existing)
            } else {
                val otherListName = bookDao.getListById(existing.listId)?.name ?: "Diğer Liste"
                ManualAddResult.AlreadyInAnotherList(existing, otherListName)
            }
        }

        val bookEntity = when (format) {
            BookFormat.PDF -> buildManualPdfBook(resolvedFile, targetListId)
            BookFormat.EPUB -> buildManualEpubBook(resolvedFile, targetListId)
        }

        val id = bookDao.insertBook(bookEntity)
        if (id > 0) {
            ManualAddResult.Added(bookEntity.copy(id = id))
        } else {
            ManualAddResult.Failed
        }
    }

    private fun inspectPdfCandidate(file: File, targetList: BookListEntity): BookEntity? {
        // 1. Aşama: Native PdfRenderer ile çok hızlı (1-2 ms) sayfa sayısı kontrolü
        val nativePageCount = getFastPdfPageCount(file)
        val minPages = targetList.minPages.coerceAtLeast(1)
        if (nativePageCount < minPages) {
            return null
        }
        if (targetList.maxPages > 0 && nativePageCount > targetList.maxPages) {
            return null
        }

        // 2. Aşama: Meta veri ve ilk sayfaların metin analizi
        var metaTitle: String? = null
        var metaAuthor: String? = null
        var metaPublisher: String? = null
        var metaIsbn: String? = null
        var firstPagesText = ""

        val needsTextExtraction = targetList.useDefaultBookHeuristics ||
            targetList.parsedFirstPagesKeywords().isNotEmpty()

        runCatching {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val info = doc.documentInformation
                if (info != null) {
                    metaTitle = info.title?.trim()?.takeIf { it.isNotBlank() }
                    metaAuthor = info.author?.trim()?.takeIf { it.isNotBlank() }
                    metaPublisher = (info.getCustomMetadataValue("Publisher")
                        ?: info.producer)?.trim()?.takeIf { it.isNotBlank() }

                    val combinedMeta = listOfNotNull(
                        info.subject,
                        info.keywords,
                        info.getCustomMetadataValue("ISBN"),
                        info.getCustomMetadataValue("isbn")
                    ).joinToString(" ")
                    metaIsbn = BookClassifier.extractIsbn(combinedMeta)
                }

                if (needsTextExtraction) {
                    val stripper = PDFTextStripper().apply {
                        startPage = 1
                        endPage = minOf(BookHeuristicsConfig.FIRST_PAGES_TO_SCAN, doc.numberOfPages)
                    }
                    firstPagesText = stripper.getText(doc).orEmpty()
                }
            }
        }

        val evaluation = BookClassifier.evaluateForList(
            input = DocumentEvaluationInput(
                fileName = file.name,
                pageCount = nativePageCount,
                author = metaAuthor,
                publisher = metaPublisher,
                isbn = metaIsbn,
                firstPagesText = firstPagesText,
                isEpub = false
            ),
            listConfig = targetList
        )

        if (!evaluation.isBook) return null

        val displayTitle = BookClassifier.sanitizeMetadataValue(metaTitle)
            ?: beautifyFileName(file.nameWithoutExtension)

        return BookEntity(
            listId = targetList.id,
            title = displayTitle,
            author = BookClassifier.sanitizeMetadataValue(metaAuthor),
            publisher = BookClassifier.sanitizeMetadataValue(metaPublisher),
            isbn = evaluation.detectedIsbn,
            filePath = file.absolutePath,
            format = BookFormat.PDF,
            totalPages = nativePageCount,
            fileSizeBytes = file.length(),
            isManuallyAdded = false,
            detectionScore = evaluation.score,
            detectionReason = evaluation.matchedReasons.joinToString(" • ")
        )
    }

    private fun inspectEpubCandidate(file: File, targetList: BookListEntity): BookEntity? {
        val preview = EpubParser.extractMetadataAndPreview(file) ?: return null

        val evaluation = BookClassifier.evaluateForList(
            input = DocumentEvaluationInput(
                fileName = file.name,
                pageCount = preview.estimatedPageCount,
                author = preview.author,
                publisher = preview.publisher,
                isbn = preview.isbn,
                firstPagesText = preview.firstPagesText,
                isEpub = true
            ),
            listConfig = targetList
        )

        if (!evaluation.isBook) return null

        val displayTitle = BookClassifier.sanitizeMetadataValue(preview.title)
            ?: beautifyFileName(file.nameWithoutExtension)

        return BookEntity(
            listId = targetList.id,
            title = displayTitle,
            author = BookClassifier.sanitizeMetadataValue(preview.author),
            publisher = BookClassifier.sanitizeMetadataValue(preview.publisher),
            isbn = evaluation.detectedIsbn ?: preview.isbn,
            filePath = file.absolutePath,
            format = BookFormat.EPUB,
            totalPages = preview.estimatedPageCount.coerceAtLeast(1),
            fileSizeBytes = file.length(),
            isManuallyAdded = false,
            detectionScore = evaluation.score,
            detectionReason = evaluation.matchedReasons.joinToString(" • ")
        )
    }

    private fun buildManualPdfBook(file: File, targetListId: Long): BookEntity {
        val pageCount = getFastPdfPageCount(file).coerceAtLeast(1)
        var metaTitle: String? = null
        var metaAuthor: String? = null
        var metaPublisher: String? = null

        runCatching {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val info = doc.documentInformation
                metaTitle = info?.title?.trim()?.takeIf { it.isNotBlank() }
                metaAuthor = info?.author?.trim()?.takeIf { it.isNotBlank() }
                metaPublisher = info?.getCustomMetadataValue("Publisher")?.trim()
            }
        }

        return BookEntity(
            listId = targetListId,
            title = BookClassifier.sanitizeMetadataValue(metaTitle)
                ?: beautifyFileName(file.nameWithoutExtension),
            author = BookClassifier.sanitizeMetadataValue(metaAuthor),
            publisher = BookClassifier.sanitizeMetadataValue(metaPublisher),
            filePath = file.absolutePath,
            format = BookFormat.PDF,
            totalPages = pageCount,
            fileSizeBytes = file.length(),
            isManuallyAdded = true,
            detectionScore = 100,
            detectionReason = "Elle eklendi • $pageCount sayfa"
        )
    }

    private fun buildManualEpubBook(file: File, targetListId: Long): BookEntity {
        val preview = EpubParser.extractMetadataAndPreview(file)
        val pageCount = (preview?.estimatedPageCount ?: 1).coerceAtLeast(1)
        return BookEntity(
            listId = targetListId,
            title = BookClassifier.sanitizeMetadataValue(preview?.title)
                ?: beautifyFileName(file.nameWithoutExtension),
            author = BookClassifier.sanitizeMetadataValue(preview?.author),
            publisher = BookClassifier.sanitizeMetadataValue(preview?.publisher),
            isbn = preview?.isbn,
            filePath = file.absolutePath,
            format = BookFormat.EPUB,
            totalPages = pageCount,
            fileSizeBytes = file.length(),
            isManuallyAdded = true,
            detectionScore = 100,
            detectionReason = "Elle eklendi • ~$pageCount sayfa"
        )
    }

    private fun getFastPdfPageCount(file: File): Int {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        }.getOrDefault(0)
    }

    private fun getAllStorageRoots(): List<File> {
        val roots = LinkedHashSet<File>()

        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?.takeIf { it.exists() }?.let { roots.add(it) }

        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            ?.takeIf { it.exists() }?.let { roots.add(it) }

        Environment.getExternalStorageDirectory()
            ?.takeIf { it.exists() }?.let { roots.add(it) }

        File("/storage/emulated/0").takeIf { it.exists() }?.let { roots.add(it) }

        return roots.toList()
    }

    private val skippedDirectoryNames = setOf(
        "dcim",
        "pictures",
        "movies",
        "music",
        "ringtones",
        "alarms",
        "notifications",
        "podcasts",
        "audiobooks",
        "recordings",
        "thumbnails"
    )

    private fun collectDocumentFilesRecursively(
        dir: File,
        outMap: MutableMap<String, File>,
        visitedDirs: MutableSet<String>
    ) {
        if (!dir.exists() || !dir.canRead()) return
        val canonicalDir = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!visitedDirs.add(canonicalDir)) return

        val children = dir.listFiles() ?: return
        for (child in children) {
            val name = child.name
            if (name.startsWith(".")) continue

            if (child.isDirectory) {
                val lowerName = name.lowercase()
                if (lowerName in skippedDirectoryNames) continue

                if (lowerName == "data" || lowerName == "obb") {
                    if (child.parentFile?.name?.equals("Android", ignoreCase = true) == true) {
                        continue
                    }
                }

                collectDocumentFilesRecursively(child, outMap, visitedDirs)
            } else if (child.isFile) {
                val ext = child.extension.lowercase()
                if (ext == "pdf" || ext == "epub") {
                    outMap.putIfAbsent(child.absolutePath, child)
                }
            }
        }
    }

    private fun collectFromMediaStore(outMap: MutableMap<String, File>) {
        runCatching {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            val selection = "${MediaStore.MediaColumns.MIME_TYPE} IN (?, ?) OR " +
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf(
                "application/pdf",
                "application/epub+zip",
                "%.pdf",
                "%.epub"
            )

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                ?.use { cursor ->
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx >= 0) {
                        while (cursor.moveToNext()) {
                            val path = cursor.getString(dataIdx)
                            if (!path.isNullOrBlank()) {
                                val file = File(path)
                                if (file.exists() && file.isFile && file.canRead()) {
                                    val ext = file.extension.lowercase()
                                    if (ext == "pdf" || ext == "epub") {
                                        outMap.putIfAbsent(file.absolutePath, file)
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun resolveFileFromUri(uri: Uri): File? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            return File(path).takeIf { it.exists() }
        }

        val docIdPath = tryResolveRealPathFromContentUri(uri)
        if (docIdPath != null && docIdPath.exists() && docIdPath.canRead()) {
            return docIdPath
        }

        val displayName = queryDisplayName(uri) ?: "belge_${System.currentTimeMillis()}.pdf"
        val manualDir = File(context.filesDir, "manual_books").apply { mkdirs() }
        val targetFile = File(manualDir, displayName)

        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.takeIf { it.exists() && it.length() > 0 }
        }.getOrNull()
    }

    private fun tryResolveRealPathFromContentUri(uri: Uri): File? {
        return runCatching {
            val pathSegment = uri.path ?: return null
            val primaryAfterColon = pathSegment.substringAfter("primary:", missingDelimiterValue = "")
            if (primaryAfterColon.isNotEmpty()) {
                val candidate = File(Environment.getExternalStorageDirectory(), primaryAfterColon)
                if (candidate.exists()) return candidate
            }
            null
        }.getOrNull()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else null
                }
        }.getOrNull()
    }

    private fun beautifyFileName(rawName: String): String {
        return rawName
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { rawName }
    }
}
