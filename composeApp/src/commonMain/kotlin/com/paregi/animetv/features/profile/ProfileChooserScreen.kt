package com.paregi.animetv.features.profile

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.paregi.animetv.LocalNavigationSound
import com.paregi.animetv.PlatformBackHandler
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.PurpleLight
import com.paregi.animetv.core.theme.TextSecondary
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.domain.ProfileAppearance
import com.paregi.animetv.domain.UserProfile
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import com.paregi.animetv.platformImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

@Composable
internal fun ProfileLoadingScreen() {
    val tokens = LocalAnimeTvTokens.current
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text("Loading profiles…", color = TextSecondary, fontSize = fsSp(tokens.fs15))
    }
}

internal val profileAvatarOptions = listOf(
    "0" to "Anymoe", "1" to "Umaru-chan", "2" to "Luffy", "3" to "Gintoki",
    "4" to "Onizuka", "5" to "Gojo Satoru", "6" to "Asa Mitaka", "zoro" to "Zoro",
    "chainsaw" to "Chainsaw", "pochita" to "Pochita", "reze" to "Reze", "sukuna" to "Sukuna",
    "gotouhitori" to "Gotou Hitori", "saber" to "Saber", "gear5" to "Luffy Gear 5",
    "nobara" to "Nobara", "power" to "Power", "ryo" to "Ryo", "igris" to "Igris",
    "sungjinwoo" to "Sung Jinwoo", "chopper" to "Chopper", "faputa" to "Faputa",
    "pain" to "Pain", "7-quanxi" to "Quanxi", "8-spike" to "Spike", "9-vash" to "Vash",
    "10-tanjiro" to "Tanjiro", "11-nezuko" to "Nezuko", "12-rimuru" to "Rimuru",
    "13-sanji" to "Sanji", "14-nami" to "Nami", "15-nicorobin" to "Nico Robin",
    "16-naruto" to "Naruto", "17-kakashi" to "Kakashi", "18-hollowichigo" to "Hollow Ichigo",
    "19-byakuya" to "Byakuya"
)

internal fun profileAvatarResource(id: String): String = when (id) {
    "1" -> "asset_1"
    "2" -> "asset_2"
    "3" -> "asset_3"
    "4" -> "asset_4"
    "5" -> "asset_5"
    "6" -> "asset_6"
    "7-quanxi" -> "asset_7_quanxi"
    "8-spike" -> "asset_8_spike"
    "9-vash" -> "asset_9_vash"
    "10-tanjiro" -> "asset_10_tanjiro"
    "11-nezuko" -> "asset_11_nezuko"
    "12-rimuru" -> "asset_12_rimuru"
    "13-sanji" -> "asset_13_sanji"
    "14-nami" -> "asset_14_nami"
    "15-nicorobin" -> "asset_15_nicorobin"
    "16-naruto" -> "asset_16_naruto"
    "17-kakashi" -> "asset_17_kakashi"
    "18-hollowichigo" -> "asset_18_hollowichigo"
    "19-byakuya" -> "asset_19_byakuya"
    "zoro" -> "zoro"
    "chainsaw" -> "chainsaw"
    "pochita" -> "pochita"
    "reze" -> "reze"
    "sukuna" -> "sukuna"
    "gotouhitori" -> "gotouhitori"
    "saber" -> "saber"
    "gear5" -> "asset_gear5"
    "nobara" -> "nobara"
    "power" -> "power"
    "ryo" -> "ryo"
    "igris" -> "igris"
    "sungjinwoo" -> "sungjinwoo"
    "chopper" -> "chopper"
    "faputa" -> "faputa"
    "pain" -> "pain"
    else -> "asset_0"
}

@Composable
internal fun ProfileAvatar(profile: UserProfile, size: Dp) {
    val modifier = Modifier.width(size).height(size).clip(CircleShape).background(PurpleLight)
    if (!profile.avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = profile.avatarUrl,
            contentDescription = profile.avatarTitle,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Image(
            painter = platformImagePainter(profileAvatarResource(profile.avatarId)),
            contentDescription = profile.avatarTitle,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Composable
internal fun ProfileChooserScreen(
    profiles: List<UserProfile>,
    defaultPrefix: String?,
    preferredPrefix: String?,
    allowCancel: Boolean,
    loginScreenStyle: Int,
    appearance: ProfileAppearance,
    backdropColor: Color,
    onSelected: (UserProfile) -> Unit,
    onCancel: () -> Unit,
    onPreviewProfile: (UserProfile) -> Unit,
    onExitApplication: () -> Unit = {}
) {
    var pinProfile by remember { mutableStateOf<UserProfile?>(null) }
    var pinValue by remember { mutableStateOf("") }
    var pinErrorActive by remember { mutableStateOf(false) }

    fun handleBackPress() {
        when {
            pinProfile != null && pinValue.isNotEmpty() -> {
                pinValue = ""
                pinErrorActive = false
            }
            pinProfile != null -> {
                pinProfile = null
                pinValue = ""
                pinErrorActive = false
            }
            allowCancel -> onCancel()
            else -> onExitApplication()
        }
    }

    if (allowCancel) {
        Dialog(
            onDismissRequest = ::handleBackPress,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ProfileChooserContent(
                profiles = profiles,
                defaultPrefix = defaultPrefix,
                preferredPrefix = preferredPrefix,
                allowCancel = true,
                loginScreenStyle = loginScreenStyle,
                appearance = appearance,
                backdropColor = backdropColor,
                pinProfile = pinProfile,
                pinValue = pinValue,
                pinErrorActive = pinErrorActive,
                onPinProfileChange = { pinProfile = it },
                onPinValueChange = { pinValue = it },
                onPinErrorActiveChange = { pinErrorActive = it },
                onBackPress = ::handleBackPress,
                onSelected = onSelected,
                onPreviewProfile = onPreviewProfile
            )
        }
    } else {
        ProfileChooserContent(
            profiles = profiles,
            defaultPrefix = defaultPrefix,
            preferredPrefix = preferredPrefix,
            allowCancel = false,
            loginScreenStyle = loginScreenStyle,
            appearance = appearance,
            backdropColor = backdropColor,
            pinProfile = pinProfile,
            pinValue = pinValue,
            pinErrorActive = pinErrorActive,
            onPinProfileChange = { pinProfile = it },
            onPinValueChange = { pinValue = it },
            onPinErrorActiveChange = { pinErrorActive = it },
            onBackPress = ::handleBackPress,
            onSelected = onSelected,
            onPreviewProfile = onPreviewProfile
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileChooserContent(
    profiles: List<UserProfile>,
    defaultPrefix: String?,
    preferredPrefix: String?,
    allowCancel: Boolean,
    loginScreenStyle: Int,
    appearance: ProfileAppearance,
    backdropColor: Color,
    pinProfile: UserProfile?,
    pinValue: String,
    pinErrorActive: Boolean,
    onPinProfileChange: (UserProfile?) -> Unit,
    onPinValueChange: (String) -> Unit,
    onPinErrorActiveChange: (Boolean) -> Unit,
    onBackPress: () -> Unit,
    onSelected: (UserProfile) -> Unit,
    onPreviewProfile: (UserProfile) -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val playClickSound = LocalNavigationSound.current
    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }

    var pinErrorCount by remember { mutableIntStateOf(0) }
    val pinClearFocusRequester = remember { FocusRequester() }
    val pinZeroFocusRequester = remember { FocusRequester() }
    var focusedPinKey by remember { mutableStateOf("0") }

    val initialFocusedPrefix = preferredPrefix
        ?.takeIf { prefix -> profiles.any { it.prefix == prefix } }
        ?: defaultPrefix?.takeIf { prefix -> profiles.any { it.prefix == prefix } }
        ?: profiles.firstOrNull()?.prefix
    var focusedProfilePrefix by remember(profiles, initialFocusedPrefix) {
        mutableStateOf(initialFocusedPrefix)
    }

    val animatedBackdrop by animateColorAsState(
        targetValue = backdropColor,
        animationSpec = tween(durationMillis = tokens.tim10s * 2, easing = tokens.easing)
    )

    val visibleWallpaper = if (loginScreenStyle > 1) appearance.wallpaperUrl else ""

    PlatformBackHandler(enabled = !allowCancel || pinProfile != null) {
        playClickSound()
        onBackPress()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(
            targetState = visibleWallpaper,
            animationSpec = tween(durationMillis = tokens.tim10s * 4, easing = tokens.easing),
            label = "login-wallpaper"
        ) { wp ->
            if (wp.isNotBlank()) {
                val localWallpaper = curatedWallpapers.firstOrNull { it.source == wp }
                if (localWallpaper != null) {
                    Image(
                        painter = platformImagePainter(localWallpaper.drawable!!),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = wp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.5f), animatedBackdrop)
                )
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = tokens.u * 5.5f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Who's Watching?",
                    color = Color(0xFF999999),
                    fontSize = fsSp(tokens.u * 3.5f),
                    lineHeight = fsSp(tokens.u * 3.5f),
                    modifier = Modifier.padding(bottom = tokens.u * 2f)
                )

                val itemBuilder: @Composable (UserProfile) -> Unit = { profile ->
                    val selected = focusedProfilePrefix == profile.prefix
                    val requester = remember(profile.prefix) { FocusRequester() }
                    val interactionSource = remember { MutableInteractionSource() }
                    val hovered by interactionSource.collectIsHoveredAsState()

                    LaunchedEffect(initialFocusedPrefix) {
                        if (profile.prefix == initialFocusedPrefix && pinProfile == null) {
                            yield()
                            runCatching { requester.requestFocus() }
                        }
                    }

                    val itemHorizontalPad = tokens.u * 1f
                    val itemTopPad = if (tokens.portrait) tokens.vh * 3f else tokens.vw * 1f
                    val itemBottomPad = if (tokens.portrait) tokens.vh * 2f else tokens.vw * 1f
                    val avatarBoxSize = tokens.u * 14f
                    val borderSize = tokens.u * 0.8f

                    Column(
                        modifier = Modifier
                            .padding(
                                start = itemHorizontalPad,
                                end = itemHorizontalPad,
                                top = itemTopPad,
                                bottom = itemBottomPad
                            )
                            .width(tokens.u * 15f)
                            .hoverable(interactionSource)
                            .focusRequester(requester)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focusedProfilePrefix = profile.prefix
                                    onPreviewProfile(profile)
                                }
                            }
                            .navigationClickable {
                                focusedProfilePrefix = profile.prefix
                                onPreviewProfile(profile)
                                if (profile.pin.isBlank()) {
                                    onSelected(profile)
                                } else {
                                    onPinProfileChange(profile)
                                    onPinValueChange("")
                                    onPinErrorActiveChange(false)
                                }
                            }
                            .navigationFocusable(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isHighlighted = selected || hovered
                        Box(
                            modifier = Modifier
                                .padding(bottom = tokens.u * 0.5f)
                                .size(avatarBoxSize)
                                .graphicsLayer {
                                    alpha = if (isHighlighted) 1f else 0.6f
                                }
                                .border(
                                    width = borderSize,
                                    color = if (isHighlighted) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .padding(borderSize),
                            contentAlignment = Alignment.Center
                        ) {
                            ProfileAvatar(profile, avatarBoxSize - borderSize * 2f)
                        }
                        Text(
                            text = profile.displayName,
                            color = if (isHighlighted) Color.White else Color(0xFF666666),
                            fontSize = fsSp(tokens.u * 2.2f),
                            lineHeight = fsSp(tokens.u * 2.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (tokens.portrait) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.Center
                    ) {
                        profiles.forEach { itemBuilder(it) }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Top
                    ) {
                        profiles.forEach { itemBuilder(it) }
                    }
                }
            }
        }

        LoginLogoWatermark(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = tokens.u * 0.5f, bottom = tokens.u * 0.5f)
                .size(width = tokens.u * 15f, height = tokens.u * 7f)
        )

        pinProfile?.let { profile ->
            var modalVisible by remember(profile.prefix) { mutableStateOf(false) }
            LaunchedEffect(profile.prefix) {
                modalVisible = true
                yield()
                runCatching { pinZeroFocusRequester.requestFocus() }
            }
            val modalProgress by animateFloatAsState(
                targetValue = if (modalVisible) 1f else 0f,
                animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
            )
            val shakePx = remember { Animatable(0f) }
            val shakeAmplitudePx = with(LocalDensity.current) { (tokens.u * 0.3f).toPx() }

            LaunchedEffect(pinErrorCount) {
                if (pinErrorCount > 0) {
                    shakePx.snapTo(0f)
                    shakePx.animateTo(
                        targetValue = 0f,
                        animationSpec = keyframes {
                            durationMillis = 400
                            0f at 0 using LinearEasing
                            shakeAmplitudePx at 80 using LinearEasing
                            -shakeAmplitudePx at 160 using LinearEasing
                            shakeAmplitudePx at 240 using LinearEasing
                            -shakeAmplitudePx at 320 using LinearEasing
                            0f at 400 using LinearEasing
                        }
                    )
                    delay(50)
                    if (pinErrorActive) {
                        onPinValueChange("")
                    }
                }
            }

            fun enterDigit(digit: String) {
                var currentValue = pinValue
                if (pinErrorActive) {
                    currentValue = ""
                    onPinValueChange("")
                    onPinErrorActiveChange(false)
                }
                if (currentValue.length >= 4) return
                val nextValue = currentValue + digit
                onPinValueChange(nextValue)
                if (nextValue.length == 4) {
                    if (nextValue == profile.pin) {
                        onPinProfileChange(null)
                        onSelected(profile)
                    } else {
                        onPinErrorActiveChange(true)
                        pinErrorCount += 1
                        focusedPinKey = "clear"
                        runCatching { pinClearFocusRequester.requestFocus() }
                    }
                }
            }

            fun handlePinKey(keyId: String) {
                when (keyId) {
                    "clear" -> {
                        onPinValueChange("")
                        onPinErrorActiveChange(false)
                    }
                    "backspace" -> {
                        if (pinErrorActive) {
                            onPinValueChange("")
                            onPinErrorActiveChange(false)
                        } else if (pinValue.isNotEmpty()) {
                            onPinValueChange(pinValue.dropLast(1))
                        }
                    }
                    else -> enterDigit(keyId)
                }
            }

            val modalWidth = if (tokens.portrait) {
                (tokens.vh * 47f).coerceAtMost(tokens.vw * 90f)
            } else {
                tokens.vw * 47f
            }
            val pinU = (modalWidth / 47f).coerceAtMost(tokens.u)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f * modalProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (pinValue.isNotEmpty()) {
                            onPinValueChange("")
                            onPinErrorActiveChange(false)
                        } else {
                            playClickSound()
                            onPinProfileChange(null)
                            onPinErrorActiveChange(false)
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val digit = keyToDigit(event.key)
                        when {
                            digit != null -> {
                                playClickSound()
                                enterDigit(digit)
                                true
                            }
                            event.key == Key.Backspace -> {
                                playClickSound()
                                handlePinKey("backspace")
                                true
                            }
                            event.key == Key.Back || event.key == Key.Escape -> {
                                if (pinValue.isNotEmpty()) {
                                    onPinValueChange("")
                                    onPinErrorActiveChange(false)
                                } else {
                                    playClickSound()
                                    onPinProfileChange(null)
                                    onPinErrorActiveChange(false)
                                }
                                true
                            }
                            else -> false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .width(modalWidth)
                        .graphicsLayer {
                            val s = 0.9f + 0.1f * modalProgress
                            scaleX = s
                            scaleY = s
                            alpha = modalProgress
                        }
                        .then(
                            if (!shadowsDisabled) {
                                Modifier.shadow(pinU * 0.56f, RoundedCornerShape(pinU * 0.8f), clip = false)
                            } else Modifier
                        )
                        .clip(RoundedCornerShape(pinU * 0.8f))
                        .background(Color(0xD91E1E1E))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(pinU * 1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.width(pinU * 15f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val avatarSize = pinU * 14f
                        val borderSize = pinU * 0.8f
                        Box(
                            modifier = Modifier
                                .padding(bottom = pinU * 0.5f)
                                .size(avatarSize)
                                .border(borderSize, Color.White, CircleShape)
                                .padding(borderSize),
                            contentAlignment = Alignment.Center
                        ) {
                            ProfileAvatar(profile, avatarSize - borderSize * 2f)
                        }
                        Text(
                            text = profile.displayName,
                            color = Color.White,
                            fontSize = fsSp(pinU * 2.2f),
                            lineHeight = fsSp(pinU * 2.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(
                        modifier = Modifier
                            .padding(start = pinU * 1f)
                            .width(pinU * 28f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = pinU * 1f)
                                .graphicsLayer { translationX = shakePx.value },
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(4) { index ->
                                Box(
                                    modifier = Modifier
                                        .padding(pinU * 0.5f)
                                        .size(width = pinU * 5f, height = pinU * 4f)
                                        .clip(RoundedCornerShape(pinU * 0.5f))
                                        .background(
                                            when {
                                                pinErrorActive -> Color(0xFF880000)
                                                index < pinValue.length -> Color.Black
                                                else -> Color(0xB30A0A0A)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (index < pinValue.length) {
                                        Text(
                                            text = "\u2B24",
                                            color = Color.White,
                                            fontSize = fsSp(pinU * 3f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        val pinKeys = listOf(
                            listOf("7", "8", "9"),
                            listOf("4", "5", "6"),
                            listOf("1", "2", "3"),
                            listOf("clear", "0", "backspace")
                        )
                        pinKeys.forEach { rowKeys ->
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowKeys.forEach { key ->
                                    val isSpecial = key == "clear" || key == "backspace"
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val hovered by interactionSource.collectIsHoveredAsState()
                                    val isKeyActive = focusedPinKey == key || hovered
                                    val requester = when (key) {
                                        "0" -> pinZeroFocusRequester
                                        "clear" -> pinClearFocusRequester
                                        else -> remember { FocusRequester() }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .padding(pinU * 0.5f)
                                            .size(width = pinU * 8f, height = pinU * 3f)
                                            .then(
                                                if (isKeyActive && !shadowsDisabled) {
                                                    Modifier.shadow(pinU * 0.06f, RoundedCornerShape(pinU * 0.5f), clip = false)
                                                } else Modifier
                                            )
                                            .clip(RoundedCornerShape(pinU * 0.5f))
                                            .background(
                                                when {
                                                    isKeyActive && isSpecial -> Color(0xFF880000)
                                                    isKeyActive -> colors.primary1
                                                    else -> Color(0x80282828)
                                                }
                                            )
                                            .hoverable(interactionSource)
                                            .focusRequester(requester)
                                            .onFocusChanged { if (it.isFocused) focusedPinKey = key }
                                            .navigationClickable {
                                                focusedPinKey = key
                                                handlePinKey(key)
                                            }
                                            .navigationFocusable(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when (key) {
                                            "clear" -> Text(
                                                text = IconGlyphs.Clear,
                                                fontFamily = LocalIconFont.current,
                                                color = Color.White,
                                                fontSize = fsSp(pinU * 2.5f)
                                            )
                                            "backspace" -> Text(
                                                text = IconGlyphs.Backspace,
                                                fontFamily = LocalIconFont.current,
                                                color = Color.White,
                                                fontSize = fsSp(pinU * 2.5f)
                                            )
                                            else -> Text(
                                                text = key,
                                                color = Color.White,
                                                fontSize = fsSp(pinU * 2.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun keyToDigit(key: Key): String? = when (key) {
    Key.Zero, Key.NumPad0 -> "0"
    Key.One, Key.NumPad1 -> "1"
    Key.Two, Key.NumPad2 -> "2"
    Key.Three, Key.NumPad3 -> "3"
    Key.Four, Key.NumPad4 -> "4"
    Key.Five, Key.NumPad5 -> "5"
    Key.Six, Key.NumPad6 -> "6"
    Key.Seven, Key.NumPad7 -> "7"
    Key.Eight, Key.NumPad8 -> "8"
    Key.Nine, Key.NumPad9 -> "9"
    else -> null
}

private data class LogoPathEntry(
    val path: androidx.compose.ui.graphics.Path,
    val scaleX: Float,
    val scaleY: Float,
    val translateX: Float,
    val translateY: Float
)

@Composable
private fun LoginLogoWatermark(modifier: Modifier = Modifier) {
    val entries = remember {
        fun parse(d: String, sx: Float, sy: Float, tx: Float, ty: Float, evenOdd: Boolean = false): LogoPathEntry {
            val path = PathParser().parsePathString(d).toPath().apply {
                if (evenOdd) fillType = PathFillType.EvenOdd
            }
            return LogoPathEntry(path, sx, sy, tx, ty)
        }
        listOf(
            parse(
                "M0.404,-0l-0.038,-0.161l-0.2,0l-0.038,0.161l-0.131,-0l0.192,-0.691l0.158,0l0.191,0.691l-0.134,-0Zm-0.216,-0.259l0.156,0l-0.078,-0.333l-0.078,0.333Z",
                916.667f, 916.667f, 595.833f - 172.083f, 834.58f + 317.882f
            ),
            parse(
                "M0.329,-0.544c0.042,0 0.075,0.014 0.099,0.042c0.024,0.028 0.036,0.066 0.036,0.114l-0,0.388l-0.125,-0l-0,-0.371c-0,-0.028 -0.005,-0.048 -0.014,-0.059c-0.009,-0.012 -0.022,-0.018 -0.039,-0.018c-0.018,0 -0.035,0.007 -0.05,0.02c-0.015,0.013 -0.03,0.033 -0.045,0.059l-0,0.369l-0.125,-0l-0,-0.529l0.109,0l0.009,0.069c0.019,-0.027 0.041,-0.048 0.064,-0.062c0.024,-0.015 0.051,-0.022 0.081,-0.022Z",
                916.667f, 916.667f, 1049.58f - 172.083f, 834.58f + 317.882f
            ),
            parse(
                "M0.191,-0.529l0,0.529l-0.125,-0l0,-0.529l0.125,0Zm-0.062,-0.266c0.023,0 0.041,0.007 0.055,0.022c0.015,0.014 0.022,0.032 0.022,0.054c0,0.022 -0.007,0.04 -0.021,0.054c-0.015,0.015 -0.033,0.022 -0.056,0.022c-0.023,-0 -0.041,-0.007 -0.056,-0.022c-0.014,-0.014 -0.021,-0.032 -0.021,-0.054c-0,-0.022 0.007,-0.04 0.021,-0.054c0.015,-0.015 0.033,-0.022 0.056,-0.022Z",
                916.667f, 916.667f, 1494.17f - 172.083f, 834.58f + 317.882f
            ),
            parse(
                "M0.578,-0.544c0.038,0 0.069,0.014 0.092,0.043c0.023,0.028 0.035,0.066 0.035,0.113l0,0.388l-0.124,-0l0,-0.372c0,-0.051 -0.016,-0.077 -0.047,-0.077c-0.017,0 -0.032,0.007 -0.046,0.02c-0.014,0.013 -0.028,0.032 -0.041,0.057l0,0.372l-0.123,-0l0,-0.372c0,-0.051 -0.016,-0.077 -0.047,-0.077c-0.017,0 -0.033,0.007 -0.047,0.02c-0.014,0.013 -0.027,0.032 -0.04,0.057l0,0.372l-0.124,-0l0,-0.529l0.109,0l0.009,0.068c0.037,-0.055 0.082,-0.083 0.137,-0.083c0.027,0 0.051,0.008 0.071,0.024c0.02,0.015 0.035,0.037 0.044,0.065c0.019,-0.029 0.039,-0.051 0.063,-0.066c0.022,-0.015 0.049,-0.023 0.079,-0.023Z",
                916.667f, 916.667f, 1693.08f - 172.083f, 834.58f + 317.882f
            ),
            parse(
                "M0.468,-0.277c0,0.006 -0.001,0.023 -0.003,0.051l-0.299,0c0.004,0.053 0.015,0.091 0.034,0.113c0.019,0.022 0.045,0.033 0.078,0.033c0.021,0 0.041,-0.004 0.06,-0.011c0.019,-0.007 0.039,-0.019 0.06,-0.034l0.052,0.071c-0.053,0.046 -0.113,0.069 -0.181,0.069c-0.074,-0 -0.131,-0.024 -0.171,-0.073c-0.04,-0.049 -0.06,-0.116 -0.06,-0.203c-0,-0.085 0.019,-0.154 0.057,-0.206c0.039,-0.051 0.092,-0.077 0.161,-0.077c0.067,0 0.119,0.023 0.156,0.069c0.037,0.046 0.056,0.112 0.056,0.198Zm-0.123,-0.036c-0,-0.048 -0.007,-0.084 -0.021,-0.107c-0.014,-0.024 -0.036,-0.036 -0.067,-0.036c-0.028,0 -0.05,0.012 -0.065,0.035c-0.015,0.023 -0.023,0.061 -0.026,0.114l0.179,0l0,-0.006Z",
                916.667f, 916.667f, 2359.5f - 172.083f, 834.58f + 317.882f
            ),
            parse(
                "M0.51,-0.696l-0.02,0.15l-0.137,0l0,0.546l-0.202,-0l0,-0.546l-0.148,0l0,-0.15l0.507,0Z",
                704.167f, 704.167f, 595.833f + 2019.97f, 834.58f + 112.347f
            ),
            parse(
                "M0.556,-0.696l-0.151,0.696l-0.264,-0l-0.163,-0.696l0.207,0l0.09,0.554l0.08,-0.554l0.201,0Z",
                704.167f, 704.167f, 932.424f + 2019.97f, 834.58f + 112.347f
            ),
            parse(
                "M3026.28,978.772c0,-5.78 -4.692,-10.472 -10.472,-10.472l-368.884,0c-5.78,0 -10.472,4.692 -10.472,10.472l-0,99.895c-0,5.78 4.692,10.472 10.472,10.472l368.884,-0c5.78,-0 10.472,-4.692 10.472,-10.472l0,-99.895Zm-73.233,103.258c-2.525,-0 -4.686,-0.899 -6.484,-2.697c-1.798,-1.798 -2.697,-3.96 -2.697,-6.485l-0,-82.632c-0,-2.525 0.899,-4.687 2.697,-6.485c1.798,-1.798 3.959,-2.697 6.484,-2.697l36.726,0c2.525,0 4.686,0.899 6.484,2.697c1.798,1.798 2.697,3.96 2.697,6.485l0,82.632c0,2.525 -0.899,4.687 -2.697,6.485c-1.798,1.798 -3.959,2.697 -6.484,2.697l-36.726,-0Zm-262.666,-0.719c-3.691,0.853 -7.115,0.154 -10.271,-2.098c-3.157,-2.251 -5.181,-5.305 -6.072,-9.16c-0.986,-4.266 -0.393,-8.335 1.779,-12.208c2.173,-3.872 4.334,-7.699 6.484,-11.48c1.791,-3.093 3.202,-6.336 4.232,-9.729c1.031,-3.392 2.432,-6.676 4.204,-9.851c1.312,-2.55 2.996,-4.798 5.052,-6.742c2.057,-1.945 4.479,-3.24 7.269,-3.885c2.789,-0.644 5.524,-0.586 8.207,0.177c2.682,0.762 5.172,2.001 7.47,3.717c2.904,2.094 5.589,4.455 8.054,7.082c2.466,2.627 5.192,4.979 8.177,7.054c3.592,2.453 7.214,4.943 10.866,7.469c3.652,2.526 5.971,5.921 6.957,10.187c0.892,3.856 0.414,7.488 -1.434,10.896c-1.848,3.409 -4.617,5.54 -8.309,6.394c-4.429,1.024 -8.903,1.669 -13.422,1.936c-4.518,0.267 -8.992,0.913 -13.422,1.937c-4.43,1.024 -8.733,2.408 -12.91,4.152c-4.177,1.743 -8.481,3.127 -12.911,4.152Zm110.076,-94.009l11.435,22.869l17.152,0l-11.434,-22.869l11.434,-0l11.435,22.869l17.152,0l-11.435,-22.869l11.435,-0l11.435,22.869l17.152,0l-11.435,-22.869l17.152,-0c3.145,-0 5.836,1.12 8.076,3.359c2.239,2.239 3.359,4.931 3.359,8.076l-0,68.608c-0,3.144 -1.12,5.836 -3.359,8.076c-2.24,2.239 -4.931,3.359 -8.076,3.359l-91.478,-0c-3.144,-0 -5.836,-1.12 -8.075,-3.359c-2.24,-2.24 -3.359,-4.932 -3.359,-8.076l-0,-68.608c-0,-3.145 1.119,-5.837 3.359,-8.076c2.239,-2.239 4.931,-3.359 8.075,-3.359Zm152.59,85.546l36.726,0l-0,-82.632l-36.726,-0l0,82.632Zm9.181,-4.59c1.301,-0 2.391,-0.44 3.271,-1.32c0.88,-0.88 1.32,-1.97 1.32,-3.271c0,-1.301 -0.44,-2.391 -1.32,-3.271c-0.88,-0.88 -1.97,-1.32 -3.271,-1.32c-1.3,0 -2.391,0.44 -3.27,1.32c-0.88,0.88 -1.32,1.97 -1.32,3.271c-0,1.301 0.44,2.391 1.32,3.271c0.879,0.88 1.97,1.32 3.27,1.32Zm18.363,-0c1.301,-0 2.391,-0.44 3.271,-1.32c0.88,-0.88 1.32,-1.97 1.32,-3.271c-0,-1.301 -0.44,-2.391 -1.32,-3.271c-0.88,-0.88 -1.97,-1.32 -3.271,-1.32c-1.3,0 -2.391,0.44 -3.271,1.32c-0.88,0.88 -1.319,1.97 -1.319,3.271c-0,1.301 0.439,2.391 1.319,3.271c0.88,0.88 1.971,1.32 3.271,1.32Zm-180.134,-46.652l0,45.739l91.478,-0l0,-45.739l-91.478,0Zm161.771,32.88c1.301,-0 2.391,-0.44 3.271,-1.32c0.88,-0.88 1.32,-1.97 1.32,-3.271c0,-1.301 -0.44,-2.391 -1.32,-3.271c-0.88,-0.88 -1.97,-1.32 -3.271,-1.32c-1.3,0 -2.391,0.44 -3.27,1.32c-0.88,0.88 -1.32,1.97 -1.32,3.271c-0,1.301 0.44,2.391 1.32,3.271c0.879,0.88 1.97,1.32 3.27,1.32Zm18.363,-0c1.301,-0 2.391,-0.44 3.271,-1.32c0.88,-0.88 1.32,-1.97 1.32,-3.271c-0,-1.301 -0.44,-2.391 -1.32,-3.271c-0.88,-0.88 -1.97,-1.32 -3.271,-1.32c-1.3,0 -2.391,0.44 -3.271,1.32c-0.88,0.88 -1.319,1.97 -1.319,3.271c-0,1.301 0.439,2.391 1.319,3.271c0.88,0.88 1.971,1.32 3.271,1.32Zm-18.363,-13.773c1.301,0 2.391,-0.44 3.271,-1.319c0.88,-0.88 1.32,-1.971 1.32,-3.271c0,-1.301 -0.44,-2.391 -1.32,-3.271c-0.88,-0.88 -1.97,-1.32 -3.271,-1.32c-1.3,0 -2.391,0.44 -3.27,1.32c-0.88,0.88 -1.32,1.97 -1.32,3.271c-0,1.3 0.44,2.391 1.32,3.271c0.879,0.879 1.97,1.319 3.27,1.319Zm18.363,0c1.301,0 2.391,-0.44 3.271,-1.319c0.88,-0.88 1.32,-1.971 1.32,-3.271c0,-1.301 -0.44,-2.391 -1.32,-3.271c-0.88,-0.88 -1.97,-1.32 -3.271,-1.32c-1.3,0 -2.391,0.44 -3.271,1.32c-0.88,0.88 -1.319,1.97 -1.319,3.271c-0,1.3 0.439,2.391 1.319,3.271c0.88,0.879 1.971,1.319 3.271,1.319Zm-312.172,-6.175c-3.446,0.796 -6.633,0.28 -9.562,-1.549c-2.929,-1.829 -4.791,-4.466 -5.588,-7.911c-0.797,-3.446 -0.281,-6.633 1.548,-9.562c1.829,-2.929 4.466,-4.792 7.912,-5.588c3.445,-0.797 6.632,-0.281 9.561,1.548c2.929,1.829 4.792,4.466 5.589,7.912c0.796,3.445 0.28,6.632 -1.549,9.561c-1.829,2.929 -4.466,4.792 -7.911,5.589Zm302.991,-12.187c3.825,-0 7.077,-1.339 9.755,-4.017c2.678,-2.678 4.017,-5.93 4.017,-9.756c-0,-3.825 -1.339,-7.077 -4.017,-9.755c-2.678,-2.678 -5.93,-4.017 -9.755,-4.017c-3.826,0 -7.078,1.339 -9.755,4.017c-2.678,2.678 -4.017,5.93 -4.017,9.755c-0,3.826 1.339,7.078 4.017,9.756c2.677,2.678 5.929,4.017 9.755,4.017Zm-229.162,-4.884c-3.445,0.797 -6.632,0.281 -9.561,-1.548c-2.929,-1.829 -4.792,-4.466 -5.589,-7.912c-0.796,-3.445 -0.28,-6.632 1.549,-9.561c1.829,-2.929 4.466,-4.792 7.911,-5.589c3.446,-0.796 6.633,-0.28 9.562,1.549c2.929,1.829 4.792,4.466 5.588,7.911c0.797,3.446 0.281,6.633 -1.548,9.562c-1.829,2.929 -4.466,4.792 -7.912,5.588Zm229.162,-4.298c-1.301,0 -2.391,-0.44 -3.271,-1.32c-0.88,-0.88 -1.32,-1.97 -1.32,-3.271c0,-1.3 0.44,-2.391 1.32,-3.27c0.88,-0.88 1.97,-1.32 3.271,-1.32c1.301,-0 2.391,0.44 3.271,1.32c0.88,0.879 1.32,1.97 1.32,3.27c-0,1.301 -0.44,2.391 -1.32,3.271c-0.88,0.88 -1.97,1.32 -3.271,1.32Zm-285.394,-3.44c-3.446,0.796 -6.633,0.28 -9.562,-1.549c-2.929,-1.829 -4.792,-4.466 -5.588,-7.911c-0.797,-3.446 -0.281,-6.633 1.548,-9.562c1.829,-2.929 4.466,-4.792 7.911,-5.588c3.446,-0.797 6.633,-0.281 9.562,1.548c2.929,1.829 4.792,4.466 5.588,7.912c0.797,3.445 0.281,6.632 -1.548,9.561c-1.829,2.929 -4.466,4.792 -7.911,5.589Zm29.531,-6.829c-3.445,0.797 -6.632,0.281 -9.561,-1.548c-2.929,-1.829 -4.792,-4.466 -5.589,-7.911c-0.796,-3.446 -0.28,-6.633 1.549,-9.562c1.829,-2.929 4.466,-4.792 7.911,-5.588c3.446,-0.797 6.633,-0.281 9.562,1.548c2.929,1.829 4.792,4.466 5.588,7.911c0.797,3.446 0.281,6.633 -1.548,9.562c-1.829,2.929 -4.466,4.792 -7.912,5.588Z",
                1.32324f, 1.32324f, -767.32f, -286.074f,
                evenOdd = true
            )
        )
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .graphicsLayer { alpha = 0.3f }
    ) {
        val coverScale = maxOf(size.width / 3800f, size.height / 1800f)
        val offsetX = (size.width - 3800f * coverScale) / 2f
        val offsetY = (size.height - 1800f * coverScale) / 2f
        withTransform({
            translate(left = offsetX, top = offsetY)
            scale(scaleX = coverScale, scaleY = coverScale, pivot = Offset.Zero)
            translate(left = 477.396f, top = 299.232f)
            scale(scaleX = 0.755718f, scaleY = 0.755718f, pivot = Offset.Zero)
        }) {
            entries.forEach { entry ->
                withTransform({
                    translate(left = entry.translateX, top = entry.translateY)
                    scale(scaleX = entry.scaleX, scaleY = entry.scaleY, pivot = Offset.Zero)
                }) {
                    drawPath(entry.path, color = Color.White)
                }
            }
        }
    }
}
