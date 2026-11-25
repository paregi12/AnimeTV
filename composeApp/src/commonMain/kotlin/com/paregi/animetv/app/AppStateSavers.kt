package com.paregi.animetv.app

import androidx.compose.runtime.saveable.listSaver
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.Episode
import com.paregi.animetv.domain.SourceCatalog

private const val SAVER_SEPARATOR = "\u001F"

internal val animeStateSaver = listSaver<Anime?, String>(
    save = { anime ->
        if (anime == null) listOf("null") else listOf(
            "anime",
            anime.id,
            anime.title,
            anime.originalTitle.orEmpty(),
            anime.titleAliases.joinToString(SAVER_SEPARATOR),
            anime.posterUrl.orEmpty(),
            anime.bannerUrl.orEmpty(),
            anime.trailerId.orEmpty(),
            anime.synopsis.orEmpty(),
            anime.genres.joinToString(SAVER_SEPARATOR),
            anime.year?.toString().orEmpty(),
            anime.score?.toString().orEmpty(),
            anime.status.orEmpty(),
            anime.duration.orEmpty(),
            anime.rating.orEmpty(),
            anime.episodeCount?.toString().orEmpty(),
            anime.format.orEmpty(),
            anime.adult.toString(),
            anime.latestSubEpisode.toString(),
            anime.latestDubEpisode.toString(),
            anime.malId.orEmpty(),
            anime.aniListId.orEmpty(),
            anime.sourceIndex.toString()
        )
    },
    restore = { values ->
        if (values.firstOrNull() != "anime" || values.size != 23) null else Anime(
            id = values[1],
            title = values[2],
            originalTitle = values[3].takeIf(String::isNotEmpty),
            titleAliases = values[4].split(SAVER_SEPARATOR).filter(String::isNotEmpty),
            posterUrl = values[5].takeIf(String::isNotEmpty),
            bannerUrl = values[6].takeIf(String::isNotEmpty),
            trailerId = values[7].takeIf(String::isNotEmpty),
            synopsis = values[8].takeIf(String::isNotEmpty),
            genres = values[9].split(SAVER_SEPARATOR).filter(String::isNotEmpty),
            year = values[10].toIntOrNull(),
            score = values[11].toFloatOrNull(),
            status = values[12].takeIf(String::isNotEmpty),
            duration = values[13].takeIf(String::isNotEmpty),
            rating = values[14].takeIf(String::isNotEmpty),
            episodeCount = values[15].toIntOrNull(),
            format = values[16].takeIf(String::isNotEmpty),
            adult = values[17] == "true",
            latestSubEpisode = values[18].toIntOrNull() ?: 0,
            latestDubEpisode = values[19].toIntOrNull() ?: 0,
            malId = values[20].takeIf(String::isNotEmpty),
            aniListId = values[21].takeIf(String::isNotEmpty),
            sourceIndex = values[22].toIntOrNull() ?: SourceCatalog.GOTAKU_INDEX
        )
    }
)

internal val episodeStateSaver = listSaver<Episode?, String>(
    save = { episode ->
        if (episode == null) listOf("null") else listOf(
            "episode",
            episode.id,
            episode.animeId,
            episode.number,
            episode.title.orEmpty(),
            episode.watchedPositionMs.toString(),
            episode.durationMs.toString(),
            episode.hasSub.toString(),
            episode.hasDub.toString(),
            episode.filler.toString()
        )
    },
    restore = { values ->
        if (values.firstOrNull() != "episode" || values.size != 10) null else Episode(
            id = values[1],
            animeId = values[2],
            number = values[3],
            title = values[4].takeIf(String::isNotEmpty),
            watchedPositionMs = values[5].toLongOrNull() ?: 0L,
            durationMs = values[6].toLongOrNull() ?: 0L,
            hasSub = values[7] == "true",
            hasDub = values[8] == "true",
            filler = values[9] == "true"
        )
    }
)

internal val episodePositionSaver = listSaver<Pair<String, Long>?, String>(
    save = { position ->
        if (position == null) listOf("null") else listOf("position", position.first, position.second.toString())
    },
    restore = { values ->
        if (values.size != 3 || values[0] != "position") null
        else values[1] to (values[2].toLongOrNull() ?: 0L)
    }
)

