package lt.neko.slopbreak.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object DarkPalette {
    val Ink = Color(0xFF0C0D10)
    val Panel = Color(0xFF15171B)
    val Raised = Color(0xFF1C1F24)
    val RaisedHigh = Color(0xFF24282E)
    val RaisedHighest = Color(0xFF2D3239)
    val Line = Color(0xFF656C76)
    val LineSoft = Color(0xFF23272D)
    val Text = Color(0xFFEDEEF0)
    val TextDim = Color(0xFF9BA1AB)
    val Ember = Color(0xFFFF8A4C)
    val EmberDeep = Color(0xFF3B2115)
    val OnEmber = Color(0xFF1E0E06)
    val OnEmberDeep = Color(0xFFFFD3BD)
    val Mint = Color(0xFF5ED69B)
    val Coral = Color(0xFFFF6B6B)
    val CoralDeep = Color(0xFF3D1A1A)
}

object LightPalette {
    val Ink = Color(0xFFF3F4F6)
    val Panel = Color(0xFFFFFFFF)
    val Raised = Color(0xFFECEEF1)
    val RaisedHigh = Color(0xFFE3E6EA)
    val RaisedHighest = Color(0xFFD7DBE0)
    val Line = Color(0xFF777E88)
    val LineSoft = Color(0xFFDADDE2)
    val Text = Color(0xFF16181C)
    val TextDim = Color(0xFF5A616B)
    val Ember = Color(0xFFD65C1E)
    val EmberDeep = Color(0xFFFFE3D4)
    val OnEmber = Color(0xFFFFFFFF)
    val OnEmberDeep = Color(0xFF6A2A0C)
    val Mint = Color(0xFF1B9660)
    val Coral = Color(0xFFCF3434)
    val CoralDeep = Color(0xFFFCE3E3)
}

private val Dark = darkColorScheme(
    primary = DarkPalette.Ember,
    onPrimary = DarkPalette.OnEmber,
    primaryContainer = DarkPalette.EmberDeep,
    onPrimaryContainer = DarkPalette.OnEmberDeep,
    secondary = DarkPalette.TextDim,
    onSecondary = DarkPalette.Ink,
    secondaryContainer = DarkPalette.RaisedHighest,
    onSecondaryContainer = DarkPalette.Text,
    tertiary = DarkPalette.Mint,
    background = DarkPalette.Ink,
    onBackground = DarkPalette.Text,
    surface = DarkPalette.Ink,
    onSurface = DarkPalette.Text,
    surfaceVariant = DarkPalette.Raised,
    onSurfaceVariant = DarkPalette.TextDim,
    surfaceContainerLowest = DarkPalette.Ink,
    surfaceContainerLow = DarkPalette.Panel,
    surfaceContainer = DarkPalette.Raised,
    surfaceContainerHigh = DarkPalette.RaisedHigh,
    surfaceContainerHighest = DarkPalette.RaisedHighest,
    inverseSurface = DarkPalette.Text,
    inverseOnSurface = DarkPalette.Ink,
    outline = DarkPalette.Line,
    outlineVariant = DarkPalette.LineSoft,
    error = DarkPalette.Coral,
    onError = DarkPalette.Ink,
    errorContainer = DarkPalette.CoralDeep,
    onErrorContainer = DarkPalette.Coral,
    scrim = Color.Black,
)

private val Light = lightColorScheme(
    primary = LightPalette.Ember,
    onPrimary = LightPalette.OnEmber,
    primaryContainer = LightPalette.EmberDeep,
    onPrimaryContainer = LightPalette.OnEmberDeep,
    secondary = LightPalette.TextDim,
    onSecondary = LightPalette.Panel,
    secondaryContainer = LightPalette.RaisedHighest,
    onSecondaryContainer = LightPalette.Text,
    tertiary = LightPalette.Mint,
    background = LightPalette.Ink,
    onBackground = LightPalette.Text,
    surface = LightPalette.Ink,
    onSurface = LightPalette.Text,
    surfaceVariant = LightPalette.Raised,
    onSurfaceVariant = LightPalette.TextDim,
    surfaceContainerLowest = LightPalette.Panel,
    surfaceContainerLow = LightPalette.Panel,
    surfaceContainer = LightPalette.Raised,
    surfaceContainerHigh = LightPalette.RaisedHigh,
    surfaceContainerHighest = LightPalette.RaisedHighest,
    inverseSurface = Color(0xFF2A2D33),
    inverseOnSurface = LightPalette.Ink,
    outline = LightPalette.Line,
    outlineVariant = LightPalette.LineSoft,
    error = LightPalette.Coral,
    onError = LightPalette.Panel,
    errorContainer = LightPalette.CoralDeep,
    onErrorContainer = LightPalette.Coral,
    scrim = Color.Black,
)

private const val THEME_FADE_MS = 700

private val DARK_GROUND = DarkPalette.Ink.luminance()
private val LIGHT_GROUND = LightPalette.Ink.luminance()

val ColorScheme.darkness: Float
    get() = ((LIGHT_GROUND - background.luminance()) / (LIGHT_GROUND - DARK_GROUND)).coerceIn(0f, 1f)

private fun mix(light: ColorScheme, dark: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return light
    if (fraction >= 1f) return dark
    fun m(a: Color, b: Color) = lerp(a, b, fraction)
    return (if (fraction < 0.5f) light else dark).copy(
        primary = m(light.primary, dark.primary),
        onPrimary = m(light.onPrimary, dark.onPrimary),
        primaryContainer = m(light.primaryContainer, dark.primaryContainer),
        onPrimaryContainer = m(light.onPrimaryContainer, dark.onPrimaryContainer),
        inversePrimary = m(light.inversePrimary, dark.inversePrimary),
        secondary = m(light.secondary, dark.secondary),
        onSecondary = m(light.onSecondary, dark.onSecondary),
        secondaryContainer = m(light.secondaryContainer, dark.secondaryContainer),
        onSecondaryContainer = m(light.onSecondaryContainer, dark.onSecondaryContainer),
        tertiary = m(light.tertiary, dark.tertiary),
        onTertiary = m(light.onTertiary, dark.onTertiary),
        tertiaryContainer = m(light.tertiaryContainer, dark.tertiaryContainer),
        onTertiaryContainer = m(light.onTertiaryContainer, dark.onTertiaryContainer),
        background = m(light.background, dark.background),
        onBackground = m(light.onBackground, dark.onBackground),
        surface = m(light.surface, dark.surface),
        onSurface = m(light.onSurface, dark.onSurface),
        surfaceVariant = m(light.surfaceVariant, dark.surfaceVariant),
        onSurfaceVariant = m(light.onSurfaceVariant, dark.onSurfaceVariant),
        surfaceTint = m(light.surfaceTint, dark.surfaceTint),
        inverseSurface = m(light.inverseSurface, dark.inverseSurface),
        inverseOnSurface = m(light.inverseOnSurface, dark.inverseOnSurface),
        error = m(light.error, dark.error),
        onError = m(light.onError, dark.onError),
        errorContainer = m(light.errorContainer, dark.errorContainer),
        onErrorContainer = m(light.onErrorContainer, dark.onErrorContainer),
        outline = m(light.outline, dark.outline),
        outlineVariant = m(light.outlineVariant, dark.outlineVariant),
        scrim = m(light.scrim, dark.scrim),
        surfaceBright = m(light.surfaceBright, dark.surfaceBright),
        surfaceDim = m(light.surfaceDim, dark.surfaceDim),
        surfaceContainer = m(light.surfaceContainer, dark.surfaceContainer),
        surfaceContainerHigh = m(light.surfaceContainerHigh, dark.surfaceContainerHigh),
        surfaceContainerHighest = m(light.surfaceContainerHighest, dark.surfaceContainerHighest),
        surfaceContainerLow = m(light.surfaceContainerLow, dark.surfaceContainerLow),
        surfaceContainerLowest = m(light.surfaceContainerLowest, dark.surfaceContainerLowest),
    )
}

private val Base = Typography()

private val Type = Typography(
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val Shape = Shapes(
    extraSmall = SquircleShape(CornerExtraSmall),
    small = SquircleShape(CornerSmall),
    medium = SquircleShape(CornerMedium),
    large = SquircleShape(CornerLarge),
    extraLarge = SquircleShape(CornerExtraLarge),
)

val SectionLabel: TextStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.6.sp)

@Composable
fun SlopbreakTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val fraction by animateFloatAsState(if (dark) 1f else 0f, tween(THEME_FADE_MS, easing = FastOutSlowInEasing), label = "theme")
    val scheme = remember(fraction) { mix(Light, Dark, fraction) }
    MaterialTheme(colorScheme = scheme, typography = Type, shapes = Shape, content = content)
}
