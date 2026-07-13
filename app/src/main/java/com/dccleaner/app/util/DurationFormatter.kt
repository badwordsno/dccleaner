package com.dccleaner.app.util

fun formatDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val seconds = safeSeconds % 60

    return if (hours > 0) {
        "${hours}시간 ${minutes}분 ${seconds}초"
    } else {
        "${minutes}분 ${seconds}초"
    }
}

fun formatDurationMillis(totalMilliseconds: Long): String {
    val totalSeconds = totalMilliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return when {
        minutes > 0L && seconds > 0L -> "${minutes}분 ${seconds}초"
        minutes > 0L -> "${minutes}분"
        else -> "${seconds}초"
    }
}
