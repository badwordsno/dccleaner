package com.dccleaner.app.model

object DeleteQueueCheckpoint {
    fun canRestoreQueue(
        hasPersistedQueue: Boolean,
        queueGalleryIndex: Int,
        currentGalleryIndex: Int,
        hasQueueFile: Boolean
    ): Boolean = hasPersistedQueue &&
            queueGalleryIndex == currentGalleryIndex &&
            hasQueueFile

    fun normalizedCursor(cursor: Int, queueSize: Int): Int =
        cursor.coerceIn(0, queueSize.coerceAtLeast(0))

    fun advanceCursor(cursor: Int, queueSize: Int): Int =
        (normalizedCursor(cursor, queueSize) + 1).coerceAtMost(queueSize.coerceAtLeast(0))

    fun shouldSkipQueueItem(itemIndex: Int, cursor: Int): Boolean =
        itemIndex < cursor.coerceAtLeast(0)

    fun findNextMissingPage(totalPages: Int, hasPage: (Int) -> Boolean): Int {
        var page = totalPages.coerceAtLeast(0)
        while (page >= 1 && hasPage(page)) page--
        return page
    }

    fun pagesInQueueOrder(totalPages: Int): IntProgression =
        totalPages.coerceAtLeast(0) downTo 1
}
