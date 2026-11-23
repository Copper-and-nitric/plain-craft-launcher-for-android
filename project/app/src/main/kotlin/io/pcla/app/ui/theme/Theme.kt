package io.pcla.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The launcher is designed dark-first: the near-black surface makes the lime accent readable and
 * the per-version cube colors pop. Light scheme exists only so the app does not look broken under
 * forced-light OEM modes, but the default is always the dark one.
 */
private val DarkColors = darkColorScheme(
    primary = Ui.Lime,
    onPrimary = Ui.OnLime,
    primaryContainer = Ui.SurfaceHigh,
    onPrimaryContainer = Ui.Lime,
    secondary = Ui.Grass,
    onSecondary = Color(0xFF0C1505),
    secondaryContainer = Ui.SurfaceHigh,
    onSecondaryContainer = Ui.Text,
    tertiary = Ui.Purple,
    onTertiary = Color.White,
    background = Ui.Bg,
    onBackground = Ui.Text,
    surface = Ui.Surface,
    onSurface = Ui.Text,
    surfaceVariant = Ui.SurfaceHigh,
    onSurfaceVariant = Ui.TextMuted,
    outline = Ui.Stroke,
    outlineVariant = Ui.Stroke,
    error = Ui.Red,
    onError = Color(0xFF2B0603),
    errorContainer = Color(0xFF3A1510),
    onErrorContainer = Ui.Red,
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F7A18),
    onPrimary = Color.White,
    background = Color(0xFFF7F8F3),
    onBackground = Color(0xFF14170F),
    surface = Color.White,
    onSurface = Color(0xFF14170F),
    surfaceVariant = Color(0xFFEDF0E4),
    onSurfaceVariant = Color(0xFF5A6152),
    outline = Color(0xFFD8DDCC),
    outlineVariant = Color(0xFFE4E8D8),
)

/** One type scale for the whole app: headline 26, card title 16, body 13, caption 11. */
private val PclTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp),
)

/** Cards 22, inner tiles 14, buttons pill. */
private val PclShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PclaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PclTypography,
        shapes = PclShapes,
        content = content,
    )
}
