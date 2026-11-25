package com.paregi.animetv.source

import com.paregi.animetv.domain.SourceCatalog
import java.util.concurrent.ConcurrentHashMap

/** A stable provider snapshot used for the full lifetime of each repository request. */
internal class CatalogSource private constructor(
    val index: Int,
    val domain: String,
    val displayName: String
) {
    val isReAnime: Boolean get() = index == SourceCatalog.REANIME_INDEX
    val root: String get() = "https://$domain"

    companion object {
        fun fromIndex(index: Int, customDomain: String? = null): CatalogSource {
            val definition = SourceCatalog.find(index) ?: SourceCatalog.defaultSource
            val domain = SourceCatalog.activeDomain(definition.index, customDomain)
            return CatalogSource(definition.index, domain, definition.displayName)
        }
    }
}

/** Owns the process-wide source choice while callers use immutable request snapshots. */
internal class CatalogSourceSelector(
    initialIndex: Int = SourceCatalog.defaultSource.index,
    initialDomains: Map<Int, String> = emptyMap()
) {
    private val customDomains = ConcurrentHashMap<Int, String>(initialDomains)

    @Volatile
    private var selected = CatalogSource.fromIndex(initialIndex, customDomains[initialIndex])

    fun select(index: Int, domain: String? = null): Int {
        if (domain != null) {
            if (domain.isBlank()) {
                customDomains.remove(index)
            } else {
                customDomains[index] = domain.trim()
            }
        }
        selected = CatalogSource.fromIndex(index, customDomains[index])
        return selected.index
    }

    fun setDomain(index: Int, domain: String?) {
        if (domain.isNullOrBlank()) {
            customDomains.remove(index)
        } else {
            customDomains[index] = domain.trim()
        }
        if (selected.index == index) {
            selected = CatalogSource.fromIndex(index, customDomains[index])
        }
    }

    fun snapshot(): CatalogSource = selected
}
