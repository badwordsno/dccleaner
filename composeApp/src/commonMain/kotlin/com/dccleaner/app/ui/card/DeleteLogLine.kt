package com.dccleaner.app.ui.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.deleteLogDisplayText
import com.dccleaner.app.model.isDeleteProgressLog
import com.dccleaner.app.model.isGuestbookRunLogCompletion
import com.dccleaner.app.model.isGuestbookRunLogStart
import kotlinx.coroutines.delay

// Bit positions follow the six-dot Braille layout:
// 0 3
// 1 4
// 2 5
private val DELETE_PROGRESS_FRAMES = intArrayOf(
    0x0B, 0x19, 0x39, 0x38, 0x3C,
    0x34, 0x26, 0x27, 0x07, 0x0F
)
private const val DELETE_PROGRESS_FRAME_MILLIS = 80L

@Composable
internal fun DeleteLogLine(
    log: String,
    showProgress: Boolean,
    separateFromPrevious: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val displayText = log.deleteLogDisplayText()
    val isActiveProgress = showProgress && log.isDeleteProgressLog()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = if (separateFromPrevious || log.isGuestbookRunLogStart()) 12.dp else 0.dp,
                bottom = if (log.isGuestbookRunLogCompletion()) 12.dp else 0.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActiveProgress) {
            val timestampEnd = displayText.indexOf("] ")
            val timestamp = if (timestampEnd >= 0) {
                displayText.substring(0, timestampEnd + 1)
            } else {
                ""
            }
            val message = if (timestampEnd >= 0) {
                displayText.substring(timestampEnd + 2)
            } else {
                displayText
            }.removePrefix("🗑️ ")

            if (timestamp.isNotEmpty()) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.width(5.dp))
            }
            BrailleProgressIndicator()
            Spacer(Modifier.width(5.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        } else {
            Text(
                text = displayText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

@Composable
private fun BrailleProgressIndicator() {
    var frameIndex by remember { mutableIntStateOf(0) }
    val color = MaterialTheme.colorScheme.primary

    // A delay-driven frame clock intentionally does not use Compose's animation
    // duration scale. The indicator must continue to communicate active work when
    // the device's "Remove animations" accessibility option is enabled.
    LaunchedEffect(Unit) {
        while (true) {
            delay(DELETE_PROGRESS_FRAME_MILLIS)
            frameIndex = (frameIndex + 1) % DELETE_PROGRESS_FRAMES.size
        }
    }

    // Draw on a fixed dot grid instead of swapping font glyphs. Font fallback and
    // per-glyph side bearings otherwise make the last-to-first frame look as if
    // the indicator shifts horizontally.
    Canvas(modifier = Modifier.size(width = 16.dp, height = 18.dp)) {
        val dotRadius = 1.35.dp.toPx()
        val columnGap = 4.5.dp.toPx()
        val rowGap = 4.5.dp.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val mask = DELETE_PROGRESS_FRAMES[frameIndex]

        repeat(6) { dot ->
            if (mask and (1 shl dot) != 0) {
                val column = dot / 3
                val row = dot % 3
                drawCircle(
                    color = color,
                    radius = dotRadius,
                    center = androidx.compose.ui.geometry.Offset(
                        x = centerX + if (column == 0) -columnGap / 2f else columnGap / 2f,
                        y = centerY + (row - 1) * rowGap
                    )
                )
            }
        }
    }
}
