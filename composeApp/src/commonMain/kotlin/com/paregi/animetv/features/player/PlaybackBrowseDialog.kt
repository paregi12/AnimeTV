package com.paregi.animetv.features.player

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.paregi.animetv.core.theme.LocalImageCdn
import com.paregi.animetv.core.theme.LocalJapaneseTitles
import com.paregi.animetv.core.theme.PurpleLight
import com.paregi.animetv.core.theme.TextPrimary
import com.paregi.animetv.core.theme.TextSecondary
import com.paregi.animetv.core.ui.displayAnimeTitle
import com.paregi.animetv.core.ui.imageCdnUrl
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable

private enum class PlaybackBrowseSection(val label: String) {
    EPISODES("Episodes"),
    RELATED("Related"),
    RECOMMENDATIONS("Recommendation")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackBrowseDialog(
    episodes: List<Episode>,
    selectedEpisodeId: String,
    related: List<Anime>,
    recommendations: List<Anime>,
    onDismiss: () -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onSelectAnime: (Anime) -> Unit
) {
    val sections = buildList {
        if (episodes.isNotEmpty()) add(PlaybackBrowseSection.EPISODES)
        if (related.isNotEmpty()) add(PlaybackBrowseSection.RELATED)
        if (recommendations.isNotEmpty()) add(PlaybackBrowseSection.RECOMMENDATIONS)
    }
    var selectedSection by remember { mutableStateOf(PlaybackBrowseSection.EPISODES) }
    val section = selectedSection.takeIf { it in sections } ?: sections.firstOrNull()
    val selectedIndex = episodes.indexOfFirst { it.id == selectedEpisodeId }.coerceAtLeast(0)
    val pageSize = 50
    val totalEpisodePages = (episodes.size + pageSize - 1) / pageSize
    var selectedEpisodePage by remember(episodes.size) { mutableStateOf(selectedIndex / pageSize) }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val infiniteTransition = rememberInfiniteTransition(label = "browse-playing")
    val playingColor by infiniteTransition.animateColor(
        initialValue = Color.White,
        targetValue = Color(0xFF33FF33),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "browse-playing-color"
    )

    LaunchedEffect(section, selectedEpisodeId) {
        if (section == PlaybackBrowseSection.EPISODES && episodes.isNotEmpty()) {
            val page = selectedIndex / pageSize
            if (page != selectedEpisodePage) {
                selectedEpisodePage = page
            }
            listState.scrollToItem(selectedIndex % pageSize)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xD9000000),
        scrimColor = Color(0x99000000),
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sections.forEach { item ->
                    val selected = item == section
                    Text(
                        "${item.label}  ${when (item) {
                            PlaybackBrowseSection.EPISODES -> episodes.size
                            PlaybackBrowseSection.RELATED -> related.size
                            PlaybackBrowseSection.RECOMMENDATIONS -> recommendations.size
                        }}",
                        color = if (selected) PurpleLight else TextSecondary,
                        modifier = Modifier.clip(RoundedCornerShape(7.dp))
                            .background(if (selected) Color(0xFF38234B) else Color(0xFF1A1A1A))
                            .navigationClickable { selectedSection = item }
                            .navigationFocusable()
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
            if (section == PlaybackBrowseSection.EPISODES && totalEpisodePages > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0 until totalEpisodePages).forEach { pageIdx ->
                        val pStart = pageIdx * pageSize + 1
                        val pEnd = minOf((pageIdx + 1) * pageSize, episodes.size)
                        val isPageSelected = pageIdx == selectedEpisodePage
                        Text(
                            "$pStart - $pEnd",
                            color = if (isPageSelected) PurpleLight else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isPageSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (isPageSelected) Color(0xFF38234B) else Color(0xFF1A1A1A))
                                .navigationClickable { selectedEpisodePage = pageIdx }
                                .navigationFocusable()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                when (section) {
                    PlaybackBrowseSection.EPISODES -> {
                        val displayedEpisodes = if (totalEpisodePages > 1) {
                            val pStart = selectedEpisodePage * pageSize
                            val pEnd = minOf(pStart + pageSize, episodes.size)
                            episodes.subList(pStart, pEnd)
                        } else {
                            episodes
                        }
                        itemsIndexed(displayedEpisodes, key = { _, item -> item.id }) { _, episode ->
                            val isSelected = episode.id == selectedEpisodeId
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF38234B) else Color(0xFF17131A))
                                    .navigationClickable { onSelectEpisode(episode) }
                                    .navigationFocusable()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 68.dp, height = 48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) PurpleLight.copy(alpha = 0.20f) else Color(0xFF222026)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        episode.number,
                                        color = if (isSelected) PurpleLight else if (episode.filler) Color(0xFFFFDDAA) else TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (episode.filler) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(3.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFAA5500))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("FILLER", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(Color(0xCC000000))
                                                .padding(vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("PLAYING", color = playingColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        "Episode ${episode.number}",
                                        color = if (isSelected) PurpleLight else if (episode.filler) Color(0xFFFFDDAA) else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    episode.title?.takeIf { it.isNotBlank() }?.let { title ->
                                        Text(
                                            title,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val versions = buildList {
                                        if (episode.hasSub) add("SUB")
                                        if (episode.hasDub) add("DUB")
                                    }
                                    if (versions.isNotEmpty()) {
                                        Text(versions.joinToString(" · "), color = TextSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    PlaybackBrowseSection.RELATED -> items(related, key = { it.id }) { anime ->
                        PlaybackBrowseAnimeRow(anime) { onSelectAnime(anime) }
                    }
                    PlaybackBrowseSection.RECOMMENDATIONS -> items(recommendations, key = { it.id }) { anime ->
                        PlaybackBrowseAnimeRow(anime) { onSelectAnime(anime) }
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun PlaybackBrowseAnimeRow(anime: Anime, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF17131A))
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
            .padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val poster = imageCdnUrl(anime.posterUrl, LocalImageCdn.current)
        if (poster != null) {
            AsyncImage(
                model = poster,
                contentDescription = displayAnimeTitle(anime, LocalJapaneseTitles.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 54.dp, height = 76.dp).clip(RoundedCornerShape(5.dp))
            )
        } else {
            Text(
                "ANIME",
                color = TextSecondary,
                fontSize = 9.sp,
                modifier = Modifier.size(width = 54.dp, height = 76.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF29232E))
                    .padding(6.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                displayAnimeTitle(anime, LocalJapaneseTitles.current),
                color = TextPrimary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val details = listOfNotNull(
                anime.format?.replace('_', ' '),
                anime.year?.toString(),
                anime.episodeCount?.let { "$it eps" },
                anime.score?.let { "★ ${it.toString().take(4)}" }
            ).joinToString("  ·  ")
            if (details.isNotBlank()) Text(details, color = TextSecondary, fontSize = 11.sp)
        }
    }
}
