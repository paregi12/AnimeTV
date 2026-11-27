package com.paregi.animetv.domain

import androidx.compose.runtime.Immutable

@Immutable
data class ProfileAppearance(
    val theme: String = "theme_dark",
    val wallpaperUrl: String = ""
)

@Immutable
data class MyListSectionPreference(
    val id: String,
    val enabled: Boolean = true
)

val MY_LIST_SECTION_IDS = listOf(
    "maltab",
    "mal",
    "malplan",
    "anilisttab",
    "anilist",
    "anilistplan",
    "fav",
    "history"
)

val DEFAULT_MY_LIST_SECTIONS = listOf(
    "maltab",
    "anilisttab",
    "fav",
    "history"
)

@Immutable
data class MyListLayout(val sections: List<MyListSectionPreference>) {
    companion object {
        fun default() = MyListLayout(
            MY_LIST_SECTION_IDS.map { id ->
                MyListSectionPreference(
                    id = id,
                    enabled = id in DEFAULT_MY_LIST_SECTIONS
                )
            }
        )
    }
}

@Immutable
data class HomeSectionPreference(
    val id: String,
    val enabled: Boolean = false
)

val HOME_SECTION_IDS = listOf(
    "recent",
    "trending",
    "popular",
    "alrecent",
    "altab",
    "altop",
    "alpopular",
    "alyear",
    "alupcoming"
)

val DEFAULT_HOME_SECTIONS = listOf(
    "recent",
    "trending",
    "popular"
)

@Immutable
data class HomeLayout(val sections: List<HomeSectionPreference>) {
    companion object {
        fun default() = HomeLayout(
            HOME_SECTION_IDS.map { id ->
                HomeSectionPreference(
                    id = id,
                    enabled = id in DEFAULT_HOME_SECTIONS
                )
            }
        )
    }
}

@Immutable
data class AppSettings(
    val selectedSource: Int = SourceCatalog.GOTAKU_INDEX,
    val sourceDomains: Map<Int, String> = emptyMap(),
    val theme: String = "theme_dark",
    val subtitleLanguage: String = "",
    val subtitleVariant: String = "",
    val subtitleVariants: Map<String, String> = emptyMap(),
    val audioLanguage: String = "",
    val subtitleStyle: Int = 0,
    val autoNext: Boolean = true,
    val autoSkipIntro: Boolean = false,
    val skipFiller: Boolean = false,
    val disableTouchGestures: Boolean = false,
    val preferDub: Boolean = false,
    val preloadNextEpisode: Boolean = true,
    val hlsProxy: Boolean = false,
    val japaneseTitles: Boolean = false,
    val progressiveCache: Boolean = true,
    val useImageCdn: Boolean = true,
    val useDnsOverHttps: Boolean = true,
    val includeChineseAnime: Boolean = false,
    val aniListSlideshow: Boolean = true,
    val highResolutionBanners: Boolean = false,
    val homeWatchlist: Boolean = false,
    val navigationSound: Boolean = true,
    val showClock: Boolean = true,
    val directPageSwitch: Boolean = true,
    val directSidebar: Boolean = true,
    val largeUi: Boolean = false,
    val fontSize: Int = 2,
    val playbackQuality: Int = 0,
    val videoScale: Int = 0,
    val seekSeconds: Int = 10,
    val videoBuffer: Int = 2,
    val httpClient: Int = 0,
    val mirrorServer: Int = 0,
    val animation: Int = 0,
    val performanceEffects: List<Boolean> = listOf(true, false, true, true, false),
    val trailerMode: Int = 1,
    val listProgressMode: Int = 0,
    val closeConfirmation: Int = 0,
    val exitMode: Int = 0,
    val loginScreenStyle: Int = 1,
    val malTrackingDialog: Int = 0,
    val cacheSizeMb: Int = 100,
    val wallpaperUrl: String = "",
    val wallpaperTitle: String = "",
    val mobileNavStyle: Int = 0,
    val mobileNavGlow: Boolean = true,
    val cardSize: Int = 0,
    val cardRadius: Int = 0,
    val badgeStyle: Int = 0
)
