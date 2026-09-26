package app.luma.chat

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Tonal palettes
//
// Material 3 builds every colour role out of tonal palettes: one hue sampled at
// fixed tones, where the tone *is* the perceptual lightness. Doing that properly
// is what keeps contrast constant across accents, so the ramps below are
// generated instead of hand-picked: a CIELCh hue and chroma, sampled at tone =
// CIE L*. Chroma is reduced until the tone fits in sRGB, so a tone never drifts
// in lightness just because the hue ran out of gamut.
// ---------------------------------------------------------------------------

private fun labInverse(value: Double): Double =
    if (value * value * value > 0.008856) value * value * value else (value - 16.0 / 116.0) / 7.787

private fun linearRgb(tone: Double, chroma: Double, hue: Double): DoubleArray {
    val radians = hue * PI / 180.0
    val a = chroma * cos(radians)
    val b = chroma * sin(radians)
    val fy = (tone + 16.0) / 116.0
    val x = labInverse(fy + a / 500.0) * 0.95047
    val y = labInverse(fy)
    val z = labInverse(fy - b / 200.0) * 1.08883
    return doubleArrayOf(
        3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
        -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
        0.0556434 * x - 0.2040259 * y + 1.0572252 * z
    )
}

private fun transfer(value: Double): Float {
    val clamped = value.coerceIn(0.0, 1.0)
    return (if (clamped <= 0.0031308) 12.92 * clamped else 1.055 * clamped.pow(1.0 / 2.4) - 0.055).toFloat()
}

private class Ramp(private val hue: Double, private val chroma: Double) {
    private fun fits(tone: Double, chroma: Double) =
        linearRgb(tone, chroma, hue).all { it > -0.0005 && it < 1.0005 }

    fun tone(tone: Int): Color {
        val lightness = tone.toDouble()
        var usable = chroma
        if (!fits(lightness, usable)) {
            var low = 0.0
            var high = usable
            repeat(16) {
                val middle = (low + high) / 2
                if (fits(lightness, middle)) low = middle else high = middle
            }
            usable = low
        }
        val rgb = linearRgb(lightness, usable, hue)
        return Color(transfer(rgb[0]), transfer(rgb[1]), transfer(rgb[2]))
    }
}

private class Palette(
    val primary: Ramp,
    val secondary: Ramp,
    val tertiary: Ramp,
    val neutral: Ramp,
    val variant: Ramp
)

/** Matches the Material 3 baseline error ramp closely enough to feel native. */
private val errorRamp = Ramp(28.0, 62.0)

private fun palette(accent: String): Palette = when (accent) {
    "blue" -> tonalPalette(265.0, 38.0)
    "rose" -> tonalPalette(350.0, 36.0)
    "amber" -> tonalPalette(80.0, 38.0)
    "teal" -> tonalPalette(200.0, 30.0)
    "graphite" -> tonalPalette(265.0, 4.0)
    "mint" -> Palette(Ramp(160.0, 24.0), Ramp(160.0, 11.0), Ramp(220.0, 20.0), Ramp(160.0, 4.0), Ramp(160.0, 8.0))
    "peach" -> Palette(Ramp(45.0, 32.0), Ramp(45.0, 13.0), Ramp(105.0, 20.0), Ramp(45.0, 4.0), Ramp(45.0, 8.0))
    else -> Palette(Ramp(305.0, 36.0), Ramp(305.0, 12.0), Ramp(5.0, 20.0), Ramp(305.0, 4.0), Ramp(305.0, 8.0))
}

private fun tonalPalette(hue: Double, chroma: Double) = Palette(Ramp(hue, chroma),
    Ramp(hue, chroma * .35), Ramp((hue + 60) % 360, chroma * .6), Ramp(hue, 4.0), Ramp(hue, 8.0))

private fun Palette.dark(): ColorScheme = darkColorScheme(
    primary = primary.tone(80), onPrimary = primary.tone(20),
    primaryContainer = primary.tone(30), onPrimaryContainer = primary.tone(90),
    inversePrimary = primary.tone(40),
    secondary = secondary.tone(80), onSecondary = secondary.tone(20),
    secondaryContainer = secondary.tone(30), onSecondaryContainer = secondary.tone(90),
    tertiary = tertiary.tone(80), onTertiary = tertiary.tone(20),
    tertiaryContainer = tertiary.tone(30), onTertiaryContainer = tertiary.tone(90),
    error = errorRamp.tone(80), onError = errorRamp.tone(20),
    errorContainer = errorRamp.tone(30), onErrorContainer = errorRamp.tone(90),
    background = neutral.tone(6), onBackground = neutral.tone(90),
    surface = neutral.tone(6), onSurface = neutral.tone(90),
    surfaceDim = neutral.tone(6), surfaceBright = neutral.tone(24),
    surfaceContainerLowest = neutral.tone(4), surfaceContainerLow = neutral.tone(10),
    surfaceContainer = neutral.tone(12), surfaceContainerHigh = neutral.tone(17),
    surfaceContainerHighest = neutral.tone(22),
    surfaceVariant = variant.tone(30), onSurfaceVariant = variant.tone(80),
    outline = variant.tone(60), outlineVariant = variant.tone(30),
    inverseSurface = neutral.tone(90), inverseOnSurface = neutral.tone(20),
    scrim = neutral.tone(0), surfaceTint = primary.tone(80)
)

private fun Palette.light(): ColorScheme = lightColorScheme(
    primary = primary.tone(40), onPrimary = primary.tone(100),
    primaryContainer = primary.tone(90), onPrimaryContainer = primary.tone(10),
    inversePrimary = primary.tone(80),
    secondary = secondary.tone(40), onSecondary = secondary.tone(100),
    secondaryContainer = secondary.tone(90), onSecondaryContainer = secondary.tone(10),
    tertiary = tertiary.tone(40), onTertiary = tertiary.tone(100),
    tertiaryContainer = tertiary.tone(90), onTertiaryContainer = tertiary.tone(10),
    error = errorRamp.tone(40), onError = errorRamp.tone(100),
    errorContainer = errorRamp.tone(90), onErrorContainer = errorRamp.tone(10),
    background = neutral.tone(98), onBackground = neutral.tone(10),
    surface = neutral.tone(98), onSurface = neutral.tone(10),
    surfaceDim = neutral.tone(87), surfaceBright = neutral.tone(98),
    surfaceContainerLowest = neutral.tone(100), surfaceContainerLow = neutral.tone(96),
    surfaceContainer = neutral.tone(94), surfaceContainerHigh = neutral.tone(92),
    surfaceContainerHighest = neutral.tone(90),
    surfaceVariant = variant.tone(90), onSurfaceVariant = variant.tone(30),
    outline = variant.tone(50), outlineVariant = variant.tone(80),
    inverseSurface = neutral.tone(20), inverseOnSurface = neutral.tone(95),
    scrim = neutral.tone(0), surfaceTint = primary.tone(40)
)

/** The swatch shown for an accent in settings: the tone the accent takes as `primary`. */
fun accentColor(name: String, dark: Boolean = true): Color = palette(name).primary.tone(if (dark) 80 else 40)

/** Material You wallpaper colours, available from Android 12. */
val dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun lumaColorScheme(context: Context, accent: String, dark: Boolean, blackBackground: Boolean = false): ColorScheme {
    val scheme = if (accent == ACCENT_DYNAMIC && dynamicColorSupported) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        palette(accent).let { if (dark) it.dark() else it.light() }
    }
    return if (dark && blackBackground) scheme.copy(background = Color.Black, surface = Color.Black,
        surfaceDim = Color.Black, surfaceContainerLowest = Color.Black) else scheme
}

const val ACCENT_DYNAMIC = "dynamic"

// ---------------------------------------------------------------------------
// Type scale and shape scale
// ---------------------------------------------------------------------------

/**
 * The full Material 3 type scale, so components we do not style by hand
 * (dialogs, list items, chips, snackbars) speak the same voice as the ones we do.
 * "Large text" scales every role together instead of a handful of them.
 */
private fun lumaTypography(scale: Float): Typography {
    fun style(size: Float, line: Float, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (line * scale).sp,
        letterSpacing = tracking.sp
    )
    return Typography(
        displayLarge = style(57f, 64f, FontWeight.Medium, -1.4f),
        displayMedium = style(45f, 52f, FontWeight.Medium, -1.1f),
        displaySmall = style(36f, 44f, FontWeight.Medium, -0.8f),
        headlineLarge = style(32f, 40f, FontWeight.Medium, -0.6f),
        headlineMedium = style(28f, 36f, FontWeight.Medium, -0.4f),
        headlineSmall = style(24f, 32f, FontWeight.Medium, -0.2f),
        titleLarge = style(22f, 28f, FontWeight.Medium),
        titleMedium = style(16f, 24f, FontWeight.Medium, 0.15f),
        titleSmall = style(14f, 20f, FontWeight.Medium, 0.1f),
        bodyLarge = style(16f, 25f, tracking = 0.15f),
        bodyMedium = style(14f, 21f, tracking = 0.2f),
        bodySmall = style(12f, 17f, tracking = 0.3f),
        labelLarge = style(14f, 20f, FontWeight.Medium, 0.1f),
        labelMedium = style(12f, 16f, FontWeight.Medium, 0.4f),
        labelSmall = style(11f, 16f, FontWeight.Medium, 0.4f)
    )
}

/**
 * Luma's shape scale. Material 3 expects components to take their corners from
 * here rather than from a number typed at the call site; this one is a couple of
 * steps rounder than the baseline, which is where the app's softness comes from.
 */
private val lumaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable fun isLumaDark(prefs: Preferences): Boolean =
    prefs.theme == "dark" || (prefs.theme == "system" && isSystemInDarkTheme())

@Composable fun LumaTheme(prefs: Preferences, content: @Composable () -> Unit) {
    val dark = isLumaDark(prefs)
    val context = LocalContext.current
    val colors = remember(context, prefs.accent, dark, prefs.blackBackground) { lumaColorScheme(context, prefs.accent, dark, prefs.blackBackground) }
    val typography = remember(prefs.largeText) { lumaTypography(if (prefs.largeText) 1.14f else 1f) }
    MaterialTheme(colorScheme = colors, typography = typography, shapes = lumaShapes) {
        Surface(color = colors.surface, contentColor = colors.onSurface, content = content)
    }
}

@Composable fun LumaMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        repeat(4) { leaf ->
            rotate(leaf * 90f + 25f) {
                drawOval(color, topLeft = Offset(size.width * .40f, size.height * .03f), size = Size(size.width * .28f, size.height * .49f))
            }
        }
        drawCircle(color, size.minDimension * .14f)
    }
}
