package com.paregi.animetv

import android.app.ActivityManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var profileRepository: AndroidProfileRepository
    private lateinit var settingsRepository: AndroidSettingsRepository
    private lateinit var animeRepository: AndroidAnimeRepository
    private lateinit var libraryRepository: AndroidLibraryRepository
    private lateinit var searchHistoryRepository: AndroidSearchHistoryRepository
    private lateinit var accountRepository: AndroidAccountRepository
    private lateinit var tvIntegrationRepository: AndroidTvIntegrationRepository
    private lateinit var translationRepository: AndroidTranslationRepository
    private lateinit var debugLogRepository: AndroidDebugLogRepository
    private lateinit var appUpdateRepository: AndroidAppUpdateRepository
    private lateinit var streamProxy: AndroidStreamProxy
    private lateinit var networkStack: AndroidNetworkStack
    private lateinit var profileNamespace: ProfileNamespace

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemUi(resources.configuration.orientation)
        debugLogRepository = AndroidDebugLogRepository.get(applicationContext)
        networkStack = AndroidNetworkStack(applicationContext)
        profileNamespace = ProfileNamespace()
        profileRepository = AndroidProfileRepository(applicationContext, profileNamespace)
        settingsRepository = AndroidSettingsRepository(applicationContext, networkStack, profileNamespace)
        streamProxy = AndroidStreamProxy(networkStack)
        translationRepository = AndroidTranslationRepository(applicationContext, networkStack)
        animeRepository = AndroidAnimeRepository(
            streamProxy = streamProxy,
            networkStack = networkStack,
            translationRepository = translationRepository
        )
        libraryRepository = AndroidLibraryRepository(applicationContext, profileNamespace)
        searchHistoryRepository = AndroidSearchHistoryRepository(applicationContext, profileNamespace)
        accountRepository = AndroidAccountRepository(applicationContext, networkStack, profileNamespace)
        tvIntegrationRepository = AndroidTvIntegrationRepository(applicationContext)
        appUpdateRepository = AndroidAppUpdateRepository(applicationContext, networkStack, this)
        tvIntegrationRepository.handleLaunchIntent(intent)
        TvChannelRefreshJobService.refreshNow(applicationContext)
        setContent {
            AnimeTvApp(
                profileRepository,
                settingsRepository,
                animeRepository,
                libraryRepository,
                searchHistoryRepository,
                accountRepository,
                tvIntegrationRepository,
                translationRepository,
                debugLogRepository,
                appUpdateRepository,
                onExitApplication = { finish() }
            )
        }
        window.decorView.post { preferHighestSupportedRefreshRate() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemUi(newConfig.orientation)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tvIntegrationRepository.handleLaunchIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            applySystemUi(Configuration.ORIENTATION_LANDSCAPE)
        }
    }

    override fun onDestroy() {
        if (isFinishing) debugLogRepository.onHostFinished()
        streamProxy.close()
        networkStack.close()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun applySystemUi(orientation: Int) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            window.addFlags(
                WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun preferHighestSupportedRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN) ||
            (getSystemService(ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        ) return

        val display = window.decorView.display ?: return
        val currentMode = display.mode
        val highestMode = display.supportedModes
            .asSequence()
            .filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight &&
                    it.refreshRate >= currentMode.refreshRate
            }
            .maxByOrNull { it.refreshRate }
            ?: return
        if (highestMode.modeId != currentMode.modeId) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = highestMode.modeId
            }
        }
    }
}

