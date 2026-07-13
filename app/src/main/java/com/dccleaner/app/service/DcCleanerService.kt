package com.dccleaner.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Binder
import android.os.IBinder
import com.dccleaner.app.model.*
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.storage.DeleteTaskStore
import com.dccleaner.app.util.formatDurationMillis
import com.dccleaner.app.util.LogManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DcCleanerService : Service() {
    companion object {

        const val CHANNEL_ID = "DCCLEANER_CHANNEL"
        const val CAPTCHA_CHANNEL_ID = "DCCLEANER_CAPTCHA_CHANNEL"
        const val NOTIFICATION_ID = 1
        const val CAPTCHA_NOTIFICATION_ID = 2

        private const val CAPTCHA_DELETE_INTERVAL = 200
        private const val CHECKPOINT_OPERATION_INTERVAL = 20
        private const val CHECKPOINT_TIME_INTERVAL_MS = 10_000L
        private const val SERVICE_PREFS_NAME = "dc_cleaner_service_state"
        private const val KEY_DAEWANGCON_ACTIVE = "daewangcon_active"
        internal const val DAEWANGCON_POST_INTERVAL_DELAY_MILLIS = 5_000L
        internal const val DAEWANGCON_COMMENT_INTERVAL_DELAY_MILLIS = 0L
        internal const val DAEWANGCON_POST_BATCH_SIZE = 5
        internal const val DAEWANGCON_COMMENT_BATCH_SIZE = 10
        internal const val DAEWANGCON_POST_BATCH_DELAY_MILLIS = 105_000L
        internal const val DAEWANGCON_COMMENT_BATCH_DELAY_MILLIS = 90_000L

        const val ACTION_START_DELETE = "START_DELETE"
        const val ACTION_STOP_DELETE = "STOP_DELETE"
        const val ACTION_START_DAEWANGCON = "START_DAEWANGCON"
        const val ACTION_STOP_DAEWANGCON = "STOP_DAEWANGCON"
    }


    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var deleteJob: Job? = null
    private var daewangconJob: Job? = null
    private var preparedDaewangcon: PreparedDaewangcon? = null
    private val daewangconNotificationLock = Any()
    private val daewangconLogLock = Any()
    private var daewangconNotificationDismissed = false
    private var recoveredDaewangconInterruption = false
    private var cleaner: Cleaner? = null
    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var notifier: DcCleanerNotifier
    private var notificationUpdateJob: Job? = null
    private var lastUpdateTime = System.currentTimeMillis()
    private var serviceStartTime = System.currentTimeMillis()
    private lateinit var logManager: LogManager
    private lateinit var deleteTaskStore: DeleteTaskStore
    private val servicePreferences by lazy {
        getSharedPreferences(SERVICE_PREFS_NAME, MODE_PRIVATE)
    }
    private var currentTask: DeleteTaskProgress? = null
    private var operationsSinceCheckpoint = 0
    private var lastCheckpointAt = 0L


    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()


    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentGallery = MutableStateFlow("")
    val currentGallery: StateFlow<String> = _currentGallery.asStateFlow()

    private val _currentGalleryEstimatedTimeLeft = MutableStateFlow(0L)
    val currentGalleryEstimatedTimeLeft: StateFlow<Long> = _currentGalleryEstimatedTimeLeft.asStateFlow()

    private val _nextCaptchaEstimatedTimeLeft = MutableStateFlow(0L)
    val nextCaptchaEstimatedTimeLeft: StateFlow<Long> = _nextCaptchaEstimatedTimeLeft.asStateFlow()

    private val _isTwoCaptchaConfigured = MutableStateFlow(false)
    val isTwoCaptchaConfigured: StateFlow<Boolean> = _isTwoCaptchaConfigured.asStateFlow()

    private val _currentTaskLoginId = MutableStateFlow("")
    val currentTaskLoginId: StateFlow<String> = _currentTaskLoginId.asStateFlow()

    private val _currentDeleteType = MutableStateFlow("")
    val currentDeleteType: StateFlow<String> = _currentDeleteType.asStateFlow()

    private var captchaCycleDeletedCount = 0
    private var captchaTotalDeletionMillis = 0L
    private var captchaDeleteAttemptStartedAt = 0L


    private val _deletedCount = MutableStateFlow(0)
    val deletedCount: StateFlow<Int> = _deletedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()


    private val _deleteLog = MutableStateFlow<List<String>>(emptyList())
    val deleteLog: StateFlow<List<String>> = _deleteLog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showCaptchaDialog = MutableStateFlow(false)
    val showCaptchaDialog: StateFlow<Boolean> = _showCaptchaDialog.asStateFlow()

    private val _captchaFlag = MutableStateFlow(false)
    val captchaFlag: StateFlow<Boolean> = _captchaFlag.asStateFlow()

    // 대왕콘 관련 상태
    private val _isDaewangconRunning = MutableStateFlow(false)
    val isDaewangconRunning: StateFlow<Boolean> = _isDaewangconRunning.asStateFlow()

    private val _isDaewangconCompleted = MutableStateFlow(false)
    val isDaewangconCompleted: StateFlow<Boolean> = _isDaewangconCompleted.asStateFlow()

    private val _daewangconErrorMessage = MutableStateFlow<String?>(null)
    val daewangconErrorMessage: StateFlow<String?> = _daewangconErrorMessage.asStateFlow()

    private val _daewangconProgress = MutableStateFlow(0f)
    val daewangconProgress: StateFlow<Float> = _daewangconProgress.asStateFlow()

    private val _daewangconLog = MutableStateFlow<List<String>>(emptyList())
    val daewangconLog: StateFlow<List<String>> = _daewangconLog.asStateFlow()

    private val _daewangconPostCount = MutableStateFlow(0)
    val daewangconPostCount: StateFlow<Int> = _daewangconPostCount.asStateFlow()

    private val _daewangconCommentCount = MutableStateFlow(0)
    val daewangconCommentCount: StateFlow<Int> = _daewangconCommentCount.asStateFlow()

    // 삭제 조건 설정 (-1: 조건 무시, 0 이상: 해당 값 이상이면 보존)
    private var minRecommendToKeep: Int = -1
    private var minCommentToKeep: Int = -1
    private var myPostFilterEnabled: Boolean = false
    private var dcconOnlyFilterEnabled: Boolean = false
    private var commentRegexFilter: String = ""
    private var minPostAgeDaysToDelete: Int = -1

    // 캡챠 에러 확인 함수
    private fun isCaptchaError(message: String): Boolean {
        return message.contains("captcha", ignoreCase = true) ||
                message.contains("g-recaptcha", ignoreCase = true) ||
                message.contains("recaptcha", ignoreCase = true)
    }


    inner class LocalBinder : Binder() {
        fun getService(): DcCleanerService = this@DcCleanerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        logManager = LogManager(applicationContext)
        deleteTaskStore = DeleteTaskStore(applicationContext)
        notifier = DcCleanerNotifier(applicationContext)
        wakeLockManager = WakeLockManager(applicationContext)
        notifier.createNotificationChannel()
        recoverInterruptedDaewangconIfNeeded()
        serviceScope.launch {
            logManager.addLog("Service", "DcCleanerService created")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = if (intent?.action == ACTION_START_DAEWANGCON) {
            notifier.createDaewangconNotification()
        } else {
            notifier.createNotification("삭제 작업 준비 중...")
        }
        startForeground(NOTIFICATION_ID, initialNotification)

        // 주기적으로 알림 업데이트 (시스템에 살아있음을 알림)
        startPeriodicNotificationUpdate()

        if (intent == null) {
            val wasDaewangconActive = recoveredDaewangconInterruption
            // START_STICKY로 프로세스만 복원된 경우 메모리의 로그인 세션과 작업은 복원할 수 없다.
            deleteTaskStore.getAll()
                .filter {
                    it.state == DeleteTaskState.RUNNING ||
                            it.state == DeleteTaskState.CAPTCHA_REQUIRED
                }
                .forEach { task ->
                    deleteTaskStore.updateState(
                        task.id,
                        DeleteTaskState.INTERRUPTED,
                        "앱 프로세스가 종료되어 작업이 중단되었습니다. 로그인 후 이어서 진행해 주세요."
                    )
                    notifier.showInterruptionNotification(
                        task.id,
                        "삭제 작업이 중단되었습니다",
                        "로그인 후 저장된 지점부터 이어서 진행할 수 있습니다."
                    )
                }
            stopDeletion(cancelNotification = true, preserveTask = true)
            if (wasDaewangconActive) notifier.showDaewangconFailedNotification()
            recoveredDaewangconInterruption = false
            return START_NOT_STICKY
        }

        intent.action?.let { action ->
            when (action) {
                ACTION_START_DELETE -> {

                    if (deleteJob?.isActive != true) {

                    }
                }

                ACTION_STOP_DELETE -> stopDeletion()
                ACTION_START_DAEWANGCON -> startPreparedDaewangcon()

                ACTION_STOP_DAEWANGCON -> stopDaewangcon()
            }
        }

        // 서비스가 강제 종료되어도 재시작되도록
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val task = currentTask
        if (deleteJob?.isActive == true &&
            (task?.state == DeleteTaskState.RUNNING || task?.state == DeleteTaskState.CAPTCHA_REQUIRED)
        ) {
            val message = "최근 앱에서 앱이 종료되어 삭제 작업이 중단되었습니다. 로그인 후 이어서 진행해 주세요."
            pauseCurrentTask(
                DeleteTaskState.INTERRUPTED,
                message,
                notify = false
            )
            runCatching {
                notifier.showInterruptionNotification(
                    task.id,
                    "삭제 작업이 중단되었습니다",
                    message
                )
            }
            notifier.cancelCaptchaNotification()
            deleteJob?.cancel()
            deleteJob = null
            cancelNotificationUpdate()
            _isDeleting.value = false
            if (daewangconJob?.isActive != true) wakeLockManager.release()
            stopForegroundCompat(removeNotification = true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (deleteJob?.isActive == true &&
            (currentTask?.state == DeleteTaskState.RUNNING ||
                    currentTask?.state == DeleteTaskState.CAPTCHA_REQUIRED)
        ) {
            pauseCurrentTask(
                DeleteTaskState.INTERRUPTED,
                "서비스가 종료되어 작업이 중단되었습니다.",
                notify = false
            )
        }
        super.onDestroy()
        android.util.Log.d("DcCleanerService", "Service destroyed")
        cancelNotificationUpdate()
        wakeLockManager.release()
        deleteJob?.cancel()
        daewangconJob?.cancel()
        serviceScope.cancel()
        // START_STICKY ensures the system restarts the service automatically if killed.
        // Explicit restartForegroundService from onDestroy is unreliable on Android 12+.
    }

    private fun startPeriodicNotificationUpdate() {
        cancelNotificationUpdate()
        serviceStartTime = System.currentTimeMillis()
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(30000) // 30초마다 업데이트
                currentCoroutineContext().ensureActive()
                when {
                    _isDaewangconRunning.value -> {
                        notifier.updateDaewangconNotification()
                        logManager.addLog("Service", "Daewangcon notification updated")
                    }
                    _isDeleting.value -> {
                        val contentText = getDeletionNotificationText()
                        notifier.updateNotification(contentText)
                        logManager.addLog("Service", "Deletion notification updated - $contentText")
                    }
                }
            }
        }
    }

    private fun getDeletionNotificationText(): String {
        val totalGalleries = _totalCount.value
        val completedGalleries = _deletedCount.value

        return if (totalGalleries > 0) {
            "총 ${totalGalleries}개 갤러리 중 ${completedGalleries}개 삭제 완료"
        } else {
            "갤러리 수집중..."
        }
    }

    fun setCleaner(cleaner: Cleaner) {
        this.cleaner = cleaner
        _isTwoCaptchaConfigured.value = cleaner.has2CaptchaKey()
    }

    fun prepareDaewangcon(
        cleaner: Cleaner,
        galleryId: String,
        postNo: String,
        postSubject: String,
        postContent: String,
        commentContent: String
    ) {
        setCleaner(cleaner)
        preparedDaewangcon = PreparedDaewangcon(
            galleryId, postNo, postSubject, postContent, commentContent
        )
    }

    fun clearPreparedDaewangcon() {
        preparedDaewangcon = null
    }

    private fun startPreparedDaewangcon() {
        val prepared = preparedDaewangcon
        preparedDaewangcon = null
        if (prepared == null) {
            _isDaewangconRunning.value = false
            _daewangconErrorMessage.value = "대왕콘 작업 시작 정보를 불러오지 못했습니다."
            cancelNotificationUpdate()
            stopForegroundCompat(removeNotification = true)
            notifier.showDaewangconFailedNotification()
            stopSelf()
            return
        }
        try {
            startDaewangcon(
                prepared.galleryId,
                prepared.postNo,
                prepared.postSubject,
                prepared.postContent,
                prepared.commentContent
            )
        } catch (e: RuntimeException) {
            _isDaewangconRunning.value = false
            _daewangconErrorMessage.value =
                "대왕콘 작업을 시작하지 못했습니다: ${e.message ?: "알 수 없는 오류"}"
            markDaewangconActive(false)
            cancelNotificationUpdate()
            wakeLockManager.release()
            stopForegroundCompat(removeNotification = true)
            notifier.showDaewangconFailedNotification()
            stopSelf()
        }
    }

    fun isDeleting(): Boolean = deleteJob?.isActive == true

    fun getCurrentTaskLoginId(): String = _currentTaskLoginId.value

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearLogs() {
        _deleteLog.value = emptyList()
    }

    fun resolveCaptcha() {
        android.util.Log.d("DcCleanerService", "resolveCaptcha called - 캡챠 플래그 해제")
        serviceScope.launch {
            logManager.addLog("Service", "사용자가 캡챠 해결 완료 버튼 클릭")
        }
        _captchaFlag.value = false
        _showCaptchaDialog.value = false
        notifier.cancelCaptchaNotification()
        resetCaptchaEstimateCycle()
        cleaner?.resetCaptchaState()
        if (!updateTask(force = true) {
            it.copy(
                state = DeleteTaskState.RUNNING,
                statusMessage = "캡챠 해결 완료, 삭제 재개 중",
                captchaRequired = false
            )
        }) return
        addLogMessage("✅ 캡챠 해결 완료 - 삭제 재개")
        android.util.Log.d("DcCleanerService", "Captcha flags cleared - captchaFlag: ${_captchaFlag.value}, showDialog: ${_showCaptchaDialog.value}")
    }


    fun startDeletion(
        selectedGalleries: List<String>,
        deleteType: String,
        galleryMap: Map<String, String>,
        twoCaptchaApiKey: String = "",
        recommendFilterEnabled: Boolean = false,
        commentFilterEnabled: Boolean = false,
        commentContentFilterEnabled: Boolean = false,
        dateFilterEnabled: Boolean = false,
        minRecommendToKeep: Int = -1,
        minCommentToKeep: Int = -1,
        myPostFilterEnabled: Boolean = false,
        dcconOnlyFilterEnabled: Boolean = false,
        commentRegexFilter: String = "",
        minPostAgeDaysToDelete: Int = -1,
        recordGuestbookLog: Boolean = true
    ) {
        if (deleteJob?.isActive == true) return

        val task = DeleteTaskProgress(
            loginId = cleaner?.getUserId().orEmpty(),
            deleteType = deleteType,
            selectedGalleries = selectedGalleries,
            galleryMap = galleryMap,
            twoCaptchaApiKey = twoCaptchaApiKey,
            recommendFilterEnabled = recommendFilterEnabled,
            commentFilterEnabled = commentFilterEnabled,
            commentContentFilterEnabled = commentContentFilterEnabled,
            dateFilterEnabled = dateFilterEnabled,
            minRecommendToKeep = minRecommendToKeep,
            minCommentToKeep = minCommentToKeep,
            myPostFilterEnabled = myPostFilterEnabled,
            dcconOnlyFilterEnabled = dcconOnlyFilterEnabled,
            commentRegexFilter = commentRegexFilter,
            minPostAgeDaysToDelete = minPostAgeDaysToDelete,
            recordGuestbookLog = recordGuestbookLog
        ).normalizedForExecution()
        applyTaskSettings(task)
        beginDeletion(task)
    }

    fun resumeDeletion(task: DeleteTaskProgress) {
        if (deleteJob?.isActive == true) return
        val normalizedTask = task.normalizedForExecution().copy(
            state = DeleteTaskState.RUNNING,
            statusMessage = "삭제 작업 재개 중",
            captchaRequired = false
        )
        applyTaskSettings(normalizedTask)
        beginDeletion(normalizedTask)
    }

    private fun applyTaskSettings(task: DeleteTaskProgress) {
        cleaner?.restore2CaptchaKey(task.twoCaptchaApiKey)
        minRecommendToKeep = task.minRecommendToKeep
        minCommentToKeep = task.minCommentToKeep
        myPostFilterEnabled = task.myPostFilterEnabled
        dcconOnlyFilterEnabled = task.dcconOnlyFilterEnabled
        commentRegexFilter = task.commentRegexFilter
        minPostAgeDaysToDelete = task.minPostAgeDaysToDelete
    }

    private fun beginDeletion(task: DeleteTaskProgress) {
        if (!wakeLockManager.isHeld) wakeLockManager.acquire()
        _currentTaskLoginId.value = task.loginId
        _currentDeleteType.value = task.deleteType
        currentTask = task
        operationsSinceCheckpoint = 0
        if (!deleteTaskStore.save(task)) {
            handleTaskPersistenceFailure("삭제 작업을 저장하지 못해 시작하지 않았습니다.")
            return
        }
        lastCheckpointAt = System.currentTimeMillis()
        resetDeletionState()
        _isTwoCaptchaConfigured.value = cleaner?.has2CaptchaKey() == true
        if (!_isTwoCaptchaConfigured.value) {
            resetCaptchaEstimateCycle()
        }

        deleteJob = serviceScope.launch {
            logManager.addLog("Delete", "선택된 갤러리: ${task.selectedGalleries.size}개, 타입: ${task.deleteType}")
            try {
                performDeletion(task)
                if (currentTask?.state != DeleteTaskState.RUNNING && currentTask != null) {
                    cancelNotificationUpdate()
                    _isDeleting.value = false
                    deleteJob = null
                    if (daewangconJob?.isActive != true) wakeLockManager.release()
                    stopForegroundCompat(removeNotification = true)
                    stopSelf()
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (e: Exception) {
                logManager.addLog("Delete", "ERROR: ${e.message}")
                handleDeletionError(e)
                cancelNotificationUpdate()
                deleteJob = null
                if (daewangconJob?.isActive != true) wakeLockManager.release()
                stopForegroundCompat(removeNotification = true)
                stopSelf()
            }
        }
    }

    fun stopDeletion(cancelNotification: Boolean = true, preserveTask: Boolean = false) {
        if (!preserveTask && deleteJob?.isActive == true) {
            pauseCurrentTask(
                DeleteTaskState.PAUSED_BY_USER,
                "사용자가 작업을 중단했습니다.",
                notify = false
            )
        }
        cancelNotificationUpdate()
        notifier.cancelCaptchaNotification()
        deleteJob?.cancel()
        deleteJob = null
        _isDeleting.value = false
        if (daewangconJob?.isActive != true) wakeLockManager.release()
        if (cancelNotification) {
            stopForegroundCompat(removeNotification = true)
            notifier.cancelNotification()
        } else {
            stopForegroundCompat(removeNotification = false)
        }
        stopSelf()
    }

    fun startDaewangcon(
        galleryId: String,
        postNo: String,
        postSubject: String,
        postContent: String,
        commentContent: String
    ) {
        if (daewangconJob?.isActive == true) return

        if (!wakeLockManager.isHeld) wakeLockManager.acquire()

        _isDaewangconRunning.value = true
        _isDaewangconCompleted.value = false
        _daewangconErrorMessage.value = null
        synchronized(daewangconNotificationLock) {
            daewangconNotificationDismissed = false
        }
        _daewangconProgress.value = 0f
        _daewangconLog.value = emptyList()
        _daewangconPostCount.value = 0
        _daewangconCommentCount.value = 0
        markDaewangconActive(true)
        notifier.updateDaewangconNotification()

        daewangconJob = serviceScope.launch {
            logManager.addLog("Daewangcon", "대왕콘 시작 - 갤러리: $galleryId, 글번호: $postNo")
            try {
                performDaewangcon(galleryId, postNo, postSubject, postContent, commentContent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logManager.addLog("Daewangcon", "ERROR: ${e.message}")
                val message = e.message ?: "알 수 없는 오류"
                _daewangconErrorMessage.value = message
                addDaewangconLog("❌ 오류: $message")
            } finally {
                finishDaewangcon()
            }
        }
    }

    fun stopDaewangcon() {
        preparedDaewangcon = null
        val job = daewangconJob
        if (job?.isActive == true) {
            _isDaewangconRunning.value = false
            addDaewangconLog("🛑 사용자에 의해 작업이 중단됨")
            job.cancel()
        } else {
            finishDaewangcon()
        }
    }

    private fun finishDaewangcon() {
        daewangconJob = null
        markDaewangconActive(false)
        _isDaewangconRunning.value = false
        if (deleteJob?.isActive != true) {
            cancelNotificationUpdate()
            wakeLockManager.release()
            stopForegroundCompat(removeNotification = true)
            synchronized(daewangconNotificationLock) {
                if (_isDaewangconCompleted.value && !daewangconNotificationDismissed) {
                    notifier.showDaewangconCompletedNotification()
                } else if (_daewangconErrorMessage.value != null && !daewangconNotificationDismissed) {
                    notifier.showDaewangconFailedNotification()
                } else {
                    notifier.cancelNotification()
                }
            }
            stopSelf()
        }
    }

    fun dismissDaewangconNotification() {
        synchronized(daewangconNotificationLock) {
            daewangconNotificationDismissed = true
            notifier.cancelNotification()
        }
        _isDaewangconCompleted.value = false
        _daewangconErrorMessage.value = null
        recoveredDaewangconInterruption = false
        if (deleteJob?.isActive != true && daewangconJob?.isActive != true) stopSelf()
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun markDaewangconActive(active: Boolean) {
        servicePreferences.edit().putBoolean(KEY_DAEWANGCON_ACTIVE, active).commit()
    }

    private fun recoverInterruptedDaewangconIfNeeded() {
        if (!servicePreferences.getBoolean(KEY_DAEWANGCON_ACTIVE, false)) return
        markDaewangconActive(false)
        recoveredDaewangconInterruption = true
        _isDaewangconRunning.value = false
        _isDaewangconCompleted.value = false
        _daewangconErrorMessage.value =
            "이전 대왕콘 작업이 앱 프로세스 종료로 중단되었습니다."
        notifier.showDaewangconFailedNotification()
    }

    private fun addDaewangconLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        synchronized(daewangconLogLock) {
            _daewangconLog.value = _daewangconLog.value + "[$timestamp] $message"
        }
        serviceScope.launch {
            logManager.addLog("Daewangcon", message)
        }
    }

    private fun createDaewangconText(count: Int): String {
        val timeSlot = (System.currentTimeMillis() % 100_000).toInt()
        val idTag = timeSlot.toString(16).padStart(5, '0')
        return "$count - 디시클린어 모바일 [$idTag]"
    }

    private suspend fun performDaewangcon(
        galleryId: String,
        postNo: String,
        postSubject: String,
        postContent: String,
        commentContent: String
    ) {
        val cleaner = this.cleaner ?: run {
            _daewangconErrorMessage.value = "로그인 정보를 불러오지 못했습니다."
            addDaewangconLog("❌ 로그인 정보를 불러오지 못했습니다.")
            return
        }

        addDaewangconLog("🎯 대왕콘 얻기 시작")
        lastUpdateTime = System.currentTimeMillis()

        val totalTasks = 30 // 글 10개 + 댓글 20개
        var completedTasks = 0
        val progressLock = Any()

        fun recordDaewangconProgress() {
            synchronized(progressLock) {
                completedTasks++
                _daewangconProgress.value = completedTasks.toFloat() / totalTasks.toFloat()
            }
        }

        coroutineScope {
            launch {
                // 글 10개 작성
                addDaewangconLog("📝 글 작성 시작 (10개)")
                for (i in 1..10) {
                    currentCoroutineContext().ensureActive()

                    // 설정된 글 개수만큼 작성한 후 묶음 대기
                    if (i > 1 &&
                        (i - 1) % DAEWANGCON_POST_BATCH_SIZE == 0 &&
                        DAEWANGCON_POST_BATCH_DELAY_MILLIS > 0L
                    ) {
                        val waitTime = formatDurationMillis(DAEWANGCON_POST_BATCH_DELAY_MILLIS)
                        addDaewangconLog("⏳ $waitTime 대기 중... (글쓰기 제한)")
                        delay(DAEWANGCON_POST_BATCH_DELAY_MILLIS)
                    }

                    val postText = createDaewangconText(i)
                    addDaewangconLog("📝 글 작성 중... ($i/10) [$postText]")
                    val writeResult = cleaner.writePost("kingcon", postText, postText)

                    when (writeResult) {
                        is WriteResult.Success -> {
                            _daewangconPostCount.value += 1
                            recordDaewangconProgress()
                            addDaewangconLog("✅ 글 작성 완료 ($i/10)")
                        }

                        is WriteResult.Failed -> {
                            addDaewangconLog("❌ 글 작성 실패 ($i/10): ${writeResult.message}")
                        }
                    }

                    // 다음 글까지 설정된 간격만큼 대기
                    if (i < 10 && DAEWANGCON_POST_INTERVAL_DELAY_MILLIS > 0L) {
                        delay(DAEWANGCON_POST_INTERVAL_DELAY_MILLIS)
                    }
                }

                addDaewangconLog("✅ 글 작성 완료!")
            }

            launch {
                // 댓글 20개 작성
                addDaewangconLog("💬 댓글 작성 시작 (20개)")
                for (i in 1..20) {
                    currentCoroutineContext().ensureActive()

                    // 설정된 댓글 개수만큼 작성한 후 묶음 대기
                    if (i > 1 &&
                        (i - 1) % DAEWANGCON_COMMENT_BATCH_SIZE == 0 &&
                        DAEWANGCON_COMMENT_BATCH_DELAY_MILLIS > 0L
                    ) {
                        val waitTime = formatDurationMillis(DAEWANGCON_COMMENT_BATCH_DELAY_MILLIS)
                        addDaewangconLog("⏳ $waitTime 대기 중... (댓글 제한)")
                        delay(DAEWANGCON_COMMENT_BATCH_DELAY_MILLIS)
                    }

                    val postText = createDaewangconText(i)
                    addDaewangconLog("💬 댓글 작성 중... ($i/20) [$postText]")
                    val commentResult = cleaner.writeComment("kingcon", "1400", postText)

                    when (commentResult) {
                        is WriteResult.Success -> {
                            _daewangconCommentCount.value += 1
                            recordDaewangconProgress()
                            addDaewangconLog("✅ 댓글 작성 완료 ($i/20)")
                        }

                        is WriteResult.Failed -> {
                            addDaewangconLog("❌ 댓글 작성 실패 ($i/20): ${commentResult.message}")
                        }
                    }

                    // 다음 댓글까지 설정된 간격만큼 대기
                    if (i < 20 && DAEWANGCON_COMMENT_INTERVAL_DELAY_MILLIS > 0L) {
                        delay(DAEWANGCON_COMMENT_INTERVAL_DELAY_MILLIS)
                    }
                }

                addDaewangconLog("✅ 댓글 작성 완료!")
            }
        }

        val postCount = _daewangconPostCount.value
        val commentCount = _daewangconCommentCount.value
        if (postCount == 10 && commentCount == 20) {
            addDaewangconLog("🎁 대왕콘 설정 요청 중...")
            when (val setBigconResult = cleaner.setBigcon()) {
                is WriteResult.Success -> {
                    addDaewangconLog("🎉 대왕콘 작업 완료! 글 10개, 댓글 20개 작성")
                    _isDaewangconCompleted.value = true
                }

                is WriteResult.Failed -> {
                    val message = "대왕콘 설정 요청에 실패했습니다: ${setBigconResult.message}"
                    _daewangconErrorMessage.value = message
                    addDaewangconLog("❌ $message")
                }
            }
        } else {
            val message = "일부 작성에 실패했습니다. (글 $postCount/10, 댓글 $commentCount/20)"
            _daewangconErrorMessage.value = message
            addDaewangconLog("❌ $message")
        }
        _isDaewangconRunning.value = false
    }

    private fun resetDeletionState() {
        _isDeleting.value = true
        _isCompleted.value = false
        _errorMessage.value = null
        _progress.value = 0f
        _currentGallery.value = ""
        _currentGalleryEstimatedTimeLeft.value = 0L
        _nextCaptchaEstimatedTimeLeft.value = 0L
        captchaCycleDeletedCount = 0
        captchaTotalDeletionMillis = 0L
        captchaDeleteAttemptStartedAt = 0L
        _deletedCount.value = 0
        _totalCount.value = 0
        _deleteLog.value = emptyList()
        _showCaptchaDialog.value = false
        _captchaFlag.value = false
        lastUpdateTime = System.currentTimeMillis()

        // WakeLock 재획득 (혹시 해제되었을 경우)
        if (!wakeLockManager.isHeld) {
            wakeLockManager.release()
            wakeLockManager.acquire()
        }
    }

    private fun handleDeletionError(exception: Exception) {
        _errorMessage.value = "삭제 중 오류 발생: ${exception.message}"
        _isDeleting.value = false
        addLogMessage("❌ 오류: ${exception.message}")
        pauseCurrentTask(
            DeleteTaskState.NETWORK_ERROR,
            "오류로 작업이 중단되었습니다: ${exception.message ?: "알 수 없는 오류"}"
        )
        serviceScope.launch {
            logManager.addLog("Delete", "ERROR: ${exception.stackTraceToString()}")
        }
    }

    @Synchronized
    private fun updateTask(
        force: Boolean = false,
        transform: (DeleteTaskProgress) -> DeleteTaskProgress
    ): Boolean {
        val updated = currentTask?.let(transform) ?: return false
        currentTask = updated
        operationsSinceCheckpoint++
        val now = System.currentTimeMillis()
        if (force || operationsSinceCheckpoint >= CHECKPOINT_OPERATION_INTERVAL ||
            now - lastCheckpointAt >= CHECKPOINT_TIME_INTERVAL_MS
        ) {
            if (!deleteTaskStore.save(updated)) {
                handleTaskPersistenceFailure()
                return false
            }
            operationsSinceCheckpoint = 0
            lastCheckpointAt = now
        }
        return true
    }

    @Synchronized
    private fun pauseCurrentTask(
        state: DeleteTaskState,
        message: String,
        captchaRequired: Boolean = false,
        notify: Boolean = true
    ): Boolean {
        val task = currentTask ?: return false
        val paused = task.copy(
            state = state,
            statusMessage = message,
            captchaRequired = captchaRequired
        )
        currentTask = paused
        if (!deleteTaskStore.save(paused)) {
            handleTaskPersistenceFailure()
            return false
        }
        if (notify) {
            val title = when (state) {
                DeleteTaskState.CAPTCHA_REQUIRED -> "캡챠 해결이 필요합니다"
                DeleteTaskState.SERVICE_TIMEOUT -> "백그라운드 작업 시간이 만료되었습니다"
                DeleteTaskState.NETWORK_ERROR -> "네트워크 오류로 삭제가 중단되었습니다"
                else -> "삭제 작업이 중단되었습니다"
            }
            notifier.showInterruptionNotification(task.id, title, message)
        }
        return true
    }

    private fun handleTaskPersistenceFailure(
        message: String = "삭제 작업 상태를 저장하지 못해 안전을 위해 작업을 중단했습니다."
    ) {
        val task = currentTask
        currentTask = task?.copy(
            state = DeleteTaskState.INTERRUPTED,
            statusMessage = message,
            captchaRequired = false
        )
        _errorMessage.value = message
        _isDeleting.value = false
        _captchaFlag.value = false
        _showCaptchaDialog.value = false
        cancelNotificationUpdate()
        notifier.cancelCaptchaNotification()
        task?.let {
            runCatching {
                notifier.showInterruptionNotification(
                    it.id,
                    "삭제 작업 저장 실패",
                    message
                )
            }
        }
        deleteJob?.cancel()
        deleteJob = null
        if (daewangconJob?.isActive != true) wakeLockManager.release()
        stopForegroundCompat(removeNotification = true)
        stopSelf()
    }

    private fun cancelNotificationUpdate() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val deletionTimedOut = deleteJob?.isActive == true
        val daewangconTimedOut = daewangconJob?.isActive == true

        if (deletionTimedOut) {
            pauseCurrentTask(
                DeleteTaskState.SERVICE_TIMEOUT,
                "Android 백그라운드 실행 시간 제한에 도달했습니다. 앱을 열어 이어서 진행해 주세요."
            )
        }
        if (daewangconTimedOut) {
            val message = "Android 백그라운드 실행 시간 제한으로 작업이 중단되었습니다."
            _isDaewangconRunning.value = false
            _isDaewangconCompleted.value = false
            _daewangconErrorMessage.value = message
            addDaewangconLog("❌ $message")
            markDaewangconActive(false)
        }

        deleteJob?.cancel()
        deleteJob = null
        daewangconJob?.cancel()
        daewangconJob = null
        _isDeleting.value = false
        _captchaFlag.value = false
        _showCaptchaDialog.value = false
        cancelNotificationUpdate()
        notifier.cancelCaptchaNotification()
        wakeLockManager.release()
        stopForegroundCompat(removeNotification = true)
        notifier.cancelNotification()
        if (daewangconTimedOut) {
            synchronized(daewangconNotificationLock) {
                if (!daewangconNotificationDismissed) {
                    notifier.showDaewangconFailedNotification()
                }
            }
        }
        stopSelf(startId)
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH
            )
        } else {
            stopForeground(removeNotification)
        }
    }

    private fun addLogMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _deleteLog.value = _deleteLog.value + "[$timestamp] $message"
        serviceScope.launch {
            logManager.addLog("Delete", message)
        }
    }

    private fun updateLastLogMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val timestampedMessage = "[$timestamp] $message"
        val currentLogs = _deleteLog.value.toMutableList()
        if (currentLogs.isNotEmpty()) {
            currentLogs[currentLogs.lastIndex] = timestampedMessage
            _deleteLog.value = currentLogs
        } else {
            _deleteLog.value = listOf(timestampedMessage)
        }
    }

    private fun updateProgress(
        deleted: Int,
        total: Int,
        galleryName: String
    ) {
        _progress.value = deleted.toFloat() / total.toFloat()
        _currentGallery.value = galleryName
        _deletedCount.value = deleted
    }

    private fun updateCurrentGalleryEstimatedTimeLeft(
        startedAt: Long,
        initialDeletedCount: Int,
        initialSkippedCount: Int,
        deletedCount: Int,
        skippedCount: Int,
        total: Int
    ) {
        _currentGalleryEstimatedTimeLeft.value = DeleteTimeEstimator.estimateRemainingSeconds(
            elapsedMillis = System.currentTimeMillis() - startedAt,
            initialDeletedCount = initialDeletedCount,
            initialSkippedCount = initialSkippedCount,
            deletedCount = deletedCount,
            skippedCount = skippedCount,
            totalCount = total
        )
    }

    private fun checkpointItemProgress(
        galleryDeleted: Int,
        gallerySkipped: Int,
        totalDeleted: Int,
        force: Boolean = false
    ): Boolean {
        return updateTask(force = force) {
            it.copy(
                currentGalleryDeleted = galleryDeleted,
                currentGallerySkipped = gallerySkipped,
                totalDeleted = totalDeleted,
                queueCursor = DeleteQueueCheckpoint.advanceCursor(it.queueCursor, it.queueSize),
                state = DeleteTaskState.RUNNING,
                statusMessage = "${it.currentGalleryName} 처리 중"
            )
        }
    }

    private fun resetCaptchaEstimateCycle() {
        captchaCycleDeletedCount = 0
        captchaTotalDeletionMillis = 0L
        captchaDeleteAttemptStartedAt = 0L
        _nextCaptchaEstimatedTimeLeft.value = 0L
    }

    private fun markCaptchaDeletionAttemptStarted() {
        if (_isTwoCaptchaConfigured.value) return
        captchaDeleteAttemptStartedAt = System.currentTimeMillis()
    }

    private fun recordSuccessfulDeletionForCaptchaEstimate() {
        if (_isTwoCaptchaConfigured.value) return

        val requestMillis = if (captchaDeleteAttemptStartedAt > 0L) {
            (System.currentTimeMillis() - captchaDeleteAttemptStartedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        captchaTotalDeletionMillis += requestMillis + Cleaner.POST_REQUEST_DELAY
        captchaDeleteAttemptStartedAt = 0L
        captchaCycleDeletedCount++
        val remaining = (CAPTCHA_DELETE_INTERVAL - captchaCycleDeletedCount)
            .coerceAtLeast(0)
        val averageMillis = captchaTotalDeletionMillis / captchaCycleDeletedCount.coerceAtLeast(1)
        _nextCaptchaEstimatedTimeLeft.value = if (remaining == 0) {
            -1L
        } else {
            averageMillis * remaining / 1000L
        }
    }

    private suspend fun performDeletion(initialTask: DeleteTaskProgress) {
        val cleaner = this.cleaner ?: return

        val selectedGalleries = initialTask.selectedGalleries
        val deleteType = initialTask.deleteType
        val galleryMap = initialTask.galleryMap

        if (deleteType == "comment" &&
            initialTask.commentContentFilterEnabled &&
            commentRegexFilter.isBlank()
        ) {
            pauseCurrentTask(
                DeleteTaskState.INTERRUPTED,
                "댓글 내용 필터가 비어 있어 삭제 작업을 시작하지 않았습니다."
            )
            _isDeleting.value = false
            return
        }

        addLogMessage("🚀 백그라운드 삭제 작업 시작")
        lastUpdateTime = System.currentTimeMillis()

        val totalGalleries = selectedGalleries.size
        var completedGalleries = initialTask.completedGalleries
        var totalDeleted = initialTask.totalDeleted

        // 전체 갤러리 수 설정 (진행률 표시용)
        _totalCount.value = totalGalleries
        _deletedCount.value = completedGalleries
        _progress.value = if (totalGalleries > 0) {
            completedGalleries.toFloat() / totalGalleries
        } else 0f
        notifier.updateNotification(getDeletionNotificationText())
        addLogMessage("📊 총 $totalGalleries 개 갤러리를 처리합니다")

        // 각 갤러리를 순차적으로 처리 (불러오기 → 삭제)
        for (index in initialTask.currentGalleryIndex until selectedGalleries.size) {
            val gno = selectedGalleries[index]
            currentCoroutineContext().ensureActive()

            val galleryName = galleryMap[gno] ?: "갤러리 #${index + 1}"
            val savedTask = currentTask ?: return
            val canRestoreQueue = DeleteQueueCheckpoint.canRestoreQueue(
                hasPersistedQueue = savedTask.hasPersistedQueue,
                queueGalleryIndex = savedTask.queueGalleryIndex,
                currentGalleryIndex = index,
                hasQueueFile = deleteTaskStore.hasQueue(savedTask.id)
            )
            _currentGalleryEstimatedTimeLeft.value = 0L
            if (!updateTask(force = true) {
                it.copy(
                    currentGalleryIndex = index,
                    currentGalleryName = galleryName,
                    completedGalleries = completedGalleries,
                    currentGalleryDeleted = if (canRestoreQueue) it.currentGalleryDeleted else 0,
                    currentGallerySkipped = if (canRestoreQueue) it.currentGallerySkipped else 0,
                    totalDeleted = totalDeleted,
                    state = DeleteTaskState.RUNNING,
                    statusMessage = if (canRestoreQueue) {
                        "$galleryName 저장 큐 복원 중"
                    } else {
                        "$galleryName 수집 중"
                    },
                    captchaRequired = false
                )
            }) return

            try {
                val postCount: Int
                var galleryDeleted: Int
                var gallerySkipped: Int

                if (canRestoreQueue) {
                    val taskWithQueue = currentTask ?: return
                    val remainingQueue = deleteTaskStore.loadRemainingQueue(
                        taskWithQueue.id,
                        taskWithQueue.queueCursor
                    )
                    val queueIsConsistent = remainingQueue != null &&
                            taskWithQueue.queueCursor in 0..taskWithQueue.queueSize &&
                            remainingQueue.size + taskWithQueue.queueCursor == taskWithQueue.queueSize
                    if (!queueIsConsistent) {
                        pauseCurrentTask(
                            DeleteTaskState.INTERRUPTED,
                            "$galleryName 저장 큐를 읽지 못했습니다. 작업 기록을 확인해 주세요."
                        )
                        _isDeleting.value = false
                        return
                    }
                    val restoredQueue = remainingQueue!!
                    cleaner.importCollectedPosts(restoredQueue)
                    postCount = taskWithQueue.queueSize
                    galleryDeleted = taskWithQueue.currentGalleryDeleted
                    gallerySkipped = taskWithQueue.currentGallerySkipped
                    addLogMessage(
                        "📦 $galleryName 저장 큐 복원: ${restoredQueue.size}개 남음 " +
                                "(${taskWithQueue.queueCursor}/${taskWithQueue.queueSize} 처리됨)"
                    )
                } else {
                    val taskId = currentTask?.id ?: return
                    val resumableCollection = savedTask.queueGalleryIndex == index &&
                            savedTask.collectionTotalPages > 0 &&
                            savedTask.collectionNextPage >= 0
                    cleaner.clearPostData()
                    deleteTaskStore.deleteQueue(taskId)

                    val totalPages: Int
                    var nextPage: Int
                    if (resumableCollection) {
                        totalPages = savedTask.collectionTotalPages
                        nextPage = deleteTaskStore.findNextMissingCollectedPage(taskId, totalPages)
                        if (nextPage != savedTask.collectionNextPage) {
                            if (!updateTask(force = true) {
                                it.copy(collectionNextPage = nextPage)
                            }) return
                        }
                        addLogMessage(
                            "📦 $galleryName 수집 이어하기: ${totalPages - nextPage}/$totalPages 페이지 저장됨"
                        )
                    } else {
                        deleteTaskStore.deleteCollectedPages(taskId)
                        totalPages = cleaner.getPageCount(gno, deleteType)
                        if (totalPages <= 0) {
                            pauseCurrentTask(
                                DeleteTaskState.NETWORK_ERROR,
                                "$galleryName 페이지 수를 불러오지 못했습니다. 네트워크 상태를 확인해 주세요."
                            )
                            _isDeleting.value = false
                            return
                        }
                        nextPage = totalPages
                        if (!updateTask(force = true) {
                            it.copy(
                                queueGalleryIndex = index,
                                queueSize = 0,
                                queueCursor = 0,
                                hasPersistedQueue = false,
                                collectionTotalPages = totalPages,
                                collectionNextPage = nextPage,
                                currentGalleryDeleted = 0,
                                currentGallerySkipped = 0
                            )
                        }) return
                    }

                    // 페이지별로 원자 저장하여 수집 도중 종료되어도 완료한 페이지는 다시 요청하지 않는다.
                    while (nextPage >= 1 && currentCoroutineContext().isActive) {
                        val currentPage = totalPages - nextPage + 1
                        updateLastLogMessage("📋 $galleryName: $currentPage/$totalPages 페이지 로딩 중...")
                        delay(Cleaner.PAGE_REQUEST_DELAY)
                        when (val pageResult = cleaner.getPostList(gno, deleteType, nextPage)) {
                            is PostListResult.Success -> {
                                val pagePosts = cleaner.exportCollectedPosts(pageResult.posts)
                                if (!deleteTaskStore.saveCollectedPage(taskId, nextPage, pagePosts)) {
                                    pauseCurrentTask(
                                        DeleteTaskState.INTERRUPTED,
                                        "$galleryName ${nextPage}페이지 수집 결과를 저장하지 못했습니다."
                                    )
                                    _isDeleting.value = false
                                    return
                                }
                                nextPage--
                                if (!updateTask(force = true) {
                                    it.copy(
                                        collectionTotalPages = totalPages,
                                        collectionNextPage = nextPage,
                                        statusMessage = "$galleryName 수집 중 ($currentPage/$totalPages 페이지 저장)"
                                    )
                                }) return
                            }

                            is PostListResult.Blocked, PostListResult.Failed -> {
                                pauseCurrentTask(
                                    DeleteTaskState.NETWORK_ERROR,
                                    "$galleryName ${nextPage}페이지 수집이 중단되었습니다. 이어하기 시 이 페이지부터 재개합니다."
                                )
                                _isDeleting.value = false
                                return
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()

                    val collectedQueue = deleteTaskStore.loadCollectedPages(taskId, totalPages)
                    if (collectedQueue == null) {
                        pauseCurrentTask(
                            DeleteTaskState.INTERRUPTED,
                            "$galleryName 페이지별 수집 데이터를 합치지 못했습니다."
                        )
                        _isDeleting.value = false
                        return
                    }
                    if (!deleteTaskStore.saveQueue(taskId, collectedQueue)) {
                        pauseCurrentTask(
                            DeleteTaskState.INTERRUPTED,
                            "$galleryName 수집 결과를 저장하지 못해 작업을 중단했습니다."
                        )
                        _isDeleting.value = false
                        return
                    }
                    postCount = collectedQueue.size
                    galleryDeleted = 0
                    gallerySkipped = 0
                    cleaner.importCollectedPosts(collectedQueue)
                    if (!updateTask(force = true) {
                        it.copy(
                            queueGalleryIndex = index,
                            queueSize = postCount,
                            queueCursor = 0,
                            hasPersistedQueue = true,
                            collectionTotalPages = 0,
                            collectionNextPage = -1,
                            statusMessage = "$galleryName 삭제 큐 저장 완료 ($postCount 개)"
                        )
                    }) return
                    deleteTaskStore.deleteCollectedPages(taskId)
                }

                if (cleaner.getPostListSize() == 0) {
                    updateLastLogMessage("📋 $galleryName: 포스트 없음")
                    completedGalleries++
                    _progress.value = completedGalleries.toFloat() / totalGalleries.toFloat()
                    _currentGallery.value = galleryName
                    _deletedCount.value = completedGalleries
                    if (!updateTask(force = true) {
                        it.copy(
                            currentGalleryIndex = index + 1,
                            completedGalleries = completedGalleries,
                            currentGalleryDeleted = 0,
                            currentGallerySkipped = 0,
                            totalDeleted = totalDeleted,
                            queueGalleryIndex = -1,
                            queueSize = 0,
                            queueCursor = 0,
                            hasPersistedQueue = false,
                            collectionTotalPages = 0,
                            collectionNextPage = -1,
                            statusMessage = "$galleryName 처리 완료"
                        )
                    }) return
                    currentTask?.id?.let(deleteTaskStore::deleteQueue)
                    continue
                }

                updateLastLogMessage(
                    "✅ $galleryName: ${cleaner.getPostListSize()}개 삭제 대기 " +
                            "(전체 큐 $postCount 개)"
                )

                // 2. 현재 갤러리의 포스트 삭제
                addLogMessage("🗑️ $galleryName 삭제 시작 ($postCount 개)")

                var skipLineAdded = false
                val currentGalleryStartedAt = System.currentTimeMillis()
                val initialGalleryDeleted = galleryDeleted
                val initialGallerySkipped = gallerySkipped
                while (cleaner.getPostListSize() > 0 && currentCoroutineContext().isActive) {
                    val postNo = cleaner.getFirstPost() ?: break

                    // 날짜 필터: 글/댓글 공통 AND 조건. 기준 미달 또는 날짜 파싱 실패 시 삭제하지 않음.
                    if (minPostAgeDaysToDelete >= 0) {
                        val ageDays = cleaner.getPostAgeDays(postNo)
                        val shouldDeleteByDate = ageDays != null && ageDays >= minPostAgeDaysToDelete

                        if (!shouldDeleteByDate) {
                            gallerySkipped++
                            if (skipLineAdded) {
                                updateLastLogMessage("⏭️ $galleryName: $gallerySkipped 개 건너뜀")
                            } else {
                                addLogMessage("⏭️ $galleryName: $gallerySkipped 개 건너뜀")
                                skipLineAdded = true
                            }
                            logManager.addLog(
                                "Service",
                                "날짜 필터 불일치 - 건너뛰기 - postNo: $postNo, ageDays: ${ageDays ?: "unknown"}, minDays: $minPostAgeDaysToDelete"
                            )

                            cleaner.removeFirstPost()
                            if (!checkpointItemProgress(
                                    galleryDeleted,
                                    gallerySkipped,
                                    totalDeleted
                                )
                            ) return
                            updateCurrentGalleryEstimatedTimeLeft(
                                currentGalleryStartedAt,
                                initialGalleryDeleted,
                                initialGallerySkipped,
                                galleryDeleted,
                                gallerySkipped,
                                postCount
                            )

                            continue
                        }
                    }

                    // 추천수/댓글수 필터: 글 작업에서만 적용
                    if (deleteType == "posting" && (minRecommendToKeep >= 0 || minCommentToKeep >= 0)) {
                        val postUrl = cleaner.getPostUrl(postNo)
                        val postDetails = if (postUrl == null) {
                            null
                        } else {
                            delay(Cleaner.POST_REQUEST_DELAY)
                            logManager.addLog("Service", "글 상세 정보 확인 중 - postNo: $postNo")
                            cleaner.getPostDetails(postUrl)
                        }

                        if (postDetails == null || !postDetails.hasCountsRequiredBy(
                                recommendFilterEnabled = initialTask.recommendFilterEnabled,
                                commentFilterEnabled = initialTask.commentFilterEnabled
                            )
                        ) {
                            gallerySkipped++
                            if (skipLineAdded) {
                                updateLastLogMessage("⏭️ $galleryName: $gallerySkipped 개 건너뜀")
                            } else {
                                addLogMessage("⏭️ $galleryName: $gallerySkipped 개 건너뜀")
                                skipLineAdded = true
                            }
                            logManager.addLog(
                                "Service",
                                "글 상세 정보 확인 실패 - 건너뛰기 - postNo: $postNo, url: ${postUrl ?: "unknown"}"
                            )

                            cleaner.removeFirstPost()
                            if (!checkpointItemProgress(
                                    galleryDeleted,
                                    gallerySkipped,
                                    totalDeleted
                                )
                            ) return
                            updateCurrentGalleryEstimatedTimeLeft(
                                currentGalleryStartedAt,
                                initialGalleryDeleted,
                                initialGallerySkipped,
                                galleryDeleted,
                                gallerySkipped,
                                postCount
                            )

                            continue
                        }

                        logManager.addLog("Service", "글 정보 - postNo: $postNo, 추천: ${postDetails.recommendCount}, 댓글: ${postDetails.commentCount}")
                        val shouldSkip =
                            (minRecommendToKeep >= 0 &&
                                    postDetails.recommendCount?.let { it >= minRecommendToKeep } == true) ||
                                    (minCommentToKeep >= 0 &&
                                            postDetails.commentCount?.let { it >= minCommentToKeep } == true)

                        if (shouldSkip) {
                            gallerySkipped++
                            if (skipLineAdded) {
                                updateLastLogMessage("⏭️ $galleryName: $gallerySkipped 개 건너뜀")
                            } else {
                                addLogMessage("⏭️ $galleryName: $gallerySkipped 개 건너뜀")
                                skipLineAdded = true
                            }
                            logManager.addLog("Service", "글 건너뛰기 - postNo: $postNo (조건 만족)")

                            cleaner.removeFirstPost()
                            if (!checkpointItemProgress(
                                    galleryDeleted,
                                    gallerySkipped,
                                    totalDeleted
                                )
                            ) return
                            updateCurrentGalleryEstimatedTimeLeft(
                                currentGalleryStartedAt,
                                initialGalleryDeleted,
                                initialGallerySkipped,
                                galleryDeleted,
                                gallerySkipped,
                                postCount
                            )

                            continue
                        }
                    }

                    // 댓글 필터 (OR 로직): 활성화된 필터 중 하나라도 해당되면 삭제
                    // 필터가 하나도 활성화되지 않으면 전부 삭제
                    if (deleteType == "comment" &&
                        (myPostFilterEnabled || dcconOnlyFilterEnabled || initialTask.commentContentFilterEnabled)
                    ) {
                        var matchesAny = false

                        // 1. 디시콘 필터 (로컬 체크, 비용 없음 - 먼저 체크)
                        if (!matchesAny && dcconOnlyFilterEnabled) {
                            if (cleaner.isPostDccon(postNo)) {
                                matchesAny = true
                            }
                        }

                        // 2. 정규식 필터 (로컬 체크, 비용 없음)
                        if (!matchesAny && initialTask.commentContentFilterEnabled) {
                            val postText = cleaner.getPostText(postNo)
                            val regexResult = runCatching {
                                val matchResult = Regex("^/(.+)/([a-zA-Z]*)$").find(commentRegexFilter)
                                if (matchResult != null) {
                                    val (pattern, flags) = matchResult.destructured
                                    val options = buildSet {
                                        if ('i' in flags) add(RegexOption.IGNORE_CASE)
                                        if ('m' in flags) add(RegexOption.MULTILINE)
                                        if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
                                    }
                                    Regex(pattern, options)
                                } else {
                                    Regex(commentRegexFilter)
                                }
                            }.getOrNull()
                            if (regexResult != null && regexResult.containsMatchIn(postText)) {
                                matchesAny = true
                            }
                        }

                        // 3. 내 글 필터 (네트워크 요청 필요 - 저렴한 필터로 결정 안 됐을 때만 체크)
                        if (!matchesAny && myPostFilterEnabled) {
                            val postUrl = cleaner.getPostUrl(postNo)
                            if (postUrl != null) {
                                delay(Cleaner.POST_REQUEST_DELAY)
                                serviceScope.launch { logManager.addLog("Service", "글 작성자 UID 확인 중 - postNo: $postNo") }
                                val writerUid = cleaner.getPostWriterUid(postUrl)
                                serviceScope.launch { logManager.addLog("Service", "글 작성자 UID - postNo: $postNo, uid: $writerUid") }
                                if (!writerUid.isNullOrEmpty() && writerUid == cleaner.getUserId()) {
                                    matchesAny = true
                                }
                            }
                        }

                        if (!matchesAny) {
                            gallerySkipped++
                            if (skipLineAdded) {
                                updateLastLogMessage("⏭️ $galleryName: $gallerySkipped 개 건너뜀")
                            } else {
                                addLogMessage("⏭️ $galleryName: $gallerySkipped 개 건너뜀")
                                skipLineAdded = true
                            }
                            serviceScope.launch { logManager.addLog("Service", "필터 불일치 - 건너뛰기 - postNo: $postNo") }

                            cleaner.removeFirstPost()
                            if (!checkpointItemProgress(
                                    galleryDeleted,
                                    gallerySkipped,
                                    totalDeleted
                                )
                            ) return
                            updateCurrentGalleryEstimatedTimeLeft(
                                currentGalleryStartedAt,
                                initialGalleryDeleted,
                                initialGallerySkipped,
                                galleryDeleted,
                                gallerySkipped,
                                postCount
                            )

                            continue
                        }
                    }

                    // 먼저 일반 삭제 시도
                    skipLineAdded = false
                    logManager.addLog("Service", "글 삭제 시도 - postNo: $postNo (일반)")
                    markCaptchaDeletionAttemptStarted()
                    var deleteResult = cleaner.deletePost(postNo, deleteType, solveCaptcha = false)

                    // 캡챠 에러 발생 시 처리
                    if (deleteResult is DeleteResult.Error && isCaptchaError(deleteResult.message)) {
                        var captchaSolved = false
                        val maxRetries = if (_isTwoCaptchaConfigured.value) 3 else 0
                        var retryCount = 0

                        // 2captcha로 최대 3번까지 자동 해결 시도
                        while (retryCount < maxRetries && !captchaSolved) {
                            retryCount++
                            addLogMessage("⚠️ 캡챠 감지됨 - 자동 해결 시도 ($retryCount/$maxRetries)")
                            logManager.addLog("Service", "캡챠 감지 - postNo: $postNo, 2captcha로 해결 시도 ($retryCount/$maxRetries)")

                            deleteResult = cleaner.deletePost(postNo, deleteType, solveCaptcha = true)

                            if (deleteResult is DeleteResult.Success) {
                                serviceScope.launch { logManager.addLog("Service", "캡챠 해결 성공 - postNo: $postNo 삭제 완료 (시도: $retryCount)") }
                                captchaSolved = true

                                galleryDeleted++
                                totalDeleted++
                                if (!checkpointItemProgress(
                                        galleryDeleted,
                                        gallerySkipped,
                                        totalDeleted,
                                        force = true
                                    )
                                ) return
                                cleaner.removeFirstPost()
                                recordSuccessfulDeletionForCaptchaEstimate()

                                // 갤러리 진행률 업데이트
                                _progress.value =
                                    (completedGalleries + (galleryDeleted.toFloat() / postCount)) / totalGalleries
                                _currentGallery.value = galleryName
                                _deletedCount.value = completedGalleries
                                updateCurrentGalleryEstimatedTimeLeft(
                                    currentGalleryStartedAt,
                                    initialGalleryDeleted,
                                    initialGallerySkipped,
                                    galleryDeleted,
                                    gallerySkipped,
                                    postCount
                                )

                                addLogMessage("✅ 캡챠 자동 해결 성공 - $galleryName: $galleryDeleted/$postCount 삭제 완료")

                                delay(2000) // 캡챠 해결 후 안정화 대기
                                break // while 루프 탈출
                            } else if (deleteResult is DeleteResult.Error) {
                                logManager.addLog("Service", "2captcha 실패 - 시도 $retryCount/$maxRetries")
                                if (retryCount < maxRetries) {
                                    addLogMessage("⚠️ 2captcha 실패 - 재시도 중... ($retryCount/$maxRetries)")
                                    delay(3000) // 재시도 전 대기
                                }
                            } else {
                                // Failed 또는 Blocked
                                logManager.addLog("Service", "삭제 결과 Failed/Blocked - 재시도 중단")
                                break
                            }
                        }

                        // 2captcha가 없거나 자동 해결에 실패하면 수동 해결 대기
                        if (!captchaSolved && deleteResult is DeleteResult.Error) {
                            if (_isTwoCaptchaConfigured.value) {
                                addLogMessage("⚠️ 2captcha 3번 실패 - 수동 해결 필요")
                                logManager.addLog("Service", "2captcha $maxRetries 번 실패 - postNo: $postNo, 수동 해결 대기")
                            } else {
                                addLogMessage("⚠️ 캡챠 감지됨 - 수동 해결 필요")
                                logManager.addLog("Service", "2captcha 미설정 - postNo: $postNo, 수동 해결 대기")
                            }
                            android.util.Log.d("DcCleanerService", "Showing captcha dialog - waiting for manual solve")
                            _captchaFlag.value = true
                            _showCaptchaDialog.value = true
                            _nextCaptchaEstimatedTimeLeft.value = 0L
                            if (!pauseCurrentTask(
                                DeleteTaskState.CAPTCHA_REQUIRED,
                                "캡챠 해결 후 앱에서 작업을 이어서 진행해 주세요.",
                                captchaRequired = true,
                                notify = false
                            )) return
                            notifier.showCaptchaNotification(currentTask?.id)
                            logManager.addLog("Service", "캡챠 다이얼로그 표시 - 사용자 해결 대기 중")

                            if (daewangconJob?.isActive != true) wakeLockManager.release()

                            while (_captchaFlag.value && currentCoroutineContext().isActive) {
                                android.util.Log.d("DcCleanerService", "Waiting for captcha resolve... (captchaFlag: ${_captchaFlag.value})")
                                delay(1000)
                            }

                            if (currentCoroutineContext().isActive && !_captchaFlag.value) {
                                if (!wakeLockManager.isHeld) wakeLockManager.acquire()
                                logManager.addLog("Service", "수동 캡챠 해결 완료 - 작업 재개")
                                android.util.Log.d("DcCleanerService", "Captcha resolved by user, continuing deletion")
                                delay(2000)
                                if (!updateTask(force = true) {
                                    it.copy(
                                        state = DeleteTaskState.RUNNING,
                                        statusMessage = "캡챠 해결 완료, 삭제 재개 중",
                                        captchaRequired = false
                                    )
                                }) return
                                continue // 수동 해결 후 다시 시도
                            } else {
                                break // 중단됨
                            }
                        } else if (captchaSolved) {
                            continue // 캡챠 자동 해결 성공 - 다음 포스트로
                        } else {
                            // Failed 또는 Blocked는 아래의 공통 실패 처리에서 해당 글만 건너뛴다.
                            logManager.addLog(
                                "Service",
                                "캡챠 처리 중 삭제 실패 - 건너뛰기 준비 - postNo: $postNo"
                            )
                        }
                    }

                    // 일반 삭제 성공 처리
                    if (deleteResult is DeleteResult.Success) {
                        serviceScope.launch { logManager.addLog("Service", "글 삭제 성공 - postNo: $postNo") }
                        galleryDeleted++
                        totalDeleted++
                        if (!checkpointItemProgress(
                                galleryDeleted,
                                gallerySkipped,
                                totalDeleted,
                                force = true
                            )
                        ) return
                        cleaner.removeFirstPost()
                        recordSuccessfulDeletionForCaptchaEstimate()

                        // 갤러리 진행률 업데이트
                        _progress.value =
                            (completedGalleries + (galleryDeleted.toFloat() / postCount)) / totalGalleries
                        _currentGallery.value = galleryName
                        _deletedCount.value = completedGalleries
                        updateCurrentGalleryEstimatedTimeLeft(
                            currentGalleryStartedAt,
                            initialGalleryDeleted,
                            initialGallerySkipped,
                            galleryDeleted,
                            gallerySkipped,
                            postCount
                        )
                        updateLastLogMessage("🗑️ $galleryName: $galleryDeleted/$postCount 삭제 완료")
                    } else {
                        val failureReason = when (deleteResult) {
                            is DeleteResult.Failed -> "네트워크 오류"
                            is DeleteResult.Blocked -> "접속 차단"
                            is DeleteResult.Error -> "삭제 오류: ${deleteResult.message.take(100)}"
                            is DeleteResult.Success -> "알 수 없는 오류"
                        }

                        gallerySkipped++
                        addLogMessage("⏭️ $galleryName: $postNo 삭제 실패로 건너뜀 ($failureReason)")
                        logManager.addLog(
                            "Service",
                            "삭제 실패 - 건너뛰기 - postNo: $postNo, reason: $failureReason"
                        )

                        // 다음 글의 캡챠 예상 시간에 이번 실패 요청 시간이 섞이지 않게 초기화한다.
                        captchaDeleteAttemptStartedAt = 0L
                        if (!checkpointItemProgress(
                                galleryDeleted,
                                gallerySkipped,
                                totalDeleted,
                                force = true
                            )
                        ) return
                        cleaner.removeFirstPost()

                        _progress.value =
                            (completedGalleries +
                                    ((galleryDeleted + gallerySkipped).toFloat() / postCount)) /
                                    totalGalleries
                        _currentGallery.value = galleryName
                        _deletedCount.value = completedGalleries
                        updateCurrentGalleryEstimatedTimeLeft(
                            currentGalleryStartedAt,
                            initialGalleryDeleted,
                            initialGallerySkipped,
                            galleryDeleted,
                            gallerySkipped,
                            postCount
                        )
                    }

                    delay(Cleaner.POST_REQUEST_DELAY)
                }
                currentCoroutineContext().ensureActive()

                if (gallerySkipped > 0) {
                    addLogMessage("✅ $galleryName 완료: $galleryDeleted 개 삭제, $gallerySkipped 개 건너뛰기")
                } else {
                    addLogMessage("✅ $galleryName 완료: $galleryDeleted 개 삭제")
                }
                completedGalleries++

                // 갤러리 완료 후 진행률 업데이트
                _progress.value = completedGalleries.toFloat() / totalGalleries.toFloat()
                _currentGallery.value = galleryName
                _deletedCount.value = completedGalleries
                _currentGalleryEstimatedTimeLeft.value = 0L
                notifier.updateNotification(getDeletionNotificationText())
                if (!updateTask(force = true) {
                    it.copy(
                        currentGalleryIndex = index + 1,
                        completedGalleries = completedGalleries,
                        currentGalleryDeleted = 0,
                        currentGallerySkipped = 0,
                        totalDeleted = totalDeleted,
                        queueGalleryIndex = -1,
                        queueSize = 0,
                        queueCursor = 0,
                        hasPersistedQueue = false,
                        collectionTotalPages = 0,
                        collectionNextPage = -1,
                        statusMessage = "$galleryName 처리 완료"
                    )
                }) return
                currentTask?.id?.let(deleteTaskStore::deleteQueue)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLogMessage("❌ $galleryName 처리 중 오류: ${e.message}")
                pauseCurrentTask(
                    DeleteTaskState.NETWORK_ERROR,
                    "$galleryName 처리 중 오류가 발생했습니다: ${e.message ?: "알 수 없는 오류"}"
                )
                _isDeleting.value = false
                return
            }
        }

        val completedTask = currentTask
        if (completedTask != null && !deleteTaskStore.remove(completedTask.id)) {
            handleTaskPersistenceFailure("완료된 삭제 작업 기록을 정리하지 못했습니다.")
            return
        }
        currentTask = null
        cleaner.clearPostData()

        _isCompleted.value = true
        _isDeleting.value = false
        _currentGalleryEstimatedTimeLeft.value = 0L
        cancelNotificationUpdate()
        notifier.showCompletedNotification("삭제 완료")
        addLogMessage("🎉 모든 작업 완료! 총 $totalDeleted 개 글/댓글 삭제 ($completedGalleries/$totalGalleries 갤러리)")
        if (initialTask.recordGuestbookLog) {
            val deletedPosts = if (deleteType == "posting") totalDeleted else 0
            val deletedComments = if (deleteType == "comment") totalDeleted else 0
            addLogMessage("📝 방명록 가동 기록 작성 중")
            val logged = cleaner.recordCleanerRunGuestbookLog(
                deletedPosts,
                deletedComments,
                onProgress = { message -> addLogMessage(message) }
            )
            if (logged) {
                addLogMessage("✅ 방명록 가동 기록 작성 완료")
            } else {
                addLogMessage("⚠️ 방명록 가동 기록 작성 실패")
            }
        }

        delay(3000) // 3초 후 서비스 종료
        stopDeletion(cancelNotification = false, preserveTask = true)
    }
}

private data class PreparedDaewangcon(
    val galleryId: String,
    val postNo: String,
    val postSubject: String,
    val postContent: String,
    val commentContent: String
)
