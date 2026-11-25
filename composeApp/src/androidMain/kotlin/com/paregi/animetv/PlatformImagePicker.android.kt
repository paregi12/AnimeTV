package com.paregi.animetv

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPlatformImagePicker(onImageSelected: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentCallback = rememberUpdatedState(onImageSelected)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            currentCallback.value(null)
        } else {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            currentCallback.value(uri.toString())
        }
    }
    return remember(launcher) { { launcher.launch(arrayOf("image/*")) } }
}
