package com.paregi.animetv

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Android implementation of the current Gotaku API and manifest envelope. */
internal object AndroidGotakuCrypto {
    private val apiSeedV8 = byteArrayOf(
        0xA9.toByte(), 0x61, 0x0D, 0xE5.toByte(), 0x89.toByte(), 0x6A, 0xC7.toByte(), 0x5B,
        0x7C, 0x35, 0x82.toByte(), 0x67, 0xEB.toByte(), 0xD8.toByte(), 0xCC.toByte(), 0x29,
        0xA1.toByte(), 0xCF.toByte(), 0x04, 0x9D.toByte(), 0x6D, 0x6A, 0x82.toByte(), 0x05,
        0x39, 0xB0.toByte(), 0x9B.toByte(), 0x37, 0x12, 0x94.toByte(), 0x50, 0x80.toByte()
    )
    private val apiSeedV10 = byteArrayOf(
        0x1E, 0x63, 0xCE.toByte(), 0xEC.toByte(), 0xB0.toByte(), 0x1A, 0xE4.toByte(), 0x93.toByte(),
        0x37, 0x23, 0x98.toByte(), 0x7B, 0x06, 0x11, 0x7E, 0xB3.toByte(),
        0x28, 0xB5.toByte(), 0xC5.toByte(), 0xD2.toByte(), 0xC2.toByte(), 0x5A, 0xE8.toByte(), 0x23,
        0xF7.toByte(), 0x14, 0x82.toByte(), 0x2B, 0x47, 0x75, 0x7B, 0x52
    )
    private val manifestSeed = "458e5403c40a73332706df406193dd6b040a2ea97a6bb59b52b294a61006e6ef"
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun decryptApi(body: ByteArray, apiVersion: String = "10"): String? {
        if (body.size <= 29) return null
        val expected = apiVersion.toIntOrNull()?.toByte() ?: 10
        var blob = body
        var version = expected
        if (body[0] != expected) {
            blob = decodeUrlSafe(String(body, StandardCharsets.UTF_8)) ?: return null
            if (blob.size <= 29) return null
            version = when (blob[0].toInt()) {
                8, 10 -> blob[0]
                else -> if (blob[0] == expected) blob[0] else return null
            }
        } else {
            version = blob[0]
        }
        val seed = if (version.toInt() == 8) apiSeedV8 else apiSeedV10
        val iv = blob.copyOfRange(1, 13)
        val key = hkdf(seed, ByteArray(0), "api-seal|v1".toByteArray(), 32) ?: return null
        val compressed = gcm(Cipher.DECRYPT_MODE, key, iv, blob.copyOfRange(13, blob.size)) ?: return null
        return runCatching {
            InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
        }.getOrNull()
    }

    fun newNonce(): ByteArray = ByteArray(16).also(SecureRandom()::nextBytes)

    fun encryptManifest(stamp: String, nonce: ByteArray): String? {
        val payload = """{"nonce":"${encodeUrlSafe(nonce)}","stamp":"$stamp"}"""
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val encrypted = gcm(Cipher.ENCRYPT_MODE, manifestSeed, iv, payload.toByteArray()) ?: return null
        return encodeUrlSafe(iv + encrypted)
    }

    fun decryptManifest(body: String, nonce: ByteArray): String? {
        val blob = decodeUrlSafe(body) ?: return null
        if (blob.size < 28) return null
        val key = hkdf(manifestSeed, nonce, "manifest-resp|v1".toByteArray(), 32) ?: return null
        return gcm(Cipher.DECRYPT_MODE, key, blob.copyOfRange(0, 12), blob.copyOfRange(12, blob.size))
            ?.toString(StandardCharsets.UTF_8)
    }

    internal fun decodeUrlSafe(value: String): ByteArray? =
        runCatching { Base64.decode(value.trim(), Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull()

    private fun encodeUrlSafe(value: ByteArray): String =
        Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).trim()

    internal fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray? {
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = hmac(actualSalt, ikm) ?: return null
        val output = ByteArrayOutputStream()
        var previous = ByteArray(0)
        var counter = 1
        while (output.size() < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte())) ?: return null
            output.write(previous)
            counter++
        }
        return Arrays.copyOf(output.toByteArray(), length)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray? = runCatching {
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
    }.getOrNull()

    internal fun gcm(mode: Int, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray? =
        runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                doFinal(data)
            }
        }.getOrNull()
}
