package com.paregi.animetv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalImageCdn
import com.paregi.animetv.core.theme.LocalJapaneseTitles
import com.paregi.animetv.core.theme.TextPrimary
import com.paregi.animetv.core.theme.TextSecondary
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable

@Composable
internal fun FilterChip(label: String, value: String, selected: Boolean = false, onClick: () -> Unit) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }

    val chipBackground = if (focused || selected) colors.primary1 else Color(0x59A0A5A7)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.u * 0.2f))
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
                color = if (value.isNotBlank() && !selected) Color.White.copy(alpha = 0.78f) else Color.White,
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

internal fun genreLabel(id: String): String = when (id) {
    "1" -> "Action"
    "2" -> "Adventure"
    "6" -> "Comedy"
    "7" -> "Drama"
    "8" -> "Fantasy"
    "13" -> "Romance"
    "14" -> "Sci-Fi"
    "15" -> "Slice of Life"
    "17" -> "Supernatural"
    else -> "Any"
}

@Composable
internal fun SearchResultRow(anime: Anime, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF17131A))
            .navigationClickable(onClick = onClick)
            .navigationFocusable(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageCdnUrl(anime.posterUrl, LocalImageCdn.current),
            contentDescription = displayAnimeTitle(anime, LocalJapaneseTitles.current),
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(70.dp).height(90.dp).background(Color(0xFF201625))
        )
        Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(displayAnimeTitle(anime, LocalJapaneseTitles.current), color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(anime.format ?: "Anime", color = TextSecondary, fontSize = 12.sp)
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
