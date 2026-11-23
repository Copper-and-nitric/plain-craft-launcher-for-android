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
 * Material colors are derived from [Ui] on every call, so switching skins only needs
 * `Ui.apply(...)` + Activity recreate — no per-composable palette plumbing.
 */
private fun darkScheme() = darkColorScheme(
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

private fun lightScheme() = lightColorScheme(
    primary = Ui.Lime,
    onPrimary = Ui.OnLime,
    primaryContainer = Ui.SurfaceHigh,
    onPrimaryContainer = Ui.LimeDeep,
    secondary = Ui.LimeDeep,
    onSecondary = Color.White,
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
    onError = Color.White,
    errorContainer = Color(0xFFFBDDDD),
    onErrorContainer = Ui.Red,
    scrim = Color.Black,
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
    darkTheme: Boolean = Ui.isDark,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme() else lightScheme(),
        typography = PclTypography,
        shapes = PclShapes,
        content = content,
    )
}
