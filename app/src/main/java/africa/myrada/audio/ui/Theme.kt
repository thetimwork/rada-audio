package africa.myrada.audio.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.myrada.audio.R

/**
 * Rada brand: Aesthetic White ground, Rich Black type, one Guardsman Red.
 * Two colour sets. Contrast cards (the device card, Advanced panels, the profile shortlist)
 * take the opposite set, so they are black in light mode and warm white in dark mode.
 */
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val powder: Color,
    val powder2: Color,
    val ink: Color,
    val black: Color,
    val muted: Color,
    val muted2: Color,
    val line: Color,
    val line2: Color,
    val pri: Color,
    val onPri: Color,
    val art1: Color,
    val art2: Color,
    val art3: Color,
    val artSh: Color,
)

val Red = Color(0xFFC10206)
val Madder = Color(0xFFA50113)
val Grey = Color(0xFF8A858B)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFFFFBF2), card = Color(0xFFFFFFFF), powder = Color(0xFFDFE2DB), powder2 = Color(0xFFEEF0EA),
    ink = Color(0xFF211D21), black = Color(0xFF010A10), muted = Color(0xFF8A858B), muted2 = Color(0xFF5E5A60),
    line = Color(0x1A010A10), line2 = Color(0x0F010A10), pri = Color(0xFF010A10), onPri = Color(0xFFFFFFFF),
    art1 = Color(0xFF010A10), art2 = Color(0xFF5E5A60), art3 = Color(0xFF211D21), artSh = Color(0x14010A10),
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF010A10), card = Color(0xFF0A141B), powder = Color(0x2EFFFBF2), powder2 = Color(0x12FFFBF2),
    ink = Color(0xDBFFFBF2), black = Color(0xFFFFFBF2), muted = Color(0x80FFFBF2), muted2 = Color(0xA8FFFBF2),
    line = Color(0x1AFFFBF2), line2 = Color(0x0FFFFBF2), pri = Color(0xFFFFFBF2), onPri = Color(0xFF010A10),
    art1 = Color(0xFFFFFBF2), art2 = Color(0xFFDFE2DB), art3 = Color(0xFFEEF0EA), artSh = Color(0x12FFFBF2),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** The colour set in force here. */
val Pal: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

val CardRadius = 16.dp
val TileRadius = 14.dp

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semi_bold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extra_bold, FontWeight.ExtraBold),
    Font(R.font.figtree_black, FontWeight.Black),
)

@Composable
private fun WithPalette(p: Palette, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides p,
        LocalTextStyle provides TextStyle(fontFamily = Figtree, color = p.ink, letterSpacing = (-0.07).sp),
        content = content,
    )
}


/** Content drawn in the opposite colour set (contrast cards). */
@Composable
fun Inverse(content: @Composable () -> Unit) {
    WithPalette(if (Pal.dark) LightPalette else DarkPalette, content)
}

/** Content that always sits on warm white, such as the vinyl radar (brand rule). */
@Composable
fun AlwaysLight(content: @Composable () -> Unit) = WithPalette(LightPalette, content)

enum class ThemeMode { System, Light, Dark }

object ThemePrefs {
    val mode = mutableStateOf(ThemeMode.System)
    fun init(ctx: Context) {
        val v = ctx.getSharedPreferences("rada_ui", Context.MODE_PRIVATE).getString("theme", null)
        mode.value = ThemeMode.entries.firstOrNull { it.name == v } ?: ThemeMode.System
    }
    fun set(ctx: Context, m: ThemeMode) {
        mode.value = m
        ctx.getSharedPreferences("rada_ui", Context.MODE_PRIVATE).edit().putString("theme", m.name).apply()
    }
}

@Composable
fun isRadaDark(): Boolean = when (ThemePrefs.mode.value) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

private val type = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Figtree), displayMedium = t.displayMedium.copy(fontFamily = Figtree),
        displaySmall = t.displaySmall.copy(fontFamily = Figtree), headlineLarge = t.headlineLarge.copy(fontFamily = Figtree),
        headlineMedium = t.headlineMedium.copy(fontFamily = Figtree), headlineSmall = t.headlineSmall.copy(fontFamily = Figtree),
        titleLarge = t.titleLarge.copy(fontFamily = Figtree), titleMedium = t.titleMedium.copy(fontFamily = Figtree),
        titleSmall = t.titleSmall.copy(fontFamily = Figtree), bodyLarge = t.bodyLarge.copy(fontFamily = Figtree),
        bodyMedium = t.bodyMedium.copy(fontFamily = Figtree), bodySmall = t.bodySmall.copy(fontFamily = Figtree),
        labelLarge = t.labelLarge.copy(fontFamily = Figtree), labelMedium = t.labelMedium.copy(fontFamily = Figtree),
        labelSmall = t.labelSmall.copy(fontFamily = Figtree),
    )
}

@Composable
fun RadaTheme(content: @Composable () -> Unit) {
    val dark = isRadaDark()
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.pri, onPrimary = p.onPri, background = p.bg, onBackground = p.ink, surface = p.card, onSurface = p.ink,
        surfaceVariant = p.powder2, onSurfaceVariant = p.muted2, outline = p.line, surfaceContainerLow = p.bg, surfaceContainerHigh = p.card,
    ) else lightColorScheme(
        primary = p.pri, onPrimary = p.onPri, background = p.bg, onBackground = p.ink, surface = p.card, onSurface = p.ink,
        surfaceVariant = p.powder2, onSurfaceVariant = p.muted2, outline = p.line, surfaceContainerLow = p.bg, surfaceContainerHigh = p.card,
    )
    MaterialTheme(colorScheme = scheme, typography = type) { WithPalette(p, content) }
}
