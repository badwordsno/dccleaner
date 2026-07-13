package com.dccleaner.app.model

data class SavedAccount(
    val id: String,
    val password: String,
    val nickname: String = ""
)
