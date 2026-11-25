package com.paregi.animetv.source.shared

import com.paregi.animetv.source.*


import java.text.Normalizer
import java.util.Locale

internal fun normalizeAnimeTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]"), "")
