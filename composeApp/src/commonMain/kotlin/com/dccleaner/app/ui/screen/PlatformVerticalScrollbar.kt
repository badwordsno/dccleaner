package com.dccleaner.app.ui.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dccleaner.app.model.UiColors

@Composable
expect fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    uiColors: UiColors,
    modifier: Modifier
)
