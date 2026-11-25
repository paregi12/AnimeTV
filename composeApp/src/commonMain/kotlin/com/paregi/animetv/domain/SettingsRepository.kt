package com.paregi.animetv.domain

interface SettingsRepository {
    suspend fun readSettings(): AppSettings
    suspend fun updateSettings(settings: AppSettings)
    suspend fun readLoginScreenStyle(): Int = 1
    suspend fun readProfileAppearance(prefix: String): ProfileAppearance = ProfileAppearance()
    suspend fun readMyListLayout(): MyListLayout = MyListLayout.default()
    suspend fun saveMyListLayout(layout: MyListLayout) {}
    suspend fun readHomeLayout(): HomeLayout = HomeLayout.default()
    suspend fun saveHomeLayout(layout: HomeLayout) {}
    suspend fun clearCache() {}
}
