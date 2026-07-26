package com.dccleaner.app.platform

import java.util.UUID

actual val currentPlatform: RuntimePlatform = RuntimePlatform(
    family = PlatformFamily.Android,
    name = "Android",
    appDataDescription = "Android app-private storage and encrypted SharedPreferences"
)

actual fun generateDeleteTaskId(): String = UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
