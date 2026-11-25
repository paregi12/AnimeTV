package com.paregi.animetv.domain

data class UserProfile(
    val prefix: String,
    val displayName: String,
    val avatarId: String = "0",
    val avatarUrl: String? = null,
    val avatarTitle: String = "Anymoe",
    val pin: String = "",
    val parentalRating: Int = 6
)

data class ProfileDirectory(
    val profiles: List<UserProfile>,
    val defaultPrefix: String? = null
)
