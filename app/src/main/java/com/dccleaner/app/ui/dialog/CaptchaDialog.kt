package com.dccleaner.app.ui.dialog

import com.dccleaner.app.model.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CaptchaDialog(
    uiColors: UiColors,
    onOpenGallog: () -> Unit,
    onResolveCaptcha: () -> Unit,
    onOpenAutoCaptchaGuide: () -> Unit
) {
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card

    AlertDialog(
        onDismissRequest = { /* 캡챠 다이얼로그는 수동으로만 닫기 */ },
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "캡챠",
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "캡챠 해결 필요",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "캡챠가 감지되었습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "1. '갤로그 열기' 버튼을 눌러주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "2. 열린 갤로그 페이지에서 글/댓글 하나를 삭제한 후 '캡챠 해결 완료' 버튼을 눌러주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "⚠️ 모바일 버전 갤로그에서는 캡챠 해결이 안됩니다\nPC 버전 갤로그에서 해주세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5722),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "작업이 일시 중지됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenGallog,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("갤로그 열기")
                    }
                    Button(
                        onClick = onResolveCaptcha,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("캡챠 해결 완료")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onOpenAutoCaptchaGuide,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("캡챠 자동 해결하기")
                }
            }
        },
        containerColor = cardColor,
        shape = RoundedCornerShape(16.dp)
    )
}
