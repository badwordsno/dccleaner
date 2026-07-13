package com.dccleaner.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.dccleaner.app.ui.screen.DcinsideScreen
import com.dccleaner.app.ui.theme.DccleanerTheme
import com.dccleaner.app.service.DcCleanerNotifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var resumeTaskId by mutableStateOf<String?>(null)
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "Notification permission granted")
        } else {
            android.util.Log.w("MainActivity", "Notification permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resumeTaskId = intent.getStringExtra(DcCleanerNotifier.EXTRA_RESUME_TASK_ID)
        enableEdgeToEdge()

        // 상태바 아이콘을 어둡게 설정 (밝은 배경에서 보이도록)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            true

        // 알림 권한 요청 (Android 13+)
        requestNotificationPermission()
        
        // 배터리 최적화 예외 요청
        requestBatteryOptimizationExemption()

        setContent {
            DccleanerTheme(darkTheme = false) {
                DcinsideScreen(
                    resumeTaskId = resumeTaskId,
                    onResumeTaskConsumed = {
                        resumeTaskId = null
                        intent.removeExtra(DcCleanerNotifier.EXTRA_RESUME_TASK_ID)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resumeTaskId = intent.getStringExtra(DcCleanerNotifier.EXTRA_RESUME_TASK_ID)
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    android.util.Log.d("MainActivity", "Notification permission already granted")
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
