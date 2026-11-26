package com.paregi.animetv.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

data class AnimeTvThemeColors(
    val primaryBg: Color,
    val primary1: Color,
    val primary2: Color,
    val primary3: Color,
    val accent1: Color,
    val dimm1: Color,
    val loginBg: Color
) {
    companion object {
        val Default = AnimeTvThemeColors(
            primaryBg = Color(0xFF030004),
            primary1 = Color(0xFF440066),
            primary2 = Color(0xFF660088),
            primary3 = Color(0xFF8800AA),
            accent1 = Color(0xFFCC55FF),
            dimm1 = Color(0xFF223344),
            loginBg = Color(0xFF330055)
        )
    }
}

val ANIME_TV_THEME_IDS: List<Pair<String, String>> = listOf(
    "theme_purple" to "Purple",
    "theme_blue" to "Blue",
    "theme_teal" to "Teal",
    "theme_green" to "Green",
    "theme_brown" to "Brown",
    "theme_red" to "Red",
    "theme_grey" to "Grey",
    "theme_dark" to "Dark"
)

fun animeTvThemeColorsFor(theme: String): AnimeTvThemeColors = when (theme) {
    "theme_purple", "theme_violet" -> AnimeTvThemeColors.Default
    "theme_blue", "theme_ocean", "theme_arctic_blue" -> AnimeTvThemeColors(
        primaryBg = Color(0xFF000003),
        primary1 = Color(0xFF001144),
        primary2 = Color(0xFF002266),
        primary3 = Color(0xFF224488),
        accent1 = Color(0xFF4477BB),
        dimm1 = Color(0xFF223344),
        loginBg = Color(0xFF000839)
    )
    "theme_teal", "theme_jade" -> AnimeTvThemeColors(
        primaryBg = Color(0xFF000404),
        primary1 = Color(0xFF003333),
        primary2 = Color(0xFF005555),
        primary3 = Color(0xFF007777),
        accent1 = Color(0xFF00AAAA),
        dimm1 = Color(0xFF223344),
        loginBg = Color(0xFF002222)
    )
    "theme_green", "theme_emerald" -> AnimeTvThemeColors(
        primaryBg = Color(0xFF000300),
        primary1 = Color(0xFF004400),
        primary2 = Color(0xFF006600),
        primary3 = Color(0xFF008800),
        accent1 = Color(0xFF33BB33),
        dimm1 = Color(0xFF223344),
        loginBg = Color(0xFF003300)
    )
    "theme_brown", "theme_amber", "theme_gold" -> AnimeTvThemeColors(
        primaryBg = Color(0xFF080400),
        primary1 = Color(0xFF442200),
        primary2 = Color(0xFF664400),
        primary3 = Color(0xFF885522),
        accent1 = Color(0xFFBB7700),
        dimm1 = Color(0xFF223344),
        loginBg = Color(0xFF331100)
    )
    "theme_red", "theme_crimson", "theme_rose", "theme_rose_gold" -> AnimeTvThemeColors(
        primaryBg = Color(0xFF060000),
        primary1 = Color(0xFF440000),
        primary2 = Color(0xFF660000),
        primary3 = Color(0xFF882222),
        accent1 = Color(0xFFBB4444),
        dimm1 = Color(0xFF223344),
        loginBg = Color(0xFF330000)
    )
    "theme_grey", "theme_graphite", "theme_white" -> AnimeTvThemeColors(
        primaryBg = Color(0xFF080808),
        primary1 = Color(0xFF112233),
        primary2 = Color(0xFF223344),
        primary3 = Color(0xFF335588),
        accent1 = Color(0xFF9FD356),
        dimm1 = Color(0xFF223344),
        loginBg = Color(0xFF333333)
    )
    "theme_dark" -> AnimeTvThemeColors(
        primaryBg = Color(0xFF040404),
        primary1 = Color(0xFF004466),
        primary2 = Color(0xFF006688),
        primary3 = Color(0xFF2288AA),
        accent1 = Color(0xFFEE8833),
        dimm1 = Color(0xFF222222),
        loginBg = Color(0xFF22262F)
    )
    else -> AnimeTvThemeColors.Default
}

val LocalAnimeTvColors: ProvidableCompositionLocal<AnimeTvThemeColors> =
    compositionLocalOf { AnimeTvThemeColors.Default }

internal val Purple: Color @Composable get() = LocalAnimeTvColors.current.primary1
internal val PurpleLight: Color @Composable get() = LocalAnimeTvColors.current.accent1
internal val TextPrimary: Color @Composable get() = Color.White
internal val TextSecondary: Color @Composable get() = Color(0xFFAAAAAA)
internal val LocalPerformanceEffects = compositionLocalOf { listOf(true, false, true, true, false) }
internal val LocalJapaneseTitles = compositionLocalOf { false }
internal val LocalImageCdn = compositionLocalOf { true }
internal val LocalIconFont = compositionLocalOf<FontFamily> { FontFamily.Default }

data class BadgeThemeColors(
    val primaryBg: Color,
    val primary1: Color,
    val primary2: Color,
    val scoreBg: Color = Color(0xFF006600),
    val durationBg: Color = Color(0xFF000000),
    val adultBg: Color = Color(0xFF880000)
) {
    companion object {
        val Default = BadgeThemeColors(
            primaryBg = Color(0xFF030004),
            primary1 = Color(0xFF440066),
            primary2 = Color(0xFF660088)
        )
    }
}

fun badgeThemeColorsFor(theme: String): BadgeThemeColors {
    val colors = animeTvThemeColorsFor(theme)
    return BadgeThemeColors(
        primaryBg = colors.primaryBg,
        primary1 = colors.primary1,
        primary2 = colors.primary2
    )
}

internal object IconGlyphs {
    const val Home = "\uE9B2"
    const val Search = "\uE8B6"
    const val MyList = "\uEF6E"
    const val Schedule = "\uEBCC"
    const val Settings = "\uE8B8"
    const val Menu = "\uE5D2"
    const val Mic = "\uE31D"
    const val Keyboard = "\uE312"
    const val Check = "\uE5CA"
    const val Done = "\uE876"
    const val Warning = "\uF083"
    const val Play = "\uE037"
    const val Back = "\uE5C4"
    const val Previous = "\uE045"
    const val Next = "\uE044"
    const val Star = "\uF09A"
    const val ClosedCaption = "\uE996"
    const val BrandFamily = "\uF4F1"
    const val SpeechToText = "\uF8A7"
    const val TextToSpeech = "\uF1BC"
    const val StepOver = "\uF6FF"
    const val Footprint = "\uF87D"
    const val MoveSelectionLeft = "\uF713"
    const val AspectRatio = "\uE85B"
    const val Hd = "\uE052"
    const val FastForward = "\uE01F"
    const val MotionPhotosPaused = "\uF227"
    const val CancelPresentation = "\uE0E9"
    const val DoNotTouch = "\uF1B0"
    const val CloudDownload = "\uE2C0"
    const val LiveTv = "\uE63A"
    const val FormatSize = "\uE245"
    const val Palette = "\uE40A"
    const val Wallpaper = "\uE1BC"
    const val SettingsOverscan = "\uE8C4"
    const val ShieldPerson = "\uF650"
    const val Animation = "\uE71C"
    const val ReadinessScore = "\uF6DD"
    const val BrandAwareness = "\uE98E"
    const val Dvr = "\uE1B2"
    const val Clock = "\uF8BC"
    const val SwitchAccess = "\uF506"
    const val ThumbnailBar = "\uF734"
    const val DoorOpen = "\uE77C"
    const val PlaylistAddCheckCircle = "\uE7E6"
    const val HighRes = "\uF54B"
    const val Movie = "\uE404"
    const val EmojiNature = "\uEA1C"
    const val JapaneseKana = "\uF513"
    const val Public = "\uE80B"
    const val Encrypted = "\uE593"
    const val DiscFull = "\uE610"
    const val FactCheck = "\uF0C5"
    const val CleaningBucket = "\uF8B4"
    const val Bolt = "\uEA0B"
    const val Build = "\uF8CD"
    const val ListAlt = "\uE0EE"
    const val Hub = "\uE9F4"
    const val CloudSync = "\uEB5A"
    const val DataExploration = "\uE76F"
    const val VolunteerActivism = "\uEA70"
    const val Bookmark = "\uE866"
    const val AvgPace = "\uF6BB"
    const val EventUpcoming = "\uF238"
    const val Info = "\uE88E"
    const val Replay = "\uE042"
    const val NextPlan = "\uEF5D"
    const val Remove = "\uE15B"
    const val Add = "\uE145"
    const val SportsEsports = "\uEA28"
    const val Pause = "\uE034"
    const val FastRewind = "\uE020"
    const val Dns = "\uE875"
    const val RecordVoiceOver = "\uE91F"
    const val Speed = "\uE9E4"
    const val VolumeUp = "\uE050"
    const val VolumeDown = "\uE04D"
    const val VolumeMute = "\uE04E"
    const val VolumeOff = "\uE04F"
    const val BrightnessMedium = "\uE1A6"
    const val ViewModule = "\uE8F0"
    const val Clear = "\uE14C"
    const val Backspace = "\uE14A"
    const val Refresh = "\uE5D5"
    const val Tune = "\uE429"
    const val Loop = "\uE028"
    const val DeleteForever = "\uE92B"
    const val RadioUnchecked = "\uE836"
    const val RadioChecked = "\uE837"
    const val DragIndicator = "\uE945"
    const val Cancel = "\uE5C9"
    const val SpaceBar = "\uE256"
    const val LockOpen = "\uE898"
    const val Block = "\uE14B"
    const val LightMode = "\uE518"
    const val GTranslate = "\uE927"
    const val Sd = "\uE9DD"
    const val FullHd = "\uF58B"
    const val FourK = "\uE072"
    const val Cloud = "\uE2BD"
    const val ArrowRight = "\uE5DF"
    const val ProgressActivity = "\uE9D0"
    const val Cycle = "\uF854"
    const val PartlyCloudyNight = "\uF174"
    const val Update = "\uE923"
    const val BlurOn = "\uE3A5"
    const val Resize = "\uF707"
    const val ShadowIcon = "\uE9DF"
    const val Gradient = "\uE3E9"
    const val TextFormat = "\uE165"
    const val FormatBold = "\uE238"
    const val Opacity = "\uE91C"
    const val ExpandMore = "\uE5CF"
    const val ChevronRight = "\uE5CC"
    const val Close = "\uE5CD"
}

internal fun colorSchemeForTheme(theme: String, loginScreen: Boolean = false): ColorScheme {
    val colors = animeTvThemeColorsFor(theme)
    val panel = blendColors(colors.primary1, colors.primaryBg, 0.42f)
    return darkColorScheme(
        primary = colors.accent1,
        onPrimary = Color.White,
        primaryContainer = colors.primary1,
        onPrimaryContainer = Color.White,
        secondary = colors.primary2,
        tertiary = colors.primary3,
        background = if (loginScreen) colors.loginBg else colors.primaryBg,
        surface = colors.primaryBg,
        surfaceVariant = panel,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFAAAAAA),
        outline = blendColors(colors.accent1, colors.primaryBg, 0.55f)
    )
}

internal fun blendColors(foreground: Color, background: Color, foregroundWeight: Float): Color {
    val weight = foregroundWeight.coerceIn(0f, 1f)
    return Color(
        red = foreground.red * weight + background.red * (1f - weight),
        green = foreground.green * weight + background.green * (1f - weight),
        blue = foreground.blue * weight + background.blue * (1f - weight),
        alpha = 1f
    )
}

internal fun appTypography(fontFamily: FontFamily): Typography {
    val default = Typography()
    return Typography(
        displayLarge = default.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = default.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = default.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = default.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = default.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = default.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = default.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = default.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = default.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = default.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = default.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = default.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = default.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = default.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = default.labelSmall.copy(fontFamily = fontFamily)
    )
}
