package com.paregi.animetv.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.paregi.animetv.LocalNavigationSound
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.fsSp

internal object SearchRemotePadPrefs {
    var muted: Boolean by mutableStateOf(false)
}

private val KEYBOARD_ROWS = listOf(
    listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
    listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "'"),
    listOf("Z", "X", "C", "V", "B", "N", "M", ",", ".", "backspace"),
    listOf("clear", "space", "search")
)

private val KEYPAD_ROWS = listOf(
    listOf("7", "8", "9"),
    listOf("4", "5", "6"),
    listOf("1", "2", "3"),
    listOf("0", "?", "mute")
)

private val ICON_KEYS = setOf("backspace", "clear", "space", "search")

@Composable
internal fun SearchKeyboardTools(
    onKeyAction: (String) -> Unit,
    onSearch: () -> Unit,
    muted: Boolean,
    initialFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val gap = tokens.vw * 0.5f
    val keyWidth = tokens.vw * 4f
    val keyHeight = tokens.vw * 3.4f

    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        KEYBOARD_ROWS.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEachIndexed { colIndex, key ->
                    val width = when (key) {
                        "clear", "search" -> tokens.vw * 8.5f
                        "space" -> tokens.vw * 26.5f
                        else -> keyWidth
                    }
                    val isIcon = key in ICON_KEYS
                    val label = when (key) {
                        "backspace" -> IconGlyphs.Backspace
                        "clear" -> IconGlyphs.Cancel
                        "space" -> IconGlyphs.SpaceBar
                        "search" -> IconGlyphs.Search
                        else -> key
                    }
                    val tone = when (key) {
                        "search" -> RemoteKeyTone.Search
                        "clear" -> RemoteKeyTone.Danger
                        else -> RemoteKeyTone.Normal
                    }
                    SearchRemotePadKey(
                        label = label,
                        isIcon = isIcon,
                        tone = tone,
                        muted = muted,
                        focusRequester = initialFocusRequester.takeIf { rowIndex == 1 && colIndex == 0 },
                        modifier = Modifier.width(width).height(keyHeight),
                        onClick = {
                            if (key == "search") onSearch() else onKeyAction(key)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchKeypadTools(
    onKeyAction: (String) -> Unit,
    muted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val gap = tokens.vw * 0.5f
    val keyWidth = tokens.vw * 4f
    val keyHeight = tokens.vw * 3.4f

    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        KEYPAD_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { key ->
                    val isMute = key == "mute"
                    SearchRemotePadKey(
                        label = if (isMute) {
                            if (muted) IconGlyphs.VolumeOff else IconGlyphs.BrandAwareness
                        } else key,
                        isIcon = isMute,
                        tone = if (isMute) RemoteKeyTone.Danger else RemoteKeyTone.Normal,
                        muted = muted,
                        playSoundOnClickEvenIfMuted = isMute,
                        modifier = Modifier.width(keyWidth).height(keyHeight),
                        onClick = {
                            if (isMute) onToggleMute() else onKeyAction(key)
                        }
                    )
                }
            }
        }
    }
}

private enum class RemoteKeyTone { Normal, Search, Danger }

@Composable
private fun SearchRemotePadKey(
    label: String,
    isIcon: Boolean,
    tone: RemoteKeyTone,
    muted: Boolean,
    playSoundOnClickEvenIfMuted: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val playClickSound = LocalNavigationSound.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = focused || hovered

    val bgColor = when {
        highlighted -> when (tone) {
            RemoteKeyTone.Search -> Color(0xFF006600)
            RemoteKeyTone.Danger -> Color(0xFF660000)
            RemoteKeyTone.Normal -> colors.primary2
        }
        else -> Color(0xFF111111)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(tokens.vw * 0.4f))
            .background(bgColor)
            .hoverable(interactionSource = interactionSource)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                if (it.isFocused && !focused && !muted) playClickSound()
                focused = it.isFocused
            }
            .clickable {
                if (!muted || playSoundOnClickEvenIfMuted) playClickSound()
                onClick()
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (highlighted) Color.White else Color(0xFFAAAAAA),
            fontFamily = if (isIcon) LocalIconFont.current else FontFamily.Default,
            fontSize = fsSp(tokens.fs17),
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            modifier = if (isIcon) Modifier.offset(y = -tokens.vw * 0.05f) else Modifier
        )
    }
}
