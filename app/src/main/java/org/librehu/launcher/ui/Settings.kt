package org.librehu.launcher.ui

import android.Manifest
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import org.librehu.launcher.standby.ObdClockStyle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.launch
import org.librehu.launcher.R
import org.librehu.launcher.data.DrawerSort
import org.librehu.launcher.data.LauncherApp
import org.librehu.launcher.data.PhoneLabel
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
    DRIVING(R.string.driving_title),
    REGION(R.string.region_title),
    GENERAL(R.string.settings_general),
}

/** Launcher settings: customisation, standby clock, SOS, general (assistant, reset). */
@Composable
fun SettingsScreen(
    theme: ThemeStore,
    controller: ThemeController,
    actions: LauncherActions,
    apps: List<LauncherApp> = emptyList(),
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
            SettingsTab.LOOK -> LookSettings(theme, controller, actions, apps = apps)
            SettingsTab.STANDBY -> StandbySettingsPage(actions)
            SettingsTab.SOS -> SosSettingsPage(actions)
            SettingsTab.LOCK -> LockSettingsPage(actions)
            SettingsTab.DRIVING -> DrivingSettingsPage(actions)
            SettingsTab.REGION -> RegionSettingsPage()
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
    apps: List<LauncherApp> = emptyList(),
) {
    val s by theme.settings.collectAsStateWithLifecycle()
    var choosingHidden by remember { mutableStateOf(false) }
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
    SettingSection(stringResource(R.string.theme_style))
    SettingChoices(
        listOf(
            ThemeStyle.MODERN to stringResource(R.string.theme_style_modern),
            ThemeStyle.RETRO to stringResource(R.string.theme_style_retro),
        ),
        s.style,
    ) { st -> actions.setTheme { it.copy(style = st) } }
    if (s.style != ThemeStyle.MODERN) SettingHint(stringResource(R.string.theme_style_hint))
    SettingSection(stringResource(R.string.theme_font))
    SettingChoices(
        listOf(false to stringResource(R.string.theme_font_normal), true to stringResource(R.string.theme_font_segment)),
        s.segmentFont,
    ) { seg -> actions.setTheme { it.copy(segmentFont = seg) } }
    SettingHint(stringResource(R.string.theme_font_hint))
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
                        .then(
                            if (a == Accent.CUSTOM) {
                                // The colour wheel: any colour, set below.
                                Modifier.background(androidx.compose.ui.graphics.Brush.sweepGradient(RAINBOW))
                            } else {
                                Modifier.background(a.color(dark))
                            },
                        )
                        .border(if (selected) 4.dp else 0.dp, CarColors.Text, CircleShape)
                        .clickable { actions.setTheme { it.copy(accent = a) } },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, null, tint = CarColors.OnAccent, modifier = Modifier.size(28.dp))
            }
        }
    }

    if (s.accent == Accent.CUSTOM) RgbPicker(s.customAccent) { argb -> actions.setTheme { it.copy(customAccent = argb) } }

    SettingSection(stringResource(R.string.home_pages))
    SettingChoices(
        (1..org.librehu.launcher.data.ThemeStore.MAX_HOME_PAGES).map { it to "$it" },
        s.homePages,
    ) { n -> actions.setTheme { it.copy(homePages = n) } }
    SettingHint(stringResource(R.string.home_pages_hint))

    SettingSection(stringResource(R.string.rail_position))
    SettingChoices(
        listOf(RailPosition.LEFT to stringResource(R.string.rail_left), RailPosition.BOTTOM to stringResource(R.string.rail_bottom)),
        s.rail,
    ) { r -> actions.setTheme { it.copy(rail = r) } }
    SettingSwitch(stringResource(R.string.right_hand_drive), stringResource(R.string.right_hand_drive_hint), s.rightHandDrive) { on ->
        actions.setTheme { it.copy(rightHandDrive = on) }
    }
    SettingSwitch(stringResource(R.string.widget_layout), stringResource(R.string.widget_layout_hint), s.widgetLayout) { on ->
        actions.setTheme { it.copy(widgetLayout = on) }
    }
    if (s.widgetLayout) SettingChoice(stringResource(R.string.widget_sizes_reset), false, actions.resetWidgetSizes)
    SettingSection(stringResource(R.string.rail_opacity))
    SettingChoices(
        org.librehu.launcher.data.ThemeStore.RAIL_OPACITIES.map { it to "$it %" },
        s.railOpacity,
    ) { o -> actions.setTheme { it.copy(railOpacity = o) } }
    SettingHint(stringResource(R.string.rail_opacity_hint))
    if (full) {
        SettingSwitch(stringResource(R.string.bar_phone), stringResource(R.string.bar_phone_hint), s.showPhoneStatus) { on ->
            actions.setTheme { it.copy(showPhoneStatus = on) }
        }
        if (s.showPhoneStatus) {
            SettingChoices(
                listOf(
                    PhoneLabel.OPERATOR to stringResource(R.string.phone_label_operator),
                    PhoneLabel.NAME to stringResource(R.string.phone_label_name),
                    PhoneLabel.BOTH to stringResource(R.string.phone_label_both),
                    PhoneLabel.NONE to stringResource(R.string.phone_label_none),
                ),
                s.phoneLabel,
            ) { l -> actions.setTheme { it.copy(phoneLabel = l) } }
            SettingSwitch(
                stringResource(R.string.phone_percent),
                stringResource(R.string.phone_percent_hint),
                s.phoneBatteryPercent,
            ) { on ->
                actions.setTheme { it.copy(phoneBatteryPercent = on) }
            }
        }
        SettingSwitch(stringResource(R.string.bar_gps), stringResource(R.string.bar_gps_hint), s.showGps) { on ->
            if (on) actions.requestPermissions(GPS_PERMISSIONS)
            actions.setTheme { it.copy(showGps = on) }
        }
        SettingSwitch(stringResource(R.string.bar_power), stringResource(R.string.bar_power_hint), s.showPower) { on ->
            actions.setTheme { it.copy(showPower = on) }
        }
        SettingSwitch(stringResource(R.string.bar_volume), stringResource(R.string.bar_volume_hint), s.showVolume) { on ->
            actions.setTheme { it.copy(showVolume = on) }
        }

        SettingSwitch(stringResource(R.string.themed_icons), stringResource(R.string.themed_icons_hint), s.themedIcons) { on ->
            actions.setTheme { it.copy(themedIcons = on) }
        }
        SettingSwitch(stringResource(R.string.aa_live_video), stringResource(R.string.aa_live_video_hint), s.aaLiveVideo) { on ->
            actions.setTheme { it.copy(aaLiveVideo = on) }
        }
        AaLowLatencySwitch()

        SettingSection(stringResource(R.string.drawer_title))
        SettingChoices(
            listOf(
                56 to stringResource(R.string.drawer_small),
                72 to stringResource(R.string.drawer_medium),
                92 to stringResource(R.string.drawer_large),
            ),
            s.drawerIconSize,
        ) { v -> actions.setTheme { it.copy(drawerIconSize = v) } }
        SettingChoices(
            listOf(
                DrawerSort.NAME to stringResource(R.string.drawer_sort_name),
                DrawerSort.NAME_DESC to stringResource(R.string.drawer_sort_name_desc),
                DrawerSort.PINNED_FIRST to stringResource(R.string.drawer_sort_pinned),
            ),
            s.drawerSort,
        ) { v -> actions.setTheme { it.copy(drawerSort = v) } }
        SettingSwitch(
            stringResource(R.string.drawer_labels),
            null,
            s.drawerLabels,
        ) { on -> actions.setTheme { it.copy(drawerLabels = on) } }
        SettingSwitch(stringResource(R.string.drawer_search_switch), null, s.drawerSearch) { on ->
            actions.setTheme { it.copy(drawerSearch = on) }
        }
        SettingChoice(stringResource(R.string.drawer_hidden_choose, s.hiddenApps.size), false) { choosingHidden = true }
        SettingHint(stringResource(R.string.drawer_hidden_hint))
        if (choosingHidden) {
            HiddenAppsDialog(apps, s.hiddenApps, { key, hide ->
                actions.setTheme { it.copy(hiddenApps = if (hide) it.hiddenApps + key else it.hiddenApps - key) }
            }) { choosingHidden = false }
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
        // Car stereo display animations (dot matrix), in the theme's colours: made for the retro styles.
        for ((kind, label) in RETRO_SCENES) {
            SettingChoice(stringResource(label), w == kind) { actions.setWallpaper(kind) }
        }
        SettingChoice(stringResource(R.string.wallpaper_online), w == WallpaperKind.ONLINE) { actions.setWallpaper(WallpaperKind.ONLINE) }
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
    if (w == WallpaperKind.ONLINE) {
        SettingHint(stringResource(R.string.wallpaper_online_source))
        SettingChoices(
            listOf(
                org.librehu.launcher.data.OnlineSource.BING to stringResource(R.string.wallpaper_online_bing),
                org.librehu.launcher.data.OnlineSource.WIKIMEDIA to stringResource(R.string.wallpaper_online_wikimedia),
                org.librehu.launcher.data.OnlineSource.PICSUM to stringResource(R.string.wallpaper_online_picsum),
            ),
            s.onlineSource,
        ) { v -> actions.setTheme { it.copy(onlineSource = v) } }
        if (s.onlineSource == org.librehu.launcher.data.OnlineSource.BING) {
            SettingHint(stringResource(R.string.wallpaper_online_market))
            SettingChoices(
                org.librehu.launcher.data.OnlineWallpaper.BING_MARKETS.map {
                    it to if (it == "auto") stringResource(R.string.wallpaper_online_market_auto) else it
                },
                s.bingMarket,
            ) { v -> actions.setTheme { it.copy(bingMarket = v) } }
        }
        SettingHint(stringResource(R.string.wallpaper_online_refresh))
        SettingChoices(
            listOf(
                org.librehu.launcher.data.OnlineRefresh.DAILY to stringResource(R.string.wallpaper_online_daily),
                org.librehu.launcher.data.OnlineRefresh.SIX_HOURS to stringResource(R.string.wallpaper_online_6h),
                org.librehu.launcher.data.OnlineRefresh.EACH_START to stringResource(R.string.wallpaper_online_start),
            ),
            s.onlineRefresh,
        ) { v -> actions.setTheme { it.copy(onlineRefresh = v) } }
        SettingSwitch(stringResource(R.string.wallpaper_online_caption), null, s.onlineCaption) { on ->
            actions.setTheme { it.copy(onlineCaption = on) }
        }
        SettingChoice(stringResource(R.string.wallpaper_online_now), false, actions.renewOnlineWallpaper)
        SettingHint(stringResource(R.string.wallpaper_online_hint))
    }
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
        SettingSwitch(stringResource(R.string.standby_phone), stringResource(R.string.standby_phone_hint), s.showPhone) { on ->
            store.update { it.copy(showPhone = on) }
        }
        SettingSwitch(stringResource(R.string.standby_car_battery), stringResource(R.string.standby_car_battery_hint), s.showCarBattery) { on ->
            store.update { it.copy(showCarBattery = on) }
        }
        StandbyObdSettings(s) { change -> store.update(change) }
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

/** OBD values under the clock: the chosen ones in order (up, remove), adding from the list, line or tiles, names. */
@Composable
private fun StandbyObdSettings(
    s: StandbySettings,
    update: ((StandbySettings) -> StandbySettings) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    SettingSection(stringResource(R.string.standby_obd))
    SettingHint(stringResource(R.string.standby_obd_hint))
    s.obdValues.forEachIndexed { i, key ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CarColors.SurfaceHigh).padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}.  " + obdClockName(key), color = CarColors.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (i > 0) {
                IconButton(onClick = {
                    update { it.copy(obdValues = it.obdValues.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                }) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.standby_obd_up), tint = CarColors.Text) }
            }
            IconButton(onClick = { update { it.copy(obdValues = it.obdValues - key) } }) {
                Icon(Icons.Default.Close, stringResource(R.string.standby_obd_remove), tint = CarColors.TextDim)
            }
        }
    }
    if (s.obdValues.size < StandbySettings.MAX_OBD) {
        Row { SettingChoice(stringResource(R.string.standby_obd_add), false) { adding = true } }
    }
    if (s.obdValues.isNotEmpty()) {
        SettingChoices(
            listOf(
                ObdClockStyle.LINE to stringResource(R.string.standby_obd_line),
                ObdClockStyle.TILES to stringResource(R.string.standby_obd_tiles),
            ),
            s.obdStyle,
        ) { st -> update { it.copy(obdStyle = st) } }
        SettingSwitch(stringResource(R.string.standby_obd_labels), null, s.obdLabels) { on -> update { it.copy(obdLabels = on) } }
    }
    if (adding) {
        val live = rememberObdLive()
        val choices = obdClockChoices(live).filter { it.first !in s.obdValues }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.standby_obd_add)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(choices.size) { n ->
                        val (key, name) = choices[n]
                        Text(
                            name + if (key.startsWith("car:")) "  (" + key.removePrefix("car:").substringBefore('/') + ")" else "",
                            color = CarColors.Text,
                            fontSize = 18.sp,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        update { it.copy(obdValues = (it.obdValues + key).take(StandbySettings.MAX_OBD)) }
                                        adding = false
                                    }.padding(vertical = 12.dp, horizontal = 8.dp),
                        )
                    }
                    item { SettingHint(stringResource(R.string.standby_obd_car_hint)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

// --- SOS ---------------------------------------------------------------------------------------------------------

/**
 * The car's name and description, kept by LibreHU-service and given to the other LibreHU apps (SOS screen, Android
 * Auto, CarPlay): typed once here (first setup) or in the service's Véhicule tab.
 */
@Composable
fun VehicleSettingsPage() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val info by org.librehu.launcher.data.VehicleLink.info
        .collectAsStateWithLifecycle()
    var available by remember { mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        available =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                org.librehu.launcher.data.VehicleLink.available(context).also {
                    if (it) {
                        org.librehu.launcher.data.VehicleLink
                            .refresh(context)
                    }
                }
            }
    }
    if (available == false) {
        SettingHint(stringResource(R.string.vehicle_no_service))
        return
    }
    val current = info ?: return
    var name by remember(current.name) { mutableStateOf(current.name) }
    var make by remember(current.make) { mutableStateOf(current.make) }
    var model by remember(current.model) { mutableStateOf(current.model) }
    var year by remember(current.year) { mutableStateOf(current.year) }
    var color by remember(current.color) { mutableStateOf(current.color) }
    var plate by remember(current.plate) { mutableStateOf(current.plate) }
    var fuel by remember(current.fuel) { mutableStateOf(current.fuel) }
    var side by remember(current.driverSide) { mutableStateOf(current.driverSide) }
    var saved by remember { mutableStateOf<Boolean?>(null) }
    SettingHint(stringResource(R.string.vehicle_hint))
    SettingText(stringResource(R.string.vehicle_name), name) { name = it.take(64) }
    SettingHint(stringResource(R.string.vehicle_name_hint))
    SettingText(stringResource(R.string.vehicle_make), make) { make = it.take(40) }
    SettingText(stringResource(R.string.vehicle_model), model) { model = it.take(40) }
    SettingText(stringResource(R.string.vehicle_year), year, KeyboardType.Number) { year = it.filter(Char::isDigit).take(4) }
    SettingText(stringResource(R.string.vehicle_color), color) { color = it.take(40) }
    SettingText(stringResource(R.string.vehicle_plate), plate) { plate = it.uppercase().take(16) }
    SettingText(stringResource(R.string.vehicle_fuel), fuel) { fuel = it.take(40) }
    SettingSection(stringResource(R.string.vehicle_driver_side))
    SettingChoices(
        listOf("left" to stringResource(R.string.vehicle_driver_left), "right" to stringResource(R.string.vehicle_driver_right)),
        side,
    ) { side = it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingChoice(stringResource(R.string.vehicle_save), true) {
            scope.launch {
                saved =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        org.librehu.launcher.data.VehicleLink.write(
                            context,
                            mapOf(
                                "make" to make,
                                "model" to model,
                                "year" to year,
                                "color" to color,
                                "plate" to plate,
                                "fuel" to fuel,
                                "driver_side" to side,
                            ) + (if (name.isNotBlank() && name != current.name) mapOf("name" to name) else emptyMap()),
                        )
                    }
            }
        }
        Spacer(Modifier.width(16.dp))
        saved?.let { ok -> SettingHint(stringResource(if (ok) R.string.vehicle_saved else R.string.vehicle_not_saved)) }
    }
}

@Composable
fun SosSettingsPage(
    actions: LauncherActions,
    full: Boolean = true,
) {
    val context = LocalContext.current
    val store = SosStore.get(context)
    val s by store.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Int?>(null) }
    SettingHint(stringResource(R.string.sos_hint))
    SettingText(stringResource(R.string.sos_number), s.number, KeyboardType.Phone) { v -> store.update { it.copy(number = v.trim()) } }
    SettingHint(stringResource(R.string.sos_number_hint))
    val ownNumber = remember { org.librehu.launcher.status.FindPhone.number(context) }
    SettingHint(
        if (ownNumber.isNotBlank()) stringResource(R.string.sos_own_number_settings, ownNumber) else stringResource(R.string.sos_own_number_none),
    )
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
    val context = LocalContext.current
    var phoneNumber by remember {
        mutableStateOf(
            org.librehu.launcher.status.FindPhone
                .typedNumber(context),
        )
    }
    val detected =
        remember {
            org.librehu.launcher.status.FindPhone
                .detectedNumber(context)
        }
    SettingSection(stringResource(R.string.find_phone))
    SettingHint(stringResource(R.string.find_phone_hint))
    SettingText(
        if (detected.isNotEmpty()) {
            stringResource(
                R.string.find_phone_number_detected,
                detected,
            )
        } else {
            stringResource(R.string.find_phone_number)
        },
        phoneNumber,
        KeyboardType.Phone,
    ) { v ->
        phoneNumber = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(20)
        org.librehu.launcher.status.FindPhone
            .setNumber(context, phoneNumber)
    }
    SettingSection(stringResource(R.string.setup_title))
    SettingHint(stringResource(R.string.setup_rerun_hint))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingChoice(stringResource(R.string.setup_rerun), false) { actions.show(Screen.SETUP) }
        SettingChoice(stringResource(R.string.setup_default_home), false, actions.openHomeSettings)
    }
    SettingSection(stringResource(R.string.backup_title))
    SettingHint(stringResource(R.string.backup_hint))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingChoice(stringResource(R.string.backup_export), false, actions.exportSettings)
        SettingChoice(stringResource(R.string.backup_import), false, actions.importSettings)
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

/** Every app with a switch: off = hidden from the drawer (and the search). */
@Composable
private fun HiddenAppsDialog(
    apps: List<LauncherApp>,
    hidden: Set<String>,
    onChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarColors.Surface,
        title = { Text(stringResource(R.string.drawer_hidden_title), color = CarColors.Text) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(sorted, key = { it.key }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChange(app.key, app.key !in hidden) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(app.icon, null, Modifier.size(40.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(app.label, color = CarColors.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = app.key !in hidden,
                            onCheckedChange = { shown -> onChange(app.key, !shown) },
                            colors = SwitchDefaults.colors(checkedTrackColor = CarColors.Accent, checkedThumbColor = CarColors.OnAccent),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.drawer_hiding_done), color = CarColors.Accent) } },
    )
}

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

/**
 * Android Auto's decoder low-latency mode (Open Headunit LibreHU, `debug-video-low-latency`), for the full screen and
 * the dashboard card alike: read and written through the fork's settings commands. Hidden when the fork does not answer.
 */
@Composable
private fun AaLowLatencySwitch() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { on = org.librehu.launcher.data.AndroidAutoLink.videoLowLatency(context) }
    val value = on ?: return
    SettingSwitch(stringResource(R.string.aa_low_latency), stringResource(R.string.aa_low_latency_hint), value) { v ->
        scope.launch { if (org.librehu.launcher.data.AndroidAutoLink.setVideoLowLatency(context, v)) on = v }
    }
}

/** Car stereo display animations offered as wallpapers. */
private val RETRO_SCENES =
    listOf(
        WallpaperKind.RETRO_DOLPHIN to R.string.wallpaper_retro_dolphin,
        WallpaperKind.RETRO_ANALYZER to R.string.wallpaper_retro_analyzer,
        WallpaperKind.RETRO_CASSETTE to R.string.wallpaper_retro_cassette,
        WallpaperKind.RETRO_ROAD to R.string.wallpaper_retro_road,
        WallpaperKind.RETRO_RALLY to R.string.wallpaper_retro_rally,
        WallpaperKind.RETRO_DEMO to R.string.wallpaper_retro_demo,
    )

private val RAINBOW =
    listOf(
        androidx.compose.ui.graphics.Color.Red,
        androidx.compose.ui.graphics.Color.Yellow,
        androidx.compose.ui.graphics.Color.Green,
        androidx.compose.ui.graphics.Color.Cyan,
        androidx.compose.ui.graphics.Color.Blue,
        androidx.compose.ui.graphics.Color.Magenta,
        androidx.compose.ui.graphics.Color.Red,
    )

/**
 * The custom colour by its red, green and blue (0..255 each), with a preview and its hexadecimal value; saved when a
 * slider is let go (the theme, the apps and the retro display's pixels follow it).
 */
@Composable
private fun RgbPicker(
    argb: Int,
    onChange: (Int) -> Unit,
) {
    var r by remember(argb) { androidx.compose.runtime.mutableIntStateOf(android.graphics.Color.red(argb)) }
    var g by remember(argb) { androidx.compose.runtime.mutableIntStateOf(android.graphics.Color.green(argb)) }
    var b by remember(argb) { androidx.compose.runtime.mutableIntStateOf(android.graphics.Color.blue(argb)) }
    fun save() = onChange(android.graphics.Color.rgb(r, g, b))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color(r, g, b)),
        )
        Text(String.format("#%02X%02X%02X", r, g, b), color = CarColors.Text, fontSize = 20.sp)
    }
    for ((label, value, tint, set) in listOf(
        RgbChannel(stringResource(R.string.rgb_red), r, androidx.compose.ui.graphics.Color.Red) { v: Int -> r = v },
        RgbChannel(stringResource(R.string.rgb_green), g, androidx.compose.ui.graphics.Color.Green) { v: Int -> g = v },
        RgbChannel(stringResource(R.string.rgb_blue), b, androidx.compose.ui.graphics.Color.Blue) { v: Int -> b = v },
    )) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = CarColors.Text, fontSize = 17.sp, modifier = Modifier.width(96.dp))
            androidx.compose.material3.Slider(
                value = value.toFloat(),
                onValueChange = { set(it.toInt()) },
                onValueChangeFinished = { save() },
                valueRange = 0f..255f,
                colors =
                    androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = tint,
                        activeTrackColor = tint,
                        inactiveTrackColor = CarColors.SurfaceHigh,
                    ),
                modifier = Modifier.weight(1f),
            )
            Text("$value", color = CarColors.TextDim, fontSize = 16.sp, modifier = Modifier.width(48.dp))
        }
    }
}

private data class RgbChannel(
    val label: String,
    val value: Int,
    val tint: androidx.compose.ui.graphics.Color,
    val set: (Int) -> Unit,
)
