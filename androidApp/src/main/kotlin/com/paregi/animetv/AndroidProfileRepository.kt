package com.paregi.animetv

import android.content.Context
import android.content.SharedPreferences
import com.paregi.animetv.domain.ProfileDirectory
import com.paregi.animetv.domain.ProfileRepository
import com.paregi.animetv.domain.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Stores local users using the original profile fields and preference names. */
class AndroidProfileRepository(
    context: Context,
    private val namespace: ProfileNamespace
) : ProfileRepository {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences("SERVER", Context.MODE_PRIVATE)

    override suspend fun loadProfiles(): ProfileDirectory = withContext(Dispatchers.IO) {
        val profiles = runCatching {
            val stored = preferences.getString(KEY_USERS, null) ?: return@runCatching listOf(defaultProfile())
            val values = JSONArray(stored)
            (0 until values.length()).mapNotNull { index ->
                val item = values.optJSONObject(index) ?: return@mapNotNull null
                val prefix = item.optString("u")
                UserProfile(
                    prefix = prefix,
                    displayName = item.optString("n").ifBlank { "Default User" },
                    avatarId = item.optString("i").ifBlank { "0" },
                    avatarUrl = item.optString("im").takeIf { it.isNotBlank() && it != "null" },
                    avatarTitle = item.optString("it").ifBlank { "Anymoe" },
                    pin = item.optString("p").takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty(),
                    parentalRating = item.optInt("x", 6).coerceIn(0, 6)
                )
            }.ifEmpty { listOf(defaultProfile()) }
        }.getOrDefault(listOf(defaultProfile())).take(MAX_PROFILES)
        val storedDefault = preferences.getString(KEY_DEFAULT_USER, "")
        val defaultPrefix = storedDefault?.takeIf { value -> profiles.any { it.prefix == value } }
        ProfileDirectory(profiles = profiles, defaultPrefix = defaultPrefix)
    }

    override suspend fun createProfile(displayName: String): UserProfile = withContext(Dispatchers.IO) {
        UserProfile(
            prefix = "u${System.currentTimeMillis()}",
            displayName = displayName.trim().take(18).ifBlank { "New User" },
            avatarId = "0",
            parentalRating = 6
        )
    }

    override suspend fun saveProfiles(directory: ProfileDirectory) {
        withContext(Dispatchers.IO) {
            val profiles = directory.profiles.take(MAX_PROFILES).ifEmpty { listOf(defaultProfile()) }
            val encoded = JSONArray().apply {
                profiles.forEach { profile ->
                    put(JSONObject()
                        .put("u", profile.prefix)
                        .put("n", profile.displayName)
                        .put("i", profile.avatarId)
                        .put("im", profile.avatarUrl)
                        .put("p", profile.pin)
                        .put("it", profile.avatarTitle)
                        .put("x", profile.parentalRating.coerceIn(0, 6)))
                }
            }
            val defaultPrefix = directory.defaultPrefix
                ?.takeIf { value -> profiles.any { it.prefix == value } }
                ?: profiles.first().prefix
            preferences.edit()
                .putString(KEY_USERS, encoded.toString())
                .putString(KEY_DEFAULT_USER, defaultPrefix)
                .apply()
        }
    }

    override suspend fun deleteProfileData(prefix: String) = withContext(Dispatchers.IO) {
        if (prefix.isBlank()) return@withContext
        val server = preferences.edit()
        preferences.all.keys
            .filter { it.startsWith(prefix) || it.startsWith("viewstorage_${prefix}") }
            .forEach { server.remove(it) }
        server.apply()

        listOf("KMP_LIBRARY", "KMP_ACCOUNTS").forEach { name ->
            val store = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = store.edit()
            store.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    override fun activate(profile: UserProfile) {
        namespace.activate(profile.prefix)
    }

    private fun defaultProfile() = UserProfile(
        prefix = "",
        displayName = "Default User",
        avatarId = "0",
        parentalRating = 6
    )

    private companion object {
        const val KEY_USERS = "viewstorage_users"
        const val KEY_DEFAULT_USER = "viewstorage_default_user"
        const val MAX_PROFILES = 5
    }
}
