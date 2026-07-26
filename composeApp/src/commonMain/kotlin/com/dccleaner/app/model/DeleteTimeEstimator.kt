package com.dccleaner.app.model

object DeleteTimeEstimator {
    fun estimateRemainingSeconds(
        elapsedMillis: Long,
        initialDeletedCount: Int,
        initialSkippedCount: Int,
        deletedCount: Int,
        skippedCount: Int,
        totalCount: Int
    ): Long {
        val remainingCount = totalCount - deletedCount - skippedCount
        if (remainingCount <= 0) return 0L

        // A resumed task has cumulative counts from before the restart. Only work
        // completed during this measurement window can be paired with elapsedMillis.
        val measuredDeleted = (deletedCount - initialDeletedCount).coerceAtLeast(0)
        val measuredSkipped = (skippedCount - initialSkippedCount).coerceAtLeast(0)
        val weightedMeasuredCount = measuredDeleted + measuredSkipped * 0.5
        if (weightedMeasuredCount <= 0.0) return 0L

        val averageMillis = elapsedMillis.coerceAtLeast(0L) / weightedMeasuredCount
        return ((averageMillis * remainingCount) / 1000.0).toLong()
    }
}
