package tj.tajikdictionary.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import tj.tajikdictionary.data.settings.ThemeSetting
import tj.tajikdictionary.domain.model.Language

// Brand palette inspired by the flag of Tajikistan (red, white, green, gold crown).
private val TajikGreen = Color(0xFF006A3A)
private val TajikGreenLight = Color(0xFF7FD8A4)
private val TajikRed = Color(0xFFC8102E)
private val TajikRedLight = Color(0xFFFFB3B0)
private val CrownGold = Color(0xFFF8C300)

private val LightColors = lightColorScheme(
    primary = TajikGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F1CC),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = TajikRed,
    onSecondary = Color.White,
    tertiary = Color(0xFF6D5E00),
    tertiaryContainer = CrownGold,
)

private val DarkColors = darkColorScheme(
    primary = TajikGreenLight,
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF00522B),
    onPrimaryContainer = Color(0xFFB7F1CC),
    secondary = TajikRedLight,
    onSecondary = Color(0xFF680011),
    tertiary = CrownGold,
)

/** Colors for language tags (TJ / RU / EN). Defined in one place, used everywhere. */
@Immutable
data class LanguageColors(val tajik: Color, val russian: Color, val english: Color) {
    fun of(language: Language): Color = when (language) {
        Language.TAJIK -> tajik
        Language.RUSSIAN -> russian
        Language.ENGLISH -> english
    }
}

private val LightLanguageColors = LanguageColors(
    tajik = Color(0xFF00843D), russian = Color(0xFF1E5BC6), english = Color(0xFFC2410C),
)
private val DarkLanguageColors = LanguageColors(
    tajik = Color(0xFF5BD18B), russian = Color(0xFF8AB4FF), english = Color(0xFFFFA36C),
)

val LocalLanguageColors = staticCompositionLocalOf { LightLanguageColors }

@Composable
fun TajikDictionaryTheme(
    themeSetting: ThemeSetting = ThemeSetting.SYSTEM,
    dynamicColor: Boolean = false, // brand colors by default; Material You is an opt-in setting (roadmap)
    content: @Composable () -> Unit,
) {
    val dark = when (themeSetting) {
        ThemeSetting.SYSTEM -> isSystemInDarkTheme()
        ThemeSetting.LIGHT -> false
        ThemeSetting.DARK -> true
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLanguageColors provides if (dark) DarkLanguageColors else LightLanguageColors,
    ) {
        MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
    }
}
