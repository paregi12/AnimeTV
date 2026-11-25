package com.paregi.animetv.domain

import androidx.compose.ui.graphics.Color
import com.paregi.animetv.core.theme.IconGlyphs

data class SubtitleStyleAxis(
    val title: String,
    val options: List<String>,
    val divisor: Int
)

/** Preserves the original app's decimal-packed `ccstyle` preference. */
object SubtitleStyleCatalog {
    val axes = listOf(
        SubtitleStyleAxis(
            "Font Type",
            listOf("Serif", "Proportional", "Itim", "Salsa", "Outfit", "Merriweather", "Cantora One", "Open Sans", "Ruluko", "Comic Neue"),
            1
        ),
        SubtitleStyleAxis("Font Size", listOf("Large", "Medium", "Small", "Extra Large"), 10),
        SubtitleStyleAxis("Font Weight", listOf("Normal", "Bold", "Bolder", "Thin"), 100),
        SubtitleStyleAxis(
            "Background",
            listOf("No Background", "Background 25%", "Background 50%", "Background 75%", "Background 100%"),
            1_000
        ),
        SubtitleStyleAxis("Color", listOf("White", "Black", "Cyan", "Yellow", "Red", "Green", "Blue", "Primary", "Accent"), 10_000),
        SubtitleStyleAxis(
            "Text Opacity",
            listOf("Full Opacity", "95% Transparent", "90% Transparent", "85% Transparent", "80% Transparent", "75% Transparent", "70% Transparent", "65% Transparent", "60% Transparent"),
            100_000
        ),
        SubtitleStyleAxis("Outline", listOf("No Outline", "Thin Outline", "Medium Outline", "Thick Outline", "Extra Thick Outline"), 1_000_000)
    )

    internal val axisIcons = listOf(
        IconGlyphs.BrandFamily,
        IconGlyphs.FormatSize,
        IconGlyphs.FormatBold,
        IconGlyphs.Gradient,
        IconGlyphs.Palette,
        IconGlyphs.Opacity,
        IconGlyphs.TextFormat
    )

    fun selectedIndex(style: Int, axis: Int): Int {
        val definition = axes.getOrNull(axis) ?: return 0
        return ((style.coerceAtLeast(0) / definition.divisor) % 10)
            .takeIf { it in definition.options.indices } ?: 0
    }

    fun select(style: Int, axis: Int, option: Int): Int {
        val definition = axes.getOrNull(axis) ?: return style.coerceAtLeast(0)
        if (option !in definition.options.indices) return style.coerceAtLeast(0)
        val normalized = style.coerceAtLeast(0)
        val currentDigit = (normalized / definition.divisor) % 10
        return (normalized - currentDigit * definition.divisor) + option * definition.divisor
    }

    fun summary(style: Int): String =
        axes.indices.joinToString(", ") { index ->
            axes[index].options[selectedIndex(style, index)]
        }

    fun resolveColor(colorIndex: Int, primary1: Color, accent1: Color): Color = when (colorIndex) {
        0 -> Color.White
        1 -> Color.Black
        2 -> Color(0xFF00FFFF)
        3 -> Color(0xFFFFFF00)
        4 -> Color(0xFFFF0000)
        5 -> Color(0xFF00FF00)
        6 -> Color(0xFF6666FF)
        7 -> primary1
        8 -> accent1
        else -> Color.White
    }

    fun resolveOutlineColor(colorIndex: Int): Color =
        if (colorIndex == 1 || colorIndex == 6 || colorIndex == 7) Color.White else Color.Black

    fun resolveTextOpacity(opacityIndex: Int): Float =
        floatArrayOf(1f, 0.95f, 0.90f, 0.85f, 0.80f, 0.75f, 0.70f, 0.65f, 0.60f)
            .getOrElse(opacityIndex) { 1f }

    fun resolveBackgroundOpacity(backgroundIndex: Int): Float =
        floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1f)
            .getOrElse(backgroundIndex) { 0f }

    fun resolveOutlineWidthVw(outlineIndex: Int): Float =
        floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f)
            .getOrElse(outlineIndex) { 0f }
}
