package com.paregi.animetv.features.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalAppToast
import com.paregi.animetv.core.ui.ConfirmDialog
import com.paregi.animetv.core.ui.ImgPickerDialog
import com.paregi.animetv.core.ui.ImgPickerItem
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.core.ui.TextPromptDialog
import com.paregi.animetv.domain.AccountRepository
import com.paregi.animetv.domain.ProfileDirectory
import com.paregi.animetv.domain.UserProfile

internal val parentalRatingShortNames = listOf("G", "PG", "PG-13", "R", "R+", "Rx", "No Limitation")

private val parentalRatingDescriptions = listOf(
    "G - All Ages",
    "PG - Children",
    "PG-13 - Teens 13 and Older",
    "R - 17+ Violence & Profanity",
    "R+ - Profanity & Mild Nudity",
    "Rx - More adult audience",
    "No Limitation"
)

private sealed interface ProfileDialogStep {
    data class Options(val targetPrefix: String, val initialIndex: Int = 0) : ProfileDialogStep
    data class ManageUsers(val initialIndex: Int = 0) : ProfileDialogStep
    data object DefaultUser : ProfileDialogStep
    data class EditName(val targetPrefix: String) : ProfileDialogStep
    data class EditAvatar(val targetPrefix: String) : ProfileDialogStep
    data class VerifyCurrentPin(val targetPrefix: String) : ProfileDialogStep
    data class SetPin(val targetPrefix: String) : ProfileDialogStep
    data class ConfirmPin(val targetPrefix: String, val firstPin: String) : ProfileDialogStep
    data class PinError(val targetPrefix: String, val message: String) : ProfileDialogStep
    data class RemovePin(val targetPrefix: String) : ProfileDialogStep
    data class ParentalControl(val targetPrefix: String) : ProfileDialogStep
    data class DeleteUser(val targetPrefix: String) : ProfileDialogStep
}

@Composable
internal fun ProfileOptionsFlowDialog(
    directory: ProfileDirectory,
    activeProfile: UserProfile,
    accountRepository: AccountRepository,
    startInManageUsers: Boolean = false,
    onDismiss: () -> Unit,
    onSwitchUser: () -> Unit,
    onCreateUser: (displayName: String) -> Unit,
    onSetDefaultUser: (prefix: String) -> Unit,
    onSaveUser: (UserProfile) -> Unit,
    onDeleteUser: (prefix: String) -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val showToast = LocalAppToast.current
    val isAdmin = activeProfile.prefix.isEmpty()

    var step by remember(startInManageUsers, activeProfile.prefix) {
        mutableStateOf<ProfileDialogStep>(
            if (startInManageUsers && isAdmin) ProfileDialogStep.ManageUsers()
            else ProfileDialogStep.Options(activeProfile.prefix)
        )
    }
    var returnToManageUsers by remember(startInManageUsers) { mutableStateOf(startInManageUsers) }
    var lastOptionsIndex by remember { mutableIntStateOf(0) }
    var lastManageIndex by remember { mutableIntStateOf(0) }

    fun findProfile(prefix: String): UserProfile? = directory.profiles.firstOrNull { it.prefix == prefix }

    fun backFromOptions(targetPrefix: String) {
        if (targetPrefix == activeProfile.prefix && !returnToManageUsers) {
            onDismiss()
        } else if (isAdmin) {
            step = ProfileDialogStep.ManageUsers(lastManageIndex)
        } else {
            onDismiss()
        }
    }

    when (val current = step) {
        is ProfileDialogStep.Options -> {
            val target = findProfile(current.targetPrefix)
            if (target == null) {
                if (isAdmin) step = ProfileDialogStep.ManageUsers(0) else onDismiss()
                return
            }
            val items = remember(target, activeProfile.prefix, directory.profiles.size, isAdmin) {
                buildList {
                    if (target.prefix == activeProfile.prefix && directory.profiles.size > 1) {
                        add(ListOrderItem(id = "switch", title = "Switch User", icon = IconGlyphs.Cycle))
                    }
                    add(
                        ListOrderItem(
                            id = "name",
                            title = "Display Name",
                            value = target.displayName,
                            icon = IconGlyphs.TextFormat
                        )
                    )
                    add(
                        ListOrderItem(
                            id = "avatar",
                            title = "Profile Picture",
                            value = target.avatarTitle.ifBlank { target.avatarId },
                            icon = IconGlyphs.Wallpaper
                        )
                    )
                    add(
                        ListOrderItem(
                            id = "pin",
                            title = "PIN",
                            value = if (target.pin.isNotBlank()) "Have PIN" else "No PIN",
                            icon = IconGlyphs.Encrypted
                        )
                    )
                    if (target.prefix.isEmpty() && isAdmin) {
                        add(ListOrderItem(id = "manage", title = "Manage Users", icon = IconGlyphs.Build))
                    } else if (isAdmin && target.prefix.isNotEmpty()) {
                        add(
                            ListOrderItem(
                                id = "parental",
                                title = "Parental Control",
                                value = parentalRatingShortNames[target.parentalRating.coerceIn(0, 6)],
                                icon = IconGlyphs.ShieldPerson
                            )
                        )
                        add(ListOrderItem(id = "delete", title = "Delete User", icon = IconGlyphs.DeleteForever))
                    }
                }
            }

            ListOrderDialog(
                title = target.displayName,
                items = items,
                mode = ListOrderMode.MENU,
                headerAvatar = { ProfileAvatar(target, tokens.u * 3f) },
                headerTrailingIcon = if (target.prefix != activeProfile.prefix && isAdmin) IconGlyphs.Build else null,
                initialSelectedIndex = current.initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                onDismiss = { backFromOptions(target.prefix) },
                onSelect = { index, item ->
                    lastOptionsIndex = index
                    when (item.id) {
                        "switch" -> onSwitchUser()
                        "name" -> step = ProfileDialogStep.EditName(target.prefix)
                        "avatar" -> step = ProfileDialogStep.EditAvatar(target.prefix)
                        "pin" -> {
                            step = if (target.prefix == activeProfile.prefix && target.pin.isNotBlank()) {
                                ProfileDialogStep.VerifyCurrentPin(target.prefix)
                            } else {
                                ProfileDialogStep.SetPin(target.prefix)
                            }
                        }
                        "manage" -> {
                            returnToManageUsers = true
                            step = ProfileDialogStep.ManageUsers(0)
                        }
                        "parental" -> step = ProfileDialogStep.ParentalControl(target.prefix)
                        "delete" -> step = ProfileDialogStep.DeleteUser(target.prefix)
                    }
                }
            )
        }

        is ProfileDialogStep.ManageUsers -> {
            val canAddNew = directory.profiles.size < 5
            val items = remember(directory.profiles, directory.defaultPrefix) {
                val nonAdminUsers = directory.profiles.filter { it.prefix.isNotBlank() }
                val defaultUser = directory.defaultPrefix
                    ?.takeIf { it.isNotBlank() }
                    ?.let { prefix -> directory.profiles.firstOrNull { it.prefix == prefix } }
                buildList {
                    if (canAddNew) {
                        add(ListOrderItem(id = "addnew", title = "Add New User", icon = IconGlyphs.Add))
                    }
                    if (directory.profiles.size > 1) {
                        add(
                            ListOrderItem(
                                id = "default",
                                title = "Default User",
                                value = defaultUser?.displayName.orEmpty(),
                                icon = IconGlyphs.Check
                            )
                        )
                    }
                    nonAdminUsers.forEach { user ->
                        add(
                            ListOrderItem(
                                id = "user:${user.prefix}",
                                title = user.displayName,
                                avatarUrl = user.avatarUrl,
                                avatarDrawable = if (user.avatarUrl.isNullOrBlank()) profileAvatarResource(user.avatarId) else null
                            )
                        )
                    }
                }
            }

            ListOrderDialog(
                title = "Manage Users",
                items = items,
                mode = ListOrderMode.MENU,
                initialSelectedIndex = current.initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                onDismiss = {
                    if (startInManageUsers) {
                        onDismiss()
                    } else {
                        returnToManageUsers = false
                        step = ProfileDialogStep.Options(activeProfile.prefix, lastOptionsIndex)
                    }
                },
                onSelect = { index, item ->
                    lastManageIndex = index
                    when {
                        item.id == "addnew" -> {
                            onCreateUser("User ${directory.profiles.size + 1}")
                            showToast("New user has been created")
                            step = ProfileDialogStep.ManageUsers(0)
                        }
                        item.id == "default" -> step = ProfileDialogStep.DefaultUser
                        item.id.startsWith("user:") -> {
                            val prefix = item.id.removePrefix("user:")
                            step = ProfileDialogStep.Options(prefix, 0)
                        }
                    }
                }
            )
        }

        ProfileDialogStep.DefaultUser -> {
            val currentDefault = directory.defaultPrefix?.takeIf { it.isNotBlank() }.orEmpty()
            val items = remember(directory.profiles, currentDefault) {
                val nonAdminUsers = directory.profiles.filter { it.prefix.isNotBlank() }
                buildList {
                    add(
                        ListOrderItem(
                            id = "",
                            title = "Always show login screen",
                            selected = currentDefault.isEmpty()
                        )
                    )
                    nonAdminUsers.forEach { user ->
                        add(
                            ListOrderItem(
                                id = user.prefix,
                                title = user.displayName,
                                selected = currentDefault == user.prefix
                            )
                        )
                    }
                }
            }
            val initialIndex = remember(items) { items.indexOfFirst { it.selected }.coerceAtLeast(0) }
            ListOrderDialog(
                title = "Set Default User",
                items = items,
                mode = ListOrderMode.LIST,
                initialSelectedIndex = initialIndex,
                onDismiss = { step = ProfileDialogStep.ManageUsers(lastManageIndex) },
                onSelect = { _, item ->
                    onSetDefaultUser(item.id)
                    step = ProfileDialogStep.ManageUsers(lastManageIndex)
                }
            )
        }

        is ProfileDialogStep.EditName -> {
            val target = findProfile(current.targetPrefix) ?: run {
                step = ProfileDialogStep.Options(current.targetPrefix, lastOptionsIndex)
                return
            }
            TextPromptDialog(
                title = "Set Display Name",
                initialValue = target.displayName,
                placeholder = "Please insert new <b>Display Name</b>",
                maxLength = 18,
                isPin = false,
                onConfirm = { entered ->
                    val trimmed = entered.trim()
                    if (trimmed.isNotBlank()) {
                        onSaveUser(target.copy(displayName = trimmed.take(18)))
                    }
                    step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex)
                },
                onDismiss = { step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex) }
            )
        }

        is ProfileDialogStep.EditAvatar -> {
            val target = findProfile(current.targetPrefix) ?: run {
                step = ProfileDialogStep.Options(current.targetPrefix, lastOptionsIndex)
                return
            }
            var remoteAvatars by remember(target.prefix) { mutableStateOf(emptyMap<String, String>()) }
            LaunchedEffect(target.prefix, activeProfile.prefix, accountRepository) {
                remoteAvatars = if (target.prefix == activeProfile.prefix) {
                    runCatching { accountRepository.loadProfileAvatars() }.getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }
            }

            val items = remember(remoteAvatars, target.avatarId, target.avatarUrl) {
                val rawItems = buildList {
                    remoteAvatars["-anilist"]?.takeIf { it.isNotBlank() }?.let { url ->
                        add(
                            ImgPickerItem(
                                id = "-anilist",
                                title = "AniList Avatar",
                                imageUrl = url,
                                selected = target.avatarId == "-anilist" || target.avatarUrl == url
                            )
                        )
                    }
                    remoteAvatars["-mal"]?.takeIf { it.isNotBlank() }?.let { url ->
                        add(
                            ImgPickerItem(
                                id = "-mal",
                                title = "MAL Avatar",
                                imageUrl = url,
                                selected = target.avatarId == "-mal" || target.avatarUrl == url
                            )
                        )
                    }
                    profileAvatarOptions.forEach { (id, title) ->
                        add(
                            ImgPickerItem(
                                id = id,
                                title = title,
                                drawableName = profileAvatarResource(id),
                                selected = target.avatarId == id && target.avatarUrl.isNullOrBlank()
                            )
                        )
                    }
                }
                if (rawItems.any { it.selected } || rawItems.isEmpty()) {
                    rawItems
                } else {
                    rawItems.mapIndexed { idx, item -> if (idx == 0) item.copy(selected = true) else item }
                }
            }

            ImgPickerDialog(
                title = "Set Profile Picture",
                items = items,
                onSelect = { selected ->
                    val isRemote = selected.id == "-anilist" || selected.id == "-mal"
                    onSaveUser(
                        target.copy(
                            avatarId = selected.id,
                            avatarUrl = if (isRemote) selected.imageUrl else null,
                            avatarTitle = selected.title
                        )
                    )
                    step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex)
                },
                onDismiss = { step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex) }
            )
        }

        is ProfileDialogStep.VerifyCurrentPin -> {
            val target = findProfile(current.targetPrefix) ?: run {
                step = ProfileDialogStep.Options(current.targetPrefix, lastOptionsIndex)
                return
            }
            TextPromptDialog(
                title = "Input your current PIN",
                initialValue = "",
                placeholder = "",
                maxLength = 4,
                isPin = true,
                onConfirm = { entered ->
                    if (entered == target.pin) {
                        step = ProfileDialogStep.SetPin(target.prefix)
                    } else {
                        showToast("PIN is Invalid")
                        step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex)
                    }
                },
                onDismiss = { step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex) }
            )
        }

        is ProfileDialogStep.SetPin -> {
            val target = findProfile(current.targetPrefix) ?: run {
                step = ProfileDialogStep.Options(current.targetPrefix, lastOptionsIndex)
                return
            }
            TextPromptDialog(
                title = "Set Pin",
                initialValue = "",
                placeholder = "• Use <b>4 Numeric</b> as PIN<br>• Leave <b>empty</b> to clear pin",
                maxLength = 4,
                isPin = true,
                onConfirm = { entered ->
                    when {
                        entered.isEmpty() && target.pin.isNotBlank() -> {
                            step = ProfileDialogStep.RemovePin(target.prefix)
                        }
                        entered.isEmpty() -> {
                            step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex)
                        }
                        entered.length != 4 || !entered.all(Char::isDigit) -> {
                            step = ProfileDialogStep.PinError(
                                target.prefix,
                                "Please use 4 numeric for pin<br>Try Again?"
                            )
                        }
                        else -> {
                            step = ProfileDialogStep.ConfirmPin(target.prefix, entered)
                        }
                    }
                },
                onDismiss = { step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex) }
            )
        }

        is ProfileDialogStep.ConfirmPin -> {
            val target = findProfile(current.targetPrefix) ?: run {
                step = ProfileDialogStep.Options(current.targetPrefix, lastOptionsIndex)
                return
            }
            TextPromptDialog(
                title = "Confirm Pin",
                initialValue = "",
                placeholder = "• Input PIN again to confirm",
                maxLength = 4,
                isPin = true,
                onConfirm = { entered ->
                    if (entered == current.firstPin) {
                        onSaveUser(target.copy(pin = entered))
                        showToast("PIN has been changed")
                        step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex)
                    } else {
                        step = ProfileDialogStep.PinError(
                            target.prefix,
                            "PIN Confirmation not match<br>Try Again?"
                        )
                    }
                },
                onDismiss = { step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex) }
            )
        }

        is ProfileDialogStep.PinError -> {
            ConfirmDialog(
                title = "Change PIN Failed",
                message = current.message,
                yesLabel = "YES",
                noLabel = "NO",
                onConfirm = { step = ProfileDialogStep.SetPin(current.targetPrefix) },
                onDismiss = { step = ProfileDialogStep.Options(current.targetPrefix, lastOptionsIndex) }
            )
        }

        is ProfileDialogStep.RemovePin -> {
            val target = findProfile(current.targetPrefix) ?: run {
                step = ProfileDialogStep.Options(current.targetPrefix, lastOptionsIndex)
                return
            }
            ConfirmDialog(
                title = "Remove PIN",
                message = "Are you sure you want to remove user PIN?",
                yesLabel = "YES",
                noLabel = "NO",
                onConfirm = {
                    onSaveUser(target.copy(pin = ""))
                    showToast("PIN has been removed")
                    step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex)
                },
                onDismiss = { step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex) }
            )
        }

        is ProfileDialogStep.ParentalControl -> {
            val target = findProfile(current.targetPrefix) ?: run {
                step = ProfileDialogStep.Options(current.targetPrefix, lastOptionsIndex)
                return
            }
            val currentRating = target.parentalRating.coerceIn(0, 6)
            val items = remember(currentRating) {
                parentalRatingDescriptions.mapIndexed { index, desc ->
                    ListOrderItem(
                        id = index.toString(),
                        title = desc,
                        selected = index == currentRating
                    )
                }
            }
            ListOrderDialog(
                title = "Parental Control",
                items = items,
                mode = ListOrderMode.LIST,
                initialSelectedIndex = currentRating,
                onDismiss = { step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex) },
                onSelect = { index, _ ->
                    onSaveUser(target.copy(parentalRating = index.coerceIn(0, 6)))
                    step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex)
                }
            )
        }

        is ProfileDialogStep.DeleteUser -> {
            val target = findProfile(current.targetPrefix) ?: run {
                step = ProfileDialogStep.ManageUsers(0)
                return
            }
            ConfirmDialog(
                title = "Delete User",
                message = "Are you sure you want to delete user <b>${target.displayName}</b>?",
                yesLabel = "YES",
                noLabel = "NO",
                onConfirm = {
                    onDeleteUser(target.prefix)
                    showToast("User has been deleted")
                    step = ProfileDialogStep.ManageUsers(0)
                },
                onDismiss = { step = ProfileDialogStep.Options(target.prefix, lastOptionsIndex) }
            )
        }
    }
}
