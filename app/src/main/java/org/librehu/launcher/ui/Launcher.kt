package org.librehu.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.librehu.launcher.R
import org.librehu.launcher.data.AppsRepository
import org.librehu.launcher.data.LauncherApp
import org.librehu.launcher.data.LauncherPrefs
import org.librehu.launcher.data.MediaRepository
import org.librehu.launcher.data.NowPlaying
import org.librehu.launcher.data.ThemeController
import org.librehu.launcher.data.ThemeSettings
import org.librehu.launcher.data.ThemeStore
import org.librehu.launcher.data.WidgetHost
import org.librehu.launcher.tpms.TpmsManager
import java.text.DateFormat
import java.util.Date

enum class Screen { HOME, APPS, SETTINGS, TPMS }

class LauncherActions(
    val launch: (ComponentName) -> Unit,
    val togglePin: (String) -> Unit,
    val movePin: (String, Int) -> Unit,
    val show: (Screen) -> Unit,
    val volumeUp: () -> Unit,
    val volumeDown: () -> Unit,
    val mediaToggle: () -> Unit,
    val mediaNext: () -> Unit,
    val mediaPrevious: () -> Unit,
    val mediaOpen: () -> Unit,
    val grantMediaAccess: () -> Unit,
    val addWidget: (LauncherPrefs.Slot, ComponentName) -> Unit,
    val removeWidget: (LauncherPrefs.Slot) -> Unit,
    val uninstall: (String) -> Unit,
    val forceStop: (String) -> Unit,
    val appInfo: (String) -> Unit,
    val setTheme: ((ThemeSettings) -> ThemeSettings) -> Unit,
    val pickWallpaper: () -> Unit,
    val clearWallpaper: () -> Unit,
    val resetAll: () -> Unit,
)

/** Car dashboard: shortcut rail on the left, dashboard or app grid on the right. */
@Composable
fun LauncherScreen(
    appsRepo: AppsRepository,
    prefs: LauncherPrefs,
    media: MediaRepository,
    widgets: WidgetHost,
    theme: ThemeStore,
    themeController: ThemeController,
    screen: Screen,
    actions: LauncherActions,
) {
    val apps by appsRepo.apps.collectAsStateWithLifecycle()
    val pins by prefs.pins.collectAsStateWithLifecycle()
    val wallpaper by theme.wallpaper.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<LauncherApp?>(null) }
    val byKey = apps.associateBy { it.key }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CarColors.Background),
    ) {
        wallpaper?.let { bmp ->
            val image = remember(bmp) { bmp.asImageBitmap() }
            Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Keep the text readable whatever the picture.
            Box(Modifier.fillMaxSize().background(CarColors.Background.copy(alpha = 0.35f)))
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Rail(pins.mapNotNull { byKey[it] }, screen, actions) { menuFor = it }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                when (screen) {
                    Screen.HOME -> Dashboard(prefs, media, widgets, actions)
                    Screen.APPS -> AppGrid(apps, pins, actions) { menuFor = it }
                    Screen.SETTINGS -> SettingsScreen(theme, themeController, actions)
                    Screen.TPMS -> TpmsScreen()
                }
            }
        }
    }
    menuFor?.let { app -> AppMenu(app, app.key in pins, actions) { menuFor = null } }
}

// --- Rail --------------------------------------------------------------------------------------------------------

@Composable
private fun Rail(
    pinned: List<LauncherApp>,
    screen: Screen,
    actions: LauncherActions,
    onMenu: (LauncherApp) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(104.dp)
                .fillMaxHeight()
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RailButton(Icons.Default.Dashboard, R.string.home, selected = screen == Screen.HOME) { actions.show(Screen.HOME) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(pinned, key = { it.key }) { app ->
                AppIcon(app, size = 64, onLongClick = { onMenu(app) }) {
                    actions.launch(app.component)
                }
            }
        }
        RailButton(Icons.Default.Apps, R.string.all_apps, selected = screen == Screen.APPS) { actions.show(Screen.APPS) }
        Row {
            SmallRailButton(Icons.AutoMirrored.Filled.VolumeDown, R.string.volume_down, actions.volumeDown)
            SmallRailButton(Icons.AutoMirrored.Filled.VolumeUp, R.string.volume_up, actions.volumeUp)
        }
        Clock()
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L * (60 - (System.currentTimeMillis() / 1000) % 60))
        }
    }
    Text(
        android.text.format.DateFormat
            .getTimeFormat(context)
            .format(now),
        color = CarColors.Text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (selected) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, stringResource(label), tint = if (selected) CarColors.OnAccent else CarColors.Text, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun SmallRailButton(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, stringResource(label), tint = CarColors.Text, modifier = Modifier.size(28.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(
    app: LauncherApp,
    size: Int,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Image(
        app.icon,
        contentDescription = app.label,
        modifier =
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

// --- Dashboard ---------------------------------------------------------------------------------------------------

@Composable
private fun Dashboard(
    prefs: LauncherPrefs,
    media: MediaRepository,
    widgets: WidgetHost,
    actions: LauncherActions,
) {
    val slots by prefs.slots.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        WidgetSlot(
            slot = LauncherPrefs.Slot.MAIN,
            appWidgetId = slots[LauncherPrefs.Slot.MAIN] ?: LauncherPrefs.NO_WIDGET,
            widgets = widgets,
            actions = actions,
            modifier = Modifier.weight(1.5f).fillMaxHeight(),
        ) { WelcomeCard() }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            MediaCard(media, actions, Modifier.weight(1f).fillMaxWidth())
            val tpmsSettings by TpmsManager
                .get(LocalContext.current)
                .settings
                .collectAsStateWithLifecycle()
            if (tpmsSettings.enabled) TpmsCard(Modifier.weight(0.9f).fillMaxWidth()) { actions.show(Screen.TPMS) }
            WidgetSlot(
                slot = LauncherPrefs.Slot.SIDE,
                appWidgetId = slots[LauncherPrefs.Slot.SIDE] ?: LauncherPrefs.NO_WIDGET,
                widgets = widgets,
                actions = actions,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { pick ->
                val fm = widgets.fmWidget()
                EmptySlot(
                    if (fm != null) Icons.Default.Radio else Icons.Default.Widgets,
                    stringResource(if (fm != null) R.string.add_radio_widget else R.string.add_widget),
                ) { if (fm != null) actions.addWidget(LauncherPrefs.Slot.SIDE, fm) else pick() }
            }
        }
    }
}

/** Big clock and date, shown until a widget is placed in the main slot. */
@Composable
private fun WelcomeCard() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L * (60 - (System.currentTimeMillis() / 1000) % 60))
        }
    }
    val context = LocalContext.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            android.text.format.DateFormat
                .getTimeFormat(context)
                .format(now),
            color = CarColors.Text,
            fontSize = 96.sp,
            fontWeight = FontWeight.Light,
        )
        Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), color = CarColors.TextDim, fontSize = 24.sp)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.main_slot_hint), color = CarColors.TextDim, fontSize = 16.sp)
    }
}

/**
 * A dashboard card holding an app widget. Empty: [empty] content (gets a "pick a widget" callback).
 * Filled: the widget, with a small edit button to replace or remove it.
 */
@Composable
private fun WidgetSlot(
    slot: LauncherPrefs.Slot,
    appWidgetId: Int,
    widgets: WidgetHost,
    actions: LauncherActions,
    modifier: Modifier,
    empty: @Composable (pick: () -> Unit) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface),
    ) {
        val hostView = remember(appWidgetId) { if (appWidgetId >= 0) widgets.createView(appWidgetId) else null }
        if (hostView != null) {
            AndroidView(factory = { hostView }, modifier = Modifier.fillMaxSize())
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CarColors.SurfaceHigh)
                        .clickable { editing = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Edit, stringResource(R.string.edit_widget), tint = CarColors.TextDim, modifier = Modifier.size(20.dp))
            }
        } else {
            Box(Modifier.fillMaxSize().clickable { picking = true }) { empty { picking = true } }
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.edit_widget)) },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    picking = true
                }) { Text(stringResource(R.string.replace)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    editing = false
                    actions.removeWidget(slot)
                }) { Text(stringResource(R.string.remove)) }
            },
        )
    }
    if (picking) {
        WidgetPicker(widgets, onDismiss = { picking = false }) { provider ->
            picking = false
            actions.addWidget(slot, provider)
        }
    }
}

@Composable
private fun EmptySlot(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = CarColors.Accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(label, color = CarColors.Text, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WidgetPicker(
    widgets: WidgetHost,
    onDismiss: () -> Unit,
    onPick: (ComponentName) -> Unit,
) {
    val context = LocalContext.current
    val providers: List<AppWidgetProviderInfo> = remember { widgets.providers.sortedBy { it.loadLabel(context.packageManager) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_widget)) },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                items(providers, key = { it.provider.flattenToString() }) { p ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(p.provider) }
                                .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = remember(p) { runCatching { p.loadIcon(context, 0)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                        if (icon != null) Image(icon, null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(p.loadLabel(context.packageManager), color = CarColors.Text, fontSize = 18.sp)
                            val appLabel =
                                remember(p) {
                                    runCatching {
                                        val pm = context.packageManager
                                        pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString()
                                    }.getOrDefault(p.provider.packageName)
                                }
                            Text(appLabel, color = CarColors.TextDim, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// --- Media -------------------------------------------------------------------------------------------------------

@Composable
private fun MediaCard(
    media: MediaRepository,
    actions: LauncherActions,
    modifier: Modifier,
) {
    val access by media.hasAccess.collectAsStateWithLifecycle()
    val np by media.nowPlaying.collectAsStateWithLifecycle()
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .clickable { if (access) actions.mediaOpen() else actions.grantMediaAccess() }
                .padding(20.dp),
    ) {
        when {
            !access -> {
                Hint(Icons.Default.MusicNote, stringResource(R.string.media_access))
            }

            np == null -> {
                Hint(Icons.Default.MusicNote, stringResource(R.string.nothing_playing))
            }

            else -> {
                NowPlayingContent(np!!, actions)
            }
        }
    }
}

@Composable
private fun NowPlayingContent(
    np: NowPlaying,
    actions: LauncherActions,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = remember(np.art) { np.art?.asImageBitmap() }
            if (art != null) {
                Image(
                    art,
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                )
            } else {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(CarColors.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.MusicNote, null, tint = CarColors.TextDim, modifier = Modifier.size(36.dp)) }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    np.title,
                    color = CarColors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(np.subtitle, color = CarColors.TextDim, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            MediaButton(Icons.Default.SkipPrevious, R.string.previous, false, actions.mediaPrevious)
            MediaButton(if (np.playing) Icons.Default.Pause else Icons.Default.PlayArrow, R.string.play_pause, true, actions.mediaToggle)
            MediaButton(Icons.Default.SkipNext, R.string.next, false, actions.mediaNext)
        }
    }
}

@Composable
private fun MediaButton(
    icon: ImageVector,
    label: Int,
    accent: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(if (accent) 64.dp else 56.dp)
                .clip(CircleShape)
                .background(if (accent) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, stringResource(label), tint = if (accent) CarColors.OnAccent else CarColors.Text, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun Hint(
    icon: ImageVector,
    text: String,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = CarColors.Accent, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = CarColors.TextDim, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

// --- App grid ----------------------------------------------------------------------------------------------------

@Composable
private fun AppGrid(
    apps: List<LauncherApp>,
    pins: List<String>,
    actions: LauncherActions,
    onMenu: (LauncherApp) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(16.dp),
    ) {
        Text(stringResource(R.string.all_apps), color = CarColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Text(stringResource(R.string.pin_hint), color = CarColors.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(132.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "settings") {
                GridTile(stringResource(R.string.settings), onClick = { actions.show(Screen.SETTINGS) }) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(CarColors.Accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Settings, null, tint = CarColors.OnAccent, modifier = Modifier.size(40.dp)) }
                }
            }
            item(key = "tpms") {
                GridTile(stringResource(R.string.tpms_title), onClick = { actions.show(Screen.TPMS) }) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(CarColors.SurfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.ic_tyre), null, tint = CarColors.Accent, modifier = Modifier.size(40.dp)) }
                }
            }
            items(apps, key = { it.key }) { app ->
                GridTile(app.label) {
                    Box {
                        AppIcon(app, size = 72, onLongClick = { onMenu(app) }) {
                            actions.launch(app.component)
                            actions.show(Screen.HOME)
                        }
                        if (app.key in pins) {
                            Icon(
                                Icons.Default.PushPin,
                                null,
                                tint = CarColors.Accent,
                                modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridTile(
    label: String,
    onClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(4.dp),
    ) {
        icon()
        Spacer(Modifier.height(8.dp))
        Text(label, color = CarColors.Text, fontSize = 15.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

/** Long press on an app (rail or grid): pin, reorder, app info, force stop, uninstall. */
@Composable
private fun AppMenu(
    app: LauncherApp,
    pinned: Boolean,
    actions: LauncherActions,
    onDismiss: () -> Unit,
) {
    val pkg = app.component.packageName
    var confirmUninstall by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Image(app.icon, null, modifier = Modifier.size(56.dp).clip(CircleShape)) },
        title = { Text(app.label) },
        text = {
            Column {
                MenuRow(Icons.Default.PushPin, stringResource(if (pinned) R.string.unpin else R.string.pin)) {
                    actions.togglePin(app.key)
                    onDismiss()
                }
                if (pinned) {
                    MenuRow(Icons.Default.ArrowUpward, stringResource(R.string.move_up)) { actions.movePin(app.key, -1) }
                    MenuRow(Icons.Default.ArrowDownward, stringResource(R.string.move_down)) { actions.movePin(app.key, 1) }
                }
                MenuRow(Icons.Default.Info, stringResource(R.string.app_info)) {
                    actions.appInfo(pkg)
                    onDismiss()
                }
                MenuRow(Icons.Default.Block, stringResource(R.string.force_stop)) {
                    actions.forceStop(pkg)
                    onDismiss()
                }
                MenuRow(Icons.Default.Delete, stringResource(R.string.uninstall)) { confirmUninstall = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text(stringResource(R.string.uninstall_confirm, app.label)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    actions.uninstall(pkg)
                    onDismiss()
                }) { Text(stringResource(R.string.uninstall)) }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = CarColors.Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = CarColors.Text, fontSize = 18.sp)
    }
}
