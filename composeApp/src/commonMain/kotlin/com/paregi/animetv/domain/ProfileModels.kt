package com.paregi.animetv.domain

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    val prefix: String,
    val displayName: String,
    val avatarId: String = "0",
    val avatarUrl: String? = null,
    val avatarTitle: String = "Anymoe",
    val pin: String = "",
    val parentalRating: Int = 6
)

@Immutable
data class ProfileDirectory(
    val profiles: List<UserProfile>,
    val defaultPrefix: String? = null
)
