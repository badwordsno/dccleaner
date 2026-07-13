package com.dccleaner.app.ui.card

import com.dccleaner.app.service.DcCleanerService
import com.dccleaner.app.util.formatDurationMillis
import com.dccleaner.app.model.*
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DaewangconCard(
    uiColors: UiColors,
    onStartDaewangcon: () -> Unit,
    isDaewangconRunning: Boolean
) {
    val postIntervalWait = formatDurationMillis(
        DcCleanerService.DAEWANGCON_POST_INTERVAL_DELAY_MILLIS
    )
    val commentIntervalWait = formatDurationMillis(
        DcCleanerService.DAEWANGCON_COMMENT_INTERVAL_DELAY_MILLIS
    )
    val postBatchWait = formatDurationMillis(
        DcCleanerService.DAEWANGCON_POST_BATCH_DELAY_MILLIS
    )
    val commentBatchWait = formatDurationMillis(
        DcCleanerService.DAEWANGCON_COMMENT_BATCH_DELAY_MILLIS
    )
    val commentIntervalDescription =
        if (DcCleanerService.DAEWANGCON_COMMENT_INTERVAL_DELAY_MILLIS > 0L) {
            "$commentIntervalWait 간격으로"
        } else {
            "대기 없이"
        }
    val postWaitMillis =
        DcCleanerService.DAEWANGCON_POST_INTERVAL_DELAY_MILLIS * 9L +
            DcCleanerService.DAEWANGCON_POST_BATCH_DELAY_MILLIS *
            (9L / DcCleanerService.DAEWANGCON_POST_BATCH_SIZE)
    val commentWaitMillis =
        DcCleanerService.DAEWANGCON_COMMENT_INTERVAL_DELAY_MILLIS * 19L +
            DcCleanerService.DAEWANGCON_COMMENT_BATCH_DELAY_MILLIS *
            (19L / DcCleanerService.DAEWANGCON_COMMENT_BATCH_SIZE)
    val fixedWaitMillis = maxOf(postWaitMillis, commentWaitMillis)
    val estimatedMinutes = (fixedWaitMillis + 59_999L) / 60_000L
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card

    Column {
        // 설정 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "대왕콘 얻기 설정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "글 10개와 댓글 20개를 동시에 자동 작성합니다\n" +
                        "글은 $postIntervalWait 간격으로, 댓글은 $commentIntervalDescription 작성하며, " +
                        "글 ${DcCleanerService.DAEWANGCON_POST_BATCH_SIZE}개마다 $postBatchWait, " +
                        "댓글 ${DcCleanerService.DAEWANGCON_COMMENT_BATCH_SIZE}개마다 " +
                        "$commentBatchWait 대기합니다\n" +
                        "동시 처리 기준 약 ${estimatedMinutes}분이 소요됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(Modifier.height(16.dp))

            }
        }

        Spacer(Modifier.height(20.dp))

        // 시작/중지 버튼
        if (!isDaewangconRunning) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                onClick = onStartDaewangcon,
                enabled = true,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "대왕콘 작업 시작",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        } else {
            Text(
                "진행 중인 작업을 전체 화면에서 표시하고 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}
