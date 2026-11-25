package com.paregi.animetv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.ui.ImgPickerDialog
import com.paregi.animetv.core.ui.ImgPickerItem
import com.paregi.animetv.domain.HomeFeed
import com.paregi.animetv.features.profile.curatedWallpapers

@Composable
internal fun WallpaperPickerDialog(
    homeFeed: HomeFeed,
    selectedWallpaperUrl: String,
    onSelect: (url: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val aniListBannerUrl = remember(homeFeed.featured) {
        homeFeed.featured.firstNotNullOfOrNull { anime ->
            anime.bannerUrl?.takeIf { it.isNotBlank() }
        }
    }

    val items = remember(aniListBannerUrl, selectedWallpaperUrl) {
        val bannerMatched = aniListBannerUrl != null && selectedWallpaperUrl == aniListBannerUrl
        val curatedMatched = curatedWallpapers.any { it.source == selectedWallpaperUrl }
        val noneSelected = !bannerMatched && !curatedMatched

        buildList {
            add(
                ImgPickerItem(
                    id = "",
                    title = "No Wallpaper",
                    icon = IconGlyphs.Block,
                    selected = noneSelected
                )
            )
            if (aniListBannerUrl != null) {
                add(
                    ImgPickerItem(
                        id = aniListBannerUrl,
                        title = "AniList Banner",
                        imageUrl = aniListBannerUrl,
                        selected = bannerMatched
                    )
                )
            }
            curatedWallpapers.forEach { choice ->
                add(
                    ImgPickerItem(
                        id = choice.source,
                        title = choice.title,
                        drawableName = choice.thumbnail ?: choice.drawable,
                        imageUrl = choice.imageUrl,
                        selected = selectedWallpaperUrl == choice.source
                    )
                )
            }
        }
    }

    ImgPickerDialog(
        title = "Select Wallpaper",
        items = items,
        onSelect = { item -> onSelect(item.id, item.title) },
        onDismiss = onDismiss
    )
}
