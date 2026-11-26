package com.paregi.animetv.source.shared

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal fun JSONArray?.toSourceStringList(): List<String> = this?.let { array ->
    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}.orEmpty()

internal fun JSONArray?.toSourceGenreList(): List<String> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val value = array.opt(index)
        if (value is JSONObject) value.optString("name").takeIf(String::isNotBlank)
        else value?.toString()?.takeIf(String::isNotBlank)
    }
}

internal fun String.toSubtitleLanguageTag(): String = when (trim().lowercase(Locale.ROOT)) {
    "english" -> "en"
    "japanese" -> "ja"
    "spanish" -> "es"
    "portuguese" -> "pt"
    "french" -> "fr"
    "german" -> "de"
    "italian" -> "it"
    "russian" -> "ru"
    "arabic" -> "ar"
    "chinese" -> "zh"
    "korean" -> "ko"
    "unknown", "" -> "und"
    else -> trim()
}

internal fun JSONObject.optSourceFlag(key: String): Boolean {
    if (isNull(key)) return false
    val value = opt(key) ?: return false
    return when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        else -> false
    }
}
