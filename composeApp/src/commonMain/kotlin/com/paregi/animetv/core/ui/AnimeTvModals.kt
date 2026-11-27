package com.paregi.animetv.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.paregi.animetv.LocalNavigationSound
import com.paregi.animetv.core.theme.AnimeTvThemeColors
import com.paregi.animetv.core.theme.AnimeTvTokens
import com.paregi.animetv.core.theme.IconGlyphs
import com.paregi.animetv.core.theme.LocalAnimeTvColors
import com.paregi.animetv.core.theme.LocalAnimeTvTokens
import com.paregi.animetv.core.theme.LocalIconFont
import com.paregi.animetv.core.theme.LocalPerformanceEffects
import com.paregi.animetv.core.theme.fsSp
import com.paregi.animetv.domain.AppUpdateInfo
import com.paregi.animetv.navigationClickable
import com.paregi.animetv.navigationFocusable
import com.paregi.animetv.platformImagePainter
import kotlinx.coroutines.yield

enum class ListOrderMode { LIST, MENU, MULTI, THEME, ORDER }

data class ListOrderItem(
    val id: String,
    val title: String,
    val value: String? = null,
    val inlineValue: Boolean = false,
    val icon: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val rightBadge: String? = null,
    val rightBadgeIcon: String? = null,
    val swatches: List<Color> = emptyList(),
    val disabledByOther: Boolean = false,
    val avatarUrl: String? = null,
    val avatarDrawable: String? = null,
    val disablingIds: List<String> = emptyList()
)

data class ImgPickerItem(
    val id: String,
    val title: String,
    val imageUrl: String? = null,
    val drawableName: String? = null,
    val icon: String? = null,
    val selected: Boolean = false
)

@Composable
private fun AnimeTvModalScrim(
    onDismiss: () -> Unit,
    content: @Composable (animProgress: Float, tokens: AnimeTvTokens, colors: AnimeTvThemeColors) -> Unit
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val animProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = tokens.tim3s, easing = tokens.easing)
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f * animProgress))
                .pointerInput(onDismiss) {
                    detectTapGestures { onDismiss() }
                },
            contentAlignment = Alignment.Center
        ) {
            content(animProgress, tokens, colors)
        }
    }
}

private fun Modifier.modalWindowSurface(
    width: Dp,
    cornerRadius: Dp,
    animProgress: Float,
    minScale: Float,
    backgroundColor: Color,
    shadowEnabled: Boolean,
    shadowElevation: Dp
): Modifier = this
    .width(width)
    .graphicsLayer {
        val s = minScale + (1f - minScale) * animProgress
        scaleX = s
        scaleY = s
        alpha = animProgress
    }
    .then(
        if (shadowEnabled) Modifier.shadow(shadowElevation, RoundedCornerShape(cornerRadius), clip = false)
        else Modifier
    )
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .pointerInput(Unit) {
        detectTapGestures {}
    }

@Composable
private fun ModalHeaderBar(
    title: String,
    titleIcon: String? = null
) {
    val tokens = LocalAnimeTvTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF181818))
            .padding(tokens.u * 0.6f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!titleIcon.isNullOrBlank()) {
            Text(
                text = titleIcon,
                fontFamily = LocalIconFont.current,
                color = Color(0xFFDDDDDD),
                fontSize = fsSp(tokens.fs12),
                modifier = Modifier.padding(end = tokens.u * 0.4f)
            )
        }
        Text(
            text = title,
            color = Color(0xFFDDDDDD),
            fontSize = fsSp(tokens.fs12),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(thickness = (tokens.u * 0.1f).coerceAtLeast(1.dp), color = Color(0xFF121212))
}

@Composable
fun ListOrderDialog(
    title: String,
    items: List<ListOrderItem>,
    mode: ListOrderMode,
    onDismiss: () -> Unit,
    onSelect: (Int, ListOrderItem) -> Unit = { _, _ -> },
    onReorder: ((List<ListOrderItem>) -> Unit)? = null,
    headerAvatar: (@Composable () -> Unit)? = null,
    headerTrailingIcon: String? = null,
    footerTip: String? = null,
    initialSelectedIndex: Int = 0,
    bottomClearance: Dp = 0.dp
) {
    val playClickSound = LocalNavigationSound.current
    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }

    var workingItems by remember(items) { mutableStateOf(items) }
    val hasDistinctIds = remember(workingItems) {
        workingItems.all { it.id.isNotBlank() } &&
            workingItems.map { it.id }.distinct().size == workingItems.size
    }
    val startIndex = remember(items, initialSelectedIndex) {
        if (items.isEmpty()) 0
        else initialSelectedIndex.coerceIn(0, items.lastIndex)
    }
    var focusedIndex by remember { mutableIntStateOf(startIndex) }
    var pendingFocusIndex by remember { mutableStateOf<Int?>(if (items.isNotEmpty()) startIndex else null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(pendingFocusIndex) {
        val target = pendingFocusIndex ?: return@LaunchedEffect
        if (target in workingItems.indices) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val fullyVisible = visible.any {
                it.index == target && it.offset >= 0 &&
                    it.offset + it.size <= listState.layoutInfo.viewportEndOffset
            }
            if (!fullyVisible) {
                listState.scrollToItem(target)
            }
        }
    }

    fun moveOrderItem(fromIndex: Int, delta: Int) {
        val toIndex = (fromIndex + delta).coerceIn(0, workingItems.lastIndex)
        if (toIndex == fromIndex) return
        val mutable = workingItems.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        workingItems = mutable
        focusedIndex = toIndex
        if (!hasDistinctIds) {
            pendingFocusIndex = toIndex
        } else {
            val visible = listState.layoutInfo.visibleItemsInfo
            val isVisible = visible.any {
                it.index == toIndex && it.offset >= 0 &&
                    it.offset + it.size <= listState.layoutInfo.viewportEndOffset
            }
            if (!isVisible) pendingFocusIndex = toIndex
        }
        playClickSound()
        onReorder?.invoke(mutable)
    }

    fun toggleOrderItem(index: Int) {
        val item = workingItems.getOrNull(index) ?: return
        if (!item.enabled) return
        val newSelected = !item.selected
        val updated = workingItems.mapIndexed { idx, entry ->
            when {
                idx == index -> entry.copy(selected = newSelected, disabledByOther = false)
                newSelected && entry.id in item.disablingIds -> entry.copy(selected = false)
                else -> entry
            }
        }
        workingItems = updated
        if (mode == ListOrderMode.MULTI) {
            onSelect(index, updated[index])
            onReorder?.invoke(updated)
        } else {
            onReorder?.invoke(updated)
        }
    }

    AnimeTvModalScrim(onDismiss = onDismiss) { animProgress, tokens, colors ->
        val dialogWidth = (tokens.u * 40f).coerceAtMost(tokens.screenWidth * 0.90f)
        val cornerRadius = tokens.u * 0.5f
        val maxListHeight = if (bottomClearance > 0.dp) {
            (tokens.vh * 75f).coerceAtMost((tokens.screenHeight - bottomClearance - tokens.u * 6f).coerceAtLeast(tokens.vh * 25f))
        } else {
            tokens.vh * 75f
        }

        Column(
            modifier = Modifier
                .padding(bottom = bottomClearance)
                .modalWindowSurface(
                    width = dialogWidth,
                    cornerRadius = cornerRadius,
                    animProgress = animProgress,
                    minScale = 0.8f,
                    backgroundColor = Color(0xFF222222),
                    shadowEnabled = !shadowsDisabled,
                    shadowElevation = tokens.u * 0.6f
                )
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || workingItems.isEmpty()) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            playClickSound()
                            onDismiss()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (mode == ListOrderMode.ORDER) {
                                moveOrderItem(focusedIndex, -1)
                            } else {
                                val next = (focusedIndex - 5).coerceAtLeast(0)
                                if (next != focusedIndex) {
                                    focusedIndex = next
                                    pendingFocusIndex = next
                                    playClickSound()
                                }
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (mode == ListOrderMode.ORDER) {
                                moveOrderItem(focusedIndex, 1)
                            } else {
                                val next = (focusedIndex + 5).coerceAtMost(workingItems.lastIndex)
                                if (next != focusedIndex) {
                                    focusedIndex = next
                                    pendingFocusIndex = next
                                    playClickSound()
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            if (title.isNotBlank() || headerAvatar != null || headerTrailingIcon != null) {
                if (headerAvatar != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF181818))
                            .padding(tokens.u * 1.6f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.u * 1f)
                    ) {
                        Box(modifier = Modifier.size(tokens.u * 3f).clip(CircleShape)) {
                            headerAvatar()
                        }
                        Text(
                            text = title,
                            color = Color(0xFFDDDDDD),
                            fontSize = fsSp(tokens.fs12),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (headerTrailingIcon != null) {
                            Text(
                                text = headerTrailingIcon,
                                fontFamily = LocalIconFont.current,
                                color = Color(0xFFDDDDDD),
                                fontSize = fsSp(tokens.fs14)
                            )
                        }
                    }
                    HorizontalDivider(thickness = (tokens.u * 0.1f).coerceAtLeast(1.dp), color = Color(0xFF121212))
                } else {
                    ModalHeaderBar(title = title)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight)
            ) {
                itemsIndexed(
                    workingItems,
                    key = { index, item -> if (hasDistinctIds) item.id else "${item.id}:$index" }
                ) { index, item ->
                    val requester = remember { FocusRequester() }
                    var rowFocused by remember { mutableStateOf(false) }
                    val interactionSource = remember { MutableInteractionSource() }
                    val rowHovered by interactionSource.collectIsHoveredAsState()
                    var rowHeightPx by remember { mutableFloatStateOf(0f) }

                    LaunchedEffect(pendingFocusIndex) {
                        if (pendingFocusIndex == index) {
                            yield()
                            runCatching { requester.requestFocus() }
                            pendingFocusIndex = null
                        }
                    }

                    val rowBackground = when {
                        rowFocused -> Color(0xFF444444)
                        rowHovered -> Color(0xFF1A1A1A)
                        else -> Color.Transparent
                    }
                    val contentAlpha = if (!item.enabled || item.disabledByOther) 0.4f else 1f
                    val verticalPad = if (tokens.portrait) tokens.vh * 1.6f else tokens.vw * 1.0f

                    val currentIndex by rememberUpdatedState(index)
                    var dragDeltaY by remember(item.id) { mutableFloatStateOf(0f) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { rowHeightPx = it.height.toFloat() }
                            .background(rowBackground)
                            .hoverable(interactionSource = interactionSource)
                            .focusRequester(requester)
                            .onFocusChanged { state ->
                                rowFocused = state.isFocused
                                if (state.isFocused) {
                                    focusedIndex = index
                                }
                            }
                            .navigationClickable(enabled = item.enabled) {
                                when (mode) {
                                    ListOrderMode.MULTI, ListOrderMode.ORDER -> toggleOrderItem(index)
                                    ListOrderMode.LIST, ListOrderMode.MENU, ListOrderMode.THEME -> onSelect(index, item)
                                }
                            }
                            .navigationFocusable(enabled = item.enabled)
                            .padding(
                                start = tokens.u * 2f,
                                end = tokens.u * 2f,
                                top = verticalPad,
                                bottom = verticalPad
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconSlotModifier = Modifier.width(tokens.u * 4f)
                        when (mode) {
                            ListOrderMode.LIST, ListOrderMode.THEME -> {
                                Box(modifier = iconSlotModifier, contentAlignment = Alignment.CenterStart) {
                                    if (item.icon != null) {
                                        Text(
                                            text = item.icon,
                                            fontFamily = LocalIconFont.current,
                                            color = Color.White.copy(alpha = contentAlpha),
                                            fontSize = fsSp(tokens.fs17)
                                        )
                                    } else {
                                        Text(
                                            text = if (item.selected) IconGlyphs.RadioChecked else IconGlyphs.RadioUnchecked,
                                            fontFamily = LocalIconFont.current,
                                            color = if (item.selected) colors.accent1 else Color(0xFF666666),
                                            fontSize = fsSp(tokens.fs17)
                                        )
                                    }
                                }
                            }
                            ListOrderMode.MENU, ListOrderMode.MULTI -> {
                                Box(modifier = iconSlotModifier, contentAlignment = Alignment.CenterStart) {
                                    when {
                                        item.avatarDrawable != null -> Image(
                                            painter = platformImagePainter(item.avatarDrawable),
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(tokens.u * 3f).clip(CircleShape)
                                        )
                                        !item.avatarUrl.isNullOrBlank() -> AsyncImage(
                                            model = item.avatarUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(tokens.u * 3f).clip(CircleShape)
                                        )
                                        else -> Text(
                                            text = item.icon ?: IconGlyphs.ArrowRight,
                                            fontFamily = LocalIconFont.current,
                                            color = Color.White.copy(alpha = contentAlpha),
                                            fontSize = fsSp(tokens.fs17)
                                        )
                                    }
                                }
                            }
                            ListOrderMode.ORDER -> {
                                Box(
                                    modifier = iconSlotModifier.pointerInput(item.id) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                dragDeltaY = 0f
                                                draggingId = item.id
                                            },
                                            onDragEnd = {
                                                dragDeltaY = 0f
                                                draggingId = null
                                            },
                                            onDragCancel = {
                                                dragDeltaY = 0f
                                                draggingId = null
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                val step = (rowHeightPx * 0.7f).coerceAtLeast(32f)
                                                dragDeltaY += dragAmount
                                                while (dragDeltaY > step) {
                                                    dragDeltaY -= step
                                                    moveOrderItem(currentIndex, 1)
                                                }
                                                while (dragDeltaY < -step) {
                                                    dragDeltaY += step
                                                    moveOrderItem(currentIndex, -1)
                                                }
                                            }
                                        )
                                    },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = IconGlyphs.DragIndicator,
                                        fontFamily = LocalIconFont.current,
                                        color = if (draggingId == item.id) colors.accent1 else Color.White.copy(alpha = contentAlpha),
                                        fontSize = fsSp(tokens.fs17)
                                    )
                                }
                            }
                        }

                        if (item.inlineValue && !item.value.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    color = Color.White.copy(alpha = contentAlpha),
                                    fontSize = fsSp(tokens.fs15),
                                    lineHeight = fsSp(tokens.fs15) * 1.15f,
                                    modifier = Modifier
                                        .width(minOf(tokens.u * 15f, dialogWidth * 0.40f))
                                        .padding(end = tokens.u * 0.6f)
                                )
                                Text(
                                    text = item.value,
                                    color = colors.accent1.copy(alpha = contentAlpha),
                                    fontSize = fsSp(tokens.fs14),
                                    lineHeight = fsSp(tokens.fs14) * 1.15f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = Color.White.copy(alpha = contentAlpha),
                                    fontSize = fsSp(tokens.fs15),
                                    lineHeight = fsSp(tokens.fs15) * 1.15f
                                )
                                if (!item.value.isNullOrBlank()) {
                                    Text(
                                        text = item.value,
                                        color = colors.accent1.copy(alpha = contentAlpha),
                                        fontSize = fsSp(tokens.fs12),
                                        lineHeight = fsSp(tokens.fs12) * 1.2f
                                    )
                                }
                            }
                        }

                        if (!item.rightBadge.isNullOrBlank()) {
                            Spacer(Modifier.width(tokens.u * 0.8f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.3f)
                            ) {
                                if (!item.rightBadgeIcon.isNullOrBlank()) {
                                    Text(
                                        text = item.rightBadgeIcon,
                                        fontFamily = LocalIconFont.current,
                                        color = colors.accent1,
                                        fontSize = fsSp(tokens.fs10)
                                    )
                                }
                                Text(
                                    text = item.rightBadge,
                                    color = colors.accent1,
                                    fontSize = fsSp(tokens.fs10)
                                )
                            }
                        }

                        if (mode == ListOrderMode.THEME && item.swatches.isNotEmpty()) {
                            Spacer(Modifier.width(tokens.u * 0.8f))
                            Row {
                                item.swatches.forEach { swatch ->
                                    Box(modifier = Modifier.size(tokens.fs14).background(swatch))
                                }
                            }
                        }

                        if (mode == ListOrderMode.MULTI) {
                            Spacer(Modifier.width(tokens.u * 0.8f))
                            Text(
                                text = if (item.selected) IconGlyphs.Check else IconGlyphs.Clear,
                                fontFamily = LocalIconFont.current,
                                color = if (item.selected) colors.accent1 else Color(0xFF666666),
                                fontSize = fsSp(tokens.fs17)
                            )
                        }

                        if (mode == ListOrderMode.ORDER) {
                            Spacer(Modifier.width(tokens.u * 0.8f))
                            AnimeTvSwitch(
                                checked = item.selected,
                                enabled = item.enabled && !item.disabledByOther
                            )
                        }
                    }
                }
            }

            if (!footerTip.isNullOrBlank()) {
                HorizontalDivider(thickness = (tokens.u * 0.1f).coerceAtLeast(1.dp), color = Color(0xFF282828))
                Text(
                    text = footerTip,
                    color = Color(0xFF888888),
                    fontSize = fsSp(tokens.fs8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .padding(tokens.u * 0.6f)
                )
            }
        }
    }
}

@Composable
internal fun AnimeTvSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tokens = LocalAnimeTvTokens.current
    val colors = LocalAnimeTvColors.current
    val trackWidth = tokens.u * 4f
    val trackHeight = tokens.u * 2f
    val thumbSize = tokens.u * 1.6f
    val thumbOffsetX by animateDpAsState(
        targetValue = if (checked) tokens.u * 2.1f else tokens.u * 0.2f,
        animationSpec = tween(durationMillis = tokens.tim2s, easing = tokens.easing)
    )
    val trackColor = when {
        !enabled && checked -> colors.dimm1
        checked -> colors.primary3
        else -> Color(0xFF333333)
    }
    val thumbColor = if (checked) Color.White else Color(0xFFEEEEEE)

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(tokens.u * 3f))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX, y = tokens.u * 0.2f)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (checked) IconGlyphs.Check else IconGlyphs.Clear,
                fontFamily = LocalIconFont.current,
                color = colors.primary1,
                fontSize = fsSp(tokens.u * 1.3f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String?,
    message: String,
    titleIcon: String? = null,
    yesLabel: String = "YES",
    noLabel: String = "NO",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }
    val playClickSound = LocalNavigationSound.current
    val yesRequester = remember { FocusRequester() }
    val noRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        yield()
        runCatching { yesRequester.requestFocus() }
    }

    AnimeTvModalScrim(onDismiss = onDismiss) { animProgress, tokens, _ ->
        val baseWidth = if (tokens.portrait) tokens.vh * 36f else tokens.vw * 40f
        val dialogWidth = baseWidth.coerceAtMost(tokens.screenWidth * 0.90f)
        val btnWidth = dialogWidth * (if (tokens.portrait) 0.40f else 0.45f)

        Column(
            modifier = Modifier
                .modalWindowSurface(
                    width = dialogWidth,
                    cornerRadius = tokens.u * 0.5f,
                    animProgress = animProgress,
                    minScale = 0.8f,
                    backgroundColor = Color(0xFF222222),
                    shadowEnabled = !shadowsDisabled,
                    shadowElevation = tokens.u * 0.6f
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                        playClickSound()
                        onDismiss()
                        true
                    } else false
                }
        ) {
            if (!title.isNullOrBlank()) {
                ModalHeaderBar(title = title, titleIcon = titleIcon)
            }

            val parsedMessage = parseDialogMarkup(message, headingSize = fsSp(tokens.fs20))
            Text(
                text = parsedMessage,
                color = Color(0xFFAAAAAA),
                fontSize = fsSp(tokens.fs15),
                lineHeight = fsSp(tokens.fs15) * 1.4f,
                textAlign = if (title.isNullOrBlank()) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = tokens.u * 1.5f, end = tokens.u * 1.5f, top = tokens.u * 1.5f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(tokens.u * 1f),
                horizontalArrangement = Arrangement.spacedBy(tokens.u * 1f, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DialogActionButton(
                    label = yesLabel,
                    width = btnWidth,
                    focusRequester = yesRequester,
                    onClick = onConfirm
                )
                if (noLabel.isNotBlank()) {
                    DialogActionButton(
                        label = noLabel,
                        width = btnWidth,
                        focusRequester = noRequester,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    update: AppUpdateInfo,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
    onDontRemind: () -> Unit,
    onDismiss: () -> Unit
) {
    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }
    val playClickSound = LocalNavigationSound.current
    val updateNowRequester = remember { FocusRequester() }
    val laterRequester = remember { FocusRequester() }
    val dontRemindRequester = remember { FocusRequester() }
    val changelogScroll = rememberScrollState()
    val secondaryActions = listOf(
        Triple("Later", laterRequester, onLater),
        Triple("Don't Remind Me", dontRemindRequester, onDontRemind)
    )

    LaunchedEffect(Unit) {
        yield()
        runCatching { updateNowRequester.requestFocus() }
    }

    AnimeTvModalScrim(onDismiss = onDismiss) { animProgress, tokens, _ ->
        val baseWidth = if (tokens.portrait) tokens.vh * 40f else tokens.vw * 48f
        val dialogWidth = baseWidth.coerceAtMost(tokens.screenWidth * 0.90f)

        Column(
            modifier = Modifier
                .modalWindowSurface(
                    width = dialogWidth,
                    cornerRadius = tokens.u * 0.5f,
                    animProgress = animProgress,
                    minScale = 0.8f,
                    backgroundColor = Color(0xFF222222),
                    shadowEnabled = !shadowsDisabled,
                    shadowElevation = tokens.u * 0.6f
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                        playClickSound()
                        onDismiss()
                        true
                    } else false
                }
        ) {
            ModalHeaderBar(
                title = "Update Available - Version ${update.versionName}",
                titleIcon = IconGlyphs.Update
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = tokens.vh * 42f)
                    .verticalScroll(changelogScroll)
                    .padding(start = tokens.u * 1.5f, end = tokens.u * 1.5f, top = tokens.u * 1.5f),
                verticalArrangement = Arrangement.spacedBy(tokens.u * 0.6f)
            ) {
                Text(
                    text = "Download Size: ${update.sizeLabel}",
                    color = Color.White,
                    fontSize = fsSp(tokens.fs15),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Changelogs:",
                    color = Color.White,
                    fontSize = fsSp(tokens.fs15),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = update.changelog,
                    color = Color(0xFFCCCCCC),
                    fontSize = fsSp(tokens.fs14),
                    lineHeight = fsSp(tokens.fs14) * 1.4f
                )
            }

            if (tokens.portrait) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(tokens.u * 1f),
                    verticalArrangement = Arrangement.spacedBy(tokens.u * 0.8f)
                ) {
                    DialogActionButton(
                        label = "Update Now",
                        focusRequester = updateNowRequester,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onUpdateNow
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.8f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        secondaryActions.forEach { (label, requester, action) ->
                            DialogActionButton(
                                label = label,
                                focusRequester = requester,
                                modifier = Modifier.weight(1f),
                                onClick = action
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(tokens.u * 1f),
                    horizontalArrangement = Arrangement.spacedBy(tokens.u * 0.8f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (listOf(Triple("Update Now", updateNowRequester, onUpdateNow)) + secondaryActions)
                        .forEach { (label, requester, action) ->
                            DialogActionButton(
                                label = label,
                                focusRequester = requester,
                                modifier = Modifier.weight(1f),
                                onClick = action
                            )
                        }
                }
            }
        }
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    initialValue: String = "",
    placeholder: String = "",
    maxLength: Int = 18,
    isPin: Boolean = false,
    okLabel: String = "OK",
    cancelLabel: String = "Cancel",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }
    val playClickSound = LocalNavigationSound.current
    var value by remember(initialValue, isPin) {
        mutableStateOf(
            if (isPin) initialValue.filter { it.isDigit() }.take(maxLength)
            else initialValue.take(maxLength)
        )
    }
    val inputRequester = remember { FocusRequester() }
    val okRequester = remember { FocusRequester() }
    val cancelRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        yield()
        runCatching { inputRequester.requestFocus() }
    }

    AnimeTvModalScrim(onDismiss = onDismiss) { animProgress, tokens, colors ->
        val baseWidth = if (tokens.portrait) tokens.vh * 36f else tokens.vw * 40f
        val dialogWidth = baseWidth.coerceAtMost(tokens.screenWidth * 0.90f)
        val btnWidth = dialogWidth * (if (tokens.portrait) 0.40f else 0.45f)

        Column(
            modifier = Modifier
                .modalWindowSurface(
                    width = dialogWidth,
                    cornerRadius = tokens.u * 0.5f,
                    animProgress = animProgress,
                    minScale = 0.8f,
                    backgroundColor = Color(0xFF222222),
                    shadowEnabled = !shadowsDisabled,
                    shadowElevation = tokens.u * 0.6f
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                        playClickSound()
                        onDismiss()
                        true
                    } else false
                }
        ) {
            ModalHeaderBar(title = title)

            if (placeholder.isNotBlank()) {
                Text(
                    text = parseDialogMarkup(placeholder, headingSize = fsSp(tokens.fs20)),
                    color = Color(0xFFAAAAAA),
                    fontSize = fsSp(tokens.fs15),
                    lineHeight = fsSp(tokens.fs15) * 1.4f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = tokens.u * 1.5f, end = tokens.u * 1.5f, top = tokens.u * 1.5f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.u * 1f, vertical = tokens.u * 1f)
                    .clip(RoundedCornerShape(tokens.u * 0.4f))
                    .background(Color(0xFF111111))
                    .padding(tokens.u * 0.6f),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { raw ->
                        val sanitized = if (isPin) raw.filter { it.isDigit() } else raw
                        value = sanitized.take(maxLength)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = fsSp(tokens.fs15),
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(colors.accent1),
                    visualTransformation = if (isPin) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(value) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter)
                            ) {
                                playClickSound()
                                onConfirm(value)
                                true
                            } else false
                        }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = tokens.u * 1f, end = tokens.u * 1f, bottom = tokens.u * 1f),
                horizontalArrangement = Arrangement.spacedBy(tokens.u * 1f, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DialogActionButton(
                    label = okLabel,
                    width = btnWidth,
                    focusRequester = okRequester,
                    onClick = { onConfirm(value) }
                )
                DialogActionButton(
                    label = cancelLabel,
                    width = btnWidth,
                    focusRequester = cancelRequester,
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    label: String,
    width: Dp? = null,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .clip(RoundedCornerShape(tokens.u * 0.6f))
            .background(bgColor)
            .hoverable(interactionSource = interactionSource)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .navigationClickable(onClick = onClick)
            .navigationFocusable()
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

@Composable
fun AboutPopupDialog(
    visible: Boolean,
    loadingText: String? = null,
    qrImageUrl: String? = null,
    qrContent: (@Composable () -> Unit)? = null,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }

    AnimeTvModalScrim(onDismiss = onDismiss) { animProgress, tokens, colors ->
        val cardScale = 0.5f + 0.5f * animProgress
        Column(
            modifier = Modifier
                .widthIn(min = tokens.u * 15f)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = animProgress
                }
                .then(
                    if (!shadowsDisabled) {
                        Modifier.shadow(tokens.u * 0.3f, RoundedCornerShape(tokens.u * 1f), clip = false)
                    } else Modifier
                )
                .clip(RoundedCornerShape(tokens.u * 1f))
                .background(Color.White)
                .then(
                    if (onClick != null) {
                        Modifier.navigationClickable(onClick = onClick).navigationFocusable()
                    } else {
                        Modifier.pointerInput(Unit) { detectTapGestures {} }
                    }
                )
                .padding(tokens.u * 2f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loadingText != null) {
                AnimeTvSpinner(
                    size = tokens.u * 4f,
                    strokeWidth = tokens.u * 0.4f,
                    color = colors.primary2
                )
                Spacer(Modifier.height(tokens.u * 0.8f))
                Text(
                    text = loadingText,
                    color = colors.primary1,
                    fontSize = fsSp(tokens.fs14),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            if (qrContent != null) {
                Box(modifier = Modifier.padding(bottom = tokens.u * 1f)) {
                    qrContent()
                }
            } else if (!qrImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = qrImageUrl,
                    contentDescription = caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(bottom = tokens.u * 1f)
                        .size(tokens.u * 20f)
                )
            }
            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    color = colors.primary1,
                    fontSize = fsSp(tokens.fs14),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ImgPickerDialog(
    title: String,
    items: List<ImgPickerItem>,
    onSelect: (ImgPickerItem) -> Unit,
    onDismiss: () -> Unit
) {
    val shadowsDisabled = LocalPerformanceEffects.current.getOrElse(2) { false }
    val playClickSound = LocalNavigationSound.current
    val initialIndex = remember(items) {
        items.indexOfFirst { it.selected }.let { if (it >= 0) it else 0 }
    }
    var pendingFocusIndex by remember(items) {
        mutableStateOf<Int?>(if (items.isNotEmpty()) initialIndex else null)
    }
    val gridState = rememberLazyGridState()

    LaunchedEffect(pendingFocusIndex) {
        val target = pendingFocusIndex ?: return@LaunchedEffect
        if (target in items.indices) {
            gridState.scrollToItem(target)
        }
    }

    AnimeTvModalScrim(onDismiss = onDismiss) { animProgress, tokens, colors ->
        val baseWidth = if (tokens.portrait) tokens.vw * 84f else tokens.vw * 43f
        val dialogWidth = baseWidth.coerceAtMost(tokens.screenWidth * 0.94f)
        val gridMaxHeight = if (tokens.portrait) tokens.vw * 64f else tokens.vw * 33f

        Column(
            modifier = Modifier
                .modalWindowSurface(
                    width = dialogWidth,
                    cornerRadius = tokens.u * 0.5f,
                    animProgress = animProgress,
                    minScale = 0.8f,
                    backgroundColor = Color(0xFF222222),
                    shadowEnabled = !shadowsDisabled,
                    shadowElevation = tokens.u * 0.6f
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                        playClickSound()
                        onDismiss()
                        true
                    } else false
                }
        ) {
            ModalHeaderBar(title = title)

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = gridState,
                contentPadding = PaddingValues(tokens.vw * 0.25f),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = gridMaxHeight)
                    .background(Color.Black)
            ) {
                itemsIndexed(items, key = { index, item -> "${item.id}:$index" }) { index, item ->
                    val requester = remember { FocusRequester() }
                    var focused by remember { mutableStateOf(false) }
                    val interactionSource = remember { MutableInteractionSource() }
                    val hovered by interactionSource.collectIsHoveredAsState()

                    LaunchedEffect(pendingFocusIndex) {
                        if (pendingFocusIndex == index) {
                            yield()
                            runCatching { requester.requestFocus() }
                            pendingFocusIndex = null
                        }
                    }

                    val outerBorderColor = when {
                        focused -> Color.White
                        hovered -> colors.primary3
                        else -> Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .border(tokens.vw * 0.4f, outerBorderColor)
                            .hoverable(interactionSource = interactionSource)
                            .focusRequester(requester)
                            .onFocusChanged { focused = it.isFocused }
                            .navigationClickable { onSelect(item) }
                            .navigationFocusable()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (item.selected) Modifier.border(tokens.vw * 0.2f, colors.accent1)
                                    else Modifier
                                )
                        ) {
                            when {
                                item.drawableName != null -> Image(
                                    painter = platformImagePainter(item.drawableName),
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                !item.imageUrl.isNullOrBlank() -> AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> Box(
                                    modifier = Modifier.fillMaxSize().background(Color(0xFF222222)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.icon ?: IconGlyphs.Block,
                                        fontFamily = LocalIconFont.current,
                                        color = Color(0xFF888888),
                                        fontSize = fsSp(tokens.vw * 5f)
                                    )
                                }
                            }

                            if ((focused || hovered) && item.title.isNotBlank()) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = fsSp(tokens.fs10),
                                    lineHeight = fsSp(tokens.fs10) * 1.2f,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .padding(tokens.vw * 0.2f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppToastOverlay(message: String?) {
    val tokens = LocalAnimeTvTokens.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = tokens.screenHeight * 0.04f),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = !message.isNullOrBlank(),
            enter = fadeIn(tween(durationMillis = tokens.tim3s, easing = tokens.easing)),
            exit = fadeOut(tween(durationMillis = tokens.tim3s, easing = tokens.easing))
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(tokens.fs40))
                    .background(Color(0xE6141414))
                    .padding(horizontal = tokens.fs10, vertical = tokens.fs15 * 0.4f)
            ) {
                Text(
                    text = message.orEmpty(),
                    color = Color.White,
                    fontSize = fsSp(tokens.fs15),
                    lineHeight = fsSp(tokens.fs15) * 1.2f,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AnimeTvSpinner(
    size: Dp,
    strokeWidth: Dp = size * 0.1f,
    color: Color = LocalAnimeTvColors.current.primary2
) {
    val transition = rememberInfiniteTransition(label = "z-loader")
    // Outer ring replicates .z-loader's net -270deg per 2.5s (-1080deg over 10s).
    val outerAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = -1080f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "z-loader-outer"
    )
    // Inner arc replicates z-loader-innerani oscillating 90deg <-> 270deg every 0.5s.
    val innerOffset by transition.animateFloat(
        initialValue = 90f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "z-loader-inner"
    )

    Canvas(modifier = Modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val inset = strokePx / 2f
        val arcSize = androidx.compose.ui.geometry.Size(
            (this.size.width - strokePx).coerceAtLeast(1f),
            (this.size.height - strokePx).coerceAtLeast(1f)
        )
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Butt)

        drawArc(
            color = color,
            startAngle = outerAngle - 45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        drawArc(
            color = color,
            startAngle = outerAngle + innerOffset - 45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
    }
}

private fun parseDialogMarkup(raw: String, headingSize: TextUnit): AnnotatedString {
    val normalized = raw
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)<c>.*?</c>"), "")
    return buildAnnotatedString {
        var i = 0
        while (i < normalized.length) {
            when {
                normalized.startsWith("<h6>", i, ignoreCase = true) -> {
                    val end = normalized.indexOf("</h6>", i + 4, ignoreCase = true)
                    if (end != -1) {
                        val content = normalized.substring(i + 4, end).trim()
                        val startIdx = length
                        append(content)
                        addStyle(
                            SpanStyle(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = headingSize
                            ),
                            startIdx,
                            length
                        )
                        if (end + 5 < normalized.length) append('\n')
                        i = end + 5
                    } else {
                        append(normalized[i])
                        i++
                    }
                }
                normalized.startsWith("<b>", i, ignoreCase = true) -> {
                    val end = normalized.indexOf("</b>", i + 3, ignoreCase = true)
                    if (end != -1) {
                        val content = normalized.substring(i + 3, end)
                        val startIdx = length
                        append(content)
                        addStyle(
                            SpanStyle(color = Color.White, fontWeight = FontWeight.Bold),
                            startIdx,
                            length
                        )
                        i = end + 4
                    } else {
                        append(normalized[i])
                        i++
                    }
                }
                else -> {
                    append(normalized[i])
                    i++
                }
            }
        }
    }
}
