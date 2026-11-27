package com.paregi.animetv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable

private val ChipInactiveBg = Color(0x59A0A5A7)
private val ChipMutedLabel = Color.White.copy(alpha = 0.78f)

@Composable
internal fun FilterChip(label: String, value: String, selected: Boolean = false, onClick: () -> Unit) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }

    val chipBackground = if (focused || selected) colors.primary1 else ChipInactiveBg
    val chipShape = remember(tokens.u) { RoundedCornerShape(tokens.u * 0.2f) }
    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(chipBackground)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
            .padding(horizontal = tokens.u * 0.8f, vertical = tokens.u * 0.2f),
        horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Text(
                text = IconGlyphs.Check,
                fontFamily = LocalIconFont.current,
                color = Color.White,
                fontSize = fsSp(tokens.fs12)
            )
        }
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = if (value.isNotBlank() && !selected) ChipMutedLabel else Color.White,
                fontSize = fsSp(tokens.fs12)
            )
        }
        if (value.isNotBlank()) {
            Text(
                text = value,
                color = Color.White,
                fontSize = fsSp(tokens.fs12)
            )
        }
    }
}

@Composable
internal fun SettingsGroupTitle(title: String) {
    val tokens = LocalAnimeTvTokens.current
    Text(
        text = title,
        color = Color(0xFFAAAAAA),
        fontSize = fsSp(tokens.fs12),
        fontWeight = FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.u * 2f, vertical = tokens.u * 0.5f)
    )
}
