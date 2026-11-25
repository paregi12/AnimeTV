package com.paregi.animetv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

@Composable
actual fun platformImagePainter(resourceName: String): Painter {
    val context = LocalContext.current
    val resourceId = remember(context, resourceName) {
        context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    }
    return if (resourceId != 0) painterResource(resourceId) else ColorPainter(Color.Transparent)
}

@Composable
actual fun platformFontFamily(icon: Boolean): FontFamily {
    val context = LocalContext.current
    val resourceName = if (icon) "paregi_icons" else "firac"
    val resourceId = remember(context, resourceName) {
        val id = context.resources.getIdentifier(resourceName, "font", context.packageName)
        if (id != 0) id else context.resources.getIdentifier(resourceName, "font", "com.paregi.animetv.shared")
    }
    return remember(resourceId) {
        if (resourceId != 0) FontFamily(Font(resourceId)) else FontFamily.Default
    }
}
