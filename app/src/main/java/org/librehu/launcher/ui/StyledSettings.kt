package org.librehu.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Settings drawn in the factory (MIB) and ambient (LHU-Link) styles. The pages stay the same composables; their
 * building blocks (SettingSection, SettingChoice, SettingSwitch…) ask [settingLook] and draw themselves like the
 * original systems: light grey bars with dark text and square check boxes for the factory style, black rows between
 * thin separators with framed buttons for the ambient style.
 */

internal enum class SettingLook { MATERIAL, FACTORY, AMBIENT }

internal val settingLook: SettingLook
    get() =
        when (CarColors.palette.style) {
            ThemeStyle.FACTORY -> SettingLook.FACTORY
            ThemeStyle.AMBIENT -> SettingLook.AMBIENT
            else -> SettingLook.MATERIAL
        }

// Factory light bar: pale grey, a touch darker at the bottom, dark text.
private val BAR_LIGHT_TOP = Color(0xFFDCDDDF)
private val BAR_LIGHT_BOTTOM = Color(0xFFC6C7C9)
private val BAR_TEXT = Color(0xFF1C1D1F)
private val BAR_VALUE = Color(0xFF55575B)
private val AMBIENT_LINE = Color(0xFF3A414B)

// The original's mark of what is selected / in force (radio preset, ON / OFF): red whatever the ambient colour.
private val SELECTION = Color(0xFFCE0300)
private val SELECTION_GLOW = Color(0xFF8B0F00)

/**
 * The page frame of the styled settings: a title with a back key when on a page, the accent line under it, the list
 * of pages ([entries], by name) or the open page scrolled.
 */
@Composable
internal fun StyledSettingsFrame(
    title: String,
    entries: List<String>,
    open: Int?,
    onOpen: (Int?) -> Unit,
    page: @Composable (Int) -> Unit,
) {
    val look = settingLook
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (open != null) {
                FramedKey(onClick = { onOpen(null) }) { Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.width(18.dp))
            }
            Text(
                if (open == null) title else entries[open],
                color = Color.White,
                fontSize = if (look == SettingLook.FACTORY) 32.sp else 26.sp,
                fontWeight = if (look == SettingLook.FACTORY) FontWeight.SemiBold else FontWeight.Light,
                fontFamily = if (look == SettingLook.FACTORY) FactoryFont else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().height(if (look == SettingLook.FACTORY) 2.dp else 1.dp).background(CarColors.Accent))
        Column(
            Modifier
                .fillMaxSize()
                .then(if (look == SettingLook.AMBIENT) Modifier.padding(top = 8.dp).border(BorderStroke(1.dp, CarColors.Accent)) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (look == SettingLook.FACTORY) 10.dp else 6.dp),
        ) {
            if (open == null) {
                entries.forEachIndexed { i, name -> StyledEntry(name) { onOpen(i) } }
            } else {
                page(open)
            }
        }
    }
}

/** A line of the list of pages: a light bar (factory) or a row with an arrow (ambient). */
@Composable
private fun StyledEntry(
    name: String,
    onClick: () -> Unit,
) {
    if (settingLook == SettingLook.FACTORY) {
        LightBar(onClick) { Text(name, color = BAR_TEXT, fontFamily = FactoryFont, fontSize = 26.sp, modifier = Modifier.weight(1f)) }
    } else {
        AmbientRow(onClick) {
            Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

/** Factory light bar: pale grey gradient, dark text, darker while pressed. */
@Composable
internal fun LightBar(
    onClick: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(Brush.verticalGradient(if (pressed) listOf(BAR_LIGHT_BOTTOM, Color(0xFFAFB0B2)) else listOf(BAR_LIGHT_TOP, BAR_LIGHT_BOTTOM)))
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Ambient row: black, white text, a thin separator under it, the ambient light while pressed. */
@Composable
internal fun AmbientRow(
    onClick: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = CarColors.Accent
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .drawBehind { if (pressed) drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.6f)))) }
                .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(AMBIENT_LINE))
    }
}

/** A key in a thin white frame (factory back key, "Reset"…), or a sheen button with an accent edge (ambient). */
@Composable
internal fun FramedKey(
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = CarColors.Accent
    val m =
        if (settingLook == SettingLook.FACTORY) {
            Modifier
                .background(if (pressed) Color(0xFF26282C) else Color.Black)
                .border(BorderStroke(1.5.dp, if (selected || pressed) accent else Color(0xFFE6E6E6)))
        } else {
            Modifier
                .background(Brush.verticalGradient(0.0225f to Color(0xFF75716C), 0.4581f to Color.Black, 1f to if (selected || pressed) accent else accent.copy(alpha = 0.45f).compositeOverBlack()))
                .border(BorderStroke(1.dp, accent))
        }
    Box(
        m.heightIn(min = 56.dp).clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun Color.compositeOverBlack(): Color = Color(red * alpha, green * alpha, blue * alpha, 1f)

/** Section title of a page. */
@Composable
internal fun StyledSection(title: String) {
    if (settingLook == SettingLook.FACTORY) {
        Text(title, color = Color.White, fontFamily = FactoryFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
    } else {
        Column(Modifier.padding(top = 14.dp)) {
            Text(title, color = CarColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.Light)
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(1.dp).background(CarColors.Accent.copy(alpha = 0.6f)))
        }
    }
}

/** One choice among others: a framed key, the chosen one in the accent colour. */
@Composable
internal fun StyledChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FramedKey(selected, onClick) {
        Text(
            label,
            // Factory: the chosen key in the accent colour; ambient: lit from the bottom, its text stays white.
            color = if (selected && settingLook == SettingLook.FACTORY) CarColors.Accent else Color.White,
            fontFamily = if (settingLook == SettingLook.FACTORY) FactoryFont else null,
            fontWeight = if (settingLook == SettingLook.FACTORY) FontWeight.Normal else FontWeight.Light,
            fontSize = 20.sp,
        )
    }
}

/** On / off: a light bar with a square check box (factory), a row with an ON / OFF key (ambient). */
@Composable
internal fun StyledSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    if (settingLook == SettingLook.FACTORY) {
        LightBar({ onChange(!checked) }) {
            Column(Modifier.weight(1f)) {
                Text(title, color = BAR_TEXT, fontFamily = FactoryFont, fontSize = 24.sp)
                if (subtitle != null) Text(subtitle, color = BAR_VALUE, fontFamily = FactoryFont, fontSize = 16.sp)
            }
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(30.dp).border(BorderStroke(2.dp, BAR_TEXT)), contentAlignment = Alignment.Center) {
                if (checked) Icon(Icons.Outlined.Check, null, tint = BAR_TEXT, modifier = Modifier.size(26.dp))
            }
        }
    } else {
        AmbientRow({ onChange(!checked) }) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
                if (subtitle != null) Text(subtitle, color = Color(0xFF98A3B0), fontSize = 15.sp)
            }
            Spacer(Modifier.width(16.dp))
            // ON | OFF keys like the original: both brushed, the state in force marked by a red bar under its word.
            Row(Modifier.border(BorderStroke(1.dp, CarColors.Accent))) {
                for (on in listOf(true, false)) {
                    val active = on == checked
                    Box(
                        Modifier
                            .width(68.dp)
                            .height(46.dp)
                            .background(Brush.verticalGradient(0.0225f to Color(0xFF75716C), 0.4581f to Color.Black, 1f to CarColors.Accent.copy(alpha = 0.6f).compositeOverBlack())),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (on) "ON" else "OFF", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Light)
                        if (active) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 5.dp)
                                    .width(44.dp)
                                    .height(4.dp)
                                    .background(SELECTION_GLOW)
                                    .border(BorderStroke(1.dp, SELECTION)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * [androidx.compose.material3.AlertDialog] in the style: square black box in a thin white frame (factory), night blue
 * framed in the accent colour (ambient); the Material dialog otherwise. Same slots.
 */
@Composable
internal fun CarAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    val look = settingLook
    if (look == SettingLook.MATERIAL) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
        )
        return
    }
    val accent = CarColors.Accent
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Color.White,
            androidx.compose.material3.LocalTextStyle provides
                androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = if (look == SettingLook.FACTORY) FactoryFont else null,
                    fontWeight = if (look == SettingLook.FACTORY) FontWeight.Normal else FontWeight.Light,
                ),
        ) {
            Column(
                Modifier
                    .width(560.dp)
                    .background(if (look == SettingLook.FACTORY) Color.Black else Color(0xF20E1626))
                    .border(BorderStroke(if (look == SettingLook.FACTORY) 1.5.dp else 1.dp, if (look == SettingLook.FACTORY) Color(0xFFE6E6E6) else accent)),
            ) {
                if (icon != null || title != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            icon()
                            Spacer(Modifier.width(16.dp))
                        }
                        if (title != null) {
                            androidx.compose.runtime.CompositionLocalProvider(
                                androidx.compose.material3.LocalTextStyle provides
                                    androidx.compose.material3.LocalTextStyle.current.copy(
                                        fontSize = 24.sp,
                                        fontWeight = if (look == SettingLook.FACTORY) FontWeight.SemiBold else FontWeight.Light,
                                    ),
                            ) { title() }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(if (look == SettingLook.FACTORY) accent else accent.copy(alpha = 0.8f)))
                }
                if (text != null) {
                    Box(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) { text() }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (look == SettingLook.FACTORY) Color(0xFF3A3B3F) else accent.copy(alpha = 0.8f)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    dismissButton?.invoke()
                    Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}
