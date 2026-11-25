package com.paregi.animetv.domain

data class DomainBenchmarkResult(
    val domain: String,
    val latencyMs: Long = -1,
    val success: Boolean = false,
    val errorMessage: String? = null
)

data class AnimeSource(
    val index: Int,
    val domain: String,
    val displayName: String,
    val mirrors: List<String> = listOf(domain),
    val benchmarkPath: String = "",
    val benchmarkKey: String = ""
)

object SourceCatalog {
    const val GOTAKU_INDEX = 9
    const val REANIME_INDEX = 10

    val sources = listOf(
        AnimeSource(
            index = GOTAKU_INDEX,
            domain = "gotaku.to",
            displayName = "Gotaku",
            mirrors = listOf("gotaku.to"),
            benchmarkPath = "/api/v1/titles?limit=1",
            benchmarkKey = "data"
        ),
        AnimeSource(
            index = REANIME_INDEX,
            domain = "reanime.to",
            displayName = "Re:ANIME",
            mirrors = listOf("reanime.to"),
            benchmarkPath = "/api/v1/search?limit=1",
            benchmarkKey = "results"
        )
    )

    val defaultSource: AnimeSource = sources.first { it.index == GOTAKU_INDEX }

    fun find(index: Int): AnimeSource? = sources.firstOrNull { it.index == index }

    fun normalize(index: Int): Int = find(index)?.index ?: defaultSource.index

    fun activeDomain(index: Int, customDomain: String?): String {
        return customDomain?.trim()?.takeIf { it.isNotBlank() } ?: find(index)?.domain ?: defaultSource.domain
    }
}
