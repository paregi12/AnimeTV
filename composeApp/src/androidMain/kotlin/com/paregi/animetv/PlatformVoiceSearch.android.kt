package com.paregi.animetv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.fsSp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
actual fun PlatformVoiceSearchButton(
    onResult: (String) -> Unit,
    inlineSearchBar: Boolean,
    iconColor: Color?,
    iconSize: TextUnit?
) {
    val context = LocalContext.current
    val available = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    if (!available) return

    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val textShadowDisabled = LocalPerformanceEffects.current.getOrElse(4) { false }
    val density = LocalDensity.current

    var recognizer by remember(context) { mutableStateOf<SpeechRecognizer?>(null) }
    var voiceActive by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("...") }
    var partialResult by remember { mutableStateOf("") }
    var voiceLevel by remember { mutableFloatStateOf(0f) }
    var transientMessage by remember { mutableStateOf(false) }
    val latestOnResult by rememberUpdatedState(onResult)

    fun closeRecognizer(cancel: Boolean) {
        val current = recognizer
        recognizer = null
        if (current != null) {
            if (cancel) runCatching { current.cancel() }
            runCatching { current.destroy() }
        }
        listening = false
        voiceLevel = 0f
    }

    fun startRecognition() {
        partialResult = ""
        status = "....."
        transientMessage = false
        voiceActive = true
        listening = true
        try {
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speechRecognizer
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status = "Speak Now..."
                }

                override fun onBeginningOfSpeech() {
                    status = "..."
                }

                override fun onRmsChanged(rmsdB: Float) {
                    voiceLevel = ((rmsdB - 3f) / 8f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    listening = false
                }

                override fun onError(error: Int) {
                    closeRecognizer(cancel = false)
                    status = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was recognized"
                        else -> "Voice search ended"
                    }
                    transientMessage = inlineSearchBar
                }

                override fun onResults(results: Bundle?) {
                    val recognized = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    closeRecognizer(cancel = false)
                    if (!recognized.isNullOrBlank()) {
                        voiceActive = false
                        latestOnResult(recognized)
                    } else {
                        status = "No speech was recognized"
                        transientMessage = inlineSearchBar
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let { partialResult = it }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer.startListening(intent)
        } catch (_: Exception) {
            closeRecognizer(cancel = true)
            status = "Voice search is unavailable"
            transientMessage = inlineSearchBar
            voiceActive = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecognition() else {
            status = "Microphone permission is required"
            transientMessage = inlineSearchBar
            voiceActive = true
        }
    }

    LaunchedEffect(transientMessage) {
        if (transientMessage) {
            delay(1500)
            transientMessage = false
            voiceActive = false
        }
    }

    DisposableEffect(context) {
        onDispose { closeRecognizer(cancel = true) }
    }

    PlatformBackHandler(enabled = inlineSearchBar && voiceActive) {
        closeRecognizer(cancel = true)
        voiceActive = false
    }

    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = focused || hovered
    val buttonSize = tokens.u * 3f
    val resolvedIconSize = iconSize ?: fsSp(if (tokens.portrait) tokens.fs25 else tokens.fs17)
    val restShadow = if (!textShadowDisabled && !highlighted) {
        Shadow(Color.Black, Offset(0f, 1.5f), 3f)
    } else null

    val iconFg = when {
        focused -> colors.primary1
        hovered -> colors.accent1
        else -> iconColor ?: Color.White
    }

    fun handleMicTrigger() {
        if (voiceActive) {
            closeRecognizer(cancel = true)
            voiceActive = false
            return
        }
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) startRecognition()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(if (highlighted && !tokens.portrait) Color.White else Color.Transparent)
            .hoverable(interactionSource = interactionSource)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(onClick = ::handleMicTrigger)
            .navigationFocusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = IconGlyphs.Mic,
            color = iconFg,
            fontFamily = LocalIconFont.current,
            fontSize = resolvedIconSize,
            style = TextStyle(shadow = restShadow)
        )

        if (inlineSearchBar && voiceActive) {
            val targetPillWidth = if (tokens.portrait) tokens.vw * 62f else tokens.vw * 59f
            var expanded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { expanded = true }

            val pillWidth by animateDpAsState(
                targetValue = if (expanded) targetPillWidth else buttonSize,
                animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
            )
            val pulse = rememberInfiniteTransition(label = "voice-inline")
            val animatedPulseColor by pulse.animateColor(
                initialValue = Color.Black,
                targetValue = colors.accent1,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "voice-border-color"
            )
            val offsetX = with(density) { (-tokens.u * 0.5f).toPx().roundToInt() }
            val offsetY = with(density) { (-tokens.u * 0.6f).toPx().roundToInt() }

            val rawDisplay = partialResult.ifBlank { status }
            val displayText = if (partialResult.isNotBlank()) {
                val clipped = if (rawDisplay.length > 24) rawDisplay.takeLast(24) else rawDisplay
                "$clipped..."
            } else rawDisplay

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(offsetX, offsetY),
                properties = PopupProperties(focusable = false)
            ) {
                Box(
                    modifier = Modifier
                        .width(pillWidth)
                        .clip(RoundedCornerShape(tokens.u * 8f))
                        .background(Color.White)
                        .border(tokens.u * 0.2f, animatedPulseColor, RoundedCornerShape(tokens.u * 8f))
                        .navigationClickable {
                            closeRecognizer(cancel = true)
                            voiceActive = false
                        }
                ) {
                    Text(
                        text = displayText,
                        color = colors.primary1,
                        fontSize = fsSp(tokens.fs15),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(
                                start = tokens.u * 4f,
                                end = tokens.u * 1f,
                                top = tokens.u * 0.5f,
                                bottom = tokens.u * 0.5f
                            )
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = tokens.u * 0.5f, top = tokens.u * 0.6f)
                            .size(buttonSize)
                            .clip(CircleShape)
                            .background(Color(180, 200, 220, (voiceLevel * 255).roundToInt().coerceIn(0, 255))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = IconGlyphs.Mic,
                            color = animatedPulseColor,
                            fontFamily = LocalIconFont.current,
                            fontSize = resolvedIconSize
                        )
                    }
                }
            }
        }
    }

    if (!inlineSearchBar && voiceActive) {
        Dialog(
            onDismissRequest = {
                closeRecognizer(cancel = true)
                voiceActive = false
            }
        ) {
            Column(
                Modifier.widthIn(max = 380.dp)
                    .clip(RoundedCornerShape(tokens.u * 0.8f))
                    .background(Color(0xFF222222))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Voice Search", color = Color.White, fontSize = 20.sp)
                Text(
                    text = partialResult.ifBlank { status },
                    color = Color(0xFFAAAAAA),
                    fontSize = 15.sp
                )
                Box(
                    Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF111111))
                ) {
                    Box(
                        Modifier.fillMaxWidth(fraction = voiceLevel.coerceAtLeast(0.08f)).height(5.dp)
                            .background(colors.accent1)
                    )
                }
                Text(
                    text = if (listening) "Cancel" else "Close",
                    color = colors.accent1,
                    modifier = Modifier.navigationClickable {
                        closeRecognizer(cancel = true)
                        voiceActive = false
                    }.navigationFocusable().padding(8.dp)
                )
            }
        }
    }
}
