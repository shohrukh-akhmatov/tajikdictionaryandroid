package tj.tajikdictionary.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import tj.tajikdictionary.data.settings.ThemeSetting
import tj.tajikdictionary.domain.model.Language

// Modern Material 3 brand system with Tajik emerald (#087653), soft lavender-white canvas (#FAF8FC),
// and deep plum-slate typography (#24212A).
val BrandGreen = Color(0xFF087653)
val BrandGreenDark = Color(0xFF6FD8AC)
val BrandBackgroundLight = Color(0xFFFAF8FC)
val BrandTextLight = Color(0xFF24212A)
val BrandSubtextLight = Color(0xFF625D6B)
val BrandOutlineLight = Color(0xFFDFDAE6)

val BrandBackgroundDark = Color(0xFF121116)
val BrandSurfaceDark = Color(0xFF1C1A23)
val BrandTextDark = Color(0xFFF2EFF7)
val BrandSubtextDark = Color(0xFFB7B1C2)
val BrandOutlineDark = Color(0xFF363242)

private val TajikRed = Color(0xFFC8102E)
private val TajikRedLight = Color(0xFFFF8F96)
private val CrownGold = Color(0xFFC58B00)
private val CrownGoldLight = Color(0xFFF5C451)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F3E5),
    onPrimaryContainer = Color(0xFF002116),
    secondary = TajikRed,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE8EB),
    onSecondaryContainer = Color(0xFF690012),
    tertiary = CrownGold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF1CC),
    onTertiaryContainer = Color(0xFF332200),
    background = BrandBackgroundLight,
    onBackground = BrandTextLight,
    surface = BrandBackgroundLight,
    onSurface = BrandTextLight,
    surfaceVariant = Color(0xFFEFEBF5),
    onSurfaceVariant = BrandSubtextLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F2F9),
    surfaceContainer = Color(0xFFEFEBF5),
    surfaceContainerHigh = Color(0xFFE8E3F0),
    surfaceContainerHighest = Color(0xFFE1DBE9),
    outline = Color(0xFF847D90),
    outlineVariant = BrandOutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenDark,
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF045239),
    onPrimaryContainer = Color(0xFFC7F2DF),
    secondary = TajikRedLight,
    onSecondary = Color(0xFF5F0010),
    secondaryContainer = Color(0xFF4C161E),
    onSecondaryContainer = Color(0xFFFFD9DC),
    tertiary = CrownGoldLight,
    onTertiary = Color(0xFF3B2900),
    tertiaryContainer = Color(0xFF4A3800),
    onTertiaryContainer = Color(0xFFFFE699),
    background = BrandBackgroundDark,
    onBackground = BrandTextDark,
    surface = BrandBackgroundDark,
    onSurface = BrandTextDark,
    surfaceVariant = Color(0xFF25222D),
    onSurfaceVariant = BrandSubtextDark,
    surfaceContainerLowest = Color(0xFF1A1820),
    surfaceContainerLow = BrandSurfaceDark,
    surfaceContainer = Color(0xFF24212C),
    surfaceContainerHigh = Color(0xFF2D2A37),
    surfaceContainerHighest = Color(0xFF373342),
    outline = Color(0xFF958EA1),
    outlineVariant = BrandOutlineDark,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.25.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
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
    tajik = Color(0xFF087653), russian = Color(0xFF2459C7), english = Color(0xFFC2410C),
)
private val DarkLanguageColors = LanguageColors(
    tajik = Color(0xFF6FD8AC), russian = Color(0xFF8AB4FF), english = Color(0xFFFFA36C),
)

val LocalLanguageColors = staticCompositionLocalOf { LightLanguageColors }

@Composable
fun TajikDictionaryTheme(
    themeSetting: ThemeSetting = ThemeSetting.SYSTEM,
    dynamicColor: Boolean = false,
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLanguageColors provides if (dark) DarkLanguageColors else LightLanguageColors,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
