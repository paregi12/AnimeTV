package com.paregi.animetv

import android.content.Context
import android.content.SharedPreferences
import com.paregi.animetv.domain.AppSettings
import com.paregi.animetv.domain.DEFAULT_HOME_SECTIONS
import com.paregi.animetv.domain.DEFAULT_MY_LIST_SECTIONS
import com.paregi.animetv.domain.HOME_SECTION_IDS
import com.paregi.animetv.domain.HomeLayout
import com.paregi.animetv.domain.HomeSectionPreference
import com.paregi.animetv.domain.MY_LIST_SECTION_IDS
import com.paregi.animetv.domain.MyListLayout
import com.paregi.animetv.domain.MyListSectionPreference
import com.paregi.animetv.domain.ProfileAppearance
import com.paregi.animetv.domain.SettingsRepository
import com.paregi.animetv.domain.SourceCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the previous preference file and key names in this new app's data space,
 * so the settings schema stays aligned while the UI is being migrated.
 */
class AndroidSettingsRepository(
    context: Context,
    private val networkStack: AndroidNetworkStack,
    private val namespace: ProfileNamespace
) : SettingsRepository {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences("SERVER", Context.MODE_PRIVATE)

    override suspend fun readSettings(): AppSettings {
        val config = runCatching {
            JSONObject(preferences.getString(namespace.storeKey("pb_cfg"), "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        val performance = config.optJSONArray("performance")?.let { values ->
            (0 until values.length()).map { values.optBoolean(it) }
        } ?: listOf(true, false, true, true, false)
        val subtitleVariants = mutableMapOf<String, String>()
        config.optJSONObject("subvariants")?.let { values ->
            val keys = values.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                values.optString(key).takeIf { it.isNotBlank() }?.let { subtitleVariants[key] = it }
            }
        }
        val wallpaper = config.optJSONObject("bgimg")

        val sourceDomains = mutableMapOf<Int, String>()
        for (source in SourceCatalog.sources) {
            val key = namespace.storeKey("sdomain_${source.index}")
            val directKey = "sdomain_${source.index}"
            val domain = preferences.getString(key, null) ?: preferences.getString(directKey, null)
            if (!domain.isNullOrBlank()) {
                sourceDomains[source.index] = domain
            }
        }

        val settings = AppSettings(
            selectedSource = preferences.getInt("source-domain", 9),
            sourceDomains = sourceDomains,
            theme = preferences.getString(namespace.storeKey("theme"), "theme_dark") ?: "theme_dark",
            subtitleLanguage = config.optString("lang", ""),
            subtitleVariant = config.optString("subvariant", ""),
            subtitleVariants = subtitleVariants,
            audioLanguage = config.optString("alang", ""),
            subtitleStyle = config.optInt("ccstyle", 0),
            autoNext = config.optBoolean("autonext", true),
            autoSkipIntro = config.optBoolean("autoskip", false),
            skipFiller = config.optBoolean("skipfiller", false),
            disableTouchGestures = config.optBoolean("disablegesture", false),
            html5Player = config.optBoolean("html5player", false),
            preferDub = config.optBoolean("dubaudio", false),
            preloadNextEpisode = config.optBoolean("preloadep", true),
            hlsProxy = config.optBoolean("hlsproxy", false),
            japaneseTitles = config.optBoolean("jptitle", false),
            progressiveCache = config.optBoolean("progcache", true),
            useImageCdn = (preferences.getString("viewstorage_imgcdnl", "1") ?: "1") == "1",
            useDnsOverHttps = config.optBoolean("usedoh", true),
            includeChineseAnime = config.optBoolean("nonjapan", false),
            aniListSlideshow = config.optBoolean("alisthomess", true),
            highResolutionBanners = config.optBoolean("alisthqbanner", false),
            homeWatchlist = config.optBoolean("homylist", false),
            navigationSound = config.optBoolean("clksound", true),
            showClock = config.optBoolean("showclock", true),
            directPageSwitch = config.optBoolean("directpage", true),
            directSidebar = config.optBoolean("directsidebar", true),
            largeUi = config.optBoolean("largeui", false),
            fontSize = config.optInt("uifontsize", 2).coerceIn(0, 3),
            playbackQuality = config.optInt("quality", 0).coerceIn(0, 3),
            videoScale = config.optInt("scale", 0).coerceIn(0, 2),
            seekSeconds = config.optInt("seekvalue", 10).coerceIn(2, 20),
            videoBuffer = config.optInt("videobuffer", 2).coerceIn(0, 4),
            httpClient = config.optInt("httpclient", 0).coerceIn(0, 2),
            mirrorServer = config.optInt("mirrorserver", 0),
            animation = config.optInt("animation", 0).coerceIn(0, 3),
            performanceEffects = performance,
            trailerMode = config.optInt("trailer", 1).coerceIn(0, 2),
            listProgressMode = config.optInt("listprog", 0).coerceIn(0, 4),
            closeConfirmation = config.optInt("closeconfirm", 0).coerceIn(0, 2),
            exitMode = (preferences.getString("viewstorage_exitmode", "0") ?: "0")
                .toIntOrNull()?.coerceIn(0, 4) ?: 0,
            loginScreenStyle = readLoginScreenStyle(),
            malTrackingDialog = config.optInt("maltrackdialog", 0).coerceIn(0, 4),
            cacheSizeMb = preferences.getInt("cache-size", 100).coerceIn(5, 150),
            wallpaperUrl = wallpaper?.optString("src", "").orEmpty(),
            wallpaperTitle = wallpaper?.optString("title", "").orEmpty(),
            mobileNavStyle = config.optInt("mobilenav", 0).coerceIn(0, 1),
            mobileNavGlow = config.optBoolean("mobilenav_glow", true),
            cardSize = config.optInt("card_size", 0).coerceIn(0, 2),
            cardRadius = config.optInt("card_radius", 0).coerceIn(0, 2),
            badgeStyle = config.optInt("badge_style", 0).coerceIn(0, 1)
        )
        networkStack.configure(settings.httpClient, settings.useDnsOverHttps, settings.cacheSizeMb)
        return settings
    }

    override suspend fun updateSettings(settings: AppSettings) {
        val config = runCatching {
            JSONObject(preferences.getString(namespace.storeKey("pb_cfg"), "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        val wallpaper = config.optJSONObject("bgimg") ?: JSONObject()
        wallpaper.put("src", settings.wallpaperUrl).put("title", settings.wallpaperTitle)
        config
            .put("lang", settings.subtitleLanguage)
            .put("subvariant", settings.subtitleVariant)
            .put("subvariants", JSONObject(settings.subtitleVariants))
            .put("alang", settings.audioLanguage)
            .put("ccstyle", settings.subtitleStyle)
            .put("autonext", settings.autoNext)
            .put("autoskip", settings.autoSkipIntro)
            .put("skipfiller", settings.skipFiller)
            .put("disablegesture", settings.disableTouchGestures)
            .put("html5player", settings.html5Player)
            .put("dubaudio", settings.preferDub)
            .put("preloadep", settings.preloadNextEpisode)
            .put("hlsproxy", settings.hlsProxy)
            .put("jptitle", settings.japaneseTitles)
            .put("progcache", settings.progressiveCache)
            .put("usedoh", settings.useDnsOverHttps)
            .put("nonjapan", settings.includeChineseAnime)
            .put("alisthomess", settings.aniListSlideshow)
            .put("alisthqbanner", settings.highResolutionBanners)
            .put("homylist", settings.homeWatchlist)
            .put("clksound", settings.navigationSound)
            .put("showclock", settings.showClock)
            .put("directpage", settings.directPageSwitch)
            .put("directsidebar", settings.directSidebar)
            .put("largeui", settings.largeUi)
            .put("uifontsize", settings.fontSize)
            .put("quality", settings.playbackQuality)
            .put("scale", settings.videoScale)
            .put("seekvalue", settings.seekSeconds)
            .put("videobuffer", settings.videoBuffer)
            .put("httpclient", settings.httpClient)
            .put("mirrorserver", settings.mirrorServer)
            .put("animation", settings.animation)
            .put("performance", JSONArray(settings.performanceEffects))
            .put("trailer", settings.trailerMode)
            .put("listprog", settings.listProgressMode)
            .put("closeconfirm", settings.closeConfirmation)
            .put("maltrackdialog", settings.malTrackingDialog)
            .put("mobilenav", settings.mobileNavStyle)
            .put("mobilenav_glow", settings.mobileNavGlow)
            .put("card_size", settings.cardSize)
            .put("card_radius", settings.cardRadius)
            .put("badge_style", settings.badgeStyle)
            .put("bgimg", wallpaper)

        val editor = preferences.edit()
            .putString(namespace.storeKey("pb_cfg"), config.toString())
            .putString(namespace.storeKey("theme"), settings.theme)
            .putInt("source-domain", settings.selectedSource)
            .putInt("cache-size", settings.cacheSizeMb)
            .putString("viewstorage_exitmode", settings.exitMode.coerceIn(0, 4).toString())
            .putString("viewstorage_loginstyle", settings.loginScreenStyle.coerceIn(0, 2).toString())
            .putString("viewstorage_imgcdnl", if (settings.useImageCdn) "1" else "0")

        for (source in SourceCatalog.sources) {
            val domain = settings.sourceDomains[source.index]
            val key = namespace.storeKey("sdomain_${source.index}")
            val directKey = "sdomain_${source.index}"
            if (!domain.isNullOrBlank()) {
                editor.putString(key, domain)
                editor.putString(directKey, domain)
            } else {
                editor.remove(key)
                editor.remove(directKey)
            }
        }
        editor.apply()
        networkStack.configure(settings.httpClient, settings.useDnsOverHttps, settings.cacheSizeMb)
    }

    override suspend fun readLoginScreenStyle(): Int = withContext(Dispatchers.IO) {
        (preferences.getString("viewstorage_loginstyle", "1") ?: "1")
            .toIntOrNull()?.coerceIn(0, 2) ?: 1
    }

    override suspend fun readProfileAppearance(prefix: String): ProfileAppearance = withContext(Dispatchers.IO) {
        val keyPrefix = "viewstorage_$prefix"
        val theme = preferences.getString("${keyPrefix}theme", "theme_dark") ?: "theme_dark"
        val config = runCatching {
            JSONObject(preferences.getString("${keyPrefix}pb_cfg", "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        val wallpaperUrl = config.optJSONObject("bgimg")?.optString("src").orEmpty()
        ProfileAppearance(theme = theme, wallpaperUrl = wallpaperUrl)
    }

    override suspend fun readMyListLayout(): MyListLayout = withContext(Dispatchers.IO) {
        val data = runCatching {
            JSONArray(preferences.getString(namespace.storeKey("_listorder_mylist"), "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        if (data.length() == 0) return@withContext MyListLayout.default()
        val parsed = (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONArray(index) ?: return@mapNotNull null
            val savedId = item.optString(0)
            val id = if (savedId == "watchlist") "fav" else savedId
            MyListSectionPreference(
                id = id,
                enabled = item.optBoolean(1, id in DEFAULT_MY_LIST_SECTIONS)
            )
        }
        MyListLayout(normalizeMyListSections(parsed))
    }

    override suspend fun saveMyListLayout(layout: MyListLayout) {
        withContext(Dispatchers.IO) {
            val sections = normalizeMyListSections(layout.sections)
            val encoded = JSONArray().apply {
                sections.forEach { put(JSONArray().put(it.id).put(it.enabled)) }
            }
            preferences.edit()
                .putString(namespace.storeKey("_listorder_mylist"), encoded.toString())
                .apply()
        }
    }

    override suspend fun readHomeLayout(): HomeLayout = withContext(Dispatchers.IO) {
        val data = runCatching {
            JSONArray(preferences.getString(namespace.storeKey("_listorder_home"), "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        if (data.length() == 0) return@withContext HomeLayout.default()
        val parsed = (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONArray(index) ?: return@mapNotNull null
            val savedId = item.optString(0)
            val id = if (savedId == "top") "popular" else savedId
            HomeSectionPreference(
                id = id,
                enabled = item.optBoolean(1, id in DEFAULT_HOME_SECTIONS)
            )
        }
        HomeLayout(normalizeHomeSections(parsed))
    }

    override suspend fun saveHomeLayout(layout: HomeLayout) {
        withContext(Dispatchers.IO) {
            val sections = normalizeHomeSections(layout.sections)
            val encoded = JSONArray().apply {
                sections.forEach { put(JSONArray().put(it.id).put(it.enabled)) }
            }
            preferences.edit()
                .putString(namespace.storeKey("_listorder_home"), encoded.toString())
                .apply()
        }
    }

    private fun normalizeMyListSections(
        raw: List<MyListSectionPreference>
    ): List<MyListSectionPreference> {
        val disabledByExclusion = mutableSetOf<String>()
        val normalized = mutableListOf<MyListSectionPreference>()
        for (item in raw) {
            val id = if (item.id == "watchlist") "fav" else item.id
            if (id !in MY_LIST_SECTION_IDS) continue
            val existingIndex = normalized.indexOfFirst { it.id == id }
            if (existingIndex < 0) {
                val enabled = item.enabled && id !in disabledByExclusion
                normalized += MyListSectionPreference(id, enabled)
                if (enabled) MY_LIST_MUTUAL_EXCLUSIONS[id]?.let(disabledByExclusion::add)
            } else if (item.enabled && !normalized[existingIndex].enabled && id !in disabledByExclusion) {
                normalized[existingIndex] = normalized[existingIndex].copy(enabled = true)
                MY_LIST_MUTUAL_EXCLUSIONS[id]?.let(disabledByExclusion::add)
            }
        }
        val missing = MY_LIST_SECTION_IDS
            .filterNot { id -> normalized.any { it.id == id } }
            .map { id ->
                val enabled = id in DEFAULT_MY_LIST_SECTIONS && id !in disabledByExclusion
                if (enabled) MY_LIST_MUTUAL_EXCLUSIONS[id]?.let(disabledByExclusion::add)
                MyListSectionPreference(id, enabled)
            }
        return normalized + missing
    }

    private fun normalizeHomeSections(
        raw: List<HomeSectionPreference>
    ): List<HomeSectionPreference> {
        val normalized = mutableListOf<HomeSectionPreference>()
        for (item in raw) {
            val id = if (item.id == "top") "popular" else item.id
            if (id !in HOME_SECTION_IDS) continue
            val existingIndex = normalized.indexOfFirst { it.id == id }
            if (existingIndex < 0) {
                normalized += item.copy(id = id)
            } else if (item.enabled && !normalized[existingIndex].enabled) {
                normalized[existingIndex] = normalized[existingIndex].copy(enabled = true)
            }
        }
        val missing = HOME_SECTION_IDS
            .filterNot { id -> normalized.any { it.id == id } }
            .map { id -> HomeSectionPreference(id = id, enabled = id in DEFAULT_HOME_SECTIONS) }
        return normalized + missing
    }

    override suspend fun clearCache() {
        networkStack.clearCache()
        withContext(Dispatchers.IO) {
            clearPlatformPlaybackCache()
            val playbackCache = File(appContext.cacheDir, "playback-cache")
            check(!playbackCache.exists() || playbackCache.deleteRecursively() || !playbackCache.exists()) {
                "Could not remove the playback cache"
            }
            val translatedSubtitleCache = File(appContext.cacheDir, "translated-subtitles")
            check(!translatedSubtitleCache.exists() || translatedSubtitleCache.deleteRecursively() || !translatedSubtitleCache.exists()) {
                "Could not remove the translated subtitle cache"
            }
        }
    }

    private companion object {
        val MY_LIST_MUTUAL_EXCLUSIONS = mapOf(
            "maltab" to "mal",
            "mal" to "maltab",
            "anilisttab" to "anilist",
            "anilist" to "anilisttab"
        )
    }
}

