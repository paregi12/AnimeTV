package com.paregi.animetv.features.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.paregi.animetv.PlatformAiringInfo
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.core.ui.AnimeTvSpinner
import com.paregi.animetv.core.ui.AnimeTvTitledShelf
import com.paregi.animetv.core.ui.FocusCard
import com.paregi.animetv.core.ui.calculateCardDimensions
import com.paregi.animetv.domain.AiringEntry
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.platformAiringInfo
import com.paregi.animetv.platformScheduleDays

private data class ScheduleEntry(
    val airing: AiringEntry,
    val info: PlatformAiringInfo
) {
    val anime: Anime get() = airing.anime
    val episodeNumber: String get() = airing.episodeNumber
    val airingAtEpochSeconds: Long get() = airing.airingAtEpochSeconds
    val hasAired: Boolean get() = info.hasAired
    val timeLabel: String get() = info.timeLabel
}

@Composable
internal fun ScheduleScreen(
    entries: List<AiringEntry>,
    loading: Boolean,
    largeUi: Boolean,
    onAnimeSelected: (Anime) -> Unit,
    onScrollOffsetChange: (Dp) -> Unit = {}
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val density = LocalDensity.current
    val maskEnabled = !LocalPerformanceEffects.current.getOrElse(3) { false }
    val scheduleDays = remember { platformScheduleDays() }

    val entriesByOffset = remember(entries) {
        entries.sortedBy { it.airingAtEpochSeconds }
            .distinctBy { it.anime.id }
            .map { ScheduleEntry(it, platformAiringInfo(it.airingAtEpochSeconds)) }
            .groupBy { it.info.dayOffset }
    }

    val todayOffset = remember(scheduleDays) {
        scheduleDays.firstOrNull { it.isToday }?.offset ?: 0
    }
    val todayEntries = remember(entriesByOffset, todayOffset) {
        entriesByOffset[todayOffset].orEmpty()
    }

    val todayShelfState = remember(todayEntries) {
        val firstUnairIndex = todayEntries.indexOfFirst { !it.hasAired }.coerceAtLeast(0)
        LazyListState(firstVisibleItemIndex = firstUnairIndex)
    }
    val dayShelfStates = remember(scheduleDays) {
        scheduleDays.associate { it.offset to LazyListState() }
    }

    val listState = rememberLazyListState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portrait = tokens.portrait
        val horizontalInset = if (portrait) tokens.vh * 2f else tokens.vw * 5f
        val topPad = if (portrait) tokens.vh * 2f + tokens.sysStatHeight else tokens.vw * 8f
        val bottomPad = if (portrait) tokens.sysNavHeight + tokens.vh * 8f else tokens.vw * 4f
        val cardDims = calculateCardDimensions(maxWidth, maxHeight, portrait, largeUi)
        val estimatedRowPx = with(density) { (cardDims.cardHeight + tokens.fs13 + tokens.u * 2.5f).toPx() }

        val scrollOffsetDp by remember(estimatedRowPx) {
            derivedStateOf {
                val px = if (listState.firstVisibleItemIndex == 0) {
                    listState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    listState.firstVisibleItemIndex * estimatedRowPx + listState.firstVisibleItemScrollOffset
                }
                with(density) { px.toDp() }
            }
        }
        LaunchedEffect(scrollOffsetDp) {
            onScrollOffsetChange(scrollOffsetDp)
        }

        if (loading && entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPad),
                contentAlignment = Alignment.Center
            ) {
                AnimeTvSpinner(
                    size = tokens.u * 8f,
                    strokeWidth = tokens.u * 0.8f,
                    color = colors.primary2
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(tokens.u * 1f)
            ) {
                if (portrait) {
                    item(key = "schedule_title", contentType = "schedule_title") {
                        Text(
                            text = "Airing Schedule",
                            color = Color.White,
                            fontSize = fsSp(tokens.fs40),
                            lineHeight = fsSp(tokens.fs40) * 1.3f,
                            modifier = Modifier
                                .padding(start = tokens.vw * 5f, end = tokens.vw * 5f, bottom = tokens.vh * 2f)
                        )
                    }
                }

                scheduleDays.forEach { day ->
                    val dayEntries = entriesByOffset[day.offset].orEmpty()
                    val headerTitle = if (day.isToday) "${day.dayName} (Today)" else day.dayName
                    item(key = "schedule_day_${day.offset}", contentType = "schedule_day") {
                        ScheduleDayShelf(
                            title = headerTitle,
                            entries = dayEntries,
                            shelfState = if (day.isToday) todayShelfState else (dayShelfStates[day.offset] ?: rememberLazyListState()),
                            horizontalInset = horizontalInset,
                            cardDims = cardDims,
                            largeUi = largeUi && !portrait,
                            maskEnabled = maskEnabled,
                            onAnimeSelected = onAnimeSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleDayShelf(
    title: String,
    entries: List<ScheduleEntry>,
    shelfState: LazyListState,
    horizontalInset: Dp,
    cardDims: com.paregi.animetv.core.ui.CardDimensions,
    largeUi: Boolean,
    maskEnabled: Boolean,
    onAnimeSelected: (Anime) -> Unit
) {
    AnimeTvTitledShelf(
        title = title,
        horizontalInset = horizontalInset,
        empty = entries.isEmpty(),
        loading = false,
        emptyText = "No schedule",
        cardHeight = cardDims.cardHeight,
        cardGap = cardDims.cardGap,
        rowState = shelfState,
        maskEnabled = maskEnabled
    ) {
        items(
            items = entries,
            key = { "${it.anime.id}:${it.airingAtEpochSeconds}" },
            contentType = { "anime_card" }
        ) { entry ->
            FocusCard(
                anime = entry.anime,
                cardWidth = cardDims.cardWidth,
                cardHeight = cardDims.cardHeight,
                posterWidth = cardDims.posterWidth,
                posterHeight = cardDims.posterHeight,
                cardTitleSize = cardDims.titleSize,
                cardTitleLineHeight = cardDims.titleLineHeight,
                onClick = { onAnimeSelected(entry.anime) },
                progressEpisode = entry.episodeNumber.takeIf { it.isNotBlank() },
                posterBottomLabel = entry.timeLabel,
                posterBottomIcon = if (entry.hasAired) IconGlyphs.Done else IconGlyphs.Schedule,
                posterBottomAired = entry.hasAired,
                largeUi = largeUi,
                useScheduleBadges = true
            )
        }
    }
}
