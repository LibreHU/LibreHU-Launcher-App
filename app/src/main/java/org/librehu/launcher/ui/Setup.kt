package org.librehu.launcher.ui

import android.Manifest
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.librehu.launcher.R
import org.librehu.launcher.data.ThemeController
import org.librehu.launcher.data.ThemeStore
import org.librehu.launcher.system.RootGrants
import org.librehu.launcher.system.RootManager

private enum class SetupStep(
    val title: Int,
) {
    WELCOME(R.string.setup_welcome),
    REGION(R.string.region_title),
    VEHICLE(R.string.vehicle_title),
    LOOK(R.string.settings_look),
    ACCESS(R.string.setup_access),
    LIBREHU(R.string.setup_librehu),
    STANDBY(R.string.standby_title),
    SOS(R.string.sos_settings),
    BLUETOOTH(R.string.bt_setup_title),
    DONE(R.string.setup_done),
}

/**
 * First start assistant (and Settings → General): root warning (SuperSU, Magisk, KernelSU…) and root for every LibreHU app at once, language,
 * time zone, date and time, the car (name and description, kept by LibreHU-service for every LibreHU app), appearance
 * and rail position, Android permissions, updates, the other LibreHU apps (backup, wheel keys, sound menu, button
 * backlight), standby clock, SOS, then the phone's Bluetooth pairing step by
 * step. Every step can be skipped; everything stays in the settings.
 */
@Composable
fun SetupScreen(
    theme: ThemeStore,
    controller: ThemeController,
    actions: LauncherActions,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = SetupStep.entries[index]
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(CarColors.Surface)
                    .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(step.title),
                    color = CarColors.Text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                for (i in SetupStep.entries.indices) {
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (i == index) CarColors.Accent else CarColors.SurfaceHigh),
                    )
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    SetupStep.WELCOME -> {
                        SettingHint(stringResource(R.string.setup_welcome_text))
                        RootWarning()
                    }

                    SetupStep.REGION -> {
                        RegionSettingsPage()
                    }

                    SetupStep.VEHICLE -> {
                        VehicleSettingsPage()
                    }

                    SetupStep.LOOK -> {
                        LookSettings(theme, controller, actions, full = false)
                    }

                    SetupStep.ACCESS -> {
                        SettingHint(stringResource(R.string.setup_access_text))
                        SetupItem(
                            stringResource(R.string.setup_default_home),
                            stringResource(R.string.setup_default_home_hint),
                            actions.openHomeSettings,
                        )
                        SetupItem(stringResource(R.string.setup_media), stringResource(R.string.media_access), actions.grantMediaAccess)
                        val context = LocalContext.current
                        context.packageManager.getLaunchIntentForPackage("org.librehu.ota")?.let { ota ->
                            SetupItem(stringResource(R.string.setup_updates), stringResource(R.string.setup_updates_hint)) {
                                runCatching { context.startActivity(ota.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                        SetupItem(stringResource(R.string.setup_permissions), stringResource(R.string.setup_permissions_hint)) {
                            actions.requestPermissions(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CALL_PHONE),
                            )
                        }
                    }

                    SetupStep.LIBREHU -> {
                        LibreHuFeatures()
                    }

                    SetupStep.STANDBY -> {
                        StandbySettingsPage(actions, full = false)
                    }

                    SetupStep.SOS -> {
                        SosSettingsPage(actions, full = false)
                    }

                    SetupStep.BLUETOOTH -> {
                        BluetoothSetupPage()
                        PhoneAppPairing(qrCode = false)
                    }

                    SetupStep.DONE -> {
                        SettingHint(stringResource(R.string.setup_done_text))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step != SetupStep.DONE) SettingChoice(stringResource(R.string.setup_skip_all), false, actions.finishSetup)
                Spacer(Modifier.weight(1f))
                if (index > 0) SettingChoice(stringResource(R.string.setup_back), false) { index-- }
                if (step == SetupStep.DONE) {
                    SettingChoice(stringResource(R.string.setup_finish), true, actions.finishSetup)
                } else {
                    SettingChoice(stringResource(if (index == 0) R.string.setup_start else R.string.setup_next), true) { index++ }
                }
            }
        }
    }
}

@Composable
private fun SetupItem(
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.SurfaceHigh)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = CarColors.Text, fontSize = 18.sp)
            Text(hint, color = CarColors.TextDim, fontSize = 14.sp)
        }
        Spacer(Modifier.size(12.dp))
        SettingChoice(stringResource(R.string.setup_open), false, onClick)
    }
}

/**
 * LibreHU needs root, and every LibreHU app that uses it must be allowed or that function does nothing. The root
 * solution is detected ([RootManager]): SuperSU / Magisk ask with a prompt per app ("Remember" / "Forever"), KernelSU
 * (Next) and APatch never ask (each app is ticked in the manager's Superuser list, the button opens it), a bare su has
 * nothing to grant. One button then asks for all the LibreHU apps that use root, one after the other ([RootGrants]).
 */
/**
 * The other LibreHU apps' settings worth a look on a new unit: restoring a backup made on the previous ROM, the
 * steering wheel keys (BtnRemap's key test), the sound menu on the wheel (off by default) and the button backlight.
 * Each line opens its app (shown only when installed); the hint says where the setting is.
 */
@Composable
private fun LibreHuFeatures() {
    val context = LocalContext.current
    SettingHint(stringResource(R.string.setup_librehu_text))
    PhoneAppQr()
    PhoneAppPairing(qrCode = true)
    for ((pkg, title, hint) in FEATURES) {
        val open = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
        SetupItem(stringResource(title), stringResource(hint)) {
            runCatching { context.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

private val FEATURES =
    listOf(
        Triple("org.librehu.service", R.string.setup_feature_backup, R.string.setup_feature_backup_hint),
        Triple("org.librehu.btnremap", R.string.setup_feature_keys, R.string.setup_feature_keys_hint),
        Triple("org.librehu.service", R.string.setup_feature_sound, R.string.setup_feature_sound_hint),
        Triple("org.librehu.service", R.string.setup_feature_backlight, R.string.setup_feature_backlight_hint),
    )

/** Latest release of LibreHU's phone app (rolling `latest-main` release of its repository). */
const val PHONE_APP_URL = "https://github.com/LibreHU/LibreHU-bridge-app/releases/download/latest-main/LibreHU-Bridge-main.apk"

/**
 * QR code of the phone app's latest release, to install it on the phone (it then pairs with [PhoneAppPairing]).
 * Made here (ZXing), no network needed.
 */
@Composable
private fun PhoneAppQr() {
    val qr = remember { qrBitmap(PHONE_APP_URL) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.SurfaceHigh)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        qr?.let {
            androidx.compose.foundation.Image(
                it.asImageBitmap(),
                null,
                Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp),
            )
            Spacer(Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setup_phone_app), color = CarColors.Text, fontSize = 18.sp)
            Text(stringResource(R.string.setup_phone_app_hint), color = CarColors.TextDim, fontSize = 14.sp)
        }
    }
}

/** QR code of [text] (ZXing), black on white. */
private fun qrBitmap(text: String): android.graphics.Bitmap? =
    runCatching {
        val m =
            com.google.zxing.qrcode.QRCodeWriter().encode(
                text,
                com.google.zxing.BarcodeFormat.QR_CODE,
                480,
                480,
                mapOf(com.google.zxing.EncodeHintType.MARGIN to 2),
            )
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        android.graphics.Bitmap.createBitmap(px, m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888)
    }.getOrNull()

/** LibreHU-service's pairing provider (signature permission `org.librehu.permission.BRIDGE`). */
private const val BRIDGE_PAIR = "org.librehu.service.bridgepair"

private fun bridgeCall(
    context: android.content.Context,
    method: String,
    arg: String? = null,
    extras: android.os.Bundle? = null,
): android.os.Bundle? =
    runCatching {
        if (context.packageManager.resolveContentProvider(BRIDGE_PAIR, 0) == null) return null
        context.contentResolver.call(android.net.Uri.parse("content://$BRIDGE_PAIR"), method, arg, extras)
    }.getOrNull()

/**
 * Pairing of the phone app with LibreHU-service ([BRIDGE_PAIR]): a one-time QR code (valid 5 minutes) to scan in the
 * app, over Wi-Fi; or, the phone already paired in Bluetooth, the app's Bluetooth pairing, confirmed here. The
 * phones paired and connected show up as soon as they are. Without LibreHU-service, a line saying it is needed.
 */
@Composable
private fun PhoneAppPairing(qrCode: Boolean) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<android.os.Bundle?>(null) }
    var available by remember { mutableStateOf(true) }
    var uri by remember { mutableStateOf<String?>(null) }
    var fingerprint by remember { mutableStateOf("") }
    var baseline by remember { mutableStateOf<Set<String>?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val b = withContext(Dispatchers.IO) { bridgeCall(context, "state") }
            available = b != null
            state = b
            if (baseline == null && b != null) baseline = b.getStringArray("devices").orEmpty().toSet()
            now = System.currentTimeMillis()
            if (uri != null && b != null && b.getLong("until") == 0L) uri = null
            delay(1_000)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (uri != null) bridgeCall(context, "cancel") }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.SurfaceHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.setup_bridge_title), color = CarColors.Text, fontSize = 18.sp)
        if (!available) {
            Text(stringResource(R.string.setup_bridge_no_service), color = CarColors.TextDim, fontSize = 14.sp)
            return@Column
        }
        val b = state ?: return@Column
        val newOnes = b.getStringArray("devices").orEmpty().filter { it !in baseline.orEmpty() }
        if (newOnes.isNotEmpty()) {
            Text(stringResource(R.string.setup_bridge_paired, newOnes.joinToString(", ")), color = CarColors.Accent, fontSize = 16.sp)
        }
        val connected = b.getStringArray("connected").orEmpty()
        if (connected.isNotEmpty()) Text(stringResource(R.string.setup_bridge_connected, connected.joinToString(", ")), color = CarColors.Accent, fontSize = 15.sp)
        if (!b.getBoolean("enabled")) {
            Text(stringResource(R.string.setup_bridge_off), color = CarColors.TextDim, fontSize = 14.sp)
            SettingChoice(stringResource(R.string.setup_bridge_enable), false) {
                bridgeCall(context, "enable", extras = android.os.Bundle().apply { putBoolean("on", true) })
            }
        }
        if (qrCode) {
            Text(stringResource(R.string.setup_bridge_wifi), color = CarColors.TextDim, fontSize = 14.sp)
            val text = uri
            if (text == null) {
                SettingChoice(stringResource(R.string.setup_bridge_show_qr), true) {
                    bridgeCall(context, "pair")?.let {
                        uri = it.getString("uri")
                        fingerprint = it.getString("fingerprint").orEmpty()
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    remember(text) { qrBitmap(text) }?.let {
                        androidx.compose.foundation.Image(
                            it.asImageBitmap(),
                            null,
                            Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val left = ((b.getLong("until") - now) / 1000).coerceAtLeast(0)
                        Text(stringResource(R.string.setup_bridge_qr_hint, left / 60, left % 60, fingerprint.take(8)), color = CarColors.TextDim, fontSize = 14.sp)
                        SettingChoice(stringResource(R.string.setup_bridge_close_qr), false) {
                            bridgeCall(context, "cancel")
                            uri = null
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.setup_bridge_bt), color = CarColors.TextDim, fontSize = 14.sp)
        val ids = b.getStringArray("requests").orEmpty()
        val names = b.getStringArray("request_names").orEmpty()
        ids.forEachIndexed { i, id ->
            Text(stringResource(R.string.setup_bridge_request, names.getOrElse(i) { "?" }), color = CarColors.Text, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingChoice(stringResource(R.string.setup_bridge_allow), true) {
                    bridgeCall(context, "answer", id, android.os.Bundle().apply { putBoolean("allow", true) })
                }
                SettingChoice(stringResource(R.string.setup_bridge_deny), false) {
                    bridgeCall(context, "answer", id, android.os.Bundle().apply { putBoolean("allow", false) })
                }
            }
        }
    }
}

/** Root statuses that name the root manager. */
private val NAMED_STATUS =
    setOf(R.string.setup_root_asking, R.string.setup_root_denied, R.string.setup_root_denied_list, R.string.setup_root_no_answer)

@Composable
private fun RootWarning() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val results = remember { mutableStateMapOf<String, RootGrants.Result>() }
    var running by remember { mutableStateOf(false) }
    val root = remember { RootManager.detect(context) }
    val manager = root.name
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.SurfaceHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = Color(0xFFF9AB00), modifier = Modifier.size(32.dp))
            Spacer(Modifier.size(12.dp))
            Text(
                when (root.kind) {
                    RootManager.Kind.NONE -> stringResource(R.string.setup_root_title_none)
                    RootManager.Kind.PLAIN -> stringResource(R.string.setup_root_title_plain)
                    else -> stringResource(R.string.setup_root_title, manager)
                },
                color = CarColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(stringResource(R.string.setup_root_text), color = CarColors.Text, fontSize = 16.sp)
        Text(
            when (root.kind) {
                RootManager.Kind.PROMPT -> stringResource(R.string.setup_root_allow, manager)
                RootManager.Kind.ALLOWLIST -> stringResource(R.string.setup_root_allow_list, manager)
                RootManager.Kind.PLAIN -> stringResource(R.string.setup_root_allow_plain)
                RootManager.Kind.NONE -> stringResource(R.string.setup_root_allow_none)
            },
            color = CarColors.TextDim,
            fontSize = 15.sp,
        )
        root.pkg?.let { pkg -> context.packageManager.getLaunchIntentForPackage(pkg) }?.let { open ->
            if (root.kind == RootManager.Kind.ALLOWLIST) {
                SettingChoice(stringResource(R.string.setup_root_open, manager), false) {
                    runCatching { context.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        SettingChoice(if (running) stringResource(R.string.setup_root_testing, manager.ifEmpty { "su" }) else stringResource(R.string.setup_root_test), !running && results.isEmpty()) {
            if (running) return@SettingChoice
            running = true
            results.clear()
            RootGrants.APPS.forEach { (pkg, _) -> results[pkg] = RootGrants.Result.WAITING }
            scope.launch {
                RootGrants.requestAll(context) { pkg, r -> results[pkg] = r }
                running = false
            }
        }
        if (running) {
            SettingChoice(stringResource(R.string.setup_root_skip), false) { RootGrants.skipCurrent() }
        }
        for ((pkg, name) in RootGrants.APPS) {
            val r = results[pkg] ?: continue
            if (r == RootGrants.Result.NOT_INSTALLED) continue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = CarColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                val status =
                    when (r) {
                        RootGrants.Result.WAITING -> R.string.setup_root_waiting
                        RootGrants.Result.ASKING -> if (root.kind == RootManager.Kind.PROMPT) R.string.setup_root_asking else R.string.setup_root_checking
                        RootGrants.Result.GRANTED -> R.string.setup_root_ok
                        RootGrants.Result.DENIED -> if (root.kind == RootManager.Kind.ALLOWLIST) R.string.setup_root_denied_list else R.string.setup_root_denied
                        RootGrants.Result.SKIPPED -> R.string.setup_root_skipped
                        RootGrants.Result.OUTDATED -> R.string.setup_root_outdated
                        else -> R.string.setup_root_no_answer
                    }
                Text(
                    if (status in NAMED_STATUS) stringResource(status, manager.ifEmpty { "su" }) else stringResource(status),
                    color =
                        when (r) {
                            RootGrants.Result.GRANTED -> CarColors.Accent
                            RootGrants.Result.DENIED, RootGrants.Result.NO_ANSWER, RootGrants.Result.OUTDATED -> Color(0xFFF44336)
                            else -> CarColors.TextDim
                        },
                    fontSize = 15.sp,
                )
            }
        }
    }
}
