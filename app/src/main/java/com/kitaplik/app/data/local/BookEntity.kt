package com.kitaplik.app.data.local

enum class BookFormat {
    PDF,
    EPUB
}

data class BookEntity(
    val id: Long = 0,
    val listId: Long = BookListEntity.DEFAULT_BOOKS_LIST_ID,
    val title: String,
    val author: String? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val filePath: String,
    val format: BookFormat,
    val totalPages: Int,
    val lastReadPage: Int = 0,
    val lastReadTimestamp: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val fileSizeBytes: Long = 0L,
    val isManuallyAdded: Boolean = false,
    val detectionScore: Int = 0,
    val detectionReason: String = ""
) {
    val progressPercent: Int
        get() = if (totalPages > 0) {
            (((lastReadPage + 1).coerceAtMost(totalPages) * 100) / totalPages)
        } else 0
}
