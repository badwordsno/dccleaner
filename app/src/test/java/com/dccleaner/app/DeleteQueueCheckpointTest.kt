package com.dccleaner.app

import com.dccleaner.app.model.CollectedPost
import com.dccleaner.app.model.DeleteQueueCheckpoint
import com.dccleaner.app.model.DeleteTaskStartValidator
import com.dccleaner.app.model.DeleteTimeEstimator
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskState
import com.dccleaner.app.model.PostDetails
import com.dccleaner.app.network.Cleaner
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteQueueCheckpointTest {
    @Test
    fun selectedGalleriesRequireACompleteCurrentGalleryMap() {
        assertFalse(
            DeleteTaskStartValidator.hasCompleteGalleryMap(
                selectedGalleries = emptyList(),
                galleryMap = emptyMap()
            )
        )
        assertFalse(
            DeleteTaskStartValidator.hasCompleteGalleryMap(
                selectedGalleries = listOf("gallery-a"),
                galleryMap = emptyMap()
            )
        )
        assertFalse(
            DeleteTaskStartValidator.hasCompleteGalleryMap(
                selectedGalleries = listOf("gallery-a", "gallery-b"),
                galleryMap = mapOf("gallery-a" to "A")
            )
        )
        assertTrue(
            DeleteTaskStartValidator.hasCompleteGalleryMap(
                selectedGalleries = listOf("gallery-a", "gallery-b"),
                galleryMap = mapOf("gallery-a" to "A", "gallery-b" to "B")
            )
        )
    }

    @Test
    fun enabledPreservationFilterRequiresItsOwnCountDom() {
        val missingRecommend = PostDetails(recommendCount = null, commentCount = 3)
        val missingComment = PostDetails(recommendCount = 2, commentCount = null)

        assertFalse(missingRecommend.hasCountsRequiredBy(
            recommendFilterEnabled = true,
            commentFilterEnabled = false
        ))
        assertFalse(missingComment.hasCountsRequiredBy(
            recommendFilterEnabled = false,
            commentFilterEnabled = true
        ))
        assertFalse(missingRecommend.hasCountsRequiredBy(
            recommendFilterEnabled = true,
            commentFilterEnabled = true
        ))
        assertFalse(missingComment.hasCountsRequiredBy(
            recommendFilterEnabled = true,
            commentFilterEnabled = true
        ))
    }

    @Test
    fun disabledPreservationFilterDoesNotRequireItsCountDom() {
        val missingRecommend = PostDetails(recommendCount = null, commentCount = 3)
        val missingComment = PostDetails(recommendCount = 2, commentCount = null)

        assertTrue(missingRecommend.hasCountsRequiredBy(
            recommendFilterEnabled = false,
            commentFilterEnabled = true
        ))
        assertTrue(missingComment.hasCountsRequiredBy(
            recommendFilterEnabled = true,
            commentFilterEnabled = false
        ))
    }

    @Test
    fun persistedFilterValuesNormalizeExecutionFlags() {
        val normalized = DeleteTaskProgress(
            loginId = "tester",
            deleteType = "posting",
            selectedGalleries = listOf("gallery-a"),
            galleryMap = mapOf("gallery-a" to "A"),
            recommendFilterEnabled = false,
            commentFilterEnabled = false,
            minRecommendToKeep = 10,
            minCommentToKeep = 20
        ).normalizedForExecution()

        assertTrue(normalized.recommendFilterEnabled)
        assertTrue(normalized.commentFilterEnabled)
        assertEquals(10, normalized.minRecommendToKeep)
        assertEquals(20, normalized.minCommentToKeep)
    }

    @Test
    fun enabledFlagsReceiveSafeDefaultsWhenPersistedValuesAreMissing() {
        val normalized = DeleteTaskProgress(
            loginId = "tester",
            deleteType = "posting",
            selectedGalleries = listOf("gallery-a"),
            galleryMap = mapOf("gallery-a" to "A"),
            recommendFilterEnabled = true,
            commentFilterEnabled = true,
            dateFilterEnabled = true
        ).normalizedForExecution()

        assertEquals(1, normalized.minRecommendToKeep)
        assertEquals(1, normalized.minCommentToKeep)
        assertEquals(5, normalized.minPostAgeDaysToDelete)
    }

    @Test
    fun persistedCommentRegexEnablesContentFilterDuringExecution() {
        val normalized = DeleteTaskProgress(
            loginId = "tester",
            deleteType = "comment",
            selectedGalleries = listOf("gallery-a"),
            galleryMap = mapOf("gallery-a" to "A"),
            commentContentFilterEnabled = false,
            commentRegexFilter = "keep.*this"
        ).normalizedForExecution()

        assertTrue(normalized.commentContentFilterEnabled)
        assertEquals("keep.*this", normalized.commentRegexFilter)
    }

    @Test
    fun newTaskReceivesOnlyNamesForItsCurrentSelection() {
        val selectedMap = DeleteTaskStartValidator.selectedGalleryMap(
            selectedGalleries = listOf("gallery-b"),
            galleryMap = mapOf("gallery-a" to "A", "gallery-b" to "B")
        )

        assertEquals(mapOf("gallery-b" to "B"), selectedMap)
    }

    @Test
    fun queueFileAloneIsNotEnoughToRestoreBeforeMetadataCommit() {
        assertFalse(
            DeleteQueueCheckpoint.canRestoreQueue(
                hasPersistedQueue = false,
                queueGalleryIndex = 2,
                currentGalleryIndex = 2,
                hasQueueFile = true
            )
        )
    }

    @Test
    fun queueRestoresAfterFileAndMetadataAreBothCommitted() {
        assertTrue(
            DeleteQueueCheckpoint.canRestoreQueue(
                hasPersistedQueue = true,
                queueGalleryIndex = 2,
                currentGalleryIndex = 2,
                hasQueueFile = true
            )
        )
    }

    @Test
    fun oneProcessedItemAdvancesCursorExactlyOnce() {
        val queueSize = 4
        val afterFirst = DeleteQueueCheckpoint.advanceCursor(0, queueSize)

        assertEquals(1, afterFirst)
        assertTrue(DeleteQueueCheckpoint.shouldSkipQueueItem(0, afterFirst))
        assertFalse(DeleteQueueCheckpoint.shouldSkipQueueItem(1, afterFirst))

        val remaining = (0 until queueSize)
            .filterNot { DeleteQueueCheckpoint.shouldSkipQueueItem(it, afterFirst) }
        assertEquals(listOf(1, 2, 3), remaining)
    }

    @Test
    fun successThenSkipConsumesTwoDifferentItemsWithoutDoubleAdvance() {
        val queueSize = 4
        val afterSuccess = DeleteQueueCheckpoint.advanceCursor(0, queueSize)
        val afterSkip = DeleteQueueCheckpoint.advanceCursor(afterSuccess, queueSize)

        assertEquals(1, afterSuccess)
        assertEquals(2, afterSkip)
        assertEquals(
            listOf(2, 3),
            (0 until queueSize)
                .filterNot { DeleteQueueCheckpoint.shouldSkipQueueItem(it, afterSkip) }
        )
    }

    @Test
    fun savedPagesTenNineEightResumeFromSeven() {
        val savedPages = setOf(10, 9, 8)
        val next = DeleteQueueCheckpoint.findNextMissingPage(10, savedPages::contains)

        assertEquals(7, next)
        assertEquals((10 downTo 1).toList(), DeleteQueueCheckpoint.pagesInQueueOrder(10).toList())
    }

    @Test
    fun cleanerImportAndSingleRemovalKeepsExactRemainingQueue() {
        val cleaner = Cleaner()
        val queue = listOf(
            CollectedPost("1", "/1", false, "first", "2026-01-01"),
            CollectedPost("2", "/2", true, "second", "2026-01-02"),
            CollectedPost("3", "/3", false, "third", "2026-01-03")
        )

        cleaner.importCollectedPosts(queue)
        cleaner.removeFirstPost()

        assertEquals(listOf("2", "3"), cleaner.exportCollectedPosts().map { it.postNo })
        assertEquals("second", cleaner.getPostText("2"))
        assertTrue(cleaner.isPostDccon("2"))
    }

    @Test
    fun deletionTaskCopyKeepsItsCompleteExecutionEnvironment() {
        val task = DeleteTaskProgress(
            loginId = "tester",
            deleteType = "comment",
            selectedGalleries = listOf("gallery-a", "gallery-b"),
            galleryMap = mapOf("gallery-a" to "A", "gallery-b" to "B"),
            twoCaptchaApiKey = "secret-key",
            recommendFilterEnabled = true,
            commentFilterEnabled = true,
            commentContentFilterEnabled = true,
            dateFilterEnabled = true,
            minRecommendToKeep = 10,
            minCommentToKeep = 20,
            myPostFilterEnabled = true,
            dcconOnlyFilterEnabled = true,
            commentRegexFilter = "test.*comment",
            minPostAgeDaysToDelete = 30
        )

        val resumed = task.copy(state = DeleteTaskState.INTERRUPTED)

        assertEquals("secret-key", resumed.twoCaptchaApiKey)
        assertEquals(listOf("gallery-a", "gallery-b"), resumed.selectedGalleries)
        assertTrue(resumed.recommendFilterEnabled)
        assertTrue(resumed.commentFilterEnabled)
        assertTrue(resumed.commentContentFilterEnabled)
        assertTrue(resumed.dateFilterEnabled)
        assertEquals(10, resumed.minRecommendToKeep)
        assertEquals(20, resumed.minCommentToKeep)
        assertTrue(resumed.myPostFilterEnabled)
        assertTrue(resumed.dcconOnlyFilterEnabled)
        assertEquals("test.*comment", resumed.commentRegexFilter)
        assertEquals(30, resumed.minPostAgeDaysToDelete)

        val progressJson = Gson().toJson(task)
        assertFalse(progressJson.contains("secret-key"))
        assertFalse(progressJson.contains("twoCaptchaApiKey"))
    }

    @Test
    fun cleanerRestoresAndClearsTheTaskCaptchaKeyExactly() {
        val cleaner = Cleaner()

        cleaner.restore2CaptchaKey("task-key")
        assertTrue(cleaner.has2CaptchaKey())

        cleaner.restore2CaptchaKey("")
        assertFalse(cleaner.has2CaptchaKey())
    }

    @Test
    fun resumedDeletionEstimateUsesOnlyWorkMeasuredAfterResume() {
        val estimate = DeleteTimeEstimator.estimateRemainingSeconds(
            elapsedMillis = 5_000L,
            initialDeletedCount = 80,
            initialSkippedCount = 0,
            deletedCount = 81,
            skippedCount = 0,
            totalCount = 100
        )

        assertEquals(95L, estimate)
    }

    @Test
    fun resumedDeletionEstimateWaitsForANewMeasurement() {
        val estimate = DeleteTimeEstimator.estimateRemainingSeconds(
            elapsedMillis = 5_000L,
            initialDeletedCount = 80,
            initialSkippedCount = 0,
            deletedCount = 80,
            skippedCount = 0,
            totalCount = 100
        )

        assertEquals(0L, estimate)
    }
}
