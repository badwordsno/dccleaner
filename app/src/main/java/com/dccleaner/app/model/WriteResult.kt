package com.dccleaner.app.model

sealed class WriteResult {
    object Success : WriteResult()
    data class Failed(val message: String) : WriteResult()
}
