package com.dccleaner.app.model

data class CollectedPost(
    val postNo: String,
    val postUrl: String? = null,
    val isDccon: Boolean = false,
    val text: String = "",
    val date: String? = null
)
