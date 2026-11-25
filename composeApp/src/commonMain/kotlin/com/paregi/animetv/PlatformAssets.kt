package com.paregi.animetv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily

@Composable
expect fun platformImagePainter(resourceName: String): Painter

@Composable
expect fun platformFontFamily(icon: Boolean): FontFamily
