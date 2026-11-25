package com.paregi.animetv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit

/** Renders a voice-search control when speech recognition is available on the platform. */
@Composable
expect fun PlatformVoiceSearchButton(
    onResult: (String) -> Unit,
    inlineSearchBar: Boolean = false,
    iconColor: Color? = null,
    iconSize: TextUnit? = null
)
