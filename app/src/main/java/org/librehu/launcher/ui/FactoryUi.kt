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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Radio
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.librehu.launcher.R
import org.librehu.launcher.data.AndroidAutoLink
import org.librehu.launcher.data.LauncherApp
import org.librehu.launcher.data.LauncherPrefs
import org.librehu.launcher.data.MediaRepository
import org.librehu.launcher.data.NowPlaying

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

/** The whole screen in the factory style; [content] draws the screens that keep their own look (settings, apps…). */
@Composable
internal fun FactoryShell(
    screen: Screen,
    pinned: List<LauncherApp>,
    media: MediaRepository,
    actions: LauncherActions,
    onAppMenu: (LauncherApp) -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (screen == Screen.HOME) {
            FactoryMenu(pinned, actions, onAppMenu)
        } else {
            Column(Modifier.fillMaxSize()) {
                FactoryStatusBar(actions)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (screen == Screen.MEDIA) {
                        FactoryMedia(media, actions)
                    } else {
                        Box(Modifier.fillMaxSize().padding(12.dp)) { content() }
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
    val appPages = pinned.chunked(8)
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
                        FactoryTile(app.label, onClick = { actions.launch(app.component) }, onLongClick = { onAppMenu(app) }) { _ ->
                            Image(app.icon, app.label, modifier = Modifier.size(64.dp))
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
        val byHeight = ((maxHeight - gap - labelHeight * 2) / 2) / 0.78f
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
    collect {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(w).combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        ) {
            Box(
                Modifier
                    .width(w)
                    .aspectRatio(1.28f)
                    .background(Brush.verticalGradient(listOf(TILE_TOP, TILE_BOTTOM)))
                    .border(BorderStroke(1.dp, TILE_BORDER)),
                contentAlignment = Alignment.Center,
            ) { icon(if (pressed) CarColors.Accent else ICON) }
            Box(Modifier.width(w).height(3.dp).background(CarColors.Accent))
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
}

@Composable
private fun TileIcon(
    icon: ImageVector,
    tint: Color,
) = Icon(icon, null, tint = tint, modifier = Modifier.size(64.dp))

// --- Status bar ----------------------------------------------------------------------------------------------------

/** MENU key on the left (back to the menu), the clock in the middle, phone status and the weather on the right. */
@Composable
private fun FactoryStatusBar(actions: LauncherActions) {
    val context = LocalContext.current
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
                Icon(Icons.Outlined.GridView, null, tint = CarColors.TextDim, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.factory_menu), color = CarColors.TextDim, fontFamily = FactoryFont, fontSize = 18.sp)
            }
            Text(
                android.text.format.DateFormat
                    .getTimeFormat(context)
                    .format(now),
                color = CarColors.Text,
                fontFamily = FactoryFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterEnd)) {
                PhoneStatusView(compact = true)
                Spacer(Modifier.width(12.dp))
                WeatherChip()
            }
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
                SoftKey(R.string.factory_source, Icons.AutoMirrored.Outlined.QueueMusic) { sources = true },
                SoftKey(R.string.factory_browse, Icons.Outlined.LibraryMusic) { if (access) actions.mediaOpen() else actions.grantMediaAccess() },
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
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
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
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF3A3B3F)))
            Box(Modifier.fillMaxWidth(shown).height(4.dp).background(CarColors.Accent))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(time((shown * duration).toLong()), color = CarColors.TextDim, fontFamily = FactoryFont, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text(time(np.durationMs), color = CarColors.TextDim, fontFamily = FactoryFont, fontSize = 16.sp)
        }
    }
}

private fun time(ms: Long): String {
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
        Row(Modifier.fillMaxWidth().height(96.dp).background(Color(0xFF07080A)), verticalAlignment = Alignment.CenterVertically) {
            keys.forEachIndexed { i, k ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight(0.75f).background(LINE))
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
            }
        }
    }
}

/** Sources: the radio, the disc player, Android Auto, CarPlay and the apps that publish media, the installed ones. */
@Composable
private fun SourcePopup(
    actions: LauncherActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val list = remember { mediaSources(context) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(460.dp)
                .background(Color(0xFF0B0B0D))
                .border(BorderStroke(1.dp, CarColors.Accent)),
        ) {
            Text(
                stringResource(R.string.factory_choose_source),
                color = CarColors.Text,
                fontFamily = FactoryFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                modifier = Modifier.padding(20.dp),
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                items(list, key = { it.first.flattenToString() }) { (component, label) ->
                    Text(
                        label,
                        color = CarColors.Text,
                        fontFamily = FactoryFont,
                        fontSize = 22.sp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    actions.launch(component)
                                }.padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Color(0xFF1E1F22)))
                }
            }
        }
    }
}

private const val CARPLAY = "org.librehu.carplay"
private val ANDROID_AUTO = arrayOf("org.librehu.headunit", "com.andrerinas.headunitrevived")
private val DIALER = "org.librehu.dialer"

/** Navigation apps tried in this order before a plain geo: link. */
private val NAV_APPS = listOf("net.osmand.plus", "net.osmand", "app.organicmaps", "com.mapswithme.maps.pro", "com.waze", "com.google.android.apps.maps", "com.generalmagic.magicearth")

private fun launchComponent(
    context: Context,
    pkg: String,
): ComponentName? = context.packageManager.getLaunchIntentForPackage(pkg)?.component

/** The first installed of [packages]; nothing installed: a short note. */
private fun openPackage(
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

private fun openNavigation(
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

private fun mediaSources(context: Context): List<Pair<ComponentName, String>> {
    val pm = context.packageManager
    val packages = linkedSetOf(LauncherPrefs.FM_PACKAGE, "org.librehu.disc", *ANDROID_AUTO, CARPLAY)
    runCatching {
        pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0).forEach { packages += it.serviceInfo.packageName }
    }
    return packages.mapNotNull { pkg ->
        val c = launchComponent(context, pkg) ?: return@mapNotNull null
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        c to label
    }
}
