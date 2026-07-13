package com.dccleaner.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

class WakeLockManager(
    private val context: Context
) {
    private var wakeLock: PowerManager.WakeLock? = null

    val isHeld: Boolean
        get() = wakeLock?.isHeld == true

    @SuppressLint("WakelockTimeout")
    fun acquire() {
        try {
            if (wakeLock?.isHeld == true) {
                android.util.Log.d("DcCleanerService", "WakeLock already held")
                return
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            // WakeLock is intentionally acquired without a timeout because deletion
            // tasks can run for extended periods. It is always explicitly released
            // in release() or when the service shuts down.
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DcCleaner::DeletionWakeLock"
            ).apply {
                acquire()
            }

            android.util.Log.d("DcCleanerService", "WakeLock acquired")
        } catch (e: Exception) {
            android.util.Log.e("DcCleanerService", "Failed to acquire WakeLock", e)
        }
    }

    fun release() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            android.util.Log.e("DcCleanerService", "Failed to release WakeLock", e)
        }
    }
}
