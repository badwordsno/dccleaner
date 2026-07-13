package com.dccleaner.app.model

data class PostDetails(
    val recommendCount: Int?,
    val commentCount: Int?
) {
    fun hasCountsRequiredBy(
        recommendFilterEnabled: Boolean,
        commentFilterEnabled: Boolean
    ): Boolean =
        (!recommendFilterEnabled || recommendCount != null) &&
                (!commentFilterEnabled || commentCount != null)
}
