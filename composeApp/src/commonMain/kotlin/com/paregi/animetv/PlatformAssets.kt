package com.paregi.animetv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import coil3.ImageLoader
import coil3.PlatformContext

expect fun buildPlatformImageLoader(context: PlatformContext): ImageLoader

@Composable
expect fun platformImagePainter(resourceName: String): Painter

@Composable
expect fun platformFontFamily(icon: Boolean): FontFamily
