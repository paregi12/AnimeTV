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
import java.io.File
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
        if (subtitleFile.isFile && subtitleFile.length() > 0L) {
            subtitleFile.setLastModified(System.currentTimeMillis())
        } else {
            val requestHeaders = headers + mapOf(
                "Accept" to "text/vtt, application/x-subrip, text/plain, */*",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36"
            )
            val document = networkStack.execute(track.url, headers = requestHeaders).use { response ->
                check(response.responseCode in 200..299) { "Subtitle request failed (${response.responseCode})" }
                response.inputStream?.use {
                    it.readBoundedBytes(MAX_SUBTITLE_BYTES).toString(Charsets.UTF_8)
                } ?: error("Subtitle response was empty")
            }
            val translatedDocument = translateSubtitleDocument(document, extension, targetLanguage)
                ?: return@withContext null
            subtitleFile.writeText(translatedDocument, Charsets.UTF_8)
            pruneSubtitleCache()
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
        val blocks = cleanDocument.split(BLANK_LINE_REGEX)
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
        val rawLines = document.split('\n')
        val parsedEntries = ArrayList<Any>(rawLines.size)
        val dialogueEntries = ArrayList<AssDialogueEntry>()
        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inEvents = trimmed.equals("[Events]", ignoreCase = true)
                parsedEntries += line
                continue
            }
            if (!inEvents) {
                parsedEntries += line
                continue
            }
            if (trimmed.startsWith("Format:", ignoreCase = true)) {
                format = trimmed.substringAfter(':').split(',').map { it.trim().lowercase() }
                parsedEntries += line
                continue
            }
            if (!trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                parsedEntries += line
                continue
            }
            val colonIndex = line.indexOf(':')
            val fields = line.substring(colonIndex + 1).split(',', limit = format.size.coerceAtLeast(1)).toMutableList()
            val textIndex = format.indexOf("text").takeIf { it >= 0 } ?: fields.lastIndex
            if (textIndex !in fields.indices) {
                parsedEntries += line
                continue
            }
            val originalText = fields[textIndex]
            if (ASS_TAG_REGEX.replace(originalText, "").isBlank()) {
                parsedEntries += line
                continue
            }
            val protectedTokens = mutableListOf<Pair<String, String>>()
            val safeText = ASS_TAG_REGEX.replace(originalText) { match ->
                val marker = "ZXQASS${protectedTokens.size}TOKENZXQ"
                protectedTokens += marker to match.value
                marker
            }
            val entry = AssDialogueEntry(
                linePrefix = line.substring(0, colonIndex + 1),
                fields = fields,
                textIndex = textIndex,
                originalText = originalText,
                safeText = safeText,
                protectedTokens = protectedTokens
            )
            parsedEntries += entry
            dialogueEntries += entry
        }
        if (dialogueEntries.isEmpty()) return null

        val translatedCaptions = translateCueCaptions(dialogueEntries.map { it.safeText }, targetLanguage)
        dialogueEntries.forEachIndexed { index, entry ->
            val translated = translatedCaptions[index]
            val restored = if (entry.protectedTokens.any { (marker, _) -> !translated.contains(marker) }) {
                entry.originalText
            } else {
                entry.protectedTokens.fold(translated) { value, (marker, original) -> value.replace(marker, original) }
            }
            entry.fields[entry.textIndex] = restored
        }
        return parsedEntries.joinToString("\n") { item ->
            when (item) {
                is String -> item
                is AssDialogueEntry -> item.linePrefix + item.fields.joinToString(",")
                else -> ""
            }
        }
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
                    response.inputStream?.use {
                        it.readBoundedBytes(MAX_TRANSLATION_HTML_BYTES).toString(Charsets.UTF_8)
                    } ?: error("Translation response was empty")
                }
                val resultHtml = RESULT_CONTAINER_REGEX.find(page)?.groupValues?.get(1)
                    ?: error("Translation result was missing")
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

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val chars = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val v = byte.toInt() and 0xFF
            chars[index * 2] = HEX_DIGITS[v ushr 4]
            chars[index * 2 + 1] = HEX_DIGITS[v and 0x0F]
        }
        return String(chars)
    }

    private fun pruneSubtitleCache() {
        runCatching {
            val now = System.currentTimeMillis()
            val files = translatedSubtitleDirectory.listFiles()?.filter(File::isFile).orEmpty()
            val kept = ArrayList<File>(files.size)
            var totalBytes = 0L
            for (file in files) {
                if (now - file.lastModified() > MAX_SUBTITLE_CACHE_AGE_MS) {
                    file.delete()
                } else {
                    kept += file
                    totalBytes += file.length()
                }
            }
            if (kept.size <= MAX_SUBTITLE_CACHE_FILES && totalBytes <= MAX_SUBTITLE_CACHE_BYTES) return
            kept.sortBy(File::lastModified)
            var remainingCount = kept.size
            for (file in kept) {
                if (remainingCount <= MAX_SUBTITLE_CACHE_FILES && totalBytes <= MAX_SUBTITLE_CACHE_BYTES) break
                val size = file.length()
                if (file.delete()) {
                    remainingCount--
                    totalBytes -= size
                }
            }
        }
    }

    private data class AssDialogueEntry(
        val linePrefix: String,
        val fields: MutableList<String>,
        val textIndex: Int,
        val originalText: String,
        val safeText: String,
        val protectedTokens: List<Pair<String, String>>
    )
    private data class CueBlock(val raw: String, val cue: CueContent?)
    private data class CueContent(val headerLines: List<String>, val caption: String)

    private companion object {
        const val MAX_SUBTITLE_BYTES = 4 * 1024 * 1024
        const val MAX_TRANSLATION_HTML_BYTES = 2 * 1024 * 1024
        const val MAX_SUBTITLE_CACHE_FILES = 60
        const val MAX_SUBTITLE_CACHE_BYTES = 10L * 1024 * 1024
        const val MAX_SUBTITLE_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000
        val HEX_DIGITS = "0123456789abcdef".toCharArray()
        val BLANK_LINE_REGEX = Regex("\n[ \t]*\n+")
        val ASS_TAG_REGEX = Regex("\\{[^}]*}|\\\\[Nn]")
        val RESULT_CONTAINER_REGEX = Regex(
            "<div[^>]*class=[\"'][^\"']*result-container[^\"']*[\"'][^>]*>(.*?)</div>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}
