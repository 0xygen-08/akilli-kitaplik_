package com.kitaplik.app.domain.organizer

import android.os.Environment
import com.kitaplik.app.data.local.BookDao
import com.kitaplik.app.data.local.BookListEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class OrganizeResult(
    val listName: String,
    val targetDirectoryPath: String,
    val movedBooksCount: Int,
    val alreadyInFolderCount: Int,
    val failedCount: Int
)

/**
 * Kullanıcının talebi üzerine her listeye özel bir klasör (ör. Documents/Kitaplar,
 * Documents/Ders Notlarım) oluşturur ve sadece o listedeki belgeleri **kopyalamadan**
 * (diskte ikinci bir kopya oluşturmadan, doğrudan dosya taşıma / rename yöntemiyle)
 * o klasörde toparlar, veritabanındaki dosya yollarını günceller.
 */
class BookOrganizer(
    private val bookDao: BookDao
) {

    suspend fun gatherListBooksWithoutCopying(
        targetList: BookListEntity
    ): OrganizeResult = withContext(Dispatchers.IO) {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            ?: File(Environment.getExternalStorageDirectory(), "Documents")

        val sanitizedFolderName = sanitizeFolderName(targetList.name)
        val targetDir = File(docsDir, sanitizedFolderName)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val booksInList = bookDao.getBooksByListIdSnapshot(targetList.id)
        var movedCount = 0
        var alreadyInPlaceCount = 0
        var failedCount = 0

        for (book in booksInList) {
            val sourceFile = File(book.filePath)
            if (!sourceFile.exists()) {
                failedCount++
                continue
            }

            if (sourceFile.parentFile?.canonicalPath == targetDir.canonicalPath) {
                alreadyInPlaceCount++
                continue
            }

            val destinationFile = resolveUniqueDestination(targetDir, sourceFile.name)

            val movedSuccessfully = moveFileWithoutCopy(sourceFile, destinationFile)
            if (movedSuccessfully) {
                bookDao.updateBookFilePath(book.id, destinationFile.absolutePath)
                movedCount++
            } else {
                failedCount++
            }
        }

        OrganizeResult(
            listName = targetList.name,
            targetDirectoryPath = targetDir.absolutePath,
            movedBooksCount = movedCount,
            alreadyInFolderCount = alreadyInPlaceCount,
            failedCount = failedCount
        )
    }

    private fun sanitizeFolderName(rawName: String): String {
        val cleaned = rawName
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.ifEmpty { "Liste_${System.currentTimeMillis()}" }
    }

    private fun moveFileWithoutCopy(source: File, destination: File): Boolean {
        if (source.renameTo(destination)) {
            return true
        }

        return runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            destination.exists() && !source.exists()
        }.getOrDefault(false)
    }

    private fun resolveUniqueDestination(targetDir: File, originalFileName: String): File {
        var candidate = File(targetDir, originalFileName)
        if (!candidate.exists()) return candidate

        val nameWithoutExt = originalFileName.substringBeforeLast('.')
        val ext = originalFileName.substringAfterLast('.', missingDelimiterValue = "")
        val dotExt = if (ext.isNotEmpty()) ".$ext" else ""

        var counter = 2
        while (candidate.exists()) {
            candidate = File(targetDir, "$nameWithoutExt ($counter)$dotExt")
            counter++
        }
        return candidate
    }
}
