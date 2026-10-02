package org.librehu.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.launcher.R
import org.librehu.launcher.data.ThemeController
import org.librehu.launcher.data.ThemeMode
import org.librehu.launcher.data.ThemeStore
import org.librehu.launcher.data.WallpaperKind

/** Launcher settings: appearance (mode, accent, wallpaper, system-wide theme) and reset. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    theme: ThemeStore,
    controller: ThemeController,
    actions: LauncherActions,
) {
    val s by theme.settings.collectAsStateWithLifecycle()
    val dark by controller.dark.collectAsStateWithLifecycle()
    val systemAllowed by controller.systemWideAllowed.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
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

        Section(stringResource(R.string.theme))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice(stringResource(R.string.theme_light), s.mode == ThemeMode.LIGHT) { actions.setTheme { it.copy(mode = ThemeMode.LIGHT) } }
            Choice(stringResource(R.string.theme_dark), s.mode == ThemeMode.DARK) { actions.setTheme { it.copy(mode = ThemeMode.DARK) } }
            Choice(stringResource(R.string.theme_auto), s.mode == ThemeMode.AUTO) { actions.setTheme { it.copy(mode = ThemeMode.AUTO) } }
        }
        Text(
            stringResource(R.string.theme_auto_hint) + "  " +
                stringResource(if (dark) R.string.theme_now_dark else R.string.theme_now_light),
            color = CarColors.TextDim,
            fontSize = 15.sp,
        )
        SwitchRow(
            stringResource(R.string.theme_system_wide),
            if (s.systemWide &&
                !systemAllowed
            ) {
                stringResource(R.string.theme_system_wide_denied)
            } else {
                stringResource(R.string.theme_system_wide_hint)
            },
            s.systemWide,
        ) { on -> actions.setTheme { it.copy(systemWide = on) } }

        Section(stringResource(R.string.accent))
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

        Section(stringResource(R.string.wallpaper))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val w = s.wallpaper
            Choice(stringResource(R.string.wallpaper_none), w == WallpaperKind.NONE) { actions.setWallpaper(WallpaperKind.NONE) }
            Choice(stringResource(R.string.wallpaper_aurora), w == WallpaperKind.AURORA) { actions.setWallpaper(WallpaperKind.AURORA) }
            Choice(stringResource(R.string.wallpaper_stars), w == WallpaperKind.STARS) { actions.setWallpaper(WallpaperKind.STARS) }
            Choice(stringResource(R.string.wallpaper_waves), w == WallpaperKind.WAVES) { actions.setWallpaper(WallpaperKind.WAVES) }
            Choice(
                stringResource(R.string.wallpaper_pick),
                w == WallpaperKind.IMAGE || w == WallpaperKind.ANIMATED,
                actions.pickImage,
            )
            Choice(stringResource(R.string.wallpaper_video), w == WallpaperKind.VIDEO, actions.pickVideo)
            Choice(stringResource(R.string.wallpaper_system), w == WallpaperKind.SYSTEM) { actions.setWallpaper(WallpaperKind.SYSTEM) }
        }
        if (s.wallpaper == WallpaperKind.SYSTEM) {
            Choice(stringResource(R.string.wallpaper_live_choose), false, actions.chooseLiveWallpaper)
        }
        Text(stringResource(R.string.wallpaper_hint), color = CarColors.TextDim, fontSize = 15.sp)

        Section(stringResource(R.string.reset))
        Choice(stringResource(R.string.reset_all), false) { confirmReset = true }
        Spacer(Modifier.height(8.dp))
    }
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

@Composable
private fun Section(title: String) {
    Text(title, color = CarColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Choice(
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

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
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
            Text(subtitle, color = CarColors.TextDim, fontSize = 14.sp)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = CarColors.Accent, checkedThumbColor = CarColors.OnAccent),
        )
    }
}
