package com.dccleaner.app.ui.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors

@Composable
actual fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    uiColors: UiColors,
    modifier: Modifier
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier,
        style = defaultScrollbarStyle().copy(
            thickness = 10.dp,
            unhoverColor = uiColors.textSecondary.copy(alpha = 0.48f),
            hoverColor = uiColors.primary.copy(alpha = 0.86f)
        )
    )
}
