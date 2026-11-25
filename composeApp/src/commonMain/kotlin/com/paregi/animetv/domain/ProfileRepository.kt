package com.paregi.animetv.domain

interface ProfileRepository {
    suspend fun loadProfiles(): ProfileDirectory
    suspend fun createProfile(displayName: String): UserProfile
    suspend fun saveProfiles(directory: ProfileDirectory)
    suspend fun deleteProfileData(prefix: String)
    fun activate(profile: UserProfile)
}
