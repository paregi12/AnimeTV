package com.paregi.animetv.source.anilist

import com.paregi.animetv.source.*
import com.paregi.animetv.source.shared.*


import org.json.JSONObject

internal typealias AniListRequestJson = suspend (
    url: String,
    body: String?,
    referer: String,
    origin: String?
) -> JSONObject

internal typealias AniListRequestText = suspend (
    url: String,
    body: String?,
    contentType: String?,
    referer: String
) -> String

/** Shared request boundary for AniList GraphQL calls. */
internal class AniListGraphQlClient(
    private val requestJsonCall: AniListRequestJson,
    private val requestTextCall: AniListRequestText
) {
    suspend fun requestJson(
        url: String,
        body: String? = null,
        referer: String,
        origin: String? = null
    ): JSONObject = requestJsonCall(url, body, referer, origin)

    suspend fun request(
        url: String,
        body: String? = null,
        contentType: String? = null,
        referer: String
    ): String = requestTextCall(url, body, contentType, referer)
}
