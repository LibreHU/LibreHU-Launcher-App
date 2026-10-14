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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.librehu.launcher.R
import org.librehu.launcher.data.ThemeController
import org.librehu.launcher.data.ThemeStore
import org.librehu.launcher.system.RootGrants

private enum class SetupStep(
    val title: Int,
) {
    WELCOME(R.string.setup_welcome),
    REGION(R.string.region_title),
    VEHICLE(R.string.vehicle_title),
    LOOK(R.string.settings_look),
    ACCESS(R.string.setup_access),
    STANDBY(R.string.standby_title),
    SOS(R.string.sos_settings),
    BLUETOOTH(R.string.bt_setup_title),
    DONE(R.string.setup_done),
}

/**
 * First start assistant (and Settings → General): SuperSU warning and root for every LibreHU app at once, language,
 * time zone, date and time, the car (name and description, kept by LibreHU-service for every LibreHU app), appearance
 * and rail position, Android permissions, updates, standby clock, SOS, then the phone's Bluetooth pairing step by
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

                    SetupStep.STANDBY -> {
                        StandbySettingsPage(actions, full = false)
                    }

                    SetupStep.SOS -> {
                        SosSettingsPage(actions, full = false)
                    }

                    SetupStep.BLUETOOTH -> {
                        BluetoothSetupPage()
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
 * LibreHU needs root through SuperSU in the ROM, and every SuperSU request of a LibreHU app must be granted
 * ("Remember" ticked) or that function does nothing. One button asks for all the LibreHU apps that use root, one
 * after the other ([RootGrants]), so all the SuperSU prompts come here, once.
 */
@Composable
private fun RootWarning() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val results = remember { mutableStateMapOf<String, RootGrants.Result>() }
    var running by remember { mutableStateOf(false) }
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
            Text(stringResource(R.string.setup_root_title), color = CarColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Text(stringResource(R.string.setup_root_text), color = CarColors.Text, fontSize = 16.sp)
        Text(stringResource(R.string.setup_root_allow), color = CarColors.TextDim, fontSize = 15.sp)
        SettingChoice(stringResource(if (running) R.string.setup_root_testing else R.string.setup_root_test), !running && results.isEmpty()) {
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
                Text(
                    stringResource(
                        when (r) {
                            RootGrants.Result.WAITING -> R.string.setup_root_waiting
                            RootGrants.Result.ASKING -> R.string.setup_root_asking
                            RootGrants.Result.GRANTED -> R.string.setup_root_ok
                            RootGrants.Result.DENIED -> R.string.setup_root_denied
                            RootGrants.Result.SKIPPED -> R.string.setup_root_skipped
                            RootGrants.Result.OUTDATED -> R.string.setup_root_outdated
                            else -> R.string.setup_root_no_answer
                        },
                    ),
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
