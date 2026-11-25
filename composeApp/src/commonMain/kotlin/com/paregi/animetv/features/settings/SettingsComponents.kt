package com.paregi.animetv.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.paregi.animetv.core.theme.ANIME_TV_THEME_IDS
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.core.ui.AnimeTvSwitch
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable

internal data class SettingToggle(
    val label: String,
    val checked: Boolean,
    val description: String? = null,
    val enabled: Boolean = true,
    val onChange: (Boolean) -> Unit
) {
    constructor(label: String, checked: Boolean, onChange: (Boolean) -> Unit) :
        this(label, checked, null, true, onChange)
}

internal data class SettingValue(
    val label: String,
    val value: String = "",
    val description: String? = null,
    val icon: String? = null,
    val valueIcon: String? = null,
    val valueBadge: String? = null,
    val onClick: () -> Unit
) {
    constructor(label: String, value: String, onClick: () -> Unit) :
        this(label, value, null, null, null, null, onClick)
}

internal fun settingIcon(label: String): String? = when (label) {
    "Subtitle Language" -> IconGlyphs.ClosedCaption
    "Subtitle Style" -> IconGlyphs.BrandFamily
    "Prefer DUB Stream" -> IconGlyphs.SpeechToText
    "Audio Language" -> IconGlyphs.TextToSpeech
    "Auto Next" -> IconGlyphs.StepOver
    "Auto Skip Intro" -> IconGlyphs.Footprint
    "Skip Filler" -> IconGlyphs.MoveSelectionLeft
    "Video Scaling" -> IconGlyphs.AspectRatio
    "Video Quality" -> IconGlyphs.Hd
    "Seek Value" -> IconGlyphs.FastForward
    "Video Buffer" -> IconGlyphs.MotionPhotosPaused
    "Close Confirmation" -> IconGlyphs.CancelPresentation
    "Disable Touch Gesture" -> IconGlyphs.DoNotTouch
    "Preload Next Episode" -> IconGlyphs.CloudDownload
    "Use HTML5 Video Player" -> IconGlyphs.LiveTv
    "Font Size" -> IconGlyphs.FormatSize
    "Interface Color" -> IconGlyphs.Palette
    "Wallpaper" -> IconGlyphs.Wallpaper
    "Large UI" -> IconGlyphs.SettingsOverscan
    "Login Style" -> IconGlyphs.ShieldPerson
    "Transition Animation" -> IconGlyphs.Animation
    "Performance UI" -> IconGlyphs.ReadinessScore
    "Navigation Sound" -> IconGlyphs.BrandAwareness
    "MyList Main Page" -> IconGlyphs.Dvr
    "Show Clock" -> IconGlyphs.Clock
    "Switch page on select" -> IconGlyphs.SwitchAccess
    "Show sidebar directly" -> IconGlyphs.ThumbnailBar
    "Exit Mode" -> IconGlyphs.DoorOpen
    "AniList Slideshow" -> IconGlyphs.PlaylistAddCheckCircle
    "HiRes AniList Banner" -> IconGlyphs.HighRes
    "Play Trailer" -> IconGlyphs.Movie
    "Chinese Anime" -> IconGlyphs.EmojiNature
    "Japanese Titles" -> IconGlyphs.JapaneseKana
    "HTTP Client" -> IconGlyphs.Public
    "Use DoH" -> IconGlyphs.Encrypted
    "Cache Size" -> IconGlyphs.DiscFull
    "Progressive Cache" -> IconGlyphs.FactCheck
    "Use Image CDN" -> IconGlyphs.CleaningBucket
    "HLS Stream Proxy" -> IconGlyphs.Bolt
    "Debug Logging" -> IconGlyphs.Build
    "View Logs", "MAL Login" -> IconGlyphs.ListAlt
    "MAL Logout", "AniList Logout" -> IconGlyphs.LockOpen
    "AniList Login" -> IconGlyphs.Hub
    "Update watch progress" -> IconGlyphs.CloudSync
    "Auto Tracking" -> IconGlyphs.DataExploration
    "Donation" -> IconGlyphs.VolunteerActivism
    "Discord Server" -> IconGlyphs.SportsEsports
    "Check for Nightly Build" -> IconGlyphs.PartlyCloudyNight
    "Check for Update" -> IconGlyphs.Update
    else -> null
}

internal fun settingDescription(label: String): String? = when (label) {
    "Subtitle Language" -> "Select language priority for video subtitle and audio"
    "Prefer DUB Stream" -> "Always use DUB stream when available"
    "Audio Language" -> "Select prefered stream audio language (only for supported source only)"
    "Video Quality" -> "Select preferred streaming quality"
    "Video Buffer" -> "Set video buffer duration in memory. Lower values reduce RAM usage on low-end devices."
    "Close Confirmation" -> "Action to close the playback"
    "Preload Next Episode" -> "Prepare episode data before video finished for faster next episode load"
    "Use HTML5 Video Player" -> "Enable if you have a problem with stuttering playback, some features may disabled"
    "Large UI" -> "Use bigger UI on anime list"
    "Login Style" -> "Change Who's Watching screen style"
    "Performance UI" -> "Enable/disable some effects for better performance gain"
    "Navigation Sound" -> "Enable navigation audio, only for supported devices"
    "MyList Main Page" -> "Set MyList as main page when AnimeTV started"
    "Show Clock" -> "Show clock in homescreen"
    "Switch page on select" -> "No need to press ok to switch page"
    "Show sidebar directly" -> "No need to press ok key to show sidebar"
    "Exit Mode" -> "Set back key to exit behavior"
    "AniList Slideshow" -> "Use AniList trending now anime list on home slideshow"
    "HiRes AniList Banner" -> "Get higher quality AniList banner image for slideshow and more info"
    "Play Trailer" -> "Play trailer on AniList slideshow"
    "Chinese Anime" -> "Also show and search chinese anime"
    "Japanese Titles" -> "Show anime titles in japanese name"
    "HTTP Client" -> "Select HTTP Client that works for your connection"
    "Use DoH" -> "Use dns over https for securely resolving domain name. Enable if your ISP blocked source domain (Only works with OkHttp)"
    "Cache Size" -> "Set maximum disk cache size for HTTP Client. Only works for okHttp and Cronet"
    "Progressive Cache" -> "Cache content progressively for better performance. Turn off if image or playback not working"
    "Use Image CDN" -> "Use image CDN from wsrv.nl to improve cache and performance"
    "HLS Stream Proxy" -> "Use hls streaming proxy if you have a problem with streaming"
    "Update watch progress" -> "Set on what state it should update MAL/AniList watch progress"
    "Auto Tracking" -> "Action to do when playing untracked anime"
    else -> null
}

internal fun normalizeThemeId(theme: String): String = when (theme) {
    "theme_purple", "theme_violet" -> "theme_purple"
    "theme_blue", "theme_ocean", "theme_arctic_blue" -> "theme_blue"
    "theme_teal", "theme_jade" -> "theme_teal"
    "theme_green", "theme_emerald" -> "theme_green"
    "theme_brown", "theme_amber", "theme_gold" -> "theme_brown"
    "theme_red", "theme_crimson", "theme_rose", "theme_rose_gold" -> "theme_red"
    "theme_grey", "theme_graphite", "theme_white" -> "theme_grey"
    "theme_dark" -> "theme_dark"
    else -> "theme_purple"
}

internal fun formatThemeName(theme: String): String {
    val normalized = normalizeThemeId(theme)
    return ANIME_TV_THEME_IDS.firstOrNull { it.first == normalized }?.second ?: "Purple"
}

@Composable
internal fun SettingToggleRow(item: SettingToggle) {
    SettingsRowFrame(
        label = item.label,
        icon = settingIcon(item.label),
        description = item.description,
        enabled = item.enabled,
        onClick = { item.onChange(!item.checked) },
        trailingContent = {
            AnimeTvSwitch(checked = item.checked, enabled = item.enabled)
        }
    )
}

@Composable
internal fun SettingValueRow(item: SettingValue) {
    SettingsRowFrame(
        label = item.label,
        icon = item.icon ?: settingIcon(item.label),
        value = item.value,
        valueIcon = item.valueIcon,
        valueBadge = item.valueBadge,
        description = item.description,
        enabled = true,
        onClick = item.onClick
    )
}

@Composable
private fun SettingsRowFrame(
    label: String,
    icon: String?,
    value: String = "",
    valueIcon: String? = null,
    valueBadge: String? = null,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()

    val rowBackground = when {
        focused -> Color(0xFF444444)
        pressed -> Color(0xFF111111)
        hovered -> Color(0xFF1A1A1A)
        else -> Color.Transparent
    }
    val verticalPad = if (tokens.portrait) tokens.vh * 1.6f else tokens.vw * 1.2f
    val resolvedDesc = description ?: settingDescription(label)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.3f }
            .background(rowBackground)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(enabled = enabled, onClick = onClick)
            .navigationFocusable()
            .padding(
                start = tokens.u * 2f,
                end = if (trailingContent != null) tokens.u * 1f else tokens.u * 5f,
                top = verticalPad,
                bottom = verticalPad
            ),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.width(tokens.u * 4f),
            contentAlignment = Alignment.TopStart
        ) {
            if (!icon.isNullOrBlank()) {
                Text(
                    text = icon,
                    color = Color.White,
                    fontFamily = LocalIconFont.current,
                    fontSize = fsSp(tokens.fs20),
                    lineHeight = fsSp(tokens.fs20) * 1.05f
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = fsSp(tokens.fs15),
                lineHeight = fsSp(tokens.fs15) * 1.2f
            )
            if (value.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.4f)
                ) {
                    if (!valueIcon.isNullOrBlank()) {
                        Text(
                            text = valueIcon,
                            fontFamily = LocalIconFont.current,
                            color = colors.accent1,
                            fontSize = fsSp(tokens.fs12),
                            lineHeight = fsSp(tokens.fs12) * 1.2f
                        )
                    }
                    Text(
                        text = value,
                        color = colors.accent1,
                        fontSize = fsSp(tokens.fs12),
                        lineHeight = fsSp(tokens.fs12) * 1.2f
                    )
                    if (!valueBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(tokens.u * 0.2f))
                                .background(colors.primaryBg)
                                .padding(horizontal = tokens.u * 0.4f)
                        ) {
                            Text(
                                text = valueBadge,
                                color = Color.White,
                                fontSize = fsSp(tokens.fs8),
                                fontWeight = FontWeight.Bold,
                                lineHeight = fsSp(tokens.fs8) * 1.2f
                            )
                        }
                    }
                }
            }
            if (!resolvedDesc.isNullOrBlank()) {
                Text(
                    text = resolvedDesc,
                    color = Color(0xFFAAAAAA),
                    fontSize = fsSp(tokens.fs10),
                    lineHeight = fsSp(tokens.fs10) * 1.2f
                )
            }
        }

        if (trailingContent != null) {
            Spacer(Modifier.width(tokens.u * 0.6f))
            trailingContent()
        }
    }
}
