package com.paregi.animetv

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.size.Precision
import java.io.File

private fun isLowRam(context: Context): Boolean {
    val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return am?.isLowRamDevice == true
}

actual fun buildPlatformImageLoader(context: PlatformContext): ImageLoader {
    val lowRam = isLowRam(context)
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, if (lowRam) 0.12 else 0.18)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.cacheDir, "coil_image_cache"))
                .maxSizeBytes(if (lowRam) 40L * 1024 * 1024 else 80L * 1024 * 1024)
                .build()
        }
        .allowRgb565(true)
        .build()
}

@Composable
actual fun platformImagePainter(resourceName: String): Painter {
    val context = LocalContext.current
    val resourceId = remember(context, resourceName) {
        context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    }
    if (resourceId == 0) {
        return remember { ColorPainter(Color.Transparent) }
    }
    val request = remember(context, resourceName, resourceId) {
        val dm = context.resources.displayMetrics
        val (targetW, targetH) = when {
            resourceName.startsWith("wallpaper_gallery_") -> {
                val lowRam = isLowRam(context)
                val maxLong = if (lowRam) 1280 else 1920
                val maxShort = if (lowRam) 720 else 1080
                val isLandscape = dm.widthPixels >= dm.heightPixels
                val capW = if (isLandscape) maxLong else maxShort
                val capH = if (isLandscape) maxShort else maxLong
                dm.widthPixels.coerceIn(1, capW) to dm.heightPixels.coerceIn(1, capH)
            }
            resourceName == "noimg" -> 360 to 360
            resourceName.startsWith("asset_") ||
                resourceName == "zoro" ||
                resourceName.startsWith("wallpaper_thumb_") -> 256 to 256
            else -> dm.widthPixels.coerceIn(1, 1280) to dm.heightPixels.coerceIn(1, 1280)
        }
        ImageRequest.Builder(context)
            .data(resourceId)
            .size(targetW, targetH)
            .precision(Precision.INEXACT)
            .allowRgb565(true)
            .build()
    }
    return rememberAsyncImagePainter(model = request)
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
