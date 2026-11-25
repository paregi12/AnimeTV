package com.paregi.animetv.features.mylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.domain.AccountState
import com.paregi.animetv.domain.DEFAULT_MY_LIST_SECTIONS
import com.paregi.animetv.domain.MY_LIST_SECTION_IDS
import com.paregi.animetv.domain.MyListLayout
import com.paregi.animetv.domain.MyListSectionPreference

private fun myListDisablingIds(id: String): List<String> = when (id) {
    "maltab" -> listOf("mal")
    "mal" -> listOf("maltab")
    "anilisttab" -> listOf("anilist")
    "anilist" -> listOf("anilisttab")
    else -> emptyList()
}

@Composable
internal fun MyListLayoutDialog(
    layout: MyListLayout,
    onDismiss: () -> Unit,
    onSave: (MyListLayout) -> Unit,
    accountState: AccountState = AccountState()
) {
    val fullSections = remember(layout) {
        val normalized = layout.sections.map {
            if (it.id == "watchlist") it.copy(id = "fav") else it
        }.filter { it.id in MY_LIST_SECTION_IDS }.distinctBy { it.id }
        val existingIds = normalized.map { it.id }.toSet()
        val missing = MY_LIST_SECTION_IDS.filterNot { it in existingIds }
            .map { MyListSectionPreference(it, enabled = it in DEFAULT_MY_LIST_SECTIONS) }
        normalized + missing
    }
    var latestSections by remember(fullSections) { mutableStateOf(fullSections) }
    val items = remember(fullSections, accountState) {
        fullSections.map { sec ->
            ListOrderItem(
                id = sec.id,
                title = myListSectionTitle(sec.id, accountState, forLayoutDialog = true),
                selected = sec.enabled,
                disablingIds = myListDisablingIds(sec.id)
            )
        }
    }

    ListOrderDialog(
        title = "Customize MyList",
        items = items,
        mode = ListOrderMode.ORDER,
        footerTip = "Use left/right to move order",
        onDismiss = {
            if (latestSections != fullSections) {
                onSave(MyListLayout(latestSections))
            } else {
                onDismiss()
            }
        },
        onReorder = { reordered ->
            latestSections = reordered.map { MyListSectionPreference(id = it.id, enabled = it.selected) }
        }
    )
}
