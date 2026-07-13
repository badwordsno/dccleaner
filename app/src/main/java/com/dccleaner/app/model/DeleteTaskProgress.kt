package com.dccleaner.app.model

import java.util.UUID

enum class DeleteTaskState {
    RUNNING,
    PAUSED_BY_USER,
    CAPTCHA_REQUIRED,
    NETWORK_ERROR,
    SERVICE_TIMEOUT,
    INTERRUPTED
}

data class DeleteTaskProgress(
    val id: String = UUID.randomUUID().toString(),
    val loginId: String,
    val deleteType: String,
    val selectedGalleries: List<String>,
    val galleryMap: Map<String, String>,
    @Transient val twoCaptchaApiKey: String = "",
    val recommendFilterEnabled: Boolean = false,
    val commentFilterEnabled: Boolean = false,
    val commentContentFilterEnabled: Boolean = false,
    val dateFilterEnabled: Boolean = false,
    val minRecommendToKeep: Int = -1,
    val minCommentToKeep: Int = -1,
    val myPostFilterEnabled: Boolean = false,
    val dcconOnlyFilterEnabled: Boolean = false,
    val commentRegexFilter: String = "",
    val minPostAgeDaysToDelete: Int = -1,
    val recordGuestbookLog: Boolean = true,
    val currentGalleryIndex: Int = 0,
    val currentGalleryName: String = "",
    val completedGalleries: Int = 0,
    val currentGalleryDeleted: Int = 0,
    val currentGallerySkipped: Int = 0,
    val totalDeleted: Int = 0,
    val queueGalleryIndex: Int = -1,
    val queueSize: Int = 0,
    val queueCursor: Int = 0,
    val hasPersistedQueue: Boolean = false,
    val collectionTotalPages: Int = 0,
    val collectionNextPage: Int = -1,
    val state: DeleteTaskState = DeleteTaskState.RUNNING,
    val statusMessage: String = "",
    val captchaRequired: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
) {
    fun normalizedForExecution(): DeleteTaskProgress {
        val effectiveRecommendFilter = recommendFilterEnabled || minRecommendToKeep >= 0
        val effectiveCommentFilter = commentFilterEnabled || minCommentToKeep >= 0
        val effectiveCommentContentFilter =
            commentContentFilterEnabled || commentRegexFilter.isNotEmpty()
        val effectiveDateFilter = dateFilterEnabled || minPostAgeDaysToDelete >= 0

        return copy(
            recommendFilterEnabled = effectiveRecommendFilter,
            commentFilterEnabled = effectiveCommentFilter,
            commentContentFilterEnabled = effectiveCommentContentFilter,
            dateFilterEnabled = effectiveDateFilter,
            minRecommendToKeep = when {
                minRecommendToKeep >= 0 -> minRecommendToKeep
                effectiveRecommendFilter -> 1
                else -> -1
            },
            minCommentToKeep = when {
                minCommentToKeep >= 0 -> minCommentToKeep
                effectiveCommentFilter -> 1
                else -> -1
            },
            minPostAgeDaysToDelete = when {
                minPostAgeDaysToDelete >= 0 -> minPostAgeDaysToDelete
                effectiveDateFilter -> 5
                else -> -1
            }
        )
    }
}
