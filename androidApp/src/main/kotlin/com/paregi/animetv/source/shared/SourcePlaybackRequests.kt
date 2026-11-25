package com.paregi.animetv.source.shared

import com.paregi.animetv.source.*


import org.json.JSONObject

internal typealias SourceRequestText = suspend (
    url: String,
    body: String?,
    contentType: String?,
    accept: String,
    source: CatalogSource,
    referer: String,
    origin: String?
) -> String

internal typealias SourceRequestJson = suspend (
    url: String,
    body: String?,
    referer: String,
    origin: String?
) -> JSONObject
