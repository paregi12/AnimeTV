package com.paregi.animetv.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.domain.DEFAULT_HOME_SECTIONS
import com.paregi.animetv.domain.HOME_SECTION_IDS
import com.paregi.animetv.domain.HomeLayout
import com.paregi.animetv.domain.HomeSectionPreference

fun homeSectionTitle(id: String): String = homeSectionTitle(id, sourceName = "Gotaku")

fun homeSectionTitle(id: String, sourceName: String): String = when (id) {
    "continue_watching" -> "Continue Watching"
    "recent" -> if (sourceName.contains("Re:ANIME", ignoreCase = true)) "Recently Released" else "Recently Updated"
    "trending" -> "Trending Today"
    "popular" -> if (sourceName.contains("Re:ANIME", ignoreCase = true)) "Most Popular" else "Popular This Week"
    "alrecent" -> "Recently Released - AniList"
    "altab" -> "AniList Tabbed List"
    "altop" -> "Top Anime - AniList"
    "alpopular" -> "All Time Popular - AniList"
    "alyear" -> "Top Year - AniList"
    "alupcoming" -> "Upcoming - AniList"
    else -> id.replaceFirstChar { it.uppercase() }
}

@Composable
internal fun HomeLayoutDialog(
    layout: HomeLayout,
    onDismiss: () -> Unit,
    onSave: (HomeLayout) -> Unit,
    sourceName: String = "Gotaku"
) {
    val resolvedSource = sourceName.ifBlank { "Gotaku" }
    val fullSections = remember(layout) {
        val base = layout.sections.map {
            if (it.id == "top") it.copy(id = "popular") else it
        }.filter { it.id in HOME_SECTION_IDS }.distinctBy { it.id }
        val existingIds = base.map { it.id }.toSet()
        val missing = HOME_SECTION_IDS.filterNot { it in existingIds }
            .map { HomeSectionPreference(it, enabled = it in DEFAULT_HOME_SECTIONS) }
        base + missing
    }
    var latestSections by remember(fullSections) { mutableStateOf(fullSections) }
    val items = remember(fullSections, resolvedSource) {
        fullSections.map { sec ->
            ListOrderItem(
                id = sec.id,
                title = homeSectionTitle(sec.id, resolvedSource),
                selected = sec.enabled
            )
        }
    }

    ListOrderDialog(
        title = "Customize Home - Source $resolvedSource",
        items = items,
        mode = ListOrderMode.ORDER,
        footerTip = "Use left/right to move order",
        onDismiss = {
            if (latestSections != fullSections) {
                onSave(HomeLayout(latestSections))
            } else {
                onDismiss()
            }
        },
        onReorder = { reordered ->
            latestSections = reordered.map { HomeSectionPreference(id = it.id, enabled = it.selected) }
        }
    )
}
