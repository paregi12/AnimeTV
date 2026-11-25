package com.paregi.animetv.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode

@Composable
internal fun NavFloatingMenu(
    section: Section,
    onDismiss: () -> Unit,
    onRefreshHome: () -> Unit,
    onCustomizeHome: () -> Unit,
    onReloadAnimeTv: () -> Unit,
    onRefreshMyList: () -> Unit,
    onCustomizeMyList: () -> Unit,
    onClearWatchHistory: () -> Unit,
    onRefreshSchedule: () -> Unit
) {
    val items = remember(section) {
        when (section) {
            Section.Home -> listOf(
                ListOrderItem(id = "refresh_home", title = "Refresh Home", icon = IconGlyphs.Refresh),
                ListOrderItem(id = "customize_home", title = "Customize Home", icon = IconGlyphs.Tune),
                ListOrderItem(id = "reload_animetv", title = "Reload AnimeTV", icon = IconGlyphs.Cycle)
            )
            Section.MyList -> listOf(
                ListOrderItem(id = "refresh_mylist", title = "Refresh MyList", icon = IconGlyphs.Refresh),
                ListOrderItem(id = "customize_mylist", title = "Customize MyList", icon = IconGlyphs.Tune),
                ListOrderItem(id = "clear_history", title = "Clear Watch History", icon = IconGlyphs.DeleteForever)
            )
            Section.Schedule -> listOf(
                ListOrderItem(id = "refresh_schedule", title = "Refresh Schedule", icon = IconGlyphs.Refresh)
            )
            else -> emptyList()
        }
    }

    if (items.isEmpty()) return

    ListOrderDialog(
        title = "",
        items = items,
        mode = ListOrderMode.MENU,
        onDismiss = onDismiss,
        onSelect = { _, item ->
            onDismiss()
            when (item.id) {
                "refresh_home" -> onRefreshHome()
                "customize_home" -> onCustomizeHome()
                "reload_animetv" -> onReloadAnimeTv()
                "refresh_mylist" -> onRefreshMyList()
                "customize_mylist" -> onCustomizeMyList()
                "clear_history" -> onClearWatchHistory()
                "refresh_schedule" -> onRefreshSchedule()
            }
        }
    )
}
