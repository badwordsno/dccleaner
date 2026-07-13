package com.dccleaner.app.ui.cleaner

import com.dccleaner.app.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PostDeleteConditionCard(
    uiColors: UiColors,
    minRecommendToKeep: String,
    onMinRecommendToKeepChange: (String) -> Unit,
    minCommentToKeep: String,
    onMinCommentToKeepChange: (String) -> Unit,
    recommendFilterEnabled: Boolean,
    onRecommendFilterEnabledChange: (Boolean) -> Unit,
    commentFilterEnabled: Boolean,
    onCommentFilterEnabledChange: (Boolean) -> Unit,
    dateFilterEnabled: Boolean,
    onDateFilterEnabledChange: (Boolean) -> Unit,
    minPostAgeDaysToDelete: String,
    onMinPostAgeDaysToDeleteChange: (String) -> Unit,
    recordGuestbookLog: Boolean,
    onRecordGuestbookLogChange: (Boolean) -> Unit,
    onShowDeleteDialog: () -> Unit
) {
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card
    val usesSlowFilter = recommendFilterEnabled || commentFilterEnabled
    val hourlyDeleteCount = if (usesSlowFilter) "900" else "1,800"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "글 삭제 조건",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            // 추천수 필터 토글
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "추천수 필터",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = recommendFilterEnabled,
                    onCheckedChange = onRecommendFilterEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primaryColor
                    )
                )
            }

            if (recommendFilterEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = minRecommendToKeep,
                    onValueChange = { value ->
                        if (value.isEmpty() || (value.toIntOrNull() != null && value.toInt() >= 0)) {
                            onMinRecommendToKeepChange(value)
                        }
                    },
                    label = { Text("최소 추천 수 (이상이면 보존)") },
                    placeholder = { Text("예: 5") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = primaryColor
                        )
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // 댓글수 필터 토글
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "댓글수 필터",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = commentFilterEnabled,
                    onCheckedChange = onCommentFilterEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primaryColor
                    )
                )
            }

            if (commentFilterEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = minCommentToKeep,
                    onValueChange = { value ->
                        if (value.isEmpty() || (value.toIntOrNull() != null && value.toInt() >= 0)) {
                            onMinCommentToKeepChange(value)
                        }
                    },
                    label = { Text("최소 댓글 수 (이상이면 보존)") },
                    placeholder = { Text("예: 5") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = primaryColor
                        )
                    }
                )
            }

            if (recommendFilterEnabled && commentFilterEnabled) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFFFFF3E0),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF57C00),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "두 조건은 OR로 적용됩니다.\n추천수 또는 댓글수 중 하나라도 조건을 충족하면 글을 보존합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF57C00)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 날짜 필터 토글
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "오래된 항목만 삭제",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = dateFilterEnabled,
                    onCheckedChange = onDateFilterEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primaryColor
                    )
                )
            }

            if (dateFilterEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = minPostAgeDaysToDelete,
                    onValueChange = { value ->
                        if (value.isEmpty() || (value.toIntOrNull() != null && value.toInt() >= 0)) {
                            onMinPostAgeDaysToDeleteChange(value)
                        }
                    },
                    label = { Text("최소 경과 일수 (이상이면 삭제)") },
                    placeholder = { Text("예: 5") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = primaryColor
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "이 조건은 다른 삭제 조건과 AND로 적용됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        primaryColor.copy(alpha = 0.08f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "예상 삭제 속도",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "시간당 약 ${hourlyDeleteCount}개",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
                if (usesSlowFilter) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "추천수·댓글수 확인 시 처리 시간이 늘어납니다.",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
            Spacer(Modifier.height(20.dp))
            DeleteStartControls(
                primaryColor = primaryColor,
                recordGuestbookLog = recordGuestbookLog,
                onRecordGuestbookLogChange = onRecordGuestbookLogChange,
                onShowDeleteDialog = onShowDeleteDialog
            )
        }
    }
}
