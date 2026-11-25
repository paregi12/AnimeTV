package com.paregi.animetv.domain

interface TranslationRepository {
    suspend fun translateText(text: String, targetLanguage: String): String
    suspend fun translateSubtitle(
        track: SubtitleTrack,
        headers: Map<String, String>,
        targetLanguage: String
    ): SubtitleTrack?
}
