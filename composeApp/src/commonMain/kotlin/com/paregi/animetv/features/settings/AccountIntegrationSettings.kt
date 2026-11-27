package com.paregi.animetv.features.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.paregi.animetv.LocalNavigationSound
import com.paregi.animetv.PlatformQrCode
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.performanceEffectDisabled
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.core.ui.AboutPopupDialog
import com.paregi.animetv.core.ui.ConfirmDialog
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.core.ui.SettingsGroupTitle
import com.paregi.animetv.domain.AccountProvider
import com.paregi.animetv.domain.AccountState
import com.paregi.animetv.domain.LoginSession
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import kotlinx.coroutines.yield

internal data class LoginDialogState(
    val provider: AccountProvider,
    val session: LoginSession?,
    val authorizing: Boolean = false
)

@Composable
internal fun AccountIntegrationRows(
    accountState: AccountState,
    progressSetting: SettingValue,
    trackingSetting: SettingValue,
    onConnect: (AccountProvider) -> Unit,
    onLogoutRequest: (AccountProvider) -> Unit
) {
    Column {
        SettingsGroupTitle("Integration")
        val malConnected = accountState.malUsername != null
        SettingValueRow(
            SettingValue(
                label = if (malConnected) "MAL Logout" else "MAL Login",
                value = accountState.malUsername.orEmpty(),
                description = if (malConnected) "Disconnect MyAnimeList account" else "Connect to MyAnimeList account",
                icon = if (malConnected) IconGlyphs.LockOpen else IconGlyphs.ListAlt
            ) {
                if (malConnected) onLogoutRequest(AccountProvider.MAL)
                else onConnect(AccountProvider.MAL)
            }
        )
        val aniListConnected = accountState.aniListUsername != null
        SettingValueRow(
            SettingValue(
                label = if (aniListConnected) "AniList Logout" else "AniList Login",
                value = accountState.aniListUsername.orEmpty(),
                description = if (aniListConnected) "Disconnect AniList account" else "Connect to AniList account",
                icon = if (aniListConnected) IconGlyphs.LockOpen else IconGlyphs.Hub
            ) {
                if (aniListConnected) onLogoutRequest(AccountProvider.ANILIST)
                else onConnect(AccountProvider.ANILIST)
            }
        )
        SettingValueRow(progressSetting)
        SettingValueRow(trackingSetting)
    }
}

@Composable
internal fun AccountIntegrationDialogs(
    malLoginTypeDialog: Boolean,
    malPasswordDialog: Boolean,
    malPasswordSubmitting: Boolean,
    malPasswordError: String?,
    loginDialog: LoginDialogState?,
    logoutConfirmation: AccountProvider?,
    onDismissMalLoginType: () -> Unit,
    onSelectMalLoginType: (usePassword: Boolean) -> Unit,
    onSubmitMalPassword: (username: String, password: String) -> Unit,
    onDismissMalPassword: () -> Unit,
    onDismissLogin: () -> Unit,
    onDismissLogout: () -> Unit,
    onDisconnect: (AccountProvider) -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val uriHandler = LocalUriHandler.current

    if (malLoginTypeDialog) {
        ListOrderDialog(
            title = "MyAnimeList Login Type",
            items = listOf(
                ListOrderItem(
                    id = "qr",
                    title = "QRCode Web Authorization",
                    icon = IconGlyphs.ViewModule
                ),
                ListOrderItem(
                    id = "password",
                    title = "Username + Password",
                    icon = IconGlyphs.Encrypted
                )
            ),
            mode = ListOrderMode.LIST,
            onDismiss = onDismissMalLoginType,
            onSelect = { index, _ -> onSelectMalLoginType(index == 1) }
        )
    }

    if (malPasswordDialog) {
        MalPasswordLoginDialog(
            submitting = malPasswordSubmitting,
            errorMessage = malPasswordError,
            onSubmit = onSubmitMalPassword,
            onDismiss = onDismissMalPassword
        )
    }

    if (malPasswordSubmitting) {
        AboutPopupDialog(
            visible = true,
            loadingText = "Please Wait..",
            onDismiss = {}
        )
    }

    loginDialog?.let { state ->
        val isAniList = state.provider == AccountProvider.ANILIST
        val session = state.session
        when {
            session == null -> {
                AboutPopupDialog(
                    visible = true,
                    loadingText = "Please Wait..",
                    onDismiss = onDismissLogin
                )
            }
            state.authorizing -> {
                AboutPopupDialog(
                    visible = true,
                    loadingText = if (isAniList) "Please finish AniList authorization" else "Please finish MAL authorization",
                    onDismiss = onDismissLogin
                )
            }
            else -> {
                AboutPopupDialog(
                    visible = true,
                    qrContent = {
                        Box(modifier = Modifier.size(tokens.u * 20f), contentAlignment = Alignment.Center) {
                            PlatformQrCode(session.loginUrl)
                        }
                    },
                    caption = if (isAniList) {
                        "Scan QR and login to AniList in your phone"
                    } else {
                        "Scan QR and login to MAL in your phone"
                    },
                    onClick = {
                        runCatching { uriHandler.openUri(session.loginUrl) }
                    },
                    onDismiss = onDismissLogin
                )
            }
        }
    }

    logoutConfirmation?.let { provider ->
        val isMal = provider == AccountProvider.MAL
        ConfirmDialog(
            title = if (isMal) "Logout MAL" else "Logout AniList",
            message = if (isMal) {
                "Are you sure you want to logout MyAnimeList integration?"
            } else {
                "Are you sure you want to logout AniList integration?"
            },
            yesLabel = "YES",
            noLabel = "NO",
            onConfirm = { onDisconnect(provider) },
            onDismiss = onDismissLogout
        )
    }
}

@Composable
private fun MalPasswordLoginDialog(
    submitting: Boolean,
    errorMessage: String?,
    onSubmit: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val playClickSound = LocalNavigationSound.current
    val shadowsDisabled = LocalPerformanceEffects.current.performanceEffectDisabled(2)

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val animProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
    )

    val usernameRequester = remember { FocusRequester() }
    val passwordRequester = remember { FocusRequester() }
    val loginRequester = remember { FocusRequester() }
    val cancelRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        yield()
        runCatching { usernameRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f * animProgress))
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center
        ) {
            val baseWidth = if (tokens.portrait) tokens.vh * 36f else tokens.vw * 40f
            val dialogWidth = baseWidth.coerceAtMost(tokens.screenWidth * 0.90f)
            val btnWidth = dialogWidth * (if (tokens.portrait) 0.40f else 0.45f)
            val cornerRadius = tokens.u * 0.5f

            Column(
                modifier = Modifier
                    .width(dialogWidth)
                    .graphicsLayer {
                        val s = 0.8f + 0.2f * animProgress
                        scaleX = s
                        scaleY = s
                        alpha = animProgress
                    }
                    .then(
                        if (!shadowsDisabled) {
                            Modifier.shadow(tokens.u * 0.6f, RoundedCornerShape(cornerRadius), clip = false)
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFF222222))
                    .pointerInput(Unit) { detectTapGestures {} }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                            playClickSound()
                            onDismiss()
                            true
                        } else false
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181818))
                        .padding(tokens.u * 0.6f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MyAnimeList Login",
                        color = Color(0xFFDDDDDD),
                        fontSize = fsSp(tokens.fs12),
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(thickness = (tokens.u * 0.1f).coerceAtLeast(1.dp), color = Color(0xFF121212))

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF7A7A),
                        fontSize = fsSp(tokens.fs12),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = tokens.u * 1.5f, end = tokens.u * 1.5f, top = tokens.u * 1f)
                    )
                }

                MalInputField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "Username",
                    isPassword = false,
                    imeAction = ImeAction.Next,
                    focusRequester = usernameRequester,
                    onImeAction = { runCatching { passwordRequester.requestFocus() } },
                    accentColor = colors.accent1
                )

                MalInputField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    focusRequester = passwordRequester,
                    onImeAction = {
                        if (!submitting) onSubmit(username, password)
                    },
                    accentColor = colors.accent1
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = tokens.u * 1f, end = tokens.u * 1f, bottom = tokens.u * 1f),
                    horizontalArrangement = Arrangement.spacedBy(tokens.u * 1f, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MalDialogButton(
                        label = "Login",
                        width = btnWidth,
                        enabled = !submitting,
                        focusRequester = loginRequester,
                        onClick = { onSubmit(username, password) }
                    )
                    MalDialogButton(
                        label = "Cancel",
                        width = btnWidth,
                        enabled = !submitting,
                        focusRequester = cancelRequester,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun MalInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean,
    imeAction: ImeAction,
    focusRequester: FocusRequester,
    onImeAction: () -> Unit,
    accentColor: Color
) {
    val tokens = LocalAnimeTvTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.u * 1f, vertical = tokens.u * 0.6f)
            .clip(RoundedCornerShape(tokens.u * 0.4f))
            .background(Color(0xFF111111))
            .padding(tokens.u * 0.6f),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = fsSp(tokens.fs15),
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(accentColor),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() }
            ),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color(0xFF666666),
                            fontSize = fsSp(tokens.fs15),
                            textAlign = TextAlign.Center
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

@Composable
private fun MalDialogButton(
    label: String,
    width: Dp,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        focused -> colors.primary1
        hovered -> colors.primary2
        else -> Color(0xFF111111)
    }

    Box(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(tokens.u * 0.6f))
            .background(bgColor)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(enabled = enabled, onClick = onClick)
            .navigationFocusable(enabled = enabled)
            .padding(tokens.u * 0.6f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = fsSp(tokens.fs15),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
