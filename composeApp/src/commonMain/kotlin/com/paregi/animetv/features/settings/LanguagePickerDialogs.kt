package com.paregi.animetv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.domain.AudioLanguageCatalog
import com.paregi.animetv.domain.SubtitleLanguageCatalog

@Composable
internal fun SubtitleLanguageDialog(
    selectedLanguageCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = SubtitleLanguageCatalog.all
    val selectedIndex = remember(selectedLanguageCode) {
        languages.indexOfFirst { language ->
            selectedLanguageCode == language.code ||
                (selectedLanguageCode.isBlank() && language.code == "hard")
        }.coerceAtLeast(0)
    }

    val items = remember(selectedIndex) {
        languages.mapIndexed { index, language ->
            ListOrderItem(
                id = language.code,
                title = language.label,
                selected = index == selectedIndex
            )
        }
    }

    ListOrderDialog(
        title = "Subtitle Language",
        items = items,
        mode = ListOrderMode.LIST,
        initialSelectedIndex = selectedIndex,
        onDismiss = onDismiss,
        onSelect = { _, item -> onSelect(item.id) }
    )
}

@Composable
internal fun AudioLanguageDialog(
    selectedLanguageCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = AudioLanguageCatalog.all
    val selectedIndex = remember(selectedLanguageCode) {
        languages.indexOfFirst { it.code == selectedLanguageCode }.coerceAtLeast(0)
    }

    val items = remember(selectedIndex) {
        languages.mapIndexed { index, language ->
            ListOrderItem(
                id = language.code.ifBlank { "original" },
                title = language.label,
                selected = index == selectedIndex
            )
        }
    }

    ListOrderDialog(
        title = "Audio Language",
        items = items,
        mode = ListOrderMode.LIST,
        initialSelectedIndex = selectedIndex,
        onDismiss = onDismiss,
        onSelect = { index, _ -> onSelect(languages[index].code) }
    )
}
