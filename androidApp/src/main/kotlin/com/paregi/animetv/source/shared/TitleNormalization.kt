package com.paregi.animetv.source.shared

import com.paregi.animetv.source.*


import java.text.Normalizer
import java.util.Locale

private val DIACRITICS_REGEX = Regex("\\p{Mn}+")
private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{N}]")

internal fun normalizeAnimeTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(DIACRITICS_REGEX, "")
    .lowercase(Locale.ROOT)
    .replace(NON_ALNUM_REGEX, "")
