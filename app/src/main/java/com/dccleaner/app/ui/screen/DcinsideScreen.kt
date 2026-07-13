package com.dccleaner.app.ui.screen

import com.dccleaner.app.MainActivity
import com.dccleaner.app.model.*
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.service.ServiceManager
import com.dccleaner.app.storage.addSavedAccount
import com.dccleaner.app.storage.getSavedAccounts
import com.dccleaner.app.storage.getSavedTwoCaptchaKey
import com.dccleaner.app.storage.removeSavedAccount
import com.dccleaner.app.storage.removeSavedTwoCaptchaKey
import com.dccleaner.app.storage.saveTwoCaptchaKey
import com.dccleaner.app.storage.DeleteTaskStore
import com.dccleaner.app.ui.card.*
import com.dccleaner.app.ui.cleaner.DcCleanerTabContent
import com.dccleaner.app.ui.dialog.*
import com.dccleaner.app.ui.guestbook.GuestbookTabContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch
import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DcinsideScreen(
    resumeTaskId: String? = null,
    onResumeTaskConsumed: () -> Unit = {}
) {

    val context = LocalContext.current
    val appContext = context.applicationContext
    val cleaner = remember(appContext) { Cleaner(appContext) }
    val serviceManager = remember(appContext) { ServiceManager(appContext) }
    val deleteTaskStore = remember(appContext) { DeleteTaskStore(appContext) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val restoreScrollTopPadding = with(LocalDensity.current) { 72.dp.roundToPx() }
    var captchaSectionScrollY by remember { mutableIntStateOf(0) }
    var deleteOptionsSectionScrollY by remember { mutableIntStateOf(0) }
    var filterOptionsSectionScrollY by remember { mutableIntStateOf(0) }

    suspend fun animateRestoreScrollTo(sectionY: Int) {
        if (sectionY <= 0) return
        scrollState.animateScrollTo(
            value = (sectionY - restoreScrollTopPadding).coerceIn(0, scrollState.maxValue),
            animationSpec = tween(
                durationMillis = 1_200,
                easing = FastOutSlowInEasing
            )
        )
        kotlinx.coroutines.delay(500)
    }


    val uiColors = remember {
        UiColors(
            primary = Color(0xFF2196F3),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFFF5F5F5),
            card = Color.White
        )
    }


    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var loginInfo by remember { mutableStateOf<UserInfo?>(null) }
    var saveLogin by remember { mutableStateOf(true) } // 로그인 정보 저장 여부 (기본값: true)
    var savedAccounts by remember { mutableStateOf<List<SavedAccount>>(emptyList()) }
    var interruptedTasks by remember { mutableStateOf<List<DeleteTaskProgress>>(emptyList()) }
    var isLoggingIn by remember { mutableStateOf(false) } // 로그인 중 상태


    var postingGallList by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var commentGallList by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedGallList by remember { mutableStateOf<List<String>>(emptyList()) }
    var userDataRefreshRequest by remember { mutableIntStateOf(0) }


    var twocaptchaKey by remember { mutableStateOf("") }
    var isTwocaptchaValid by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingTwocaptcha by remember { mutableStateOf(false) }

    var deleteType by remember { mutableStateOf("posting") }
    var deleteLog by remember { mutableStateOf<List<String>>(emptyList()) }

    // 삭제 조건 설정
    var minRecommendToKeep by remember { mutableStateOf("1") }
    var minCommentToKeep by remember { mutableStateOf("1") }
    var recommendFilterEnabled by remember { mutableStateOf(false) }
    var commentFilterEnabled by remember { mutableStateOf(false) }
    var myPostFilterEnabled by remember { mutableStateOf(false) }
    var dcconOnlyFilterEnabled by remember { mutableStateOf(false) }
    var commentContentFilterEnabled by remember { mutableStateOf(false) }
    var commentContentRegex by remember { mutableStateOf("") }
    var dateFilterEnabled by remember { mutableStateOf(false) }
    var minPostAgeDaysToDelete by remember { mutableStateOf("5") }
    var recordGuestbookLog by remember { mutableStateOf(true) }


    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteProgressDialog by remember { mutableStateOf(false) }
    var showStopDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember {
        mutableStateOf(
            deleteTaskStore.getAll().any { task ->
                task.state == DeleteTaskState.RUNNING ||
                        task.state == DeleteTaskState.CAPTCHA_REQUIRED
            }
        )
    }
    var isCompleted by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0f) }
    var currentGallery by remember { mutableStateOf("") }
    var totalPosts by remember { mutableStateOf(0) }
    var deletedPosts by remember { mutableStateOf(0) }
    var estimatedTimeLeft by remember { mutableStateOf(0L) }


    // 캡챠 다이얼로그는 서비스 상태를 직접 사용 (로컬 상태 제거)
    // var showCaptchaDialog by remember { mutableStateOf(false) }
    // var captchaFlag by remember { mutableStateOf(false) }


    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<SavedAccount?>(null) }
    var deleteTaskToRemove by remember { mutableStateOf<DeleteTaskProgress?>(null) }
    var restoringTaskId by remember { mutableStateOf<String?>(null) }
    var activeResumeTaskId by remember { mutableStateOf<String?>(null) }
    var restoringMessage by remember { mutableStateOf("") }

    // 탭 관련 상태
    var selectedTab by remember { mutableStateOf(0) }

    // 대왕콘 관련 상태
    var daewangconGalleryId by remember { mutableStateOf("") }
    var daewangconPostNo by remember { mutableStateOf("") }
    var daewangconPostSubject by remember { mutableStateOf("테스트 제목") }
    var daewangconPostContent by remember { mutableStateOf("테스트 내용") }
    var daewangconCommentContent by remember { mutableStateOf("테스트 댓글") }
    var showDaewangconDialog by remember { mutableStateOf(false) }
    var showDaewangconProgressDialog by remember { mutableStateOf(false) }
    var showStopDaewangconDialog by remember { mutableStateOf(false) }

    // 버전 체크 관련 상태
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var isCheckingVersion by remember { mutableStateOf(false) }


    val isServiceDeleting by serviceManager.isDeleting.collectAsState()
    val isServiceConnected by serviceManager.isServiceConnected.collectAsState()
    val serviceProgress by serviceManager.progress.collectAsState()
    val serviceCurrentGallery by serviceManager.currentGallery.collectAsState()
    val serviceCurrentGalleryEstimatedTimeLeft by serviceManager.currentGalleryEstimatedTimeLeft.collectAsState()
    val serviceNextCaptchaEstimatedTimeLeft by serviceManager.nextCaptchaEstimatedTimeLeft.collectAsState()
    val serviceIsTwoCaptchaConfigured by serviceManager.isTwoCaptchaConfigured.collectAsState()
    val serviceTaskLoginId by serviceManager.currentTaskLoginId.collectAsState()
    val serviceDeleteType by serviceManager.currentDeleteType.collectAsState()
    val serviceDeletedCount by serviceManager.deletedCount.collectAsState()
    val serviceTotalCount by serviceManager.totalCount.collectAsState()
    val serviceDeleteLog by serviceManager.deleteLog.collectAsState()
    val serviceIsCompleted by serviceManager.isCompleted.collectAsState()
    val serviceErrorMessage by serviceManager.errorMessage.collectAsState()
    val serviceShowCaptchaDialog by serviceManager.showCaptchaDialog.collectAsState()
    val serviceCaptchaFlag by serviceManager.captchaFlag.collectAsState()

    // 대왕콘 관련 서비스 상태
    val isDaewangconRunning by serviceManager.isDaewangconRunning.collectAsState()
    val isDaewangconCompleted by serviceManager.isDaewangconCompleted.collectAsState()
    val daewangconErrorMessage by serviceManager.daewangconErrorMessage.collectAsState()
    val daewangconProgress by serviceManager.daewangconProgress.collectAsState()
    val daewangconLog by serviceManager.daewangconLog.collectAsState()
    val daewangconPostCount by serviceManager.daewangconPostCount.collectAsState()
    val daewangconCommentCount by serviceManager.daewangconCommentCount.collectAsState()

    // 방명록 관련 상태
    var guestbookUserListText by remember { mutableStateOf("") }
    var guestbookMessageText by remember { mutableStateOf("") }
    var guestbookShowConfirmDialog by remember { mutableStateOf(false) }
    var guestbookShowResultDialog by remember { mutableStateOf(false) }
    var guestbookIsSending by remember { mutableStateOf(false) }
    var guestbookProgressDone by remember { mutableStateOf(0) }
    var guestbookProgressTotal by remember { mutableStateOf(0) }
    var guestbookSuccessCount by remember { mutableStateOf(0) }
    var guestbookFailCount by remember { mutableStateOf(0) }

    var deleteTaskWasRunning by remember { mutableStateOf(false) }
    var daewangconTaskWasRunning by remember { mutableStateOf(false) }
    var guestbookTaskWasRunning by remember { mutableStateOf(false) }

    fun requestUserDataRefresh() {
        userDataRefreshRequest++
    }

    LaunchedEffect(userDataRefreshRequest) {
        if (userDataRefreshRequest == 0) return@LaunchedEffect

        val uiLoginId = cleaner.getUserId()
        if (uiLoginId.isBlank()) {
            loginInfo = null
            id = serviceTaskLoginId
            postingGallList = emptyMap()
            commentGallList = emptyMap()
            selectedGallList = emptyList()
        } else {
            try {
                loginInfo = cleaner.getUserInfo()

                val postingGallResult = cleaner.getGallList("posting")
                if (postingGallResult is GallListResult.Success) {
                    postingGallList = postingGallResult.galleries
                }

                val commentGallResult = cleaner.getGallList("comment")
                if (commentGallResult is GallListResult.Success) {
                    commentGallList = commentGallResult.galleries
                }

                selectedGallList = if (deleteType == "posting") {
                    postingGallList.keys.toList()
                } else {
                    commentGallList.keys.toList()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DcinsideScreen", "사용자 데이터 새로고침 오류", e)
            }
        }

        interruptedTasks = deleteTaskStore.getForLogin(
            uiLoginId.ifBlank { serviceTaskLoginId }
        )
    }

    LaunchedEffect(isServiceDeleting) {
        if (deleteTaskWasRunning && !isServiceDeleting) {
            requestUserDataRefresh()
        }
        deleteTaskWasRunning = isServiceDeleting
    }

    LaunchedEffect(isDaewangconRunning) {
        if (daewangconTaskWasRunning && !isDaewangconRunning) {
            requestUserDataRefresh()
        }
        daewangconTaskWasRunning = isDaewangconRunning
    }

    LaunchedEffect(guestbookIsSending) {
        if (guestbookTaskWasRunning && !guestbookIsSending) {
            requestUserDataRefresh()
        }
        guestbookTaskWasRunning = guestbookIsSending
    }

    LaunchedEffect(isDaewangconRunning, isDaewangconCompleted, daewangconErrorMessage) {
        if (isDaewangconRunning || isDaewangconCompleted || daewangconErrorMessage != null) {
            showDaewangconProgressDialog = true
        }
    }

    LaunchedEffect(Unit) {
        val savedKey = getSavedTwoCaptchaKey(appContext)
        if (savedKey.isNotBlank()) {
            twocaptchaKey = savedKey
            isTwocaptchaValid = null
            isCheckingTwocaptcha = true
            try {
                isTwocaptchaValid = cleaner.set2CaptchaKey(savedKey)
            } finally {
                isCheckingTwocaptcha = false
            }
        }
    }

    LaunchedEffect(isTwocaptchaValid, twocaptchaKey) {
        if (isTwocaptchaValid == true && twocaptchaKey.isNotBlank()) {
            saveTwoCaptchaKey(appContext, twocaptchaKey)
        }
    }


    DisposableEffect(Unit) {
        serviceManager.bindService()
        // 저장된 계정 정보 로드
        savedAccounts = getSavedAccounts(context)
        onDispose {
            serviceManager.unbindService()
        }
    }

    LaunchedEffect(resumeTaskId) {
        resumeTaskId?.let(deleteTaskStore::get)?.let { task ->
            id = task.loginId
            pw = ""
        }
    }

    // GitHub Releases에서 최신 버전 가져오기
    LaunchedEffect(Unit) {
        isCheckingVersion = true
        try {
            withContext(Dispatchers.IO) {
                val connection = java.net.URL("https://api.github.com/repos/dccleaner3/dccleaner/releases/latest")
                    .openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                val response = try {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
                val json = JSONObject(response)
                latestVersion = json.getString("tag_name").removePrefix("v")
            }
        } catch (e: Exception) {
            Log.w("DcinsideScreen", "Failed to fetch latest version: ${e.message}")
        } finally {
            isCheckingVersion = false
        }
    }


    LaunchedEffect(isServiceDeleting, isServiceConnected) {
        if (!isServiceConnected) return@LaunchedEffect
        if (isServiceDeleting) {
            isDeleting = true
            if (serviceDeleteType == "posting" || serviceDeleteType == "comment") {
                deleteType = serviceDeleteType
            }
            showDeleteConfirmDialog = false
            showDeleteProgressDialog = true
        } else if (!isServiceDeleting && isDeleting && !serviceIsCompleted) {
            isDeleting = false
            restoringTaskId = null
            if (activeResumeTaskId != null) {
                activeResumeTaskId = null
                selectedGallList = if (deleteType == "posting") {
                    postingGallList.keys.toList()
                } else {
                    commentGallList.keys.toList()
                }
            }
            if (loginInfo != null) {
                interruptedTasks = deleteTaskStore.getForLogin(cleaner.getUserId())
            } else if (serviceTaskLoginId.isNotBlank()) {
                id = serviceTaskLoginId
            }
        }
    }

    LaunchedEffect(serviceDeleteType, isServiceDeleting) {
        if (isServiceDeleting &&
            (serviceDeleteType == "posting" || serviceDeleteType == "comment")
        ) {
            deleteType = serviceDeleteType
        }
    }

    LaunchedEffect(serviceTaskLoginId, isServiceDeleting) {
        if (isServiceDeleting && serviceTaskLoginId.isNotBlank()) {
            id = serviceTaskLoginId
        }
    }

    LaunchedEffect(serviceIsCompleted) {
        if (serviceIsCompleted) {
            isCompleted = true
            isDeleting = false
            restoringTaskId = null
            activeResumeTaskId = null
            onResumeTaskConsumed()
            showDeleteProgressDialog = true
        }
    }

    LaunchedEffect(serviceProgress) {
        currentProgress = serviceProgress
    }

    LaunchedEffect(serviceCurrentGallery) {
        currentGallery = serviceCurrentGallery
    }

    LaunchedEffect(serviceCurrentGalleryEstimatedTimeLeft) {
        estimatedTimeLeft = serviceCurrentGalleryEstimatedTimeLeft
    }

    LaunchedEffect(serviceDeletedCount) {
        deletedPosts = serviceDeletedCount
    }

    LaunchedEffect(serviceTotalCount) {
        totalPosts = serviceTotalCount
    }

    LaunchedEffect(serviceDeleteLog) {
        deleteLog = serviceDeleteLog
    }

    LaunchedEffect(serviceErrorMessage) {
        serviceErrorMessage?.let { error ->
            errorMessage = error
            showDeleteProgressDialog = false
            showErrorDialog = true
            serviceManager.clearError()
        }
    }

    // 캡챠 상태 동기화는 제거 - 서비스 상태를 직접 사용
    // LaunchedEffect(serviceShowCaptchaDialog) {
    //     showCaptchaDialog = serviceShowCaptchaDialog
    // }

    // LaunchedEffect(serviceCaptchaFlag) {
    //     captchaFlag = serviceCaptchaFlag
    // }

    val coroutine = rememberCoroutineScope()


    val primaryColor = uiColors.primary
    val secondaryColor = uiColors.secondary
    val backgroundColor = uiColors.background
    val cardColor = uiColors.card
    val deleteUiActive = isServiceDeleting || isDeleting
    val displayedProgress = if (isServiceDeleting) serviceProgress else currentProgress
    val displayedGallery = if (isServiceDeleting) serviceCurrentGallery else currentGallery
    val displayedDeletedCount = if (isServiceDeleting) serviceDeletedCount else deletedPosts
    val displayedTotalCount = if (isServiceDeleting) serviceTotalCount else totalPosts
    val displayedDeleteLog = if (isServiceDeleting) serviceDeleteLog else deleteLog

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {


            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(primaryColor, secondaryColor)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "디시클리너 모바일",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (deleteUiActive) {
                DeleteRunningContent(
                    uiColors = uiColors,
                    loginId = serviceTaskLoginId,
                    currentGallery = displayedGallery,
                    progress = displayedProgress,
                    deletedCount = displayedDeletedCount,
                    totalCount = displayedTotalCount,
                    latestLog = displayedDeleteLog.lastOrNull(),
                    onStop = { showStopDeleteDialog = true }
                )
            } else if (loginInfo == null) {
                LoginCard(
                    uiColors = uiColors,
                    savedAccounts = savedAccounts,
                    id = id,
                    onIdChange = { id = it },
                    pw = pw,
                    onPwChange = { pw = it },
                    saveLogin = saveLogin,
                    onSaveLoginChange = { saveLogin = it },
                    isLoggingIn = isLoggingIn,
                    onSavedAccountClick = { account ->
                        id = account.id
                        pw = account.password
                    },
                    onDeleteSavedAccountClick = { account ->
                        accountToDelete = account
                        showDeleteAccountDialog = true
                    },
                    onLoginClick = {
                        coroutine.launch {
                            isLoggingIn = true // 로그인 시작
                            val success = cleaner.login(id, pw)

                            if (!success) {
                                Log.w("DcinsideScreen", "로그인 실패 감지")
                                errorMessage = "로그인에 실패했습니다.\n아이디와 비밀번호를 확인해주세요."
                                showErrorDialog = true
                                isLoggingIn = false // 로그인 실패 시 로딩 해제
                                return@launch
                            }

                            Log.d("DcinsideScreen", "로그인 성공")

                            // 로그인 정보 저장
                            if (saveLogin && id.isNotBlank() && pw.isNotBlank()) {
                                val userInfo = cleaner.getUserInfo()
                                val account = SavedAccount(
                                    id = id,
                                    password = pw,
                                    nickname = userInfo?.nickname ?: ""
                                )
                                addSavedAccount(context, account)
                                savedAccounts = getSavedAccounts(context)
                            }

                            loginInfo = cleaner.getUserInfo()
                            interruptedTasks = deleteTaskStore.getForLogin(cleaner.getUserId())

                            val posting = cleaner.getGallList("posting")
                            val comment = cleaner.getGallList("comment")
                            Log.d("DcinsideScreen",
                                "게시글: ${(posting as? GallListResult.Success)?.galleries?.size ?: 0}개, " +
                                    "댓글: ${(comment as? GallListResult.Success)?.galleries?.size ?: 0}개"
                            )

                            postingGallList =
                                (posting as? GallListResult.Success)?.galleries
                                    ?: emptyMap()
                            commentGallList =
                                (comment as? GallListResult.Success)?.galleries
                                    ?: emptyMap()
                            selectedGallList = if (deleteType == "posting") {
                                postingGallList.keys.toList()
                            } else {
                                commentGallList.keys.toList()
                            }

                            isLoggingIn = false // 로그인 완료
                        }
                    }
                )

            } else {

                // 사용자 정보 카드
                UserInfoCard(
                    uiColors = uiColors,
                    loginInfo = loginInfo!!,
                    onLogoutClick = {
                        val activity = context as Activity
                        activity.finishAffinity()
                        val intent = Intent(activity, MainActivity::class.java)
                        activity.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                )

                Spacer(Modifier.height(20.dp))

                InterruptedDeleteTasksCard(
                    uiColors = uiColors,
                    tasks = interruptedTasks,
                    resumeEnabled = !isServiceDeleting && !isDeleting && restoringTaskId == null,
                    restoringTaskId = restoringTaskId,
                    focusedTaskId = resumeTaskId,
                    onResume = resume@{ task ->
                        if (restoringTaskId != null) return@resume
                        if (!DeleteTaskStartValidator.hasCompleteGalleryMap(
                                task.selectedGalleries,
                                task.galleryMap
                            )
                        ) {
                            errorMessage = "저장된 작업의 갤러리 정보를 복원하지 못했습니다."
                            showErrorDialog = true
                            return@resume
                        }
                        activeResumeTaskId = task.id
                        onResumeTaskConsumed()
                        deleteType = task.deleteType
                        selectedTab = 0
                        isCompleted = false
                        twocaptchaKey = ""
                        isTwocaptchaValid = null
                        selectedGallList = emptyList()
                        recommendFilterEnabled = false
                        commentFilterEnabled = false
                        myPostFilterEnabled = false
                        dcconOnlyFilterEnabled = false
                        commentContentFilterEnabled = false
                        dateFilterEnabled = false
                        minRecommendToKeep = ""
                        minCommentToKeep = ""
                        commentContentRegex = ""
                        minPostAgeDaysToDelete = ""
                        restoringTaskId = task.id
                        restoringMessage = "저장된 실행 환경을 준비하고 있습니다"
                        coroutine.launch {
                            kotlinx.coroutines.delay(100)
                            suspend fun typeRestoredValue(
                                value: String,
                                onValueChange: (String) -> Unit
                            ) {
                                if (value.isEmpty()) {
                                    onValueChange("")
                                    kotlinx.coroutines.delay(200)
                                    return
                                }
                                val steps = minOf(8, value.length)
                                for (step in 1..steps) {
                                    val characterCount =
                                        ((value.length * step) + steps - 1) / steps
                                    onValueChange(value.take(characterCount))
                                    kotlinx.coroutines.delay(120)
                                }
                                kotlinx.coroutines.delay(200)
                            }
                            try {
                                if (task.twoCaptchaApiKey.isNotBlank()) {
                                    restoringMessage = "2Captcha 설정 위치로 이동하고 있습니다"
                                    animateRestoreScrollTo(captchaSectionScrollY)
                                    restoringMessage = "저장된 2Captcha 키를 입력하고 있습니다"
                                    val savedKey = task.twoCaptchaApiKey
                                    val typingSteps = minOf(6, savedKey.length.coerceAtLeast(1))
                                    for (step in 1..typingSteps) {
                                        val characterCount =
                                            ((savedKey.length * step) + typingSteps - 1) / typingSteps
                                        twocaptchaKey = savedKey.take(characterCount)
                                        kotlinx.coroutines.delay(100)
                                    }

                                    kotlinx.coroutines.delay(250)
                                    isCheckingTwocaptcha = true
                                    restoringMessage = "키 확인 버튼을 실행하고 있습니다"
                                    kotlinx.coroutines.delay(400)
                                    val keyValid = cleaner.set2CaptchaKey(savedKey)
                                    isCheckingTwocaptcha = false
                                    isTwocaptchaValid = keyValid
                                    if (!keyValid) {
                                        cleaner.restore2CaptchaKey("")
                                        restoringTaskId = null
                                        activeResumeTaskId = null
                                        selectedGallList = if (deleteType == "posting") {
                                            postingGallList.keys.toList()
                                        } else {
                                            commentGallList.keys.toList()
                                        }
                                        errorMessage = "저장된 2Captcha 키 확인에 실패했습니다. 키를 다시 확인한 뒤 이어하기를 눌러주세요."
                                        showErrorDialog = true
                                        return@launch
                                    }
                                    restoringMessage = "2Captcha 키 확인이 완료되었습니다"
                                    kotlinx.coroutines.delay(600)
                                } else {
                                    twocaptchaKey = ""
                                    isTwocaptchaValid = null
                                    cleaner.restore2CaptchaKey("")
                                }

                                restoringMessage = "삭제 옵션 위치로 이동하고 있습니다"
                                animateRestoreScrollTo(deleteOptionsSectionScrollY)
                                restoringMessage = "선택한 갤러리를 복원하고 있습니다"
                                selectedGallList = task.selectedGalleries
                                kotlinx.coroutines.delay(500)

                                restoringMessage = "필터 설정 위치로 이동하고 있습니다"
                                animateRestoreScrollTo(filterOptionsSectionScrollY)

                                val savedRecommendFilter =
                                    task.recommendFilterEnabled || task.minRecommendToKeep >= 0
                                val savedCommentFilter =
                                    task.commentFilterEnabled || task.minCommentToKeep >= 0
                                val savedContentFilter =
                                    task.commentContentFilterEnabled || task.commentRegexFilter.isNotEmpty()
                                val savedDateFilter =
                                    task.dateFilterEnabled || task.minPostAgeDaysToDelete >= 0
                                val savedRecommendValue =
                                    task.minRecommendToKeep.takeIf { it >= 0 }?.toString() ?: "1"
                                val savedCommentValue =
                                    task.minCommentToKeep.takeIf { it >= 0 }?.toString() ?: "1"
                                val savedDateValue =
                                    task.minPostAgeDaysToDelete.takeIf { it >= 0 }?.toString() ?: "5"

                                if (task.deleteType == "posting") {
                                    if (savedRecommendFilter) {
                                        restoringMessage = "추천 수 필터를 적용하고 있습니다"
                                        recommendFilterEnabled = true
                                        kotlinx.coroutines.delay(250)
                                        typeRestoredValue(savedRecommendValue) {
                                            minRecommendToKeep = it
                                        }
                                    } else {
                                        minRecommendToKeep = savedRecommendValue
                                    }
                                    if (savedCommentFilter) {
                                        restoringMessage = "댓글 수 필터를 적용하고 있습니다"
                                        commentFilterEnabled = true
                                        kotlinx.coroutines.delay(250)
                                        typeRestoredValue(savedCommentValue) {
                                            minCommentToKeep = it
                                        }
                                    } else {
                                        minCommentToKeep = savedCommentValue
                                    }
                                } else {
                                    if (task.myPostFilterEnabled) {
                                        restoringMessage = "내 글 필터를 켜고 있습니다"
                                        myPostFilterEnabled = true
                                        kotlinx.coroutines.delay(300)
                                    }
                                    if (task.dcconOnlyFilterEnabled) {
                                        restoringMessage = "디시콘 전용 필터를 켜고 있습니다"
                                        dcconOnlyFilterEnabled = true
                                        kotlinx.coroutines.delay(300)
                                    }
                                    if (savedContentFilter) {
                                        restoringMessage = "댓글 정규식 필터를 적용하고 있습니다"
                                        commentContentFilterEnabled = true
                                        kotlinx.coroutines.delay(250)
                                        typeRestoredValue(task.commentRegexFilter) {
                                            commentContentRegex = it
                                        }
                                    } else {
                                        commentContentRegex = task.commentRegexFilter
                                    }
                                }

                                if (savedDateFilter) {
                                    restoringMessage = "작성일 필터를 적용하고 있습니다"
                                    dateFilterEnabled = true
                                    kotlinx.coroutines.delay(250)
                                    typeRestoredValue(savedDateValue) {
                                        minPostAgeDaysToDelete = it
                                    }
                                } else {
                                    minPostAgeDaysToDelete = savedDateValue
                                }

                                restoringMessage = "저장된 설정으로 이어서 삭제를 시작합니다"
                                kotlinx.coroutines.delay(800)
                                restoringTaskId = null
                                isDeleting = true
                                showDeleteProgressDialog = true
                                if (!serviceManager.resumeDeletion(cleaner, task)) {
                                    isDeleting = false
                                    showDeleteProgressDialog = false
                                    activeResumeTaskId = null
                                    selectedGallList = if (deleteType == "posting") {
                                        postingGallList.keys.toList()
                                    } else {
                                        commentGallList.keys.toList()
                                    }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                restoringTaskId = null
                                activeResumeTaskId = null
                                isCheckingTwocaptcha = false
                                throw e
                            } catch (e: Exception) {
                                restoringTaskId = null
                                activeResumeTaskId = null
                                selectedGallList = if (deleteType == "posting") {
                                    postingGallList.keys.toList()
                                } else {
                                    commentGallList.keys.toList()
                                }
                                isCheckingTwocaptcha = false
                                errorMessage = "저장된 설정 복원에 실패했습니다: ${e.message ?: "알 수 없는 오류"}"
                                showErrorDialog = true
                            }
                        }
                    },
                    onDelete = { task ->
                        deleteTaskToRemove = task
                    }
                )

                if (interruptedTasks.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                }

                if (!isDeleting && !showDeleteProgressDialog) {
                // 탭 선택기와 설명서를 하나의 카드로 표시
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = cardColor,
                            contentColor = primaryColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("디시 클리너") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("대왕콘 얻기") }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("방명록 쓰기") }
                            )
                        }

                        if (selectedTab == 0 || selectedTab == 2) {
                            TextButton(
                                onClick = {
                                    val manualUrl = if (selectedTab == 0) {
                                        "https://dccleaner3.github.io/dccleaner/cleaner"
                                    } else {
                                        "https://dccleaner3.github.io/dccleaner/guestbook"
                                    }
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            android.net.Uri.parse(manualUrl)
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)
                            ) {
                                Text(
                                    "설명서",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    painter = painterResource(com.dccleaner.app.R.drawable.ic_open_in_new),
                                    contentDescription = "외부 링크 열기",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                Spacer(Modifier.height(20.dp))

                // 탭 컨텐츠
                when (selectedTab) {
                    0 -> {
                        // 디시 클리너 탭 컨텐츠
                        Column {
                            DcCleanerTabContent(
                            cleaner = cleaner,
                            serviceManager = serviceManager,
                            uiColors = uiColors,
                            postingGallList = postingGallList,
                            commentGallList = commentGallList,
                            deleteType = deleteType,
                            onDeleteTypeChange = { newDeleteType ->
                                if (deleteType != newDeleteType) {
                                    deleteType = newDeleteType
                                    selectedGallList = if (newDeleteType == "posting") {
                                        postingGallList.keys.toList()
                                    } else {
                                        commentGallList.keys.toList()
                                    }
                                }
                            },
                            twocaptchaKey = twocaptchaKey,
                            onTwocaptchaKeyChange = { newKey ->
                                twocaptchaKey = newKey
                                if (newKey.isBlank()) {
                                    removeSavedTwoCaptchaKey(appContext)
                                    cleaner.restore2CaptchaKey("")
                                }
                            },
                            isTwocaptchaValid = isTwocaptchaValid,
                            onTwocaptchaValidChange = { isTwocaptchaValid = it },
                            isCheckingTwocaptcha = isCheckingTwocaptcha,
                            onIsCheckingTwocaptchaChange = { isCheckingTwocaptcha = it },
                            onShowDeleteDialog = {
                                val currentGallList = if (deleteType == "posting") {
                                    postingGallList
                                } else {
                                    commentGallList
                                }
                                if (!DeleteTaskStartValidator.hasCompleteGalleryMap(
                                        selectedGallList,
                                        currentGallList
                                    )
                                ) {
                                    errorMessage = "갤러리 목록을 불러온 뒤 다시 시도해 주세요."
                                    showErrorDialog = true
                                } else {
                                    showDeleteConfirmDialog = true
                                }
                            },
                            coroutine = coroutine,
                            snackbarHostState = snackbarHostState,
                            selectedGallList = selectedGallList,
                            onSelectedGallListChange = { selectedGallList = it },
                            minRecommendToKeep = minRecommendToKeep,
                            onMinRecommendToKeepChange = { minRecommendToKeep = it },
                            minCommentToKeep = minCommentToKeep,
                            onMinCommentToKeepChange = { minCommentToKeep = it },
                            recommendFilterEnabled = recommendFilterEnabled,
                            onRecommendFilterEnabledChange = { recommendFilterEnabled = it },
                            commentFilterEnabled = commentFilterEnabled,
                            onCommentFilterEnabledChange = { commentFilterEnabled = it },
                            myPostFilterEnabled = myPostFilterEnabled,
                            onMyPostFilterEnabledChange = { myPostFilterEnabled = it },
                            dcconOnlyFilterEnabled = dcconOnlyFilterEnabled,
                            onDcconOnlyFilterEnabledChange = { dcconOnlyFilterEnabled = it },
                            commentContentFilterEnabled = commentContentFilterEnabled,
                            onCommentContentFilterEnabledChange = { commentContentFilterEnabled = it },
                            commentContentRegex = commentContentRegex,
                            onCommentContentRegexChange = { commentContentRegex = it },
                            dateFilterEnabled = dateFilterEnabled,
                            onDateFilterEnabledChange = { dateFilterEnabled = it },
                            minPostAgeDaysToDelete = minPostAgeDaysToDelete,
                            onMinPostAgeDaysToDeleteChange = { minPostAgeDaysToDelete = it },
                            recordGuestbookLog = recordGuestbookLog,
                            onRecordGuestbookLogChange = { recordGuestbookLog = it },
                            captchaSectionMarker = Modifier.onGloballyPositioned { coordinates ->
                                val target = (
                                    scrollState.value + coordinates.positionInRoot().y
                                    ).roundToInt()
                                if (captchaSectionScrollY != target) {
                                    captchaSectionScrollY = target
                                }
                            },
                            deleteOptionsSectionMarker = Modifier.onGloballyPositioned { coordinates ->
                                val target = (
                                    scrollState.value + coordinates.positionInRoot().y
                                    ).roundToInt()
                                if (deleteOptionsSectionScrollY != target) {
                                    deleteOptionsSectionScrollY = target
                                }
                            },
                            filterOptionsSectionMarker = Modifier.onGloballyPositioned { coordinates ->
                                val target = (
                                    scrollState.value + coordinates.positionInRoot().y
                                    ).roundToInt()
                                if (filterOptionsSectionScrollY != target) {
                                    filterOptionsSectionScrollY = target
                                }
                            }
                            )
                        }
                    }

                    1 -> {
                        // 대왕콘 얻기 탭 컨텐츠
                        DaewangconCard(
                            uiColors = uiColors,
                            onStartDaewangcon = { showDaewangconDialog = true },
                            isDaewangconRunning = isDaewangconRunning
                        )
                    }

                    2 -> {
                        // 방명록 쓰기 탭 컨텐츠
                        GuestbookTabContent(
                            cleaner = cleaner,
                            uiColors = uiColors,
                            coroutine = coroutine,
                            userListText = guestbookUserListText,
                            onUserListTextChange = { guestbookUserListText = it },
                            messageText = guestbookMessageText,
                            onMessageTextChange = { guestbookMessageText = it },
                            showConfirmDialog = guestbookShowConfirmDialog,
                            onShowConfirmDialogChange = { guestbookShowConfirmDialog = it },
                            showResultDialog = guestbookShowResultDialog,
                            onShowResultDialogChange = { guestbookShowResultDialog = it },
                            isSending = guestbookIsSending,
                            onIsSendingChange = { guestbookIsSending = it },
                            progressDone = guestbookProgressDone,
                            onProgressDoneChange = { guestbookProgressDone = it },
                            progressTotal = guestbookProgressTotal,
                            onProgressTotalChange = { guestbookProgressTotal = it },
                            successCount = guestbookSuccessCount,
                            onSuccessCountChange = { guestbookSuccessCount = it },
                            failCount = guestbookFailCount,
                            onFailCountChange = { guestbookFailCount = it }
                        )
                    }


                }

                Spacer(Modifier.height(10.dp))


                if (deleteLog.isNotEmpty() && selectedTab == 0) {
                    DeleteLogCard(
                        uiColors = uiColors,
                        deleteLog = deleteLog
                    )
                }
                }

            }

            if (!deleteUiActive) {
                if (loginInfo == null) {
                    Spacer(Modifier.height(24.dp))
                }
                VersionInfoCard(
                    uiColors = uiColors,
                    latestVersion = latestVersion,
                    isCheckingVersion = isCheckingVersion,
                    onUpdateClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/dccleaner3/dccleaner/releases/latest")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(Modifier.height(100.dp))
        }


        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.Center)
        )

        if (restoringTaskId != null) {
            DeleteTaskRestoreOverlay(
                uiColors = uiColors,
                message = restoringMessage
            )
        }


        if (showErrorDialog) {
            ErrorDialog(
                uiColors = uiColors,
                errorMessage = errorMessage,
                onDismiss = { showErrorDialog = false }
            )
        }

        deleteTaskToRemove?.let { task ->
            DeleteTaskRecordDialog(
                uiColors = uiColors,
                onConfirm = {
                    if (deleteTaskStore.remove(task.id)) {
                        interruptedTasks = deleteTaskStore.getForLogin(cleaner.getUserId())
                        if (resumeTaskId == task.id) onResumeTaskConsumed()
                    } else {
                        errorMessage = "저장된 삭제 작업 기록을 삭제하지 못했습니다."
                        showErrorDialog = true
                    }
                    deleteTaskToRemove = null
                },
                onDismiss = { deleteTaskToRemove = null }
            )
        }


        if (showDeleteConfirmDialog) {
            StartDeletionDialog(
                uiColors = uiColors,
                deleteType = deleteType,
                selectedGalleryCount = selectedGallList.size,
                activeFilters = buildList {
                    if (deleteType == "posting" && recommendFilterEnabled) {
                        add("추천 ${minRecommendToKeep.ifBlank { "1" }}개 이상 보존")
                    }
                    if (deleteType == "posting" && commentFilterEnabled) {
                        add("댓글 ${minCommentToKeep.ifBlank { "1" }}개 이상 보존")
                    }
                    if (deleteType == "comment" && myPostFilterEnabled) add("내 글 필터")
                    if (deleteType == "comment" && dcconOnlyFilterEnabled) add("디시콘 전용")
                    if (deleteType == "comment" && commentContentFilterEnabled) add("댓글 정규식")
                    if (dateFilterEnabled) {
                        add("작성 후 ${minPostAgeDaysToDelete.ifBlank { "5" }}일 이상")
                    }
                    if (twocaptchaKey.isNotBlank()) add("2Captcha 자동 해결")
                },
                onConfirm = confirm@{
                    val currentGallList =
                        if (deleteType == "posting") postingGallList else commentGallList
                    if (!DeleteTaskStartValidator.hasCompleteGalleryMap(
                            selectedGallList,
                            currentGallList
                        )
                    ) {
                        showDeleteConfirmDialog = false
                        errorMessage = "갤러리 목록을 불러온 뒤 다시 시도해 주세요."
                        showErrorDialog = true
                        return@confirm
                    }
                    val selectedGalleryMap = DeleteTaskStartValidator.selectedGalleryMap(
                        selectedGallList,
                        currentGallList
                    )

                    showDeleteConfirmDialog = false
                    restoringTaskId = null
                    activeResumeTaskId = null
                    onResumeTaskConsumed()
                    isDeleting = true
                    isCompleted = false
                    showDeleteProgressDialog = true
                    deleteLog = emptyList()
                    currentProgress = 0f
                    deletedPosts = 0
                    totalPosts = 0

                    val started = serviceManager.startDeletion(
                        cleaner = cleaner,
                        selectedGalleries = selectedGallList,
                        deleteType = deleteType,
                        galleryMap = selectedGalleryMap,
                        twoCaptchaApiKey = twocaptchaKey,
                        recommendFilterEnabled = recommendFilterEnabled,
                        commentFilterEnabled = commentFilterEnabled,
                        commentContentFilterEnabled = commentContentFilterEnabled,
                        dateFilterEnabled = dateFilterEnabled,
                        minRecommendToKeep = if (recommendFilterEnabled) minRecommendToKeep.toIntOrNull() ?: 1 else -1,
                        minCommentToKeep = if (commentFilterEnabled) minCommentToKeep.toIntOrNull() ?: 1 else -1,
                        myPostFilterEnabled = myPostFilterEnabled,
                        dcconOnlyFilterEnabled = dcconOnlyFilterEnabled,
                        commentRegexFilter = if (commentContentFilterEnabled) commentContentRegex else "",
                        minPostAgeDaysToDelete = if (dateFilterEnabled) minPostAgeDaysToDelete.toIntOrNull() ?: 5 else -1,
                        recordGuestbookLog = recordGuestbookLog
                    )
                    if (!started) {
                        isDeleting = false
                        showDeleteProgressDialog = false
                    }
                },
                onDismiss = { showDeleteConfirmDialog = false }
            )
        }

        if (showDeleteProgressDialog) {
            TaskProgressDialog {
                DeleteProgressCard(
                    uiColors = uiColors,
                    deleteType = deleteType,
                    isCompleted = isCompleted,
                    isDeleting = deleteUiActive,
                    totalPosts = displayedTotalCount,
                    deletedPosts = displayedDeletedCount,
                    currentProgress = displayedProgress,
                    estimatedTimeLeft = estimatedTimeLeft,
                    nextCaptchaEstimatedTimeLeft = serviceNextCaptchaEstimatedTimeLeft,
                    isTwoCaptchaConfigured = serviceIsTwoCaptchaConfigured,
                    currentGallery = displayedGallery,
                    deleteLog = displayedDeleteLog,
                    onClose = {
                        showDeleteProgressDialog = false
                        restoringTaskId = null
                        activeResumeTaskId = null
                        onResumeTaskConsumed()
                        deleteLog = emptyList()
                        currentProgress = 0f
                        deletedPosts = 0
                        totalPosts = 0
                        estimatedTimeLeft = 0L
                        isCompleted = false
                    },
                    onComplete = {
                        showDeleteProgressDialog = false
                        restoringTaskId = null
                        activeResumeTaskId = null
                        onResumeTaskConsumed()
                        serviceManager.dismissDeletionNotification()
                        serviceManager.clearLogs()
                        currentProgress = 0f
                        deletedPosts = 0
                        totalPosts = 0
                        estimatedTimeLeft = 0L
                        isCompleted = false
                    },
                    onStop = {
                        showStopDeleteDialog = true
                    }
                )
            }
        }

        if (showStopDeleteDialog) {
            StopDeleteDialog(
                uiColors = uiColors,
                onConfirm = {
                    showStopDeleteDialog = false
                    serviceManager.stopDeletion()
                    currentGallery = ""
                    deleteLog = deleteLog + "🛑 사용자에 의해 중단됨"
                },
                onDismiss = {
                    showStopDeleteDialog = false
                }
            )
        }


        // 캡챠 다이얼로그 - 서비스 상태를 직접 사용
        if (serviceShowCaptchaDialog) {
            CaptchaDialog(
                uiColors = uiColors,
                onOpenGallog = {
                    val userId = serviceTaskLoginId
                        .ifBlank { serviceManager.getCurrentTaskLoginId() }
                        .ifBlank { cleaner.getUserId() }
                    if (userId.isNotEmpty()) {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://gallog.dcinside.com/$userId")
                        )
                        context.startActivity(intent)
                    }
                },
                onResolveCaptcha = {
                    // 캡챠 해결 완료 - 서비스 상태 업데이트
                    serviceManager.resolveCaptcha()
                },
                onOpenAutoCaptchaGuide = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse(
                            "https://dccleaner3.github.io/dccleaner/cleaner#-2captcha-%EC%9E%90%EB%8F%99-%ED%95%B4%EA%B2%B0"
                        )
                    ).addCategory(Intent.CATEGORY_BROWSABLE)
                    context.startActivity(intent)
                }
            )
        }

        // 계정 삭제 확인 다이얼로그
        if (showDeleteAccountDialog && accountToDelete != null) {
            DeleteAccountDialog(
                uiColors = uiColors,
                accountToDelete = accountToDelete!!,
                onConfirm = {
                    accountToDelete?.let { account ->
                        removeSavedAccount(context, account.id)
                        savedAccounts = getSavedAccounts(context)
                    }
                    showDeleteAccountDialog = false
                    accountToDelete = null
                },
                onDismiss = {
                    showDeleteAccountDialog = false
                    accountToDelete = null
                }
            )
        }

        // 대왕콘 시작 확인 다이얼로그
        if (showDaewangconDialog) {
            DaewangconStartDialog(
                uiColors = uiColors,
                onStart = {
                    showDaewangconDialog = false
                    showDaewangconProgressDialog = true
                    serviceManager.startDaewangcon(
                        cleaner = cleaner,
                        galleryId = daewangconGalleryId,
                        postNo = daewangconPostNo,
                        postSubject = daewangconPostSubject,
                        postContent = daewangconPostContent,
                        commentContent = daewangconCommentContent
                    )
                },
                onDismiss = { showDaewangconDialog = false }
            )
        }

        if (showDaewangconProgressDialog) {
            TaskProgressDialog {
                DaewangconProgressCard(
                    uiColors = uiColors,
                    isRunning = isDaewangconRunning,
                    isCompleted = isDaewangconCompleted,
                    errorMessage = daewangconErrorMessage,
                    progress = daewangconProgress,
                    logs = daewangconLog,
                    postCount = daewangconPostCount,
                    commentCount = daewangconCommentCount,
                    onClose = {
                        showDaewangconProgressDialog = false
                        serviceManager.dismissDaewangconNotification()
                    },
                    onStop = { showStopDaewangconDialog = true }
                )
            }
        }

        if (showStopDaewangconDialog) {
            StopDaewangconDialog(
                uiColors = uiColors,
                onConfirm = {
                    showStopDaewangconDialog = false
                    serviceManager.stopDaewangcon()
                },
                onDismiss = { showStopDaewangconDialog = false }
            )
        }
    }
}
