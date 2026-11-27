package com.paregi.animetv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface QrRenderState {
    data object Loading : QrRenderState
    data class Ready(val bitmap: ImageBitmap) : QrRenderState
    data object Failed : QrRenderState
}

@Composable
actual fun PlatformQrCode(value: String) {
    val qrContent = value
    val qrState by produceState<QrRenderState>(initialValue = QrRenderState.Loading, key1 = qrContent) {
        val encoded = withContext(Dispatchers.Default) {
            runCatching {
                val matrix = QRCodeWriter().encode(
                    qrContent,
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
                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
                    .asImageBitmap()
            }.getOrNull()
        }
        this.value = if (encoded != null) QrRenderState.Ready(encoded) else QrRenderState.Failed
    }
    when (val state = qrState) {
        QrRenderState.Loading -> Box(modifier = Modifier.size(240.dp))
        QrRenderState.Failed -> Text("Could not create login QR code", color = Color.White)
        is QrRenderState.Ready -> Image(
            bitmap = state.bitmap,
            contentDescription = "Login QR code",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(240.dp)
        )
    }
}
