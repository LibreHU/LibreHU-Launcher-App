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

data class CarPalette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val textDim: Color,
) {
    companion object {
        /** [translucent]: cards let a wallpaper show through. */
        fun of(
            dark: Boolean,
            accent: Accent,
            translucent: Boolean = false,
        ): CarPalette {
            val a = if (translucent) 0.86f else 1f
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
