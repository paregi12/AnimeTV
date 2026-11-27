package com.paregi.animetv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.paregi.animetv.core.ui.AboutPopupDialog
import com.paregi.animetv.core.ui.ConfirmDialog
import com.paregi.animetv.core.ui.ListOrderDialog
import com.paregi.animetv.core.ui.ListOrderItem
import com.paregi.animetv.core.ui.ListOrderMode
import com.paregi.animetv.domain.AnimeRepository
import com.paregi.animetv.domain.AppSettings
import com.paregi.animetv.domain.DomainBenchmarkResult
import com.paregi.animetv.domain.SourceCatalog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun SourceMirrorBenchmarkDialog(
    sourceIndex: Int,
    settings: AppSettings,
    animeRepository: AnimeRepository,
    onDismiss: () -> Unit,
    onSelectDomain: (String) -> Unit
) {
    val source = SourceCatalog.find(sourceIndex) ?: SourceCatalog.defaultSource
    val candidates = remember(sourceIndex, settings.sourceDomains[sourceIndex]) {
        buildList {
            addAll(source.mirrors)
            settings.sourceDomains[sourceIndex]?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()
    }

    var benchmarkResults by remember(sourceIndex, candidates) {
        mutableStateOf<Map<String, DomainBenchmarkResult>>(emptyMap())
    }
    var warningDomain by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sourceIndex, candidates) {
        benchmarkResults = emptyMap()
        coroutineScope {
            candidates.forEach { domain ->
                launch {
                    val result = withTimeoutOrNull(4_000L) {
                        animeRepository.benchmarkDomain(sourceIndex, domain)
                    } ?: DomainBenchmarkResult(
                        domain = domain,
                        latencyMs = 4_000L,
                        success = false,
                        errorMessage = "Timeout"
                    )
                    benchmarkResults = benchmarkResults + (domain to result)
                }
            }
        }
    }

    if (candidates.isEmpty()) {
        AboutPopupDialog(
            visible = true,
            loadingText = "No mirrors available",
            onDismiss = onDismiss
        )
        return
    }

    val failedDomain = warningDomain
    if (failedDomain != null) {
        ConfirmDialog(
            title = "Domain Warning!!",
            message = "Target Domain <b>$failedDomain</b> is failed in benchmark!!!<br>Do you want to continue?",
            yesLabel = "YES",
            noLabel = "NO",
            onConfirm = {
                warningDomain = null
                onSelectDomain(failedDomain)
            },
            onDismiss = { warningDomain = null }
        )
        return
    }

    val fastestIndex = remember(candidates, benchmarkResults) {
        var bestIdx = 0
        var bestLatency = Long.MAX_VALUE
        candidates.forEachIndexed { idx, domain ->
            val result = benchmarkResults[domain]
            if (result != null && result.success && result.latencyMs < bestLatency) {
                bestLatency = result.latencyMs
                bestIdx = idx
            }
        }
        bestIdx
    }

    val items = remember(candidates, benchmarkResults, fastestIndex) {
        candidates.mapIndexed { idx, domain ->
            val result = benchmarkResults[domain]
            val valueText = when {
                result == null -> "Testing..."
                result.success -> "OK - ${result.latencyMs}ms"
                else -> "ERROR"
            }
            ListOrderItem(
                id = domain,
                title = domain,
                value = valueText,
                selected = idx == fastestIndex
            )
        }
    }

    val benchmarking = benchmarkResults.size < candidates.size
    ListOrderDialog(
        title = if (benchmarking) "Select Source Domain (Benchmarking...)" else "Select Source Domain",
        items = items,
        mode = ListOrderMode.LIST,
        initialSelectedIndex = fastestIndex,
        onDismiss = onDismiss,
        onSelect = { _, item ->
            val result = benchmarkResults[item.id]
            if (result != null && !result.success) {
                warningDomain = item.id
            } else {
                onSelectDomain(item.id)
            }
        }
    )
}
