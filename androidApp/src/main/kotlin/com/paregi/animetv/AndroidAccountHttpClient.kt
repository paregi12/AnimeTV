package com.paregi.animetv

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** HTTP framing and response handling for AniList, MAL, and the legacy login relay. */
internal class AndroidAccountHttpClient(private val networkStack: AndroidNetworkStack) {
    fun relayJson(url: String): JSONObject = JSONObject(
        getText(url, mapOf("Accept" to "application/json"))
    )

    fun postJson(url: String, body: String, headers: Map<String, String>): JSONObject {
        val requestHeaders = buildMap {
            put("Content-Type", "application/json")
            put("Accept", "application/json")
            put("User-Agent", USER_AGENT)
            putAll(headers)
        }
        val response = networkStack.execute(
            url, "POST", requestHeaders, body.toByteArray(StandardCharsets.UTF_8)
        )
        return try {
            JSONObject(readResponse(response))
        } finally {
            response.close()
        }
    }

    fun getText(url: String, headers: Map<String, String> = emptyMap()): String {
        val requestHeaders = buildMap {
            put("User-Agent", USER_AGENT)
            putAll(headers)
        }
        val response = networkStack.execute(url, headers = requestHeaders)
        return try {
            readResponse(response)
        } finally {
            response.close()
        }
    }

    fun putForm(url: String, body: String, headers: Map<String, String>) {
        val requestHeaders = buildMap {
            put("Content-Type", "application/x-www-form-urlencoded")
            put("Accept", "application/json")
            put("User-Agent", USER_AGENT)
            putAll(headers)
        }
        val response = networkStack.execute(
            url, "PUT", requestHeaders, body.toByteArray(StandardCharsets.UTF_8)
        )
        try {
            readResponse(response)
        } finally {
            response.close()
        }
    }

    fun deleteRequest(url: String, headers: Map<String, String>) {
        val requestHeaders = buildMap {
            put("Accept", "application/json")
            put("User-Agent", USER_AGENT)
            putAll(headers)
        }
        val response = networkStack.execute(url, "DELETE", requestHeaders)
        try {
            readResponse(response)
        } finally {
            response.close()
        }
    }

    fun formEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun readResponse(response: AndroidNetworkResponse): String {
        val status = response.responseCode
        check(status in 200..299) { "Account service request failed with HTTP $status" }
        return response.inputStream?.use {
            it.readBoundedBytes(MAX_RESPONSE_BYTES).toString(StandardCharsets.UTF_8)
        }.orEmpty()
    }

    private companion object {
        const val USER_AGENT = "AnimeTV KMP/6.6.7 (Android)"
        const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
    }
}
