package com.dccleaner.app.ui.guestbook

import com.dccleaner.app.ui.dialog.GuestbookConfirmDialog
import com.dccleaner.app.ui.dialog.GuestbookResultDialog

import com.dccleaner.app.network.Cleaner

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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun GuestbookTabContent(
    cleaner: Cleaner,
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
) {
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card

    val userIds = userListText.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = cardColor),
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
                    onValueChange = onUserListTextChange,
                    label = { Text("유저 리스트 (줄바꿈으로 구분)") },
                    placeholder = { Text("abc123\nfdsa33\ndfsasdf324") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    maxLines = Int.MAX_VALUE,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        focusedLabelColor = primaryColor
                    ),
                    enabled = !isSending
                )

                if (userIds.isNotEmpty()) {
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
                    onValueChange = onMessageTextChange,
                    label = { Text("방명록 메시지") },
                    placeholder = { Text("방명록에 작성할 내용을 입력하세요") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = Int.MAX_VALUE,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        focusedLabelColor = primaryColor
                    ),
                    enabled = !isSending
                )

                Spacer(Modifier.height(16.dp))

                if (isSending) {
                    Text(
                        "$progressDone / $progressTotal 전송 완료",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = primaryColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (progressTotal > 0) progressDone.toFloat() / progressTotal.toFloat() else 0f
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
                    onClick = { onShowConfirmDialogChange(true) },
                    enabled = !isSending && userIds.isNotEmpty() && messageText.isNotBlank(),
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
                        "방명록 전송",
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
            userCount = userIds.size,
            onConfirm = {
                onShowConfirmDialogChange(false)
                val ids = userIds.toList()
                val message = messageText
                onIsSendingChange(true)
                onProgressDoneChange(0)
                onProgressTotalChange(ids.size)
                onSuccessCountChange(0)
                onFailCountChange(0)
                coroutine.launch {
                    val doneAtomic = AtomicInteger(0)
                    val successAtomic = AtomicInteger(0)
                    withContext(Dispatchers.IO) {
                        ids.map { userId ->
                            async<Boolean> {
                                val success = cleaner.writeGuestbook(userId, message)
                                val done = doneAtomic.incrementAndGet()
                                if (success) successAtomic.incrementAndGet()
                                withContext(Dispatchers.Main) {
                                    onProgressDoneChange(done)
                                }
                                success
                            }
                        }.awaitAll()
                    }
                    onSuccessCountChange(successAtomic.get())
                    onFailCountChange(ids.size - successAtomic.get())
                    onIsSendingChange(false)
                    onShowResultDialogChange(true)
                }
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
