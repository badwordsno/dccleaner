package com.dccleaner.app.platform

enum class PlatformFamily {
    Android,
    Desktop
}

data class RuntimePlatform(
    val family: PlatformFamily,
    val name: String,
    val appDataDescription: String
)

interface ExternalNavigator {
    fun openUrl(url: String): Boolean
}

interface SecureAccountRepository {
    fun getSavedAccountIds(): List<String>
}

expect val currentPlatform: RuntimePlatform

expect fun generateDeleteTaskId(): String

expect fun currentTimeMillis(): Long
