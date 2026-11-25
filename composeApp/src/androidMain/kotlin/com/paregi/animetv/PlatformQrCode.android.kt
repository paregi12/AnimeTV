package com.paregi.animetv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
actual fun PlatformQrCode(value: String) {
    val bitmap = remember(value) {
        runCatching {
            val matrix = QRCodeWriter().encode(
                value,
                BarcodeFormat.QR_CODE,
                360,
                360,
                mapOf(EncodeHintType.MARGIN to 1)
            )
            val pixels = IntArray(matrix.width * matrix.height) { index ->
                val x = index % matrix.width
                val y = index / matrix.width
                if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
    if (bitmap == null) {
        Text("Could not create login QR code", color = Color.White)
    } else {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = "Login QR code",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(240.dp)
        )
    }
}
