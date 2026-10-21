package org.librehu.launcher.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.dp

/** Accent colours: a light tone for dark mode, a deeper one for light mode. */
enum class Accent(
    val onDark: Long,
    val onLight: Long,
) {
    BLUE(0xFF8AB4F8, 0xFF1A73E8),
    TEAL(0xFF78D9EC, 0xFF12858F),
    GREEN(0xFF81C995, 0xFF188038),
    YELLOW(0xFFFDD663, 0xFFB06000),
    ORANGE(0xFFFCAD70, 0xFFE8710A),
    RED(0xFFF28B82, 0xFFD93025),
    PINK(0xFFFF8BCB, 0xFFC5221F),
    PURPLE(0xFFC58AF9, 0xFF9334E6),
    ;

    fun color(dark: Boolean) = Color(if (dark) onDark else onLight)
}

/** Look of the launcher: the usual one, or a retro one (its own colours, fixed-width font, scan lines on screens). */
enum class ThemeStyle {
    MODERN,

    /** Amber vacuum fluorescent display of 80s car radios. */
    RETRO_AMBER,

    /** Green phosphor / LCD. */
    RETRO_GREEN,

    /** Warm 70s colours (cassette deck). */
    RETRO_CASSETTE,
}

data class CarPalette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val textDim: Color,
    val style: ThemeStyle = ThemeStyle.MODERN,
) {
    val retro: Boolean get() = style != ThemeStyle.MODERN

    /** Scan lines over the screen (the display styles, at night). */
    val scanLines: Boolean get() = dark && (style == ThemeStyle.RETRO_AMBER || style == ThemeStyle.RETRO_GREEN)

    companion object {
        /** Retro palettes: dark (night, headlights) and light (day), [accent] ignored: each style has its own. */
        private fun retro(
            style: ThemeStyle,
            dark: Boolean,
            a: Float,
        ): CarPalette {
            fun p(
                bg: Long,
                surface: Long,
                high: Long,
                accent: Long,
                onAccent: Long,
                text: Long,
                dim: Long,
            ) = CarPalette(dark, Color(bg), Color(surface).copy(alpha = a), Color(high), Color(accent), Color(onAccent), Color(text), Color(dim), style)
            return when (style) {
                ThemeStyle.RETRO_AMBER ->
                    if (dark) {
                        p(0xFF0A0805, 0xFF17120A, 0xFF2A2010, 0xFFFFB000, 0xFF1A1205, 0xFFFFC14D, 0xFFB07D2A)
                    } else {
                        p(0xFFF4E9D2, 0xFFFBF3E2, 0xFFE8D9B8, 0xFFC77700, 0xFFFFFFFF, 0xFF3A2A10, 0xFF7A6040)
                    }
                ThemeStyle.RETRO_GREEN ->
                    if (dark) {
                        p(0xFF050A06, 0xFF0C160E, 0xFF16261A, 0xFF39FF6A, 0xFF04140A, 0xFF7DFF9B, 0xFF3F9E57)
                    } else {
                        p(0xFFB9C4A0, 0xFFC9D3B0, 0xFFA9B48E, 0xFF2F3B1F, 0xFFDCE4C6, 0xFF1F2A14, 0xFF4A5734)
                    }
                else ->
                    if (dark) {
                        p(0xFF1B1410, 0xFF2A1F18, 0xFF3A2B21, 0xFFE8743B, 0xFF1B1410, 0xFFF2E2C4, 0xFFB39A7A)
                    } else {
                        p(0xFFEFE3CC, 0xFFF8EFDC, 0xFFE3D2B2, 0xFFC2502A, 0xFFFFFFFF, 0xFF3B2A1E, 0xFF7C6650)
                    }
            }
        }

        /** [translucent]: cards let a wallpaper show through. */
        fun of(
            dark: Boolean,
            accent: Accent,
            translucent: Boolean = false,
            style: ThemeStyle = ThemeStyle.MODERN,
        ): CarPalette {
            val a = if (translucent) 0.86f else 1f
            if (style != ThemeStyle.MODERN) return retro(style, dark, a)
            return if (dark) {
                CarPalette(
                    dark = true,
                    background = Color(0xFF000000),
                    surface = Color(0xFF1E1F22).copy(alpha = a),
                    surfaceHigh = Color(0xFF2B2D31),
                    accent = accent.color(true),
                    onAccent = Color(0xFF202124),
                    text = Color(0xFFE8EAED),
                    textDim = Color(0xFF9AA0A6),
                )
            } else {
                CarPalette(
                    dark = false,
                    background = Color(0xFFF1F3F4),
                    surface = Color(0xFFFFFFFF).copy(alpha = a),
                    surfaceHigh = Color(0xFFE8EAED),
                    accent = accent.color(false),
                    onAccent = Color(0xFFFFFFFF),
                    text = Color(0xFF202124),
                    textDim = Color(0xFF5F6368),
                )
            }
        }
    }
}

/**
 * Car UI colours. Backed by snapshot state: composables reading them recompose when the theme changes
 * (light / dark, headlights, accent).
 */
object CarColors {
    var palette: CarPalette by mutableStateOf(CarPalette.of(true, org.librehu.launcher.ui.Accent.BLUE))

    val Background: Color get() = palette.background
    val Surface: Color get() = palette.surface
    val SurfaceHigh: Color get() = palette.surfaceHigh
    val Accent: Color get() = palette.accent
    val OnAccent: Color get() = palette.onAccent
    val Text: Color get() = palette.text
    val TextDim: Color get() = palette.textDim
}

@Composable
fun CarTheme(content: @Composable () -> Unit) {
    val p = CarColors.palette
    val scheme =
        if (p.dark) {
            darkColorScheme(
                background = p.background,
                surface = p.surfaceHigh,
                surfaceVariant = p.surfaceHigh,
                primary = p.accent,
                onPrimary = p.onAccent,
                onBackground = p.text,
                onSurface = p.text,
                onSurfaceVariant = p.textDim,
            )
        } else {
            lightColorScheme(
                background = p.background,
                surface = p.surface.copy(alpha = 1f),
                surfaceVariant = p.surfaceHigh,
                primary = p.accent,
                onPrimary = p.onAccent,
                onBackground = p.text,
                onSurface = p.text,
                onSurfaceVariant = p.textDim,
            )
        }
    MaterialTheme(
        colorScheme = scheme.withCarRoles(p.accent, p.onAccent, p.surface, p.surfaceHigh, p.text, p.textDim),
        typography = if (p.retro) RetroTypography else androidx.compose.material3.Typography(),
        content = content,
    )
}

/**
 * Material 3 roles the car palette does not name, derived from it. Without them dialogs, cards, switches,
 * sliders, chips and progress tracks keep Material's default purple instead of the accent.
 */
internal fun ColorScheme.withCarRoles(
    accent: Color,
    onAccent: Color,
    surface: Color,
    surfaceHigh: Color,
    text: Color,
    textDim: Color,
): ColorScheme {
    val solid = surface.copy(alpha = 1f)
    val high = surfaceHigh.copy(alpha = 1f)
    val container = lerp(solid, accent, 0.30f)
    return copy(
        primaryContainer = container,
        onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = container,
        onTertiaryContainer = text,
        surfaceTint = Color.Transparent,
        surfaceBright = high,
        surfaceDim = solid,
        surfaceContainerLowest = solid,
        surfaceContainerLow = solid,
        surfaceContainer = solid,
        surfaceContainerHigh = high,
        surfaceContainerHighest = high,
        outline = textDim,
        outlineVariant = lerp(high, textDim, 0.35f),
    )
}

/** Fixed-width font everywhere for the retro styles (the system's monospace: no font file to ship). */
private val RetroTypography: androidx.compose.material3.Typography =
    androidx.compose.material3.Typography().run {
        val f = androidx.compose.ui.text.font.FontFamily.Monospace
        copy(
            displayLarge = displayLarge.copy(fontFamily = f),
            displayMedium = displayMedium.copy(fontFamily = f),
            displaySmall = displaySmall.copy(fontFamily = f),
            headlineLarge = headlineLarge.copy(fontFamily = f),
            headlineMedium = headlineMedium.copy(fontFamily = f),
            headlineSmall = headlineSmall.copy(fontFamily = f),
            titleLarge = titleLarge.copy(fontFamily = f),
            titleMedium = titleMedium.copy(fontFamily = f),
            titleSmall = titleSmall.copy(fontFamily = f),
            bodyLarge = bodyLarge.copy(fontFamily = f),
            bodyMedium = bodyMedium.copy(fontFamily = f),
            bodySmall = bodySmall.copy(fontFamily = f),
            labelLarge = labelLarge.copy(fontFamily = f),
            labelMedium = labelMedium.copy(fontFamily = f),
            labelSmall = labelSmall.copy(fontFamily = f),
        )
    }

/** Horizontal scan lines over the screen (retro display styles at night): one cached drawing, no animation. */
@Composable
fun ScanLines(modifier: androidx.compose.ui.Modifier) {
    androidx.compose.foundation.layout.Spacer(
        modifier.drawWithCache {
            val step = 3.dp.toPx()
            val line = Color.Black.copy(alpha = 0.18f)
            onDrawWithContent {
                var y = 0f
                while (y < size.height) {
                    drawLine(line, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                    y += step
                }
            }
        },
    )
}
