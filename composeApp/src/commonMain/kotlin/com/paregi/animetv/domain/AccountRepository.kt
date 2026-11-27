package com.paregi.animetv.domain

import androidx.compose.runtime.Immutable

interface AccountRepository {
    suspend fun readAccounts(): AccountState
    suspend fun loadProfileAvatars(): Map<String, String> = emptyMap()
    suspend fun startLogin(provider: AccountProvider): LoginSession
    suspend fun loginMalWithPassword(username: String, password: String): ConnectedAccount {
        error("Unsupported")
    }
    suspend fun pollLogin(session: LoginSession): LoginPollResult
    suspend fun cancelLogin(session: LoginSession)
    suspend fun disconnect(provider: AccountProvider)
    suspend fun loadTracking(anime: Anime): AnimeTrackingState
    suspend fun setTracking(anime: Anime, provider: AccountProvider, tracked: Boolean)
    suspend fun setTrackingStatus(anime: Anime, provider: AccountProvider, status: String?) {
        setTracking(anime, provider, tracked = status != null)
    }
    suspend fun loadAccountList(provider: AccountProvider, status: String, page: Int): TrackedAnimePage
    suspend fun updateTrackedProgress(anime: Anime, episodeNumber: Int)
}

enum class AccountProvider { ANILIST, MAL }

@Immutable
data class ConnectedAccount(val provider: AccountProvider, val username: String)

@Immutable
data class AccountState(
    val aniListUsername: String? = null,
    val malUsername: String? = null,
    val aniListLibrary: List<Anime> = emptyList(),
    val malLibrary: List<Anime> = emptyList()
)

@Immutable
data class ProviderTrackingState(
    val connected: Boolean = false,
    val mediaId: String? = null,
    val entryId: String? = null,
    val progress: Int? = null,
    val status: String? = null
) {
    val canTrack: Boolean get() = connected && mediaId != null
    val isTracked: Boolean get() = entryId != null
}

@Immutable
data class AnimeTrackingState(
    val aniList: ProviderTrackingState = ProviderTrackingState(),
    val mal: ProviderTrackingState = ProviderTrackingState()
)

@Immutable
data class TrackedAnimeEntry(
    val anime: Anime,
    val status: String,
    val progress: Int
)

@Immutable
data class TrackedAnimePage(
    val entries: List<TrackedAnimeEntry> = emptyList(),
    val hasNextPage: Boolean = false
)

@Immutable
data class LoginSession(
    val provider: AccountProvider,
    val id: String,
    val loginUrl: String
)

@Immutable
sealed interface LoginPollResult {
    data object Waiting : LoginPollResult
    data object Authorizing : LoginPollResult
    @Immutable
    data class Complete(val account: ConnectedAccount) : LoginPollResult
    @Immutable
    data class Failed(val message: String) : LoginPollResult
}
