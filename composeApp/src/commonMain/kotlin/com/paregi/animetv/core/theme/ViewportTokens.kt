package com.paregi.animetv.core.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class AnimeTvTokens(
    val screenWidth: Dp,
    val screenHeight: Dp,
    val vw: Dp,
    val vh: Dp,
    val u: Dp,
    val portrait: Boolean,
    val isTouch: Boolean,
    val largeUi: Boolean,
    val sysStatHeight: Dp,
    val sysNavHeight: Dp,
    val tim1s: Int,
    val tim2s: Int,
    val tim3s: Int,
    val tim4s: Int,
    val tim5s: Int,
    val tim7s: Int,
    val tim8s: Int,
    val tim10s: Int,
    val easing: Easing,
    val fs7: Dp,
    val fs8: Dp,
    val fs10: Dp,
    val fs11: Dp,
    val fs12: Dp,
    val fs13: Dp,
    val fs14: Dp,
    val fs15: Dp,
    val fs17: Dp,
    val fs20: Dp,
    val fs22: Dp,
    val fs25: Dp,
    val fs30: Dp,
    val fs40: Dp,
    val fs60: Dp,
    val fs100: Dp
) {
    @Composable
    fun fsSp(dp: Dp): TextUnit = com.paregi.animetv.core.theme.fsSp(dp)
}

private val AnimeTvEasing: Easing = CubicBezierEasing(0.25f, 0.8f, 0.25f, 1f)

@Composable
fun fsSp(dp: Dp): TextUnit {
    // Neutralize OS/Compose fontScale so --fs-*vw/vh remains strictly viewport-proportional.
    val scale = LocalDensity.current.fontScale.coerceAtLeast(0.01f)
    return (dp.value / scale).sp
}

private data class FontTierMultipliers(
    val f7: Float,
    val f8: Float,
    val f10: Float,
    val f11: Float,
    val f12: Float,
    val f13: Float,
    val f14: Float,
    val f15: Float,
    val f17: Float,
    val f20: Float,
    val f22: Float,
    val f25: Float,
    val f30: Float,
    val f40: Float,
    val f60: Float,
    val f100: Float
)

private val LandscapeFontTiers = listOf(
    FontTierMultipliers(0.7f, 0.8f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.7f, 2.0f, 2.2f, 2.5f, 3.0f, 4.0f, 6.0f, 10.0f),
    FontTierMultipliers(0.9f, 1.0f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f, 1.8f, 2.2f, 2.4f, 2.7f, 3.0f, 4.0f, 6.0f, 10.0f),
    FontTierMultipliers(1.1f, 1.2f, 1.4f, 1.5f, 1.6f, 1.7f, 1.8f, 1.9f, 2.0f, 2.4f, 2.6f, 2.9f, 3.4f, 4.4f, 6.4f, 10.0f),
    FontTierMultipliers(1.2f, 1.3f, 1.5f, 1.6f, 1.7f, 1.8f, 1.9f, 2.0f, 2.1f, 2.5f, 2.7f, 3.0f, 3.5f, 4.5f, 6.5f, 10.0f)
)

private val PortraitFontTiers = listOf(
    FontTierMultipliers(0.9f, 1.0f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f, 1.8f, 2.2f, 2.4f, 2.7f, 3.0f, 4.0f, 6.0f, 10.0f),
    FontTierMultipliers(1.1f, 1.2f, 1.4f, 1.5f, 1.6f, 1.7f, 1.8f, 1.9f, 2.0f, 2.4f, 2.6f, 2.9f, 3.4f, 4.4f, 6.4f, 10.0f),
    FontTierMultipliers(1.4f, 1.5f, 1.7f, 1.8f, 1.9f, 2.0f, 2.1f, 2.2f, 2.3f, 2.7f, 2.9f, 3.2f, 3.7f, 4.7f, 6.7f, 10.2f),
    FontTierMultipliers(1.6f, 1.7f, 1.9f, 2.0f, 2.1f, 2.2f, 2.3f, 2.4f, 2.5f, 2.9f, 3.1f, 3.4f, 3.9f, 4.9f, 6.9f, 10.4f)
)

private fun buildAnimeTvTokens(
    maxWidth: Dp,
    maxHeight: Dp,
    sysStatHeight: Dp,
    sysNavHeight: Dp,
    fontSizeTier: Int,
    largeUi: Boolean,
    animationTier: Int,
    isTouch: Boolean
): AnimeTvTokens {
    val vw = maxWidth / 100f
    val vh = maxHeight / 100f
    val portrait = maxHeight > maxWidth
    val u = if (portrait) vh else vw
    val tier = fontSizeTier.coerceIn(0, 3)
    val m = if (portrait) PortraitFontTiers[tier] else LandscapeFontTiers[tier]
    val timings = when (animationTier.coerceIn(0, 3)) {
        0 -> intArrayOf(100, 200, 300, 400, 500, 700, 800, 1000)
        1 -> intArrayOf(50, 100, 150, 200, 250, 350, 400, 500)
        2 -> intArrayOf(25, 50, 75, 100, 125, 175, 200, 250)
        else -> intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    }
    return AnimeTvTokens(
        screenWidth = maxWidth,
        screenHeight = maxHeight,
        vw = vw,
        vh = vh,
        u = u,
        portrait = portrait,
        isTouch = isTouch,
        largeUi = largeUi && !portrait,
        sysStatHeight = sysStatHeight,
        sysNavHeight = sysNavHeight,
        tim1s = timings[0],
        tim2s = timings[1],
        tim3s = timings[2],
        tim4s = timings[3],
        tim5s = timings[4],
        tim7s = timings[5],
        tim8s = timings[6],
        tim10s = timings[7],
        easing = AnimeTvEasing,
        fs7 = u * m.f7,
        fs8 = u * m.f8,
        fs10 = u * m.f10,
        fs11 = u * m.f11,
        fs12 = u * m.f12,
        fs13 = u * m.f13,
        fs14 = u * m.f14,
        fs15 = u * m.f15,
        fs17 = u * m.f17,
        fs20 = u * m.f20,
        fs22 = u * m.f22,
        fs25 = u * m.f25,
        fs30 = u * m.f30,
        fs40 = u * m.f40,
        fs60 = u * m.f60,
        fs100 = u * m.f100
    )
}

@Composable
fun rememberAnimeTvTokens(
    maxWidth: Dp,
    maxHeight: Dp,
    sysStatHeight: Dp = 0.dp,
    sysNavHeight: Dp = 0.dp,
    fontSizeTier: Int = 2,
    largeUi: Boolean = false,
    animationTier: Int = 0,
    isTouch: Boolean = false
): AnimeTvTokens = remember(
    maxWidth,
    maxHeight,
    sysStatHeight,
    sysNavHeight,
    fontSizeTier,
    largeUi,
    animationTier,
    isTouch
) {
    buildAnimeTvTokens(
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        sysStatHeight = sysStatHeight,
        sysNavHeight = sysNavHeight,
        fontSizeTier = fontSizeTier,
        largeUi = largeUi,
        animationTier = animationTier,
        isTouch = isTouch
    )
}

val LocalAnimeTvTokens: ProvidableCompositionLocal<AnimeTvTokens> = compositionLocalOf {
    buildAnimeTvTokens(
        maxWidth = 960.dp,
        maxHeight = 540.dp,
        sysStatHeight = 0.dp,
        sysNavHeight = 0.dp,
        fontSizeTier = 2,
        largeUi = false,
        animationTier = 0,
        isTouch = false
    )
}

val LocalAppToast: ProvidableCompositionLocal<(String) -> Unit> = compositionLocalOf { {} }
