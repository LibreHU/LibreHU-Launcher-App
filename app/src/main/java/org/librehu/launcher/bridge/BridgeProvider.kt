package org.librehu.launcher.bridge

import android.content.Context
import android.content.Intent
import org.librehu.launcher.R
import org.librehu.launcher.data.DrawerSort
import org.librehu.launcher.data.OnlineRefresh
import org.librehu.launcher.data.OnlineSource
import org.librehu.launcher.data.OnlineWallpaper
import org.librehu.launcher.data.PhoneLabel
import org.librehu.launcher.data.RailPosition
import org.librehu.launcher.data.SpectrumPalette
import org.librehu.launcher.data.ThemeMode
import org.librehu.launcher.data.ThemeStore
import org.librehu.launcher.data.WallpaperKind
import org.librehu.launcher.lock.LockActivity
import org.librehu.launcher.lock.LockLook
import org.librehu.launcher.lock.LockStore
import org.librehu.launcher.sos.SosActivity
import org.librehu.launcher.sos.SosSettings
import org.librehu.launcher.sos.SosStore
import org.librehu.launcher.speed.LimitSound
import org.librehu.launcher.speed.LimitSpeedSource
import org.librehu.launcher.speed.SpeedLimitManager
import org.librehu.launcher.standby.ClockStyle
import org.librehu.launcher.standby.ObdClockStyle
import org.librehu.launcher.standby.StandbyActivity
import org.librehu.launcher.standby.StandbySettings
import org.librehu.launcher.standby.StandbyStore
import org.librehu.launcher.tpms.CarInvert
import org.librehu.launcher.tpms.TpmsManager
import org.librehu.launcher.tpms.TpmsSettings
import org.librehu.launcher.ui.Accent
import org.librehu.launcher.ui.ThemeStyle
import org.librehu.launcher.weather.WeatherRepo
import org.librehu.launcher.weather.WeatherSettings

/**
 * Every setting of the launcher for LibreHU's phone app (through LibreHU-service's bridge, [BridgeKitProvider]):
 * look (theme, style, font, colour), wallpaper, home screen and shortcut bar, app drawer, standby clock, lock screen,
 * SOS, driving (weather, speed limits) and tyre pressures. Same labels and same stores as the launcher's screens.
 * Not here: pictures and videos (picked on the head unit), hidden apps, OBD values of the standby clock, SOS contacts
 * and vehicle details (the vehicle sheet is on LibreHU-service's Vehicle page).
 */
class BridgeProvider : BridgeKitProvider() {
    override fun pages(context: Context): List<KitPage> =
        listOf(look(context), wallpaper(context), home(context), drawer(context), standby(context), lock(context), sos(context), driving(context), tpms(context))

    private fun look(c: Context): KitPage {
        val store = ThemeStore.get(c)
        val t = store.settings.value
        return page(c, "look", c.getString(R.string.settings_look), "palette") {
            pick("mode", s(R.string.theme), t.mode, label = {
                s(
                    when (it) {
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                        ThemeMode.AUTO -> R.string.theme_auto
                    },
                )
            }) { v -> store.update { it.copy(mode = v) } }
            switch("system_wide", s(R.string.theme_system_wide), t.systemWide, s(R.string.theme_system_wide_hint)) { v -> store.update { it.copy(systemWide = v) } }
            pick("style", s(R.string.theme_style), t.style, s(R.string.theme_style_hint), label = {
                s(
                    when (it) {
                        ThemeStyle.RETRO -> R.string.theme_style_retro
                        ThemeStyle.FACTORY -> R.string.theme_style_factory
                        ThemeStyle.MODERN -> R.string.theme_style_modern
                    },
                )
            }) { v -> store.update { it.copy(style = v) } }
            choice(
                "font",
                s(R.string.theme_font),
                t.segmentFont,
                listOf(false to s(R.string.theme_font_normal), true to s(R.string.theme_font_segment)),
                s(R.string.theme_font_hint),
            ) { v -> store.update { it.copy(segmentFont = v) } }
            pick("accent", s(R.string.accent), t.accent, label = { s(ACCENT_NAMES.getValue(it)) }) { v -> store.update { it.copy(accent = v) } }
            if (t.accent == Accent.CUSTOM) {
                val argb = t.customAccent
                fun channel(
                    id: String,
                    label: Int,
                    shift: Int,
                ) = slider(id, s(label), (argb shr shift) and 0xFF, 0, 255) { v ->
                    store.update { it.copy(customAccent = (it.customAccent and (0xFF shl shift).inv()) or (v shl shift) or (0xFF shl 24)) }
                }
                channel("red", R.string.rgb_red, 16)
                channel("green", R.string.rgb_green, 8)
                channel("blue", R.string.rgb_blue, 0)
            }
            switch("themed_icons", s(R.string.themed_icons), t.themedIcons, s(R.string.themed_icons_hint)) { v -> store.update { it.copy(themedIcons = v) } }
        }
    }

    private fun wallpaper(c: Context): KitPage {
        val store = ThemeStore.get(c)
        val t = store.settings.value
        return page(c, "wallpaper", c.getString(R.string.wallpaper), "image") {
            // Pictures and videos are picked on the head unit; the current one stays offered.
            val kinds =
                WALLPAPERS.filter { it.first !in PICKED || it.first == t.wallpaper }.map { (k, r) -> k to s(r) }
            choice("kind", s(R.string.wallpaper), t.wallpaper, kinds, s(R.string.wallpaper_hint)) { v -> store.update { it.copy(wallpaper = v) } }
            if (org.librehu.launcher.ui.gpuDrawn(t.wallpaper)) {
                switch("gpu", s(R.string.gpu_animations), t.gpuAnimations, s(R.string.gpu_animations_hint)) { v -> store.update { it.copy(gpuAnimations = v) } }
            }
            if (t.wallpaper == WallpaperKind.SPECTRUM) {
                pick("spectrum_palette", s(R.string.spectrum_colors), t.spectrumPalette, label = { s(SPECTRUM_NAMES.getValue(it)) }) { v ->
                    store.update { it.copy(spectrumPalette = v) }
                }
                switch("spectrum_audio", s(R.string.spectrum_audio), t.spectrumAudio, s(R.string.spectrum_audio_hint)) { v -> store.update { it.copy(spectrumAudio = v) } }
            }
            if (t.wallpaper == WallpaperKind.ONLINE) {
                pick("online_source", s(R.string.wallpaper_online_source), t.onlineSource, label = {
                    s(
                        when (it) {
                            OnlineSource.BING -> R.string.wallpaper_online_bing
                            OnlineSource.WIKIMEDIA -> R.string.wallpaper_online_wikimedia
                            OnlineSource.PICSUM -> R.string.wallpaper_online_picsum
                        },
                    )
                }) { v -> store.update { it.copy(onlineSource = v) } }
                if (t.onlineSource == OnlineSource.BING) {
                    choice(
                        "bing_market",
                        s(R.string.wallpaper_online_market),
                        t.bingMarket,
                        OnlineWallpaper.BING_MARKETS.map { it to if (it == "auto") s(R.string.wallpaper_online_market_auto) else it },
                    ) { v -> store.update { it.copy(bingMarket = v) } }
                }
                pick("online_refresh", s(R.string.wallpaper_online_refresh), t.onlineRefresh, label = {
                    s(
                        when (it) {
                            OnlineRefresh.DAILY -> R.string.wallpaper_online_daily
                            OnlineRefresh.SIX_HOURS -> R.string.wallpaper_online_6h
                            OnlineRefresh.EACH_START -> R.string.wallpaper_online_start
                        },
                    )
                }) { v -> store.update { it.copy(onlineRefresh = v) } }
                switch("online_caption", s(R.string.wallpaper_online_caption), t.onlineCaption) { v -> store.update { it.copy(onlineCaption = v) } }
            }
        }
    }

    private fun home(c: Context): KitPage {
        val store = ThemeStore.get(c)
        val t = store.settings.value
        return page(c, "home", c.getString(R.string.settings_general), "home") {
            slider("home_pages", s(R.string.home_pages), t.homePages, 1, ThemeStore.MAX_HOME_PAGES, hint = s(R.string.home_pages_hint)) { v ->
                store.update { it.copy(homePages = v) }
            }
            switch("widget_layout", s(R.string.widget_layout), t.widgetLayout, s(R.string.widget_layout_hint)) { v -> store.update { it.copy(widgetLayout = v) } }
            switch("right_hand_drive", s(R.string.right_hand_drive), t.rightHandDrive, s(R.string.right_hand_drive_hint)) { v ->
                store.update { it.copy(rightHandDrive = v) }
            }
            section(s(R.string.rail_position))
            pick("rail", s(R.string.rail_position), t.rail, label = { s(if (it == RailPosition.LEFT) R.string.rail_left else R.string.rail_bottom) }) { v ->
                store.update { it.copy(rail = v) }
            }
            choice("rail_opacity", s(R.string.rail_opacity), t.railOpacity, ThemeStore.RAIL_OPACITIES.map { it to "$it %" }, s(R.string.rail_opacity_hint)) { v ->
                store.update { it.copy(railOpacity = v) }
            }
            switch("phone", s(R.string.bar_phone), t.showPhoneStatus, s(R.string.bar_phone_hint)) { v -> store.update { it.copy(showPhoneStatus = v) } }
            if (t.showPhoneStatus) {
                pick("phone_label", s(R.string.bar_phone), t.phoneLabel, label = {
                    s(
                        when (it) {
                            PhoneLabel.OPERATOR -> R.string.phone_label_operator
                            PhoneLabel.NAME -> R.string.phone_label_name
                            PhoneLabel.BOTH -> R.string.phone_label_both
                            PhoneLabel.NONE -> R.string.phone_label_none
                        },
                    )
                }) { v -> store.update { it.copy(phoneLabel = v) } }
                switch("phone_percent", s(R.string.phone_percent), t.phoneBatteryPercent, s(R.string.phone_percent_hint)) { v ->
                    store.update { it.copy(phoneBatteryPercent = v) }
                }
            }
            switch("gps", s(R.string.bar_gps), t.showGps, s(R.string.bar_gps_hint)) { v -> store.update { it.copy(showGps = v) } }
            switch("power", s(R.string.bar_power), t.showPower, s(R.string.bar_power_hint)) { v -> store.update { it.copy(showPower = v) } }
            switch("volume", s(R.string.bar_volume), t.showVolume, s(R.string.bar_volume_hint)) { v -> store.update { it.copy(showVolume = v) } }
            switch("aa_live_video", s(R.string.aa_live_video), t.aaLiveVideo, s(R.string.aa_live_video_hint)) { v -> store.update { it.copy(aaLiveVideo = v) } }
            switch("aa_turn_popup", s(R.string.aa_turn_popup), t.aaTurnPopup, s(R.string.aa_turn_popup_hint)) { v -> store.update { it.copy(aaTurnPopup = v) } }
        }
    }

    private fun drawer(c: Context): KitPage {
        val store = ThemeStore.get(c)
        val t = store.settings.value
        return page(c, "drawer", c.getString(R.string.drawer_title), "apps") {
            choice(
                "icon_size",
                s(R.string.drawer_title),
                t.drawerIconSize,
                listOf(56 to s(R.string.drawer_small), 72 to s(R.string.drawer_medium), 92 to s(R.string.drawer_large)),
            ) { v -> store.update { it.copy(drawerIconSize = v) } }
            switch("labels", s(R.string.drawer_labels), t.drawerLabels) { v -> store.update { it.copy(drawerLabels = v) } }
            pick("sort", s(R.string.drawer_title), t.drawerSort, label = {
                s(
                    when (it) {
                        DrawerSort.NAME -> R.string.drawer_sort_name
                        DrawerSort.NAME_DESC -> R.string.drawer_sort_name_desc
                        DrawerSort.PINNED_FIRST -> R.string.drawer_sort_pinned
                    },
                )
            }) { v -> store.update { it.copy(drawerSort = v) } }
            switch("search", s(R.string.drawer_search_switch), t.drawerSearch) { v -> store.update { it.copy(drawerSearch = v) } }
        }
    }

    private fun standby(c: Context): KitPage {
        val store = StandbyStore.get(c)
        val t = store.settings.value
        return page(c, "standby", c.getString(R.string.standby_title), "clock") {
            choice(
                "idle",
                s(R.string.standby_idle),
                t.idleMinutes,
                StandbySettings.IDLE_CHOICES.map { it to if (it == 0) s(R.string.never) else "$it ${s(R.string.minutes)}" },
                s(R.string.standby_hint),
            ) { v -> store.update { it.copy(idleMinutes = v) } }
            pick("style", s(R.string.standby_style), t.style, label = { s(if (it == ClockStyle.ANALOG) R.string.standby_analog else R.string.standby_digital) }) { v ->
                store.update { it.copy(style = v) }
            }
            switch("seconds", s(R.string.standby_seconds), t.showSeconds) { v -> store.update { it.copy(showSeconds = v) } }
            switch("date", s(R.string.standby_date), t.showDate) { v -> store.update { it.copy(showDate = v) } }
            switch("media", s(R.string.standby_media), t.showMedia, s(R.string.standby_media_hint)) { v -> store.update { it.copy(showMedia = v) } }
            switch("phone", s(R.string.standby_phone), t.showPhone, s(R.string.standby_phone_hint)) { v -> store.update { it.copy(showPhone = v) } }
            switch("car_battery", s(R.string.standby_car_battery), t.showCarBattery, s(R.string.standby_car_battery_hint)) { v ->
                store.update { it.copy(showCarBattery = v) }
            }
            pick("obd_style", s(R.string.standby_obd), t.obdStyle, label = { s(if (it == ObdClockStyle.TILES) R.string.standby_obd_tiles else R.string.standby_obd_line) }) { v ->
                store.update { it.copy(obdStyle = v) }
            }
            switch("obd_labels", s(R.string.standby_obd_labels), t.obdLabels) { v -> store.update { it.copy(obdLabels = v) } }
            switch("accent", s(R.string.standby_accent), t.accentColor) { v -> store.update { it.copy(accentColor = v) } }
            choice(
                "brightness",
                s(R.string.standby_brightness),
                t.brightness,
                StandbySettings.BRIGHTNESS_CHOICES.map {
                    it to if (it == StandbySettings.KEEP_BRIGHTNESS) s(R.string.standby_brightness_keep) else "${(it * 100).toInt()} %"
                },
            ) { v -> store.update { it.copy(brightness = v) } }
            switch("pause_media", s(R.string.standby_pause), t.pauseMedia, s(R.string.standby_pause_hint)) { v -> store.update { it.copy(pauseMedia = v) } }
            switch("shift", s(R.string.standby_shift), t.shift, s(R.string.standby_shift_hint)) { v -> store.update { it.copy(shift = v) } }
            button("show", s(R.string.standby_show)) { StandbyActivity.show(c) }
        }
    }

    private fun lock(c: Context): KitPage {
        val store = LockStore.get(c)
        val t = store.settings.value
        return page(c, "lock", c.getString(R.string.lock_title), "lock") {
            text("pin", s(R.string.lock_pin_label), t.pin, 8, s(R.string.lock_pin_hint)) { v -> store.update { it.copy(pin = v.filter(Char::isDigit)) } }
            pick("look", s(R.string.lock_look), t.look, label = { s(if (it == LockLook.BLACK) R.string.lock_look_black else R.string.lock_look_clock) }) { v ->
                store.update { it.copy(look = v) }
            }
            switch("pause_media", s(R.string.standby_pause), t.pauseMedia) { v -> store.update { it.copy(pauseMedia = v) } }
            switch("toggle", s(R.string.lock_toggle), t.toggle, s(R.string.lock_toggle_hint)) { v -> store.update { it.copy(toggle = v) } }
            button("lock_now", s(R.string.lock_now), confirm = true) { LockActivity.lock(c) }
        }
    }

    private fun sos(c: Context): KitPage {
        val store = SosStore.get(c)
        val t = store.settings.value
        return page(c, "sos", c.getString(R.string.sos_settings), "sos") {
            text("number", s(R.string.sos_number), t.number, 16, s(R.string.sos_number_hint)) { v ->
                v.filter { it.isDigit() || it == '+' }.takeIf { it.isNotEmpty() }?.let { n -> store.update { it.copy(number = n) } }
            }
            choice(
                "countdown",
                s(R.string.sos_countdown_title),
                t.countdown,
                SosSettings.COUNTDOWN_CHOICES.map { it to if (it == 0) s(R.string.sos_no_auto) else "$it ${s(R.string.seconds)}" },
            ) { v -> store.update { it.copy(countdown = v) } }
            switch("on_rail", s(R.string.sos_on_rail), t.onRail) { v -> store.update { it.copy(onRail = v) } }
            switch("on_dashboard", s(R.string.sos_on_dashboard), t.onDashboard) { v -> store.update { it.copy(onDashboard = v) } }
            switch("long_press", s(R.string.sos_long_press), t.longPress, s(R.string.sos_long_press_hint)) { v -> store.update { it.copy(longPress = v) } }
            button("test", s(R.string.sos_test)) {
                c.startActivity(Intent(c, SosActivity::class.java).putExtra(SosActivity.EXTRA_TEST, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun driving(c: Context): KitPage {
        val weather = WeatherRepo.get(c)
        val limits = SpeedLimitManager.get(c)
        val w = weather.settings.value
        val l = limits.settings.value
        return page(c, "driving", c.getString(R.string.driving_title), "car") {
            section(s(R.string.weather_title))
            switch("weather", s(R.string.weather_enable), w.enabled, s(R.string.weather_enable_hint)) { v -> weather.update { it.copy(enabled = v) } }
            switch("weather_bar", s(R.string.weather_status_bar), w.statusBar) { v -> weather.update { it.copy(statusBar = v) } }
            switch("fahrenheit", s(R.string.bridge_fahrenheit), w.fahrenheit) { v -> weather.update { it.copy(fahrenheit = v) } }
            choice("weather_refresh", s(R.string.weather_refresh), w.refreshMinutes, WeatherSettings.REFRESH_CHOICES.map { it to "$it ${s(R.string.minutes_short)}" }) { v ->
                weather.update { it.copy(refreshMinutes = v) }
            }
            button("weather_now", s(R.string.weather_refresh_now)) { weather.refresh() }
            section(s(R.string.limit_title))
            switch("limit", s(R.string.limit_enable), l.enabled, s(R.string.limit_enable_hint)) { v -> limits.update { it.copy(enabled = v) } }
            switch("limit_alert", s(R.string.limit_alert), l.alert, s(R.string.limit_alert_hint)) { v -> limits.update { it.copy(alert = v) } }
            switch("limit_bar", s(R.string.limit_status_bar), l.statusBar) { v -> limits.update { it.copy(statusBar = v) } }
            slider("limit_tolerance", s(R.string.limit_tolerance), l.toleranceKmh, 0, 20, unit = "km/h", hint = s(R.string.limit_tolerance_hint)) { v ->
                limits.update { it.copy(toleranceKmh = v) }
            }
            slider("limit_tolerance_pct", s(R.string.limit_tolerance), l.tolerancePercent, 0, 20, unit = "%") { v -> limits.update { it.copy(tolerancePercent = v) } }
            slider("limit_delay", s(R.string.limit_delay), l.delaySeconds, 0, 10, unit = "s") { v -> limits.update { it.copy(delaySeconds = v) } }
            pick("limit_sound", s(R.string.limit_sound), l.sound, label = { s(LIMIT_SOUNDS.getValue(it)) }) { v -> limits.update { it.copy(sound = v) } }
            slider("limit_volume", s(R.string.limit_sound), l.volume, 0, 100, unit = "%") { v -> limits.update { it.copy(volume = v) } }
            choice(
                "limit_repeat",
                s(R.string.limit_repeat),
                l.repeatSeconds,
                listOf(0, 5, 10, 30).map { it to if (it == 0) s(R.string.limit_repeat_once) else "$it s" },
            ) { v -> limits.update { it.copy(repeatSeconds = v) } }
            switch("limit_chime", s(R.string.limit_change_chime), l.changeChime, s(R.string.limit_change_chime_hint)) { v -> limits.update { it.copy(changeChime = v) } }
            pick("limit_source", s(R.string.limit_source), l.source, s(R.string.limit_source_hint), label = {
                s(
                    when (it) {
                        LimitSpeedSource.AUTO -> R.string.limit_source_auto
                        LimitSpeedSource.GPS -> R.string.speed_gps
                        LimitSpeedSource.OBD -> R.string.speed_obd
                    },
                )
            }) { v -> limits.update { it.copy(source = v) } }
            text("limit_server", s(R.string.limit_server), l.server, 200) { v ->
                if (v.startsWith("https://")) limits.update { it.copy(server = v) }
            }
            button("limit_test", s(R.string.limit_sound_test)) { limits.test() }
        }
    }

    private fun tpms(c: Context): KitPage {
        val m = TpmsManager.get(c)
        val t = m.settings.value
        fun up(f: (TpmsSettings) -> TpmsSettings) = m.updateSettings(f)
        return page(c, "tpms", c.getString(R.string.tpms_title), "tire") {
            switch("enabled", s(R.string.tpms_on_dashboard), t.enabled) { v -> up { it.copy(enabled = v) } }
            switch("ble", s(R.string.tpms_ble_enable), t.bleEnabled, s(R.string.tpms_ble_hint)) { v -> up { it.copy(bleEnabled = v) } }
            section(s(R.string.tpms_thresholds))
            slider("low", s(R.string.tpms_low_threshold), t.lowKpa, 100, 300, 5, "kPa") { v -> up { it.copy(lowKpa = v) } }
            slider("high", s(R.string.tpms_high_threshold), t.highKpa, 200, 450, 5, "kPa") { v -> up { it.copy(highKpa = v) } }
            slider("hot", s(R.string.tpms_hot_threshold), t.highCelsius, 40, 120, 1, "°C") { v -> up { it.copy(highCelsius = v) } }
            section(s(R.string.tpms_display))
            pick("unit", s(R.string.tpms_display), t.unit, label = { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { v -> up { it.copy(unit = v) } }
            switch("fahrenheit", s(R.string.bridge_fahrenheit), t.fahrenheit) { v -> up { it.copy(fahrenheit = v) } }
            switch("spare", s(R.string.tpms_show_spare), t.showSpare) { v -> up { it.copy(showSpare = v) } }
            choice("refresh", s(R.string.tpms_refresh), t.refreshSec, TpmsSettings.REFRESH_CHOICES.map { it to "$it s" }, s(R.string.tpms_refresh_hint)) { v ->
                up { it.copy(refreshSec = v) }
            }
            section(s(R.string.tpms_alarms))
            switch("alarms", s(R.string.tpms_alarms), t.alarms) { v -> up { it.copy(alarms = v) } }
            switch("sound", s(R.string.tpms_sound_on), t.alarmSound) { v -> up { it.copy(alarmSound = v) } }
            choice(
                "repeat",
                s(R.string.tpms_sound_repeat),
                t.soundRepeatSec,
                TpmsSettings.REPEAT_CHOICES.map { it to if (it == 0) s(R.string.tpms_sound_once) else "$it s" },
            ) { v -> up { it.copy(soundRepeatSec = v) } }
            section(s(R.string.tpms_car))
            slider("front_axle", s(R.string.tpms_front_axle), t.frontAxle, 0, 50, unit = "%") { v -> up { it.copy(frontAxle = v) } }
            slider("rear_axle", s(R.string.tpms_rear_axle), t.rearAxle, 50, 100, unit = "%") { v -> up { it.copy(rearAxle = v) } }
            pick("invert", s(R.string.tpms_car_invert), t.carInvert, label = {
                s(
                    when (it) {
                        CarInvert.NONE -> R.string.tpms_car_invert_none
                        CarInvert.IN_LIGHT -> R.string.tpms_car_invert_light
                        CarInvert.IN_DARK -> R.string.tpms_car_invert_dark
                    },
                )
            }) { v -> up { it.copy(carInvert = v) } }
        }
    }

    private companion object {
        val ACCENT_NAMES =
            mapOf(
                Accent.BLUE to R.string.bridge_accent_blue,
                Accent.TEAL to R.string.bridge_accent_teal,
                Accent.GREEN to R.string.bridge_accent_green,
                Accent.YELLOW to R.string.bridge_accent_yellow,
                Accent.ORANGE to R.string.bridge_accent_orange,
                Accent.RED to R.string.bridge_accent_red,
                Accent.PINK to R.string.bridge_accent_pink,
                Accent.PURPLE to R.string.bridge_accent_purple,
                Accent.CUSTOM to R.string.bridge_accent_custom,
            )

        val SPECTRUM_NAMES =
            mapOf(
                SpectrumPalette.ACCENT to R.string.spectrum_accent,
                SpectrumPalette.ICE to R.string.spectrum_ice,
                SpectrumPalette.FIRE to R.string.spectrum_fire,
                SpectrumPalette.LIME to R.string.spectrum_lime,
                SpectrumPalette.MAGENTA to R.string.spectrum_magenta,
                SpectrumPalette.CYAN to R.string.spectrum_cyan,
                SpectrumPalette.VIOLET to R.string.spectrum_violet,
            )

        val LIMIT_SOUNDS =
            mapOf(
                LimitSound.BEEP to R.string.limit_sound_beep,
                LimitSound.DOUBLE to R.string.limit_sound_double,
                LimitSound.TRIPLE to R.string.limit_sound_triple,
                LimitSound.CHIME to R.string.limit_sound_chime,
                LimitSound.ALARM to R.string.limit_sound_alarm,
                LimitSound.NOTIFICATION to R.string.limit_sound_notification,
                LimitSound.NONE to R.string.limit_sound_none,
            )

        /** Wallpapers with a file picked on the head unit. */
        val PICKED = setOf(WallpaperKind.IMAGE, WallpaperKind.ANIMATED, WallpaperKind.VIDEO)

        val WALLPAPERS =
            listOf(
                WallpaperKind.NONE to R.string.wallpaper_none,
                WallpaperKind.SPECTRUM to R.string.wallpaper_spectrum,
                WallpaperKind.AURORA to R.string.wallpaper_aurora,
                WallpaperKind.STARS to R.string.wallpaper_stars,
                WallpaperKind.WAVES to R.string.wallpaper_waves,
                WallpaperKind.RETRO_DOLPHIN to R.string.wallpaper_retro_dolphin,
                WallpaperKind.RETRO_ANALYZER to R.string.wallpaper_retro_analyzer,
                WallpaperKind.RETRO_CASSETTE to R.string.wallpaper_retro_cassette,
                WallpaperKind.RETRO_ROAD to R.string.wallpaper_retro_road,
                WallpaperKind.RETRO_RALLY to R.string.wallpaper_retro_rally,
                WallpaperKind.RETRO_GALAXY to R.string.wallpaper_retro_galaxy,
                WallpaperKind.RETRO_DEMO to R.string.wallpaper_retro_demo,
                WallpaperKind.VAPORWAVE to R.string.wallpaper_vaporwave,
                WallpaperKind.ONLINE to R.string.wallpaper_online,
                WallpaperKind.SYSTEM to R.string.wallpaper_system,
                WallpaperKind.IMAGE to R.string.wallpaper_pick,
                WallpaperKind.ANIMATED to R.string.wallpaper_pick,
                WallpaperKind.VIDEO to R.string.wallpaper_video,
            )
    }
}
