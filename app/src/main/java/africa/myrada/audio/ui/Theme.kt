package africa.myrada.audio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RadaColors {
    val Bg = Color(0xFF141312)
    val Panel = Color(0xFF242220)
    val Panel2 = Color(0xFF2C2724)
    val Line = Color(0xFF594536)
    val Text = Color(0xFFF2EDE6)
    val Muted = Color(0xFFA39A91)
    val Copper = Color(0xFFEDA86E)
    val CopperDeep = Color(0xFFD48B51)
    val OnCopper = Color(0xFF21150E)
    val Amber = Color(0xFFE0B25C)
    val Red = Color(0xFFC9000B)
}

private val scheme = darkColorScheme(
    primary = RadaColors.Copper,
    onPrimary = RadaColors.OnCopper,
    secondary = RadaColors.CopperDeep,
    onSecondary = RadaColors.OnCopper,
    background = RadaColors.Bg,
    onBackground = RadaColors.Text,
    surface = RadaColors.Bg,
    onSurface = RadaColors.Text,
    surfaceVariant = RadaColors.Panel,
    onSurfaceVariant = RadaColors.Muted,
    secondaryContainer = Color(0xFF3A2A1C),
    onSecondaryContainer = RadaColors.Copper,
    outline = RadaColors.Line,
)

@Composable
fun RadaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
