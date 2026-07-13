package com.dccleaner.app.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LogManager(private val context: Context) {
    companion object {
        private const val LOG_FILE_NAME = "dccleaner_log.txt"
        private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB
    }

    private val logFile: File
        get() = File(context.getExternalFilesDir(null), LOG_FILE_NAME)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 로그 메시지 추가
     */
    suspend fun addLog(tag: String, message: String) = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] [$tag] $message\n"
            
            // 파일 크기 체크 및 로테이션
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                rotateLog()
            }
            
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 로그 파일 전체 내용 읽기
     */
    suspend fun readLogs(): String = withContext(Dispatchers.IO) {
        try {
            if (logFile.exists()) {
                logFile.readText()
            } else {
                "로그가 없습니다."
            }
        } catch (e: Exception) {
            "로그 읽기 실패: ${e.message}"
        }
    }

    /**
     * 로그 파일 삭제
     */
    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        try {
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 로그 파일 로테이션 (크기가 커지면 백업하고 새로 시작)
     */
    private fun rotateLog() {
        try {
            val backupFile = File(context.getExternalFilesDir(null), "${LOG_FILE_NAME}.old")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
