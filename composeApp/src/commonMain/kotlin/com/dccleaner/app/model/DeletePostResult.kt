package com.dccleaner.app.model

data class DeletePostResult(
    val status: Boolean,
    val data: String,
    val postData: PostDeleteData?
)
