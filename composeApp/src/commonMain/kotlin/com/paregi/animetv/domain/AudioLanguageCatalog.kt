package com.paregi.animetv.domain

data class AudioLanguage(val label: String, val code: String)

/** Audio language choices from the original player settings. */
object AudioLanguageCatalog {
    val all = listOf(
        AudioLanguage("Original", ""),
        AudioLanguage("English", "eng"),
        AudioLanguage("French", "fre"),
        AudioLanguage("German", "ger"),
        AudioLanguage("Hindi", "hin"),
        AudioLanguage("Indonesian", "ind"),
        AudioLanguage("Italian", "ita"),
        AudioLanguage("Portuguese (Brazil)", "por"),
        AudioLanguage("Spanish", "spa"),
        AudioLanguage("Tamil", "tam"),
        AudioLanguage("Telugu", "tel")
    )
}
