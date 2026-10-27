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

    /** Any colour, set with its red, green and blue ([org.librehu.launcher.data.ThemeSettings.customAccent]). */
    CUSTOM(0xFFFF9800, 0xFFFF9800),
    ;

    fun color(dark: Boolean) = Color(if (dark) onDark else onLight)
}

/**
 * Look of the launcher: the usual one, or a retro one (a display's colours drawn from the accent colour, scan lines on
 * screens at night). The font is chosen apart ([CarPalette.segment]).
 */
enum class ThemeStyle {
    MODERN,

    /** A car stereo display of the 80s-2000s (VFD, LCD, OEL) in the accent colour. */
    RETRO,

    /**
     * A factory infotainment screen: black, menu of tiles underlined in the accent colour, soft-key bars, status bar
     * on top, Source Sans 3 (FactoryUi.kt). Drawn with LibreHU's own icons, no maker's artwork or name.
     */
    FACTORY,
    ;

    companion object {
        /** Stored name, the three retro styles of before (amber, green, cassette) read as [RETRO]. */
        fun parse(name: String?): ThemeStyle =
            when {
                name?.startsWith("RETRO") == true -> RETRO
                name == "FACTORY" -> FACTORY
                else -> MODERN
            }
    }
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
    /** Segment display font everywhere (LibreHU Seg14), any style. */
    val segment: Boolean = false,
) {
    val retro: Boolean get() = style == ThemeStyle.RETRO

    /** Scan lines over the screen (retro, at night). */
    val scanLines: Boolean get() = dark && retro

    companion object {
        /**
         * Retro palette from the accent colour, as a display lit in that colour: at night a near-black glass tinted by
         * it and text in it; by day a pale panel tinted by it and dark text of its hue.
         */
        private fun retro(
            dark: Boolean,
            accent: Color,
            a: Float,
            segment: Boolean,
        ): CarPalette {
            val black = Color(0xFF050505)
            val white = Color(0xFFF7F5F0)
            return if (dark) {
                CarPalette(
                    dark = true,
                    background = lerp(black, accent, 0.05f),
                    surface = lerp(black, accent, 0.10f).copy(alpha = a),
                    surfaceHigh = lerp(black, accent, 0.18f),
                    accent = accent,
                    onAccent = lerp(black, accent, 0.10f),
                    text = lerp(accent, Color.White, 0.25f),
                    textDim = lerp(black, accent, 0.62f),
                    style = ThemeStyle.RETRO,
                    segment = segment,
                )
            } else {
                val deep = lerp(Color.Black, accent, 0.45f)
                CarPalette(
                    dark = false,
                    background = lerp(white, accent, 0.14f),
                    surface = lerp(white, accent, 0.07f).copy(alpha = a),
                    surfaceHigh = lerp(white, accent, 0.22f),
                    accent = deep,
                    onAccent = white,
                    text = lerp(Color.Black, accent, 0.25f),
                    textDim = lerp(Color.Black, accent, 0.5f).copy(alpha = 0.8f),
                    style = ThemeStyle.RETRO,
                    segment = segment,
                )
            }
        }

        /** Factory screen: always black, nearly black tiles, white text, the accent for lines and pressed icons. */
        private fun factory(
            accent: Color,
            segment: Boolean,
        ) = CarPalette(
            dark = true,
            background = Color(0xFF000000),
            surface = Color(0xFF16171A),
            surfaceHigh = Color(0xFF26282C),
            accent = accent,
            onAccent = Color(0xFF000000),
            text = Color(0xFFF2F2F2),
            textDim = Color(0xFFA9ABAF),
            style = ThemeStyle.FACTORY,
            segment = segment,
        )

        /** [translucent]: cards let a wallpaper show through. */
        fun of(
            dark: Boolean,
            accent: Color,
            translucent: Boolean = false,
            style: ThemeStyle = ThemeStyle.MODERN,
            segment: Boolean = false,
        ): CarPalette {
            val a = if (translucent) 0.86f else 1f
            if (style == ThemeStyle.RETRO) return retro(dark, accent, a, segment)
            if (style == ThemeStyle.FACTORY) return factory(accent, segment)
            return if (dark) {
                CarPalette(
                    dark = true,
                    background = Color(0xFF000000),
                    surface = Color(0xFF1E1F22).copy(alpha = a),
                    surfaceHigh = Color(0xFF2B2D31),
                    accent = accent,
                    onAccent = Color(0xFF202124),
                    text = Color(0xFFE8EAED),
                    textDim = Color(0xFF9AA0A6),
                    segment = segment,
                )
            } else {
                CarPalette(
                    dark = false,
                    background = Color(0xFFF1F3F4),
                    surface = Color(0xFFFFFFFF).copy(alpha = a),
                    surfaceHigh = Color(0xFFE8EAED),
                    accent = accent,
                    onAccent = Color(0xFFFFFFFF),
                    text = Color(0xFF202124),
                    textDim = Color(0xFF5F6368),
                    segment = segment,
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
    var palette: CarPalette by mutableStateOf(CarPalette.of(true, org.librehu.launcher.ui.Accent.BLUE.color(true)))

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
        typography =
            when {
                p.segment -> RetroTypography
                p.style == ThemeStyle.FACTORY -> FactoryTypography
                else -> androidx.compose.material3.Typography()
            },
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

/**
 * Segment display font everywhere when chosen: DSEG14, the one of LibreHU-Disc's retro display, with the
 * accented letters drawn bare (capitals without accents, as on a car stereo's display; tools/make_retro_font.py).
 */
private val RetroTypography: androidx.compose.material3.Typography =
    androidx.compose.material3.Typography().run {
        val f = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(org.librehu.launcher.R.font.librehu_seg14))
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

/**
 * Colours of the screens that stay dark (standby clock, lock screen): black and white in the modern style, the retro
 * display's night colours (from the accent, segment font, scan lines) in the retro style.
 */
class NightLook(
    val retro: Boolean,
    val background: Color,
    val text: Color,
    val dim: Color,
    /** Panel behind the PIN pad, keys and the slider's track. */
    val panel: Color,
    val key: Color,
)

@Composable
fun nightLook(): NightLook {
    val p = CarColors.palette
    if (!p.retro) return NightLook(false, Color.Black, Color(0xFFE8EAED), Color(0xFF9AA0A6), Color(0xCC202124), Color(0x33FFFFFF))
    val n = if (p.dark) p else androidx.compose.runtime.remember(p.accent, p.segment) { CarPalette.of(true, p.accent, style = ThemeStyle.RETRO, segment = p.segment) }
    return NightLook(true, n.background, n.text, n.textDim, n.surface, n.surfaceHigh)
}

/** Source Sans 3 (Adobe, SIL Open Font License 1.1, third_party/SourceSans3-LICENSE.md) for the factory style. */
val FactoryFont: androidx.compose.ui.text.font.FontFamily =
    androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.font.Font(org.librehu.launcher.R.font.source_sans3_regular, androidx.compose.ui.text.font.FontWeight.Normal),
        androidx.compose.ui.text.font.Font(org.librehu.launcher.R.font.source_sans3_semibold, androidx.compose.ui.text.font.FontWeight.SemiBold),
        androidx.compose.ui.text.font.Font(org.librehu.launcher.R.font.source_sans3_semibold, androidx.compose.ui.text.font.FontWeight.Bold),
    )

private val FactoryTypography: androidx.compose.material3.Typography =
    androidx.compose.material3.Typography().run {
        val f = FactoryFont
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
