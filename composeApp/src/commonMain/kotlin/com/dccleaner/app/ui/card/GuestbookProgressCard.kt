package com.dccleaner.app.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors

@Composable
fun GuestbookProgressCard(
    uiColors: UiColors,
    isSending: Boolean,
    progressDone: Int,
    progressTotal: Int,
    successCount: Int,
    failCount: Int,
    onClose: () -> Unit
) {
    val progress = if (progressTotal > 0) {
        progressDone.toFloat() / progressTotal.toFloat()
    } else {
        0f
    }
    val logs = listOf(
        "성공: ${successCount}명",
        "실패: ${failCount}명",
        "진행: ${progressDone} / ${progressTotal}"
    )

    TaskProgressCard(
        title = if (isSending) "방명록 전송 진행중" else "방명록 전송 완료",
        icon = Icons.AutoMirrored.Filled.Send,
        iconTint = uiColors.primary,
        primaryColor = uiColors.primary,
        backgroundColor = uiColors.surfaceVariant,
        cardColor = uiColors.card,
        outlineColor = uiColors.outline,
        logTitle = "전송 상태",
        logs = logs,
        canClose = !isSending,
        onClose = onClose,
        progressContent = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$progressDone / $progressTotal 전송 완료",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = uiColors.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = uiColors.primary
                )
            }
        },
        actionContent = {
            Button(
                onClick = onClose,
                enabled = !isSending,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("확인", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
