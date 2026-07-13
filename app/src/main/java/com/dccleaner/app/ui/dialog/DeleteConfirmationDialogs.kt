package com.dccleaner.app.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dccleaner.app.model.UiColors

@Composable
fun StartDeletionDialog(
    uiColors: UiColors,
    deleteType: String,
    selectedGalleryCount: Int,
    activeFilters: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val targetLabel = if (deleteType == "posting") "게시글" else "댓글"

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        containerColor = uiColors.card,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(50)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.padding(12.dp)
                )
            }
        },
        title = {
            Text(
                "삭제 작업을 시작할까요?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = uiColors.background,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("삭제 대상", fontWeight = FontWeight.SemiBold)
                        Text("내 갤로그의 $targetLabel")
                        Text("선택 갤러리: ${selectedGalleryCount}개")
                        Text(
                            "적용 조건: ${activeFilters.ifEmpty { listOf("추가 필터 없음") }.joinToString(" · ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF546E7A)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "삭제된 글/댓글은 복구할 수 없습니다.",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    "작업 중 앱이 종료돼도 저장된 지점부터 이어서 진행할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1565C0)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("삭제 시작", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = uiColors.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("취소", maxLines = 1, softWrap = false)
            }
        }
    )
}
