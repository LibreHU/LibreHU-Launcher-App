package org.librehu.launcher.ui

import android.Manifest
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.librehu.launcher.R
import org.librehu.launcher.data.ThemeController
import org.librehu.launcher.data.ThemeStore

private enum class SetupStep(
    val title: Int,
) {
    WELCOME(R.string.setup_welcome),
    LOOK(R.string.settings_look),
    ACCESS(R.string.setup_access),
    STANDBY(R.string.standby_title),
    SOS(R.string.sos_settings),
    DONE(R.string.setup_done),
}

/**
 * First start assistant (and Settings → General): appearance and rail position, Android permissions, standby clock,
 * SOS. Every step can be skipped; everything stays in the settings.
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
