package org.librehu.launcher.ui

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.launcher.R
import org.librehu.launcher.data.RailPosition
import org.librehu.launcher.data.SpectrumPalette
import org.librehu.launcher.data.ThemeController
import org.librehu.launcher.data.ThemeMode
import org.librehu.launcher.data.ThemeStore
import org.librehu.launcher.data.WallpaperKind
import org.librehu.launcher.lock.LockLook
import org.librehu.launcher.lock.LockStore
import org.librehu.launcher.sos.SosContact
import org.librehu.launcher.sos.SosSettings
import org.librehu.launcher.sos.SosStore
import org.librehu.launcher.standby.ClockStyle
import org.librehu.launcher.standby.StandbySettings
import org.librehu.launcher.standby.StandbyStore

private enum class SettingsTab(
    val label: Int,
) {
    LOOK(R.string.settings_look),
    STANDBY(R.string.standby_title),
    SOS(R.string.sos_settings),
    LOCK(R.string.lock_title),
    GENERAL(R.string.settings_general),
}

/** Launcher settings: customisation, standby clock, SOS, general (assistant, reset). */
@Composable
fun SettingsScreen(
    theme: ThemeStore,
    controller: ThemeController,
    actions: LauncherActions,
) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.LOOK) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings), color = CarColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        SettingChoices(SettingsTab.entries.map { it to stringResource(it.label) }, tab) { tab = it }
        when (tab) {
            SettingsTab.LOOK -> LookSettings(theme, controller, actions)
            SettingsTab.STANDBY -> StandbySettingsPage(actions)
            SettingsTab.SOS -> SosSettingsPage(actions)
            SettingsTab.LOCK -> LockSettingsPage(actions)
            SettingsTab.GENERAL -> GeneralSettings(actions)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// --- Customisation -----------------------------------------------------------------------------------------------

/** [full] = false: the short version shown by the first start assistant. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookSettings(
    theme: ThemeStore,
    controller: ThemeController,
    actions: LauncherActions,
    full: Boolean = true,
) {
    val s by theme.settings.collectAsStateWithLifecycle()
    val dark by controller.dark.collectAsStateWithLifecycle()
    val systemAllowed by controller.systemWideAllowed.collectAsStateWithLifecycle()

    SettingSection(stringResource(R.string.theme))
    SettingChoices(
        listOf(
            ThemeMode.LIGHT to stringResource(R.string.theme_light),
            ThemeMode.DARK to stringResource(R.string.theme_dark),
            ThemeMode.AUTO to stringResource(R.string.theme_auto),
        ),
        s.mode,
    ) { m -> actions.setTheme { it.copy(mode = m) } }
    SettingHint(
        stringResource(R.string.theme_auto_hint) + "  " + stringResource(if (dark) R.string.theme_now_dark else R.string.theme_now_light),
    )
    if (full) {
        SettingSwitch(
            stringResource(R.string.theme_system_wide),
            stringResource(if (s.systemWide && !systemAllowed) R.string.theme_system_wide_denied else R.string.theme_system_wide_hint),
            s.systemWide,
        ) { on -> actions.setTheme { it.copy(systemWide = on) } }
    }

    SettingSection(stringResource(R.string.accent))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Accent.entries.forEach { a ->
            val selected = a == s.accent
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(a.color(dark))
                        .border(if (selected) 4.dp else 0.dp, CarColors.Text, CircleShape)
                        .clickable { actions.setTheme { it.copy(accent = a) } },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, null, tint = CarColors.OnAccent, modifier = Modifier.size(28.dp))
            }
        }
    }

    SettingSection(stringResource(R.string.rail_position))
    SettingChoices(
        listOf(RailPosition.LEFT to stringResource(R.string.rail_left), RailPosition.BOTTOM to stringResource(R.string.rail_bottom)),
        s.rail,
    ) { r -> actions.setTheme { it.copy(rail = r) } }
    if (full) {
        SettingSwitch(stringResource(R.string.bar_phone), stringResource(R.string.bar_phone_hint), s.showPhoneStatus) { on ->
            actions.setTheme { it.copy(showPhoneStatus = on) }
        }
        SettingSwitch(stringResource(R.string.bar_gps), stringResource(R.string.bar_gps_hint), s.showGps) { on ->
            if (on) actions.requestPermissions(GPS_PERMISSIONS)
            actions.setTheme { it.copy(showGps = on) }
        }
        SettingSwitch(stringResource(R.string.bar_power), stringResource(R.string.bar_power_hint), s.showPower) { on ->
            actions.setTheme { it.copy(showPower = on) }
        }
    }

    SettingSection(stringResource(R.string.wallpaper))
    val w = s.wallpaper
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingChoice(stringResource(R.string.wallpaper_none), w == WallpaperKind.NONE) { actions.setWallpaper(WallpaperKind.NONE) }
        SettingChoice(
            stringResource(R.string.wallpaper_spectrum),
            w == WallpaperKind.SPECTRUM,
        ) { actions.setWallpaper(WallpaperKind.SPECTRUM) }
        SettingChoice(stringResource(R.string.wallpaper_aurora), w == WallpaperKind.AURORA) { actions.setWallpaper(WallpaperKind.AURORA) }
        SettingChoice(stringResource(R.string.wallpaper_stars), w == WallpaperKind.STARS) { actions.setWallpaper(WallpaperKind.STARS) }
        SettingChoice(stringResource(R.string.wallpaper_waves), w == WallpaperKind.WAVES) { actions.setWallpaper(WallpaperKind.WAVES) }
        if (full) {
            SettingChoice(
                stringResource(R.string.wallpaper_pick),
                w == WallpaperKind.IMAGE || w == WallpaperKind.ANIMATED,
                actions.pickImage,
            )
            SettingChoice(stringResource(R.string.wallpaper_video), w == WallpaperKind.VIDEO, actions.pickVideo)
            SettingChoice(
                stringResource(R.string.wallpaper_system),
                w == WallpaperKind.SYSTEM,
            ) { actions.setWallpaper(WallpaperKind.SYSTEM) }
        }
    }
    if (w == WallpaperKind.SYSTEM) SettingChoice(stringResource(R.string.wallpaper_live_choose), false, actions.chooseLiveWallpaper)
    if (w == WallpaperKind.SPECTRUM) {
        SettingHint(stringResource(R.string.spectrum_colors))
        SettingChoices(
            SpectrumPalette.entries.map {
                it to spectrumName(it)
            },
            s.spectrumPalette,
        ) { p -> actions.setTheme { it.copy(spectrumPalette = p) } }
        SettingSwitch(stringResource(R.string.spectrum_audio), stringResource(R.string.spectrum_audio_hint), s.spectrumAudio) { on ->
            if (on) actions.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))
            actions.setTheme { it.copy(spectrumAudio = on) }
        }
        if (s.spectrumAudio) {
            val status by SpectrumAudio.status.collectAsStateWithLifecycle()
            SettingHint(spectrumStatus(status))
        }
        SettingHint(stringResource(R.string.spectrum_credits))
    }
    if (full) SettingHint(stringResource(R.string.wallpaper_hint))
}

@Composable
private fun spectrumName(p: SpectrumPalette): String =
    stringResource(
        when (p) {
            SpectrumPalette.ACCENT -> R.string.spectrum_accent
            SpectrumPalette.ICE -> R.string.spectrum_ice
            SpectrumPalette.FIRE -> R.string.spectrum_fire
            SpectrumPalette.LIME -> R.string.spectrum_lime
            SpectrumPalette.MAGENTA -> R.string.spectrum_magenta
            SpectrumPalette.CYAN -> R.string.spectrum_cyan
            SpectrumPalette.VIOLET -> R.string.spectrum_violet
        },
    )

@Composable
private fun spectrumStatus(st: SpectrumAudio.Status): String =
    when (st.kind) {
        SpectrumAudio.Kind.OFF -> stringResource(R.string.spectrum_status_off)
        SpectrumAudio.Kind.NO_PERMISSION -> stringResource(R.string.spectrum_status_permission)
        SpectrumAudio.Kind.ERROR -> stringResource(R.string.spectrum_status_error, st.detail)
        SpectrumAudio.Kind.LISTENING -> stringResource(R.string.spectrum_status_listening)
        SpectrumAudio.Kind.SILENT -> stringResource(R.string.spectrum_status_silent)
        SpectrumAudio.Kind.SOUND -> stringResource(R.string.spectrum_status_sound)
    }

// --- Lock screen -------------------------------------------------------------------------------------------------

@Composable
fun LockSettingsPage(actions: LauncherActions) {
    val store = LockStore.get(LocalContext.current)
    val s by store.settings.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf(s.pin) }
    SettingHint(stringResource(R.string.lock_hint))
    SettingSection(stringResource(R.string.lock_look))
    SettingChoices(
        listOf(LockLook.CLOCK to stringResource(R.string.lock_look_clock), LockLook.BLACK to stringResource(R.string.lock_look_black)),
        s.look,
    ) { l -> store.update { it.copy(look = l) } }
    SettingSection(stringResource(R.string.lock_pin))
    SettingText(stringResource(R.string.lock_pin_label), pin, KeyboardType.NumberPassword) { v ->
        pin = v.filter { it.isDigit() }.take(8)
        store.update { it.copy(pin = pin) }
    }
    SettingHint(stringResource(R.string.lock_pin_hint))
    SettingSwitch(stringResource(R.string.lock_toggle), stringResource(R.string.lock_toggle_hint), s.toggle) { on ->
        store.update {
            it.copy(toggle = on)
        }
    }
    SettingSwitch(stringResource(R.string.standby_pause), stringResource(R.string.standby_pause_hint), s.pauseMedia) { on ->
        store.update { it.copy(pauseMedia = on) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingChoice(stringResource(R.string.lock_now), false) { actions.power(PowerChoice.LOCK) }
    }
}

// --- Standby clock -----------------------------------------------------------------------------------------------

@Composable
fun StandbySettingsPage(
    actions: LauncherActions,
    full: Boolean = true,
) {
    val store = StandbyStore.get(LocalContext.current)
    val s by store.settings.collectAsStateWithLifecycle()
    SettingHint(stringResource(R.string.standby_hint))
    SettingSection(stringResource(R.string.standby_idle))
    SettingChoices(
        StandbySettings.IDLE_CHOICES.map { it to if (it == 0) stringResource(R.string.never) else stringResource(R.string.minutes, it) },
        s.idleMinutes,
    ) { m -> store.update { it.copy(idleMinutes = m) } }
    SettingSection(stringResource(R.string.standby_style))
    SettingChoices(
        listOf(
            ClockStyle.DIGITAL to stringResource(R.string.standby_digital),
            ClockStyle.ANALOG to stringResource(R.string.standby_analog),
        ),
        s.style,
    ) { st -> store.update { it.copy(style = st) } }
    SettingSwitch(stringResource(R.string.standby_seconds), null, s.showSeconds) { on -> store.update { it.copy(showSeconds = on) } }
    SettingSwitch(stringResource(R.string.standby_date), null, s.showDate) { on -> store.update { it.copy(showDate = on) } }
    if (full) {
        SettingSwitch(stringResource(R.string.standby_media), stringResource(R.string.standby_media_hint), s.showMedia) { on ->
            store.update { it.copy(showMedia = on) }
        }
        SettingSwitch(stringResource(R.string.standby_accent), null, s.accentColor) { on -> store.update { it.copy(accentColor = on) } }
        SettingSwitch(stringResource(R.string.standby_pause), stringResource(R.string.standby_pause_hint), s.pauseMedia) { on ->
            store.update { it.copy(pauseMedia = on) }
        }
        SettingSwitch(stringResource(R.string.standby_shift), stringResource(R.string.standby_shift_hint), s.shift) { on ->
            store.update {
                it.copy(shift = on)
            }
        }
        SettingSection(stringResource(R.string.standby_brightness))
        SettingChoices(
            StandbySettings.BRIGHTNESS_CHOICES.map {
                it to if (it < 0) stringResource(R.string.standby_brightness_keep) else "${(it * 100).toInt()} %"
            },
            s.brightness,
        ) { b -> store.update { it.copy(brightness = b) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingChoice(stringResource(R.string.standby_show), false, actions.standby)
        SettingChoice(stringResource(R.string.standby_dream), false, actions.openDreamSettings)
    }
    SettingHint(stringResource(R.string.standby_dream_hint))
}

// --- SOS ---------------------------------------------------------------------------------------------------------

@Composable
fun SosSettingsPage(
    actions: LauncherActions,
    full: Boolean = true,
) {
    val store = SosStore.get(LocalContext.current)
    val s by store.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Int?>(null) }
    SettingHint(stringResource(R.string.sos_hint))
    SettingText(stringResource(R.string.sos_number), s.number, KeyboardType.Phone) { v -> store.update { it.copy(number = v.trim()) } }
    SettingHint(stringResource(R.string.sos_number_hint))
    SettingSection(stringResource(R.string.sos_button))
    SettingSwitch(stringResource(R.string.sos_on_rail), null, s.onRail) { on -> store.update { it.copy(onRail = on) } }
    SettingSwitch(stringResource(R.string.sos_on_dashboard), null, s.onDashboard) { on -> store.update { it.copy(onDashboard = on) } }
    SettingSwitch(stringResource(R.string.sos_long_press), stringResource(R.string.sos_long_press_hint), s.longPress) { on ->
        store.update { it.copy(longPress = on) }
    }
    SettingHint(stringResource(R.string.sos_widget_info))
    SettingSection(stringResource(R.string.sos_countdown_title))
    SettingChoices(
        SosSettings.COUNTDOWN_CHOICES.map {
            it to
                if (it == 0) stringResource(R.string.sos_no_auto) else stringResource(R.string.seconds, it)
        },
        s.countdown,
    ) { c -> store.update { it.copy(countdown = c) } }
    SettingSection(stringResource(R.string.sos_contacts))
    s.contacts.forEachIndexed { i, c ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { editing = i }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(c.name.ifBlank { c.number }, color = CarColors.Text, fontSize = 18.sp)
                Text(c.number, color = CarColors.TextDim, fontSize = 14.sp)
            }
            Icon(
                Icons.Default.Delete,
                stringResource(R.string.remove),
                tint = CarColors.TextDim,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .clickable { store.update { st -> st.copy(contacts = st.contacts.filterIndexed { j, _ -> j != i }) } }
                        .padding(8.dp),
            )
        }
    }
    if (s.contacts.size < SosSettings.MAX_CONTACTS) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(CarColors.SurfaceHigh)
                .clickable { editing = -1 }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, null, tint = CarColors.Accent)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sos_add_contact), color = CarColors.Text, fontSize = 18.sp)
        }
    }
    if (full) {
        SettingSection(stringResource(R.string.sos_info))
        SettingHint(stringResource(R.string.sos_info_hint))
        SettingText(stringResource(R.string.sos_owner), s.owner) { v -> store.update { it.copy(owner = v) } }
        SettingText(stringResource(R.string.sos_medical), s.medical) { v -> store.update { it.copy(medical = v) } }
        SettingText(stringResource(R.string.sos_vehicle), s.vehicle) { v -> store.update { it.copy(vehicle = v) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingChoice(stringResource(R.string.sos_permissions), false) {
            actions.requestPermissions(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.ACCESS_FINE_LOCATION))
        }
        SettingChoice(stringResource(R.string.sos_test), false, actions.sosTest)
    }
    SettingHint(stringResource(R.string.sos_call_hint))

    editing?.let { i ->
        val initial = s.contacts.getOrNull(i) ?: SosContact("", "")
        ContactDialog(initial, onDismiss = { editing = null }) { c ->
            store.update { st ->
                st.copy(
                    contacts =
                        if (i >=
                            0
                        ) {
                            st.contacts.mapIndexed { j, old -> if (j == i) c else old }
                        } else {
                            st.contacts + c
                        },
                )
            }
            editing = null
        }
    }
}

@Composable
private fun ContactDialog(
    initial: SosContact,
    onDismiss: () -> Unit,
    onSave: (SosContact) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var number by remember { mutableStateOf(initial.number) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sos_contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.sos_contact_name)) })
                OutlinedTextField(
                    number,
                    { number = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sos_contact_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = number.isNotBlank(), onClick = { onSave(SosContact(name.trim(), number.trim())) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// --- General -----------------------------------------------------------------------------------------------------

@Composable
private fun GeneralSettings(actions: LauncherActions) {
    var confirmReset by remember { mutableStateOf(false) }
    SettingSection(stringResource(R.string.setup_title))
    SettingHint(stringResource(R.string.setup_rerun_hint))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingChoice(stringResource(R.string.setup_rerun), false) { actions.show(Screen.SETUP) }
        SettingChoice(stringResource(R.string.setup_default_home), false, actions.openHomeSettings)
    }
    SettingSection(stringResource(R.string.reset))
    SettingChoice(stringResource(R.string.reset_all), false) { confirmReset = true }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_all)) },
            text = { Text(stringResource(R.string.reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    actions.resetAll()
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// --- Building blocks (also used by the first start assistant) ----------------------------------------------------

@Composable
fun SettingSection(title: String) {
    Text(title, color = CarColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
}

@Composable
fun SettingHint(text: String) = Text(text, color = CarColors.TextDim, fontSize = 15.sp)

@Composable
fun SettingChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (selected) CarColors.OnAccent else CarColors.Text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SettingChoices(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((value, label) in options) SettingChoice(label, value == selected) { onSelect(value) }
    }
}

@Composable
fun SettingSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onChange(!checked) }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = CarColors.Text, fontSize = 18.sp)
            if (subtitle != null) Text(subtitle, color = CarColors.TextDim, fontSize = 14.sp)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = CarColors.Accent, checkedThumbColor = CarColors.OnAccent),
        )
    }
}

/** Text setting, saved on each change. */
@Composable
fun SettingText(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}
