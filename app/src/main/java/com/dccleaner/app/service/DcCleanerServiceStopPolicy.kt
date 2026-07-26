package com.dccleaner.app.service

internal const val COMPLETION_NOTIFICATION_DETACH_DELAY_MILLIS = 3_000L
internal const val TASK_REMOVED_INTERRUPTION_MESSAGE =
    "최근 앱에서 앱이 종료되어 삭제 작업이 중단되었습니다. 로그인 후 이어서 진행해 주세요."
internal const val DAEWANGCON_TIMEOUT_MESSAGE =
    "Android 백그라운드 실행 시간 제한으로 작업이 중단되었습니다."
internal const val DAEWANGCON_RECOVERY_FAILURE_MESSAGE =
    "이전 대왕콘 작업이 앱 프로세스 종료로 중단되었습니다."

internal enum class DeletionForegroundStopMode {
    DetachAfterCompletionDelay,
    RemoveImmediately
}

internal enum class DaewangconFinishMode {
    KeepForegroundForDeletion,
    StopForegroundAndService
}

internal fun deletionForegroundStopMode(isCompleted: Boolean): DeletionForegroundStopMode =
    if (isCompleted) {
        DeletionForegroundStopMode.DetachAfterCompletionDelay
    } else {
        DeletionForegroundStopMode.RemoveImmediately
    }

internal fun daewangconFinishMode(isDeleting: Boolean): DaewangconFinishMode =
    if (isDeleting) {
        DaewangconFinishMode.KeepForegroundForDeletion
    } else {
        DaewangconFinishMode.StopForegroundAndService
    }
