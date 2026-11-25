package com.paregi.animetv

import androidx.compose.runtime.Composable

/** Opens the platform image picker and returns a durable image URI when one is selected. */
@Composable
expect fun rememberPlatformImagePicker(onImageSelected: (String?) -> Unit): () -> Unit
