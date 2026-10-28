package org.librehu.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.librehu.launcher.R
import org.librehu.launcher.data.AndroidAutoLink
import org.librehu.launcher.data.LauncherApp
import org.librehu.launcher.data.LauncherPrefs
import org.librehu.launcher.data.MediaRepository
import org.librehu.launcher.data.NowPlaying
import org.librehu.launcher.data.ThemeSettings
import org.librehu.launcher.speed.SpeedLimitManager

/*
 * Factory style ([ThemeStyle.FACTORY]): the layout of a factory infotainment screen (black, a MENU of eight tiles
 * underlined in the accent colour, a second page of pinned apps, a status bar on the other screens, a Media screen with
 * soft keys), drawn with LibreHU's own Material icons and Source Sans 3. No maker's artwork, logo or name. Settings,
 * apps, OBD and TPMS keep their screens, under the factory status bar and in the factory palette.
 */

private val TILE_TOP = Color(0xFF1D1E22)
private val TILE_BOTTOM = Color(0xFF0B0B0D)
private val TILE_BORDER = Color(0xFF2C2D31)
private val ICON = Color(0xFFDADADA)
private val LINE = Color(0xFF3A3B3F)
private val STATUS = Color(0xFFD2D3D8)
private val SOFT_KEYS_HEIGHT = 97.dp

/** The whole screen in the factory style; [content] draws the screens that keep their own look (settings, apps…). */
@Composable
internal fun FactoryShell(
    screen: Screen,
    apps: List<LauncherApp>,
    pins: List<String>,
    look: ThemeSettings,
    media: MediaRepository,
    actions: LauncherActions,
    onAppMenu: (LauncherApp) -> Unit,
    content: @Composable () -> Unit,
) {
    val byKey = apps.associateBy { it.key }
    val pinned = pins.mapNotNull { byKey[it] }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (screen == Screen.HOME) {
            FactoryMenu(pinned, actions, onAppMenu)
        } else {
            Column(Modifier.fillMaxSize()) {
                FactoryStatusBar(actions)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (screen) {
                        Screen.MEDIA -> FactoryMedia(media, actions)
                        Screen.APPS -> FactoryApps(apps, look, actions, onAppMenu)
                        else -> Box(Modifier.fillMaxSize().padding(12.dp)) { content() }
                    }
                }
            }
        }
    }
}

// --- MENU ----------------------------------------------------------------------------------------------------------

private class MenuEntry(
    val icon: ImageVector,
    val label: Int,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/** MENU: eight tiles, then pages of eight pinned apps, swiped sideways, dots under them. No status bar, like the original. */
@Composable
private fun FactoryMenu(
    pinned: List<LauncherApp>,
    actions: LauncherActions,
    onAppMenu: (LauncherApp) -> Unit,
) {
    val context = LocalContext.current
    val entries =
        remember(actions) {
            listOf(
                MenuEntry(Icons.Outlined.Radio, R.string.factory_radio) { openPackage(context, actions, LauncherPrefs.FM_PACKAGE) },
                MenuEntry(Icons.Outlined.PlayCircle, R.string.factory_media) { actions.show(Screen.MEDIA) },
                MenuEntry(Icons.Outlined.Apps, R.string.factory_apps) { actions.show(Screen.APPS) },
                // Android Auto (to the front when a session runs); a long press opens CarPlay.
                MenuEntry(Icons.Outlined.PhoneAndroid, R.string.factory_projection, onLongClick = { openPackage(context, actions, CARPLAY) }) {
                    if (AndroidAutoLink.state.value.active) AndroidAutoLink.show(context) else openPackage(context, actions, *ANDROID_AUTO)
                },
                MenuEntry(Icons.Outlined.Phone, R.string.factory_phone) { openPackage(context, actions, DIALER) },
                MenuEntry(Icons.Outlined.DirectionsCar, R.string.factory_vehicle) { actions.show(Screen.OBD) },
                MenuEntry(Icons.Outlined.Navigation, R.string.factory_navigation) { openNavigation(context, actions) },
                MenuEntry(Icons.Outlined.Settings, R.string.factory_setup) { actions.show(Screen.SETTINGS) },
            )
        }
    // Pinned apps then an Add tile (to the apps, where a long press pins one).
    val appPages = (pinned.map<LauncherApp, LauncherApp?> { it } + null).chunked(8)
    val count = 1 + appPages.size
    val pager = androidx.compose.foundation.pager.rememberPagerState { count }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            TileGrid {
                if (page == 0) {
                    entries.forEach { e -> FactoryTile(stringResource(e.label), onClick = e.onClick, onLongClick = e.onLongClick) { tint -> TileIcon(e.icon, tint) } }
                } else {
                    appPages[page - 1].forEach { app ->
                        if (app == null) {
                            FactoryTile(stringResource(R.string.factory_add), onClick = { actions.show(Screen.APPS) }) { tint -> TileIcon(Icons.Outlined.Add, tint) }
                        } else {
                            FactoryTile(app.label, onClick = { actions.launch(app.component) }, onLongClick = { onAppMenu(app) }) { _ ->
                                Image(app.icon, app.label, modifier = Modifier.size(64.dp))
                            }
                        }
                    }
                }
            }
        }
        if (count > 1) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                repeat(count) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 5.dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) CarColors.Accent else Color(0xFFE0E0E0)),
                    )
                }
            }
        }
    }
}

/** Up to eight tiles, four by two, centred, sized from the screen. */
@Composable
private fun TileGrid(tiles: @Composable TileScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val gap = 24.dp
        val labelHeight = 46.dp
        val byWidth = (maxWidth - gap * 3) / 4
        val byHeight = ((maxHeight - gap - labelHeight * 2) / 2) * 1.37f
        val tileWidth = minOf(byWidth, byHeight, 240.dp)
        val scope = TileScope(tileWidth)
        val items = mutableListOf<@Composable () -> Unit>()
        scope.collect = { items += it }
        scope.tiles()
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { it() }
                    // Short last row stays left-aligned with the grid.
                    repeat(4 - row.size) { Spacer(Modifier.width(tileWidth)) }
                }
            }
        }
    }
}

private class TileScope(
    val tileWidth: androidx.compose.ui.unit.Dp,
) {
    var collect: (@Composable () -> Unit) -> Unit = {}
}

/** A nearly black tile with a line of the accent colour under it and its name below; pressed, its icon turns the accent colour. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileScope.FactoryTile(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: @Composable (Color) -> Unit,
) {
    val w = tileWidth
    collect { FactoryTileView(w, label, onClick, onLongClick, icon) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FactoryTileView(
    width: androidx.compose.ui.unit.Dp,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(width).combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .width(width)
                .aspectRatio(1.37f)
                .background(Brush.verticalGradient(listOf(TILE_TOP, TILE_BOTTOM)))
                .border(BorderStroke(1.dp, TILE_BORDER)),
            contentAlignment = Alignment.Center,
        ) { icon(if (pressed) CarColors.Accent else ICON) }
        Box(Modifier.width(width).height(3.dp).background(CarColors.Accent))
        Text(
            label,
            color = if (pressed) CarColors.Accent else Color.White,
            fontFamily = FactoryFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TileIcon(
    icon: ImageVector,
    tint: Color,
) = Icon(icon, null, tint = tint, modifier = Modifier.size(64.dp))

// --- Status bar ----------------------------------------------------------------------------------------------------

/**
 * Left: a compass and the road the car is on (from the speed limit alerts, when on), a touch goes back to MENU; the
 * clock in the middle with the phone after it; the weather on the right.
 */
@Composable
private fun FactoryStatusBar(actions: LauncherActions) {
    val context = LocalContext.current
    val road by SpeedLimitManager
        .get(context)
        .state
        .collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(java.util.Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.util.Date()
            delay(1000L * (60 - (System.currentTimeMillis() / 1000) % 60))
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterStart).clickable { actions.show(Screen.HOME) }.padding(vertical = 6.dp),
            ) {
                Icon(Icons.Outlined.Explore, null, tint = STATUS, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    road.road.ifEmpty { stringResource(R.string.factory_menu) },
                    color = STATUS,
                    fontFamily = FactoryFont,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.Center)) {
                // The globe of the original: online services reachable.
                Icon(Icons.Outlined.Public, null, tint = STATUS, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(30.dp))
                Text(
                    android.text.format.DateFormat
                        .getTimeFormat(context)
                        .format(now),
                    color = STATUS,
                    fontFamily = FactoryFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                )
                Spacer(Modifier.width(24.dp))
                PhoneStatusView(compact = true)
            }
            Box(Modifier.align(Alignment.CenterEnd)) { WeatherChip() }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(LINE))
    }
}

// --- Media ---------------------------------------------------------------------------------------------------------

/** Media: title and artist on the left, the cover on the right with a glow while playing, progress, soft keys at the bottom. */
@Composable
private fun FactoryMedia(
    media: MediaRepository,
    actions: LauncherActions,
) {
    val access by media.hasAccess.collectAsStateWithLifecycle()
    val np by media.nowPlaying.collectAsStateWithLifecycle()
    var sources by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp)) {
            val p = np
            when {
                !access ->
                    Text(
                        stringResource(R.string.factory_no_access),
                        color = CarColors.TextDim,
                        fontFamily = FactoryFont,
                        fontSize = 22.sp,
                        modifier = Modifier.align(Alignment.Center).clickable { actions.grantMediaAccess() },
                    )

                p == null ->
                    Text(
                        stringResource(R.string.factory_no_media),
                        color = CarColors.TextDim,
                        fontFamily = FactoryFont,
                        fontSize = 22.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )

                else -> NowPlayingFactory(p, actions)
            }
        }
        val playing = np?.playing == true
        SoftKeys(
            listOf(
                SoftKey(R.string.factory_source, Icons.Outlined.MusicNote) { sources = !sources },
                SoftKey(R.string.factory_browse, Icons.AutoMirrored.Outlined.QueueMusic) { if (access) actions.mediaOpen() else actions.grantMediaAccess() },
                SoftKey(R.string.factory_previous, Icons.Outlined.ChevronLeft, actions.mediaPrevious),
                SoftKey(if (playing) R.string.factory_pause else R.string.factory_play, if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, actions.mediaToggle),
                SoftKey(R.string.factory_next, Icons.Outlined.ChevronRight, actions.mediaNext),
                null,
                SoftKey(R.string.factory_setup, Icons.Outlined.Settings) { actions.show(Screen.SETTINGS) },
            ),
        )
    }
    if (sources) SourcePopup(actions) { sources = false }
    }
}

@Composable
private fun NowPlayingFactory(
    np: NowPlaying,
    actions: LauncherActions,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MusicNote, null, tint = CarColors.Text, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(np.title, color = CarColors.Text, fontFamily = FactoryFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (np.subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, null, tint = CarColors.Text, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(18.dp))
                        Text(np.subtitle, color = CarColors.Text, fontFamily = FactoryFont, fontSize = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Cover(np)
        }
        if (np.durationMs > 0) Progress(np, actions.mediaSeek)
    }
}

/** The cover (or a note on a dark square), with a glow of the accent colour under it while playing. */
@Composable
private fun Cover(np: NowPlaying) {
    val art = remember(np.art) { np.art?.asImageBitmap() }
    val accent = CarColors.Accent
    Box(
        Modifier
            .fillMaxHeight(0.85f)
            .aspectRatio(1f)
            .drawBehind {
                if (np.playing) {
                    drawOval(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width / 2, size.height),
                            radius = size.width * 0.6f,
                        ),
                        topLeft = Offset(-size.width * 0.1f, size.height * 0.82f),
                        size = androidx.compose.ui.geometry.Size(size.width * 1.2f, size.height * 0.36f),
                    )
                }
            },
    ) {
        if (art != null) {
            Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().border(1.dp, Color(0xFF8A8C90)))
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF1A1B1E)).border(1.dp, TILE_BORDER), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MusicNote, null, tint = CarColors.TextDim, modifier = Modifier.size(96.dp))
            }
        }
    }
}

/** Full-width progress line, tap or drag to seek when the player allows it; times under it. */
@Composable
private fun Progress(
    np: NowPlaying,
    onSeek: (Long) -> Unit,
) {
    var position by remember { mutableLongStateOf(np.positionNow()) }
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(np) {
        position = np.positionNow()
        while (np.playing) {
            delay(1000L - position % 1000L)
            position = np.positionNow()
        }
    }
    val duration = np.durationMs.toFloat()
    val shown = drag ?: (position / duration).coerceIn(0f, 1f)
    // One line like the original: elapsed time, the bar, the time left.
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time((shown * duration).toLong()), color = CarColors.Text, fontFamily = FactoryFont, fontSize = 20.sp, modifier = Modifier.width(72.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(np.canSeek) {
                    if (!np.canSeek) return@pointerInput
                    detectTapGestures { o ->
                        val t = (o.x / size.width).coerceIn(0f, 1f) * duration
                        onSeek(t.toLong())
                        position = t.toLong()
                    }
                }.pointerInput(np.canSeek) {
                    if (!np.canSeek) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            drag?.let { f ->
                                onSeek((f * duration).toLong())
                                position = (f * duration).toLong()
                            }
                            drag = null
                        },
                        onDragCancel = { drag = null },
                    ) { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF3A3B3F)))
            Box(Modifier.fillMaxWidth(shown).height(3.dp).background(CarColors.Accent))
        }
        Text(
            "-" + time(((1f - shown) * duration).toLong()),
            color = CarColors.Text,
            fontFamily = FactoryFont,
            fontSize = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(80.dp),
        )
    }
}

internal fun time(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

private class SoftKey(
    val label: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** The bar of soft keys at the bottom: name above, icon under it, thin separators, a line of the accent colour on top. */
@Composable
private fun SoftKeys(keys: List<SoftKey?>) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(CarColors.Accent))
        Row(Modifier.fillMaxWidth().height(SOFT_KEYS_HEIGHT - 1.dp).background(Color(0xFF07080A)), verticalAlignment = Alignment.CenterVertically) {
            keys.forEachIndexed { i, k ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (k != null) {
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().clickable(interactionSource = interaction, indication = null, onClick = k.onClick),
                        ) {
                            Text(stringResource(k.label), color = if (pressed) CarColors.Accent else Color.White, fontFamily = FactoryFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1)
                            Icon(k.icon, null, tint = if (pressed) CarColors.Accent else Color.White, modifier = Modifier.size(36.dp).padding(top = 4.dp))
                        }
                    }
                }
                if (k != null && i < keys.lastIndex) Box(Modifier.width(1.dp).fillMaxHeight(0.75f).background(LINE))
            }
        }
    }
}

/**
 * Sources: the radio, the disc player, Android Auto, CarPlay and the apps that publish media (the installed ones), in a
 * framed box over the screen, two by row, its point on the Source key. A touch outside closes it.
 */
@Composable
private fun SourcePopup(
    actions: LauncherActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val list = remember { mediaSources(context) }
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = SOFT_KEYS_HEIGHT - 6.dp)) {
            Column(
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(0.76f)
                    .background(Color.Black)
                    .border(BorderStroke(1.5.dp, Color(0xFFE6E6E6)))
                    // Touches inside do not close it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 12.dp),
            ) {
                if (list.isEmpty()) {
                    Text(stringResource(R.string.factory_no_app), color = CarColors.TextDim, fontFamily = FactoryFont, fontSize = 22.sp, modifier = Modifier.padding(20.dp))
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(list.chunked(2)) { row ->
                        Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
                            row.forEachIndexed { i, src ->
                                if (i == 1) Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(Color(0xFF6A6B70)))
                                SourceCell(src, Modifier.weight(1f)) {
                                    onDismiss()
                                    actions.launch(src.component)
                                }
                            }
                            if (row.size == 1) {
                                Box(Modifier.width(1.dp).fillMaxHeight(0.7f).background(Color(0xFF6A6B70)))
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF6A6B70)))
                    }
                }
            }
            // The point under the box, over the Source key.
            Box(
                Modifier
                    .padding(start = 44.dp)
                    .size(width = 28.dp, height = 14.dp)
                    .drawBehind {
                        val path =
                            androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width / 2, size.height)
                                lineTo(size.width, 0f)
                            }
                        drawPath(path, Color.Black)
                        drawPath(path, Color(0xFFE6E6E6), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                    },
            )
        }
    }
}

@Composable
private fun SourceCell(
    src: MediaSource,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (pressed) CarColors.Accent else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxHeight().clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(horizontal = 16.dp),
    ) {
        if (src.icon != null) {
            Icon(src.icon, null, tint = tint, modifier = Modifier.size(30.dp))
        } else if (src.image != null) {
            Image(src.image, null, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(20.dp))
        Text(src.label, color = tint, fontFamily = FactoryFont, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal class MediaSource(
    val component: ComponentName,
    val label: String,
    val icon: ImageVector?,
    val image: androidx.compose.ui.graphics.ImageBitmap?,
)

// --- Apps ----------------------------------------------------------------------------------------------------------

/** All apps as tiles, four by row; soft keys: Search, All (with the hidden ones), MENU, Setup. Long press: app menu (pin…). */
@Composable
private fun FactoryApps(
    apps: List<LauncherApp>,
    look: ThemeSettings,
    actions: LauncherActions,
    onAppMenu: (LauncherApp) -> Unit,
) {
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(false) }
    val shown =
        remember(apps, look.hiddenApps, query, all) {
            val q = query.trim()
            apps
                .filter { (all || it.key !in look.hiddenApps) && (q.isEmpty() || it.label.contains(q, ignoreCase = true)) }
                .sortedBy { it.label.lowercase() }
        }
    Column(Modifier.fillMaxSize()) {
        if (search) {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.factory_search_hint), fontFamily = FactoryFont) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = STATUS) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontFamily = FactoryFont, fontSize = 22.sp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 8.dp),
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gap = 40.dp
            val tile = minOf((maxWidth - 112.dp - gap * 3) / 4, 240.dp)
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns =
                    androidx.compose.foundation.lazy.grid.GridCells
                        .Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 56.dp, vertical = 24.dp),
            ) {
                if (query.isBlank()) {
                    item(key = "tpms") {
                        FactoryTileView(tile, stringResource(R.string.tpms_title), onClick = { actions.show(Screen.TPMS) }) { tint ->
                            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_tyre), null, tint = tint, modifier = Modifier.size(64.dp))
                        }
                    }
                }
                items(shown.size, key = { shown[it].key }) { i ->
                    val app = shown[i]
                    FactoryTileView(tile, app.label, onClick = { actions.launch(app.component) }, onLongClick = { onAppMenu(app) }) { _ ->
                        Image(app.icon, app.label, modifier = Modifier.size(64.dp))
                    }
                }
            }
        }
        SoftKeys(
            listOf(
                SoftKey(R.string.factory_search, Icons.Outlined.Search) {
                    search = !search
                    if (!search) query = ""
                },
                SoftKey(R.string.factory_all, Icons.Outlined.Apps) { all = !all },
                SoftKey(R.string.factory_menu, Icons.Outlined.Home) { actions.show(Screen.HOME) },
                null,
                null,
                null,
                SoftKey(R.string.factory_setup, Icons.Outlined.Settings) { actions.show(Screen.SETTINGS) },
            ),
        )
    }
}

internal const val CARPLAY = "org.librehu.carplay"
internal val ANDROID_AUTO = arrayOf("org.librehu.headunit", "com.andrerinas.headunitrevived")
internal const val DIALER = "org.librehu.dialer"

/** Navigation apps tried in this order before a plain geo: link. */
private val NAV_APPS = listOf("net.osmand.plus", "net.osmand", "app.organicmaps", "com.mapswithme.maps.pro", "com.waze", "com.google.android.apps.maps", "com.generalmagic.magicearth")

private fun launchComponent(
    context: Context,
    pkg: String,
): ComponentName? = context.packageManager.getLaunchIntentForPackage(pkg)?.component

/** The first installed of [packages]; nothing installed: a short note. */
internal fun openPackage(
    context: Context,
    actions: LauncherActions,
    vararg packages: String,
) {
    val c = packages.firstNotNullOfOrNull { launchComponent(context, it) }
    if (c != null) {
        actions.launch(c)
    } else {
        android.widget.Toast.makeText(context, R.string.factory_no_app, android.widget.Toast.LENGTH_SHORT).show()
    }
}

internal fun openNavigation(
    context: Context,
    actions: LauncherActions,
) {
    NAV_APPS.firstNotNullOfOrNull { launchComponent(context, it) }?.let {
        actions.launch(it)
        return
    }
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (geo.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(geo) }
    } else {
        actions.show(Screen.APPS)
    }
}

internal fun mediaSources(context: Context): List<MediaSource> {
    val pm = context.packageManager
    val packages = linkedSetOf(LauncherPrefs.FM_PACKAGE, "org.librehu.disc", *ANDROID_AUTO, CARPLAY)
    runCatching {
        pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0).forEach { packages += it.serviceInfo.packageName }
    }
    return packages.mapNotNull { pkg ->
        val c = launchComponent(context, pkg) ?: return@mapNotNull null
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        val icon =
            when (pkg) {
                LauncherPrefs.FM_PACKAGE -> Icons.Outlined.Radio
                "org.librehu.disc" -> Icons.Outlined.Album
                CARPLAY, in ANDROID_AUTO -> Icons.Outlined.PhoneAndroid
                else -> null
            }
        val image =
            if (icon != null) {
                null
            } else {
                runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            }
        MediaSource(c, label, icon, image)
    }
}
