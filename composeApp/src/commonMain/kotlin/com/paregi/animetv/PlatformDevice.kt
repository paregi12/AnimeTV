package com.paregi.animetv

import androidx.compose.runtime.Composable

/** Identifies a television-class host independently of the current window width. */
@Composable
expect fun platformIsTelevision(): Boolean

@Composable
expect fun platformIsTouchDevice(): Boolean
