package com.dccleaner.app.ui.guestbook

import com.dccleaner.app.ui.dialog.GuestbookConfirmDialog
import com.dccleaner.app.ui.dialog.GuestbookResultDialog

import com.dccleaner.app.model.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GuestbookTabContent(
    uiColors: UiColors,
    coroutine: CoroutineScope,
    userListText: String,
    onUserListTextChange: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    showConfirmDialog: Boolean,
    onShowConfirmDialogChange: (Boolean) -> Unit,
    showResultDialog: Boolean,
    onShowResultDialogChange: (Boolean) -> Unit,
    isSending: Boolean,
    onIsSendingChange: (Boolean) -> Unit,
    progressDone: Int,
    onProgressDoneChange: (Int) -> Unit,
    progressTotal: Int,
    onProgressTotalChange: (Int) -> Unit,
    successCount: Int,
    onSuccessCountChange: (Int) -> Unit,
    failCount: Int,
    onFailCountChange: (Int) -> Unit,
    onResolveUserList: suspend (String) -> String,
    onStartGuestbookSend: (List<String>, String) -> Unit,
) {
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card
    var isLoadingRawList by remember { mutableStateOf(false) }
    var rawListError by remember { mutableStateOf<String?>(null) }
    var resolvedUserIds by remember { mutableStateOf<List<String>?>(null) }

    val userIds = remember(userListText) {
        userListText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val isRawLinkInput = userListText.trim().let {
        it.startsWith("https://") || it.startsWith("http://")
    }
    val rawLinkCount = if (isRawLinkInput) userIds.size else 0
    val latestOnUserListTextChange by rememberUpdatedState(onUserListTextChange)
    val latestOnMessageTextChange by rememberUpdatedState(onMessageTextChange)
    val handleUserListTextChange: (String) -> Unit = remember {
        { value ->
            rawListError = null
            resolvedUserIds = null
            latestOnUserListTextChange(value)
        }
    }
    val handleMessageTextChange: (String) -> Unit = remember {
        { value -> latestOnMessageTextChange(value) }
    }

    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = androidx.compose.foundation.BorderStroke(1.dp, uiColors.outline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "방명록 쓰기",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = userListText,
                    onValueChange = handleUserListTextChange,
                    label = { Text("유저 리스트 또는 raw 링크") },
                    placeholder = { Text("abc123\nfdsa33\ndfsasdf324\nhttps://pastebin.com/raw/HTQm23Fd") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    maxLines = Int.MAX_VALUE,
                    shape = RoundedCornerShape(12.dp),
                    colors = dccleanerOutlinedTextFieldColors(uiColors),
                    enabled = !isSending && !isLoadingRawList
                )

                if (rawListError != null) {
                    Text(
                        rawListError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = uiColors.danger,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (isLoadingRawList) {
                    Text(
                        "raw 링크에서 유저 리스트를 불러오는 중...",
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (resolvedUserIds != null) {
                    Text(
                        "${resolvedUserIds?.size ?: 0}명 불러옴",
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (isRawLinkInput) {
                    Text(
                        "전송 전에 raw 링크 ${rawLinkCount}개의 유저 리스트를 모두 불러옵니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (userIds.isNotEmpty()) {
                    Text(
                        "${userIds.size}명 입력됨",
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = messageText,
                    onValueChange = handleMessageTextChange,
                    label = { Text("방명록 메시지") },
                    placeholder = { Text("방명록에 작성할 내용을 입력하세요") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = Int.MAX_VALUE,
                    shape = RoundedCornerShape(12.dp),
                    colors = dccleanerOutlinedTextFieldColors(uiColors),
                    enabled = !isSending
                )

                Spacer(Modifier.height(16.dp))

                if (isSending || isLoadingRawList) {
                    Text(
                        if (isLoadingRawList) "유저 리스트 불러오는 중..." else "$progressDone / $progressTotal 전송 완료",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = primaryColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (isLoadingRawList) 0f else if (progressTotal > 0) progressDone.toFloat() / progressTotal.toFloat() else 0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = primaryColor
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    onClick = {
                        rawListError = null
                        if (!isRawLinkInput) {
                            onShowConfirmDialogChange(true)
                            return@Button
                        }
                        if (resolvedUserIds != null) {
                            onShowConfirmDialogChange(true)
                            return@Button
                        }
                        coroutine.launch {
                            isLoadingRawList = true
                            try {
                                val rawLinks = withContext(Dispatchers.Default) {
                                    parseRawLinks(userListText)
                                }
                                val resolvedTexts = rawLinks.mapIndexed { index, url ->
                                    try {
                                        onResolveUserList(url)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        val detail = e.message?.takeIf { it.isNotBlank() }
                                            ?: "유저 리스트를 불러오지 못했습니다."
                                        error("${index + 1}번째 raw 링크 처리 실패: $detail")
                                    }
                                }
                                val resolvedIds = withContext(Dispatchers.Default) {
                                    mergeUserListTexts(resolvedTexts)
                                }
                                if (resolvedIds.isEmpty()) {
                                    rawListError = "전송할 유저 ID가 없습니다."
                                    return@launch
                                }
                                resolvedUserIds = resolvedIds
                                onShowConfirmDialogChange(true)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                rawListError = e.message ?: "raw 링크에서 유저 리스트를 불러오지 못했습니다."
                            } finally {
                                isLoadingRawList = false
                            }
                        }
                    },
                    enabled = !isSending && !isLoadingRawList &&
                        (isRawLinkInput || userIds.isNotEmpty()) &&
                        messageText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isRawLinkInput) "링크 불러와 전송" else "방명록 전송",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // 전송 확인 다이얼로그
    if (showConfirmDialog) {
        GuestbookConfirmDialog(
            uiColors = uiColors,
            userCount = resolvedUserIds?.size ?: userIds.size,
            onConfirm = {
                onShowConfirmDialogChange(false)
                val message = messageText
                val ids = resolvedUserIds ?: userIds
                onIsSendingChange(true)
                onProgressDoneChange(0)
                onProgressTotalChange(ids.size)
                onSuccessCountChange(0)
                onFailCountChange(0)
                if (ids.isEmpty()) {
                    rawListError = "전송할 유저 ID가 없습니다."
                    onIsSendingChange(false)
                    return@GuestbookConfirmDialog
                }
                onStartGuestbookSend(ids, message)
            },
            onDismiss = { onShowConfirmDialogChange(false) }
        )
    }

    // 전송 완료 다이얼로그
    if (showResultDialog) {
        GuestbookResultDialog(
            uiColors = uiColors,
            successCount = successCount,
            failCount = failCount,
            onDismiss = { onShowResultDialogChange(false) }
        )
    }
}

internal fun parseRawLinks(input: String): List<String> {
    val links = input.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    require(links.isNotEmpty()) { "raw 링크를 입력해주세요." }
    require(links.all { it.startsWith("https://") || it.startsWith("http://") }) {
        "raw 링크는 줄마다 http 또는 https 주소만 입력해주세요."
    }
    return links
}

internal fun mergeUserListTexts(texts: List<String>): List<String> =
    texts.asSequence()
        .flatMap { it.lineSequence() }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
