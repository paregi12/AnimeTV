package com.paregi.animetv

import android.content.Context
import android.net.Uri
import androidx.core.text.HtmlCompat
import com.paregi.animetv.domain.SubtitleLanguageCatalog
import com.paregi.animetv.domain.SubtitleTrack
import com.paregi.animetv.domain.TranslationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Uses the same Google Translate service as the original app for summaries and soft subtitles. */
class AndroidTranslationRepository(
    context: Context,
    private val networkStack: AndroidNetworkStack
) : TranslationRepository {
    private val translatedSubtitleDirectory =
        context.applicationContext.cacheDir.resolve("translated-subtitles")

    override suspend fun translateText(text: String, targetLanguage: String): String =
        withContext(Dispatchers.IO) {
            if (text.isBlank() || targetLanguage.isBlank()) return@withContext text
            val translations = mutableListOf<String>()
            for (chunk in splitAtWordBoundaries(text, 450)) {
                translations += translateChunk(chunk, targetLanguage)
            }
            translations.joinToString("")
        }

    override suspend fun translateSubtitle(
        track: SubtitleTrack,
        headers: Map<String, String>,
        targetLanguage: String
    ): SubtitleTrack? = withContext(Dispatchers.IO) {
        val extension = track.url.substringBefore('?').substringAfterLast('.', "").lowercase()
        if (extension !in setOf("vtt", "srt", "ass", "ssa") || targetLanguage.isBlank()) return@withContext null
        if (!translatedSubtitleDirectory.exists() && !translatedSubtitleDirectory.mkdirs()) {
            return@withContext null
        }
        val cacheKey = sha256("${track.url}\n$targetLanguage")
        val outputExtension = if (extension == "srt") "vtt" else extension
        val subtitleFile = translatedSubtitleDirectory.resolve("$cacheKey.$outputExtension")
        if (!subtitleFile.isFile || subtitleFile.length() == 0L) {
            val requestHeaders = headers + mapOf(
                "Accept" to "text/vtt, application/x-subrip, text/plain, */*",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36"
            )
            val document = networkStack.execute(track.url, headers = requestHeaders).use { response ->
                check(response.responseCode in 200..299) { "Subtitle request failed (${response.responseCode})" }
                response.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Subtitle response was empty")
            }
            val translatedDocument = translateSubtitleDocument(document, extension, targetLanguage)
                ?: return@withContext null
            subtitleFile.writeText(translatedDocument, Charsets.UTF_8)
        }
        val languageLabel = SubtitleLanguageCatalog.all.firstOrNull { it.code == targetLanguage }?.label
            ?: targetLanguage
        track.copy(
            id = "${track.id}:translated:$targetLanguage",
            language = targetLanguage,
            label = "$languageLabel (translated)",
            url = Uri.fromFile(subtitleFile).toString(),
            defaultSelected = true
        )
    }

    private suspend fun translateSubtitleDocument(
        document: String,
        extension: String,
        targetLanguage: String
    ): String? {
        val cleanDocument = document.removePrefix("\uFEFF").replace("\r", "")
        if (extension == "ass" || extension == "ssa") {
            return translateAssDocument(cleanDocument, targetLanguage)
        }
        val blocks = cleanDocument.split(Regex("\n[ \t]*\n+"))
        val cueBlocks = blocks.map { block ->
            val lines = block.split('\n')
            val timestampIndex = lines.indexOfFirst { it.contains("-->") }
            if (timestampIndex < 0 || timestampIndex == lines.lastIndex) return@map CueBlock(block, null)
            val caption = lines.drop(timestampIndex + 1).joinToString("\n").trim()
            if (caption.isBlank()) return@map CueBlock(block, null)
            CueBlock(block, CueContent(lines.take(timestampIndex + 1), caption))
        }
        val cueContents = cueBlocks.mapNotNull { it.cue }
        if (cueContents.isEmpty()) return null
        val translatedCaptions = translateCueCaptions(cueContents.map { it.caption }, targetLanguage)
        var translatedIndex = 0
        val result = cueBlocks.map { cueBlock ->
            val cue = cueBlock.cue ?: return@map cueBlock.raw
            val translated = translatedCaptions[translatedIndex++]
            (cue.headerLines.map { line ->
                if (line.contains("-->")) line.replace(',', '.') else line
            } + translated.split('\n')).joinToString("\n")
        }
        return (if (cleanDocument.trimStart().startsWith("WEBVTT")) "" else "WEBVTT\n\n") +
            result.joinToString("\n\n").trimEnd() + "\n"
    }

    private suspend fun translateAssDocument(document: String, targetLanguage: String): String? {
        var inEvents = false
        var format = emptyList<String>()
        var translatedCount = 0
        val translatedLines = document.split('\n').map { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inEvents = trimmed.equals("[Events]", ignoreCase = true)
                return@map line
            }
            if (!inEvents) return@map line
            if (trimmed.startsWith("Format:", ignoreCase = true)) {
                format = trimmed.substringAfter(':').split(',').map { it.trim().lowercase() }
                return@map line
            }
            if (!trimmed.startsWith("Dialogue:", ignoreCase = true)) return@map line
            val colonIndex = line.indexOf(':')
            val fields = line.substring(colonIndex + 1).split(',', limit = format.size.coerceAtLeast(1)).toMutableList()
            val textIndex = format.indexOf("text").takeIf { it >= 0 } ?: fields.lastIndex
            if (textIndex !in fields.indices) return@map line
            fields[textIndex] = translateAssText(fields[textIndex], targetLanguage)
            translatedCount++
            line.substring(0, colonIndex + 1) + fields.joinToString(",")
        }
        if (translatedCount == 0) return null
        return translatedLines.joinToString("\n")
    }

    private suspend fun translateAssText(text: String, targetLanguage: String): String {
        val protectedTokens = mutableListOf<Pair<String, String>>()
        val safeText = Regex("\\{[^}]*}|\\\\[Nn]").replace(text) { match ->
            val marker = "ZXQASS${protectedTokens.size}TOKENZXQ"
            protectedTokens += marker to match.value
            marker
        }
        val translated = translateText(safeText, targetLanguage)
        if (protectedTokens.any { (marker, _) -> !translated.contains(marker) }) return text
        return protectedTokens.fold(translated) { value, (marker, original) -> value.replace(marker, original) }
    }

    private suspend fun translateCueCaptions(captions: List<String>, targetLanguage: String): List<String> {
        val result = MutableList(captions.size) { "" }
        var start = 0
        while (start < captions.size) {
            val marker = "ZXQ${System.nanoTime()}CUEZXQ"
            var end = start
            var combinedLength = 0
            while (end < captions.size) {
                val nextLength = captions[end].length + if (end > start) marker.length + 2 else 0
                if (end > start && combinedLength + nextLength > 420) break
                combinedLength += nextLength
                end++
            }
            val chunk = captions.subList(start, end)
            if (chunk.size == 1) {
                result[start] = translateText(chunk.single(), targetLanguage)
            } else {
                val translated = try {
                    translateText(chunk.joinToString(" $marker "), targetLanguage)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                val parts = translated?.split(marker)?.map(String::trim)
                if (parts?.size == chunk.size) {
                    parts.forEachIndexed { index, text -> result[start + index] = text }
                } else {
                    chunk.forEachIndexed { index, text ->
                        result[start + index] = translateText(text, targetLanguage)
                    }
                }
            }
            start = end
        }
        return result
    }

    private suspend fun translateChunk(text: String, targetLanguage: String): String {
        val url = Uri.parse("https://translate.google.com/m").buildUpon()
            .appendQueryParameter("tl", targetLanguage)
            .appendQueryParameter("sl", "en")
            .appendQueryParameter("q", text)
            .build()
            .toString()
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val page = networkStack.execute(
                    url,
                    headers = mapOf(
                        "Accept" to "text/html,application/xhtml+xml,*/*",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36"
                    )
                ).use { response ->
                    check(response.responseCode in 200..299) { "Translation request failed (${response.responseCode})" }
                    response.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Translation response was empty")
                }
                val resultHtml = Regex(
                    "<div[^>]*class=[\"'][^\"']*result-container[^\"']*[\"'][^>]*>(.*?)</div>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).find(page)?.groupValues?.get(1) ?: error("Translation result was missing")
                val translated = HtmlCompat.fromHtml(resultHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    .toString()
                    .trim()
                if (translated.isNotBlank()) return translated
                error("Translation response did not contain translated text")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                lastError = error
                if (attempt < 2) delay(500L * (attempt + 1))
            }
        }
        throw lastError ?: error("Translation failed")
    }

    private fun splitAtWordBoundaries(text: String, limit: Int): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + limit).coerceAtMost(text.length)
            if (end < text.length) {
                val boundary = text.lastIndexOf(' ', end)
                if (boundary > start) end = boundary + 1
            }
            chunks += text.substring(start, end)
            start = end
        }
        return chunks
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class CueBlock(val raw: String, val cue: CueContent?)
    private data class CueContent(val headerLines: List<String>, val caption: String)
}
