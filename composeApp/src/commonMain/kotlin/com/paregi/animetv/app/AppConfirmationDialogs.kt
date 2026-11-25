package com.paregi.animetv.app

import androidx.compose.runtime.Composable
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.ui.ConfirmDialog
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.domain.AccountProvider

@Composable
internal fun TrackingPromptDialog(
    prompt: TrackingPromptState,
    onDismiss: () -> Unit,
    onTrackProvider: (AccountProvider) -> Unit,
    onTrackBoth: () -> Unit,
    onPlayWithoutTracking: () -> Unit
) {
    val items = buildList {
        add(ListOrderItem(id = "notrack", title = "Don't Track", icon = IconGlyphs.Close))
        if (prompt.providers.size > 1) {
            add(ListOrderItem(id = "trackall", title = "Track on AniList and MAL", icon = IconGlyphs.Done))
        }
        if (AccountProvider.ANILIST in prompt.providers) {
            add(
                ListOrderItem(
                    id = "anilist",
                    title = "Track on AniList",
                    icon = IconGlyphs.PlaylistAddCheckCircle
                )
            )
        }
        if (AccountProvider.MAL in prompt.providers) {
            add(
                ListOrderItem(
                    id = "mal",
                    title = "Track on MAL",
                    icon = IconGlyphs.PlaylistAddCheckCircle
                )
            )
        }
    }

    ListOrderDialog(
        title = "Tracking Anime",
        items = items,
        mode = ListOrderMode.MENU,
        onDismiss = onDismiss,
        onSelect = { _, item ->
            when (item.id) {
                "notrack" -> onPlayWithoutTracking()
                "trackall" -> onTrackBoth()
                "anilist" -> onTrackProvider(AccountProvider.ANILIST)
                "mal" -> onTrackProvider(AccountProvider.MAL)
            }
        }
    )
}

@Composable
internal fun PlaybackCloseConfirmationDialog(
    visible: Boolean,
    onKeepWatching: () -> Unit,
    onClosePlayback: () -> Unit
) {
    if (!visible) return
    ConfirmDialog(
        title = null,
        message = "Close the playback?",
        yesLabel = "YES",
        noLabel = "NO",
        onConfirm = onClosePlayback,
        onDismiss = onKeepWatching
    )
}

@Composable
internal fun ApplicationExitConfirmationDialog(
    visible: Boolean,
    onCancel: () -> Unit,
    onExit: () -> Unit
) {
    if (!visible) return
    ConfirmDialog(
        title = "Exit AnimeTV?",
        titleIcon = IconGlyphs.DoorOpen,
        message = "Are you sure you want to exit AnimeTV?",
        yesLabel = "YES",
        noLabel = "NO",
        onConfirm = onExit,
        onDismiss = onCancel
    )
}
