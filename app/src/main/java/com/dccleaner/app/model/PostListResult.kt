package com.dccleaner.app.model

sealed class PostListResult {
    object Blocked : PostListResult()
    object Failed : PostListResult()
    data class Success(val posts: List<String>) : PostListResult()
}
