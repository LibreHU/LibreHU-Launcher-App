package org.librehu.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
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
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import org.librehu.launcher.R
import org.librehu.launcher.data.AndroidAutoLink
import org.librehu.launcher.data.LauncherApp
import org.librehu.launcher.data.LauncherPrefs
import org.librehu.launcher.data.MediaRepository
import org.librehu.launcher.data.NowPlaying

/*
 * Ambient style ([ThemeStyle.AMBIENT]): the layout of a connected-car tablet screen. Black fading to night blue, the
 * accent colour as the ambient light (glow at the bottom, frames, pressed buttons), a top bar (clock, map and audio
 * shortcuts, page dots, phone, weather), home pages of widgets in a frame, a menu of six tiles, and a bar of buttons
 * at the bottom (Menu / Back, the screen's main action, Options). LibreHU's own Material icons: no maker's artwork,
 * logo, font or name. Settings, apps, OBD and TPMS keep their screens between the two bars.
 */

private val SHEEN = Color(0xFF75716C)
private val BAR_TOP = Color(0xFF3A3D42)
private val BAR_BOTTOM = Color(0xFF191819)
private val LIGHT = Color(0xFFEEF2F6)
private val DIM = Color(0xFF98A3B0)

/**
 * The ambient light: the accent colour, deeper and fully saturated (the lines are the accent itself, e.g. #0078DC,
 * the light under them a deep blue like #003CC7).
 */
private fun deep(accent: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt()), hsv)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf((hsv[0] + 12f) % 360f, 1f, hsv[2] * 0.9f)))
}

/** The whole screen in the ambient style; [content] draws the screens that keep their own look (settings, apps…). */
@Composable
internal fun AmbientShell(
    screen: Screen,
    apps: List<LauncherApp>,
    pins: List<String>,
    media: MediaRepository,
    actions: LauncherActions,
    onAppMenu: (LauncherApp) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val accent = CarColors.Accent
    val byKey = apps.associateBy { it.key }
    val pinned = pins.mapNotNull { byKey[it] }
    // Home page 0: clock and what is playing; then pinned apps four by page, the last page ending with Add.
    val appPages = (pinned.map<LauncherApp, LauncherApp?> { it } + null).chunked(4)
    val pager = rememberPagerState { 1 + appPages.size }
    var sources by remember { mutableStateOf(false) }
    // The menu and its sub-menus (Navigation, Multimedia, Phone, Apps): kept while a tile's screen is open, so Back
    // comes back to the same sub-menu; the menu itself again after the home screen.
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(AmbientSection.ROOT) }
    androidx.compose.runtime.LaunchedEffect(screen) { if (screen == Screen.HOME) section = AmbientSection.ROOT }
    // Pop-up menu of the Options key (null: closed).
    var options by remember { mutableStateOf<List<AmbientOption>?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind {
                // The ambient light: an oval of the deep accent rising behind the bottom bar.
                val glow = deep(accent)
                drawOval(
                    Brush.radialGradient(
                        0f to glow,
                        0.55f to Color.Transparent,
                        center = Offset(size.width / 2, size.height),
                        radius = size.width * 0.5f,
                    ),
                    topLeft = Offset(0f, size.height * 0.62f),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.76f),
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            AmbientTopBar(
                actions,
                if (screen == Screen.HOME) pager else null,
                when (screen) {
                    Screen.MENU -> stringResource(section.title)
                    Screen.MEDIA -> stringResource(R.string.ambient_multimedia)
                    Screen.APPS -> stringResource(R.string.factory_apps)
                    Screen.SETTINGS -> stringResource(R.string.ambient_system)
                    Screen.OBD -> stringResource(R.string.factory_vehicle)
                    else -> ""
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                when (screen) {
                    Screen.HOME -> AmbientHome(pager, appPages, media, actions, onAppMenu)
                    Screen.MENU -> AmbientMenu(section, { section = it }, actions)
                    Screen.MEDIA -> AmbientMedia(media, actions)
                    Screen.APPS -> AmbientApps(apps, actions, onAppMenu)
                    else -> content()
                }
            }
            // Back: one step up (a sub-menu to the menu, the menu to home…); a long press, straight home.
            val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            val back =
                AmbientButton(R.string.ambient_back, Icons.AutoMirrored.Outlined.Undo, onLongClick = { actions.show(Screen.HOME) }) {
                    when {
                        screen == Screen.MENU && section != AmbientSection.ROOT -> section = AmbientSection.ROOT
                        // The screen shown before (MainActivity keeps the trail).
                        backDispatcher != null -> backDispatcher.onBackPressed()
                        else -> actions.show(Screen.HOME)
                    }
                }
            val optionList = ambientOptions(screen, actions) { sources = true }
            val optionsKey = AmbientButton(R.string.ambient_options, Icons.Outlined.ListAlt, enabled = optionList.isNotEmpty()) { options = optionList }
            AmbientBottomBar(
                when (screen) {
                    Screen.HOME -> listOf(AmbientButton(R.string.ambient_menu, null) { actions.show(Screen.MENU) }, optionsKey)
                    // Main action of the menu: the phone projection (a long press: CarPlay).
                    Screen.MENU ->
                        listOf(
                            back,
                            AmbientButton(R.string.factory_projection, null, onLongClick = { openPackage(context, actions, CARPLAY) }) {
                                if (AndroidAutoLink.state.value.active) AndroidAutoLink.show(context) else openPackage(context, actions, *ANDROID_AUTO)
                            },
                            optionsKey,
                        )
                    Screen.MEDIA -> listOf(back, AmbientButton(R.string.ambient_sources, null) { sources = true }, optionsKey)
                    else -> listOf(back, optionsKey)
                },
            )
        }
        if (sources) AmbientSources(actions) { sources = false }
        options?.let { list -> AmbientOptionsPopup(list) { options = null } }
    }
}

// --- Bars ----------------------------------------------------------------------------------------------------------

/** Clock, map and audio shortcuts on the left; page dots in the middle (home); phone and weather on the right. */
@Composable
private fun AmbientTopBar(
    actions: LauncherActions,
    pager: PagerState?,
    title: String,
) {
    val context = LocalContext.current
    val now = rememberMinute()
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    android.text.format.DateFormat
                        .getTimeFormat(context)
                        .format(now),
                    color = LIGHT,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.width(20.dp))
                ShortcutIcon(Icons.Outlined.Map) { openNavigation(context, actions) }
                Spacer(Modifier.width(8.dp))
                ShortcutIcon(Icons.Outlined.MusicNote) { actions.show(Screen.MEDIA) }
            }
            // In the middle: the page dots on the home pages, the name of the screen elsewhere.
            if (pager != null && pager.pageCount > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.Center)) {
                    repeat(pager.pageCount) { i ->
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.2f)),
                        )
                    }
                }
            } else if (title.isNotEmpty()) {
                Text(title, color = LIGHT, fontSize = 22.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.Center))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterEnd)) {
                // The phone in a box drawn like the shortcuts, the outside temperature after it.
                Row(
                    Modifier.height(40.dp).border(BorderStroke(1.5.dp, CarColors.Accent)).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Phone, null, tint = LIGHT, modifier = Modifier.size(24.dp))
                    PhoneStatusView(compact = true)
                }
                Spacer(Modifier.width(14.dp))
                WeatherChip()
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(2.dp).background(CarColors.Accent))
    }
}

@Composable
private fun ShortcutIcon(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .border(BorderStroke(1.5.dp, CarColors.Accent))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = LIGHT, modifier = Modifier.size(24.dp)) }
}

private class AmbientButton(
    val label: Int,
    val icon: ImageVector?,
    val onLongClick: (() -> Unit)? = null,
    /** Off: drawn at 40 % and not touchable, like the original's greyed keys. */
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** Two or three buttons side by side: grey sheen on top, black in the middle, the accent light at the bottom. */
@Composable
private fun AmbientBottomBar(buttons: List<AmbientButton>) {
    Row(Modifier.fillMaxWidth().height(62.dp).padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
        buttons.forEach { BarButton(it) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.BarButton(b: AmbientButton) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = CarColors.Accent
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer { alpha = if (b.enabled) 1f else 0.4f }
                .background(
                    Brush.verticalGradient(
                        0.0225f to SHEEN,
                        (if (pressed) 0.25f else 0.4581f) to Color.Black,
                        1f to if (pressed) accent else deep(accent),
                    ),
                ).border(BorderStroke(1.dp, accent))
                .then(
                    if (b.enabled) {
                        Modifier.combinedClickable(interactionSource = interaction, indication = null, onClick = b.onClick, onLongClick = b.onLongClick)
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (b.icon != null) {
            Icon(b.icon, stringResource(b.label), tint = LIGHT, modifier = Modifier.size(30.dp))
        } else {
            Text(stringResource(b.label), color = LIGHT, fontSize = 22.sp, fontWeight = FontWeight.Light)
        }
    }
}

// --- Home ----------------------------------------------------------------------------------------------------------

/** The home pages in a lit frame: clock and what is playing, then pinned apps as small widgets. */
@Composable
private fun AmbientHome(
    pager: PagerState,
    appPages: List<List<LauncherApp?>>,
    media: MediaRepository,
    actions: LauncherActions,
    onAppMenu: (LauncherApp) -> Unit,
) {
    Frame(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (page == 0) {
                    Widget(Modifier.weight(1f)) { ClockWidget() }
                    Widget(Modifier.weight(1f), onClick = { actions.show(Screen.MEDIA) }) { MediaWidget(media, actions) }
                } else {
                    // Four small widgets: two by two.
                    val apps = appPages[page - 1]
                    for (col in 0 until 2) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (row in 0 until 2) {
                                val i = row * 2 + col
                                val m = Modifier.weight(1f).fillMaxWidth()
                                when {
                                    i >= apps.size -> Spacer(m)
                                    apps[i] == null -> Widget(m, onClick = { actions.show(Screen.APPS) }) { AppWidget(null, stringResource(R.string.ambient_add)) }
                                    else -> {
                                        val app = apps[i]!!
                                        Widget(m, onClick = { actions.launch(app.component) }, onLongClick = { onAppMenu(app) }) { AppWidget(app, app.label) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A panel: a thin line of the accent colour around it, the ambient light of the screen showing through. */
@Composable
private fun Frame(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = CarColors.Accent
    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .border(BorderStroke(1.dp, accent)),
        content = content,
    )
}

/** The light along the bottom of a panel: nothing over its top 64 %, then rising to the edge. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.bottomGlow(
    accent: Color,
    strength: Float,
) = drawRect(
    Brush.verticalGradient(
        0f to Color.Transparent,
        0.64f to Color.Transparent,
        1f to accent.copy(alpha = strength),
    ),
)

/** A widget: a frame whose bottom edge glows in the accent colour, brighter while pressed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Widget(
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = CarColors.Accent
    Box(
        modifier
            .fillMaxHeight()
            .background(Color.Black)
            .drawBehind { bottomGlow(if (pressed) accent else deep(accent), 0.85f) }
            .border(BorderStroke(1.dp, accent))
            .then(
                if (onClick != null) Modifier.combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick) else Modifier,
            ),
        content = content,
    )
}

/** Analog clock: white ticks and hands on black, no dial, the date inside under the centre. */
@Composable
private fun BoxScope.ClockWidget() {
    val now = rememberMinute()
    val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(now)
    Box(Modifier.align(Alignment.Center).padding(16.dp).aspectRatio(1f).heightIn(max = 300.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val c = Offset(size.width / 2, size.height / 2)
            for (i in 0 until 60) {
                val a = i * PI / 30
                val hour = i % 5 == 0
                val inner = if (hour) r * 0.9f else r * 0.94f
                drawLine(
                    Color.White,
                    Offset(c.x + (inner * sin(a)).toFloat(), c.y - (inner * cos(a)).toFloat()),
                    Offset(c.x + (r * sin(a)).toFloat(), c.y - (r * cos(a)).toFloat()),
                    if (hour) r * 0.035f else r * 0.012f,
                )
            }
            val cal = java.util.Calendar.getInstance().apply { time = now }
            val m = cal.get(java.util.Calendar.MINUTE)
            val h = cal.get(java.util.Calendar.HOUR) + m / 60f
            fun hand(
                angle: Double,
                length: Float,
                width: Float,
            ) = drawLine(Color.White, c, Offset(c.x + (length * sin(angle)).toFloat(), c.y - (length * cos(angle)).toFloat()), width)
            hand(h * PI / 6, r * 0.6f, r * 0.03f)
            hand(m * PI / 30, r * 0.8f, r * 0.018f)
            drawCircle(Color.White, r * 0.065f, c)
        }
        Text(date, color = LIGHT, fontSize = 22.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.Center).padding(top = 120.dp))
    }
}

/** What is playing: cover, title, artist, previous / play / next. */
@Composable
private fun BoxScope.MediaWidget(
    media: MediaRepository,
    actions: LauncherActions,
) {
    val np by media.nowPlaying.collectAsStateWithLifecycle()
    val p = np
    Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Cover(p, Modifier.weight(1f, fill = false).aspectRatio(1f).heightIn(max = 180.dp))
        Spacer(Modifier.height(12.dp))
        Text(p?.title ?: stringResource(R.string.factory_no_media), color = LIGHT, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!p?.subtitle.isNullOrEmpty()) Text(p!!.subtitle, color = DIM, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Transport(p?.playing == true, actions, small = true)
    }
}

@Composable
private fun BoxScope.AppWidget(
    app: LauncherApp?,
    label: String,
) {
    Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        if (app != null) {
            Image(app.icon, null, modifier = Modifier.size(56.dp))
        } else {
            GlowIcon(Icons.Outlined.Add, 56.dp)
        }
        Spacer(Modifier.width(18.dp))
        Text(label, color = LIGHT, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// --- Menu ----------------------------------------------------------------------------------------------------------

/** The menu and its sub-menus, each a grid of tiles with its own title. */
internal enum class AmbientSection(
    val title: Int,
) {
    ROOT(R.string.ambient_menu),
    NAV(R.string.factory_navigation),
    MULTIMEDIA(R.string.ambient_multimedia),
    PHONE(R.string.factory_phone),
    APPS(R.string.factory_apps),
}

private class AmbientTile(
    val icon: ImageVector,
    val label: Int,
    val onClick: () -> Unit,
)

/** Opens [intent] as a new task; nothing to open it: a short note. */
private fun startOrNote(
    context: android.content.Context,
    intent: android.content.Intent,
) {
    try {
        context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: RuntimeException) {
        android.widget.Toast.makeText(context, R.string.factory_no_app, android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** The LibreHU phone app on one of its tabs (CONTACTS, RECENTS, KEYPAD, SETTINGS). */
private fun dialerTab(
    context: android.content.Context,
    tab: String,
) {
    val i = context.packageManager.getLaunchIntentForPackage(DIALER)
    if (i == null) {
        android.widget.Toast.makeText(context, R.string.factory_no_app, android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    startOrNote(context, i.putExtra("org.librehu.dialer.extra.OPEN_TAB", tab))
}

@Composable
private fun sectionTiles(
    section: AmbientSection,
    onSection: (AmbientSection) -> Unit,
    actions: LauncherActions,
): List<AmbientTile> {
    val context = LocalContext.current
    return when (section) {
        AmbientSection.ROOT ->
            listOf(
                AmbientTile(Icons.Outlined.Navigation, R.string.factory_navigation) { onSection(AmbientSection.NAV) },
                AmbientTile(Icons.Outlined.PlayCircle, R.string.ambient_multimedia) { onSection(AmbientSection.MULTIMEDIA) },
                AmbientTile(Icons.Outlined.Phone, R.string.factory_phone) { onSection(AmbientSection.PHONE) },
                AmbientTile(Icons.Outlined.Apps, R.string.factory_apps) { onSection(AmbientSection.APPS) },
                AmbientTile(Icons.Outlined.DirectionsCar, R.string.factory_vehicle) { actions.show(Screen.OBD) },
                AmbientTile(Icons.Outlined.Settings, R.string.ambient_system) { actions.show(Screen.SETTINGS) },
            )
        AmbientSection.NAV ->
            listOf(
                // Destination: the navigation app's search (geo: query), else the app itself.
                AmbientTile(Icons.Outlined.Flag, R.string.ambient_destination) {
                    val geo = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q="))
                    if (geo.resolveActivity(context.packageManager) != null) startOrNote(context, geo) else openNavigation(context, actions)
                },
                AmbientTile(Icons.Outlined.Map, R.string.ambient_map) { openNavigation(context, actions) },
                AmbientTile(Icons.Outlined.Speed, R.string.ambient_nav_settings) { actions.show(Screen.SETTINGS) },
            )
        AmbientSection.MULTIMEDIA ->
            listOf(
                AmbientTile(Icons.Outlined.Radio, R.string.factory_radio) { openPackage(context, actions, LauncherPrefs.FM_PACKAGE) },
                AmbientTile(Icons.Outlined.Album, R.string.ambient_media) { actions.show(Screen.MEDIA) },
                AmbientTile(Icons.Outlined.Image, R.string.ambient_photo) {
                    startOrNote(context, android.content.Intent.makeMainSelectorActivity(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_GALLERY))
                },
                AmbientTile(Icons.Outlined.Movie, R.string.ambient_video) { openPackage(context, actions, "org.videolan.vlc", "org.librehu.disc") },
                AmbientTile(Icons.Outlined.Tune, R.string.ambient_sound) { startOrNote(context, android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) },
            )
        AmbientSection.PHONE ->
            listOf(
                AmbientTile(Icons.Outlined.Contacts, R.string.ambient_contacts) { dialerTab(context, "CONTACTS") },
                AmbientTile(Icons.Outlined.History, R.string.ambient_call_log) { dialerTab(context, "RECENTS") },
                AmbientTile(Icons.Outlined.Dialpad, R.string.ambient_dial) { dialerTab(context, "KEYPAD") },
                AmbientTile(Icons.Outlined.Smartphone, R.string.ambient_smartphone) {
                    startOrNote(context, android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                AmbientTile(Icons.Outlined.Settings, R.string.ambient_phone_settings) { dialerTab(context, "SETTINGS") },
            )
        AmbientSection.APPS ->
            listOf(
                AmbientTile(Icons.Outlined.Apps, R.string.ambient_my_apps) { actions.show(Screen.APPS) },
                AmbientTile(Icons.Outlined.ShoppingBag, R.string.ambient_store) { openPackage(context, actions, "org.librehu.ota") },
                AmbientTile(Icons.Outlined.ManageSearch, R.string.ambient_app_manager) {
                    startOrNote(context, android.content.Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS))
                },
            )
    }
}

/** A menu: its tiles three by row, side by side in thin frames of the accent colour. */
@Composable
internal fun AmbientMenu(
    section: AmbientSection,
    onSection: (AmbientSection) -> Unit,
    actions: LauncherActions,
) {
    val tiles = sectionTiles(section, onSection, actions)
    // A new set of tiles zooms in, like the original.
    androidx.compose.runtime.key(section) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tiles.chunked(3).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { t -> MenuTile(t, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // Fewer than six tiles: keep the tiles the size of the menu's.
            repeat(2 - (tiles.size + 2) / 3) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** A menu tile: black in a thin frame of the accent colour, icon over its name; pressed, lit from the bottom. */
@Composable
private fun MenuTile(
    t: AmbientTile,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = CarColors.Accent
    // Previews and screenshots draw the end of the animation.
    val still = androidx.compose.ui.platform.LocalInspectionMode.current
    val zoom = remember { androidx.compose.animation.core.Animatable(if (still) 1f else 0.86f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { zoom.animateTo(1f, androidx.compose.animation.core.tween(220)) }
    Box(
        modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = zoom.value
                scaleY = zoom.value
                alpha = (zoom.value - 0.86f) / 0.14f
            }.background(Color.Black.copy(alpha = 0.75f))
            .drawBehind { if (pressed) bottomGlow(deep(accent), 0.9f) }
            .border(BorderStroke(1.dp, accent))
            .clickable(interactionSource = interaction, indication = null, onClick = t.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MetalIcon(t.icon, 76.dp)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(t.label), color = LIGHT, fontSize = 23.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** An icon in brushed metal: white at the top fading to grey at the bottom, like the original's grey glyphs. */
@Composable
private fun MetalIcon(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp,
) = Icon(
    icon,
    null,
    tint = Color.White,
    modifier =
        Modifier
            .size(size)
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFC9CDD3), Color(0xFF7E848D))),
                    blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn,
                )
            },
)

// --- Apps --------------------------------------------------------------------------------------------------------

/** My apps: every app as a tile framed in the accent colour, four by row, sorted by name. Long press: its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AmbientApps(
    apps: List<LauncherApp>,
    actions: LauncherActions,
    onAppMenu: (LauncherApp) -> Unit,
) {
    val look by org.librehu.launcher.data.ThemeStore
        .get(LocalContext.current)
        .settings
        .collectAsStateWithLifecycle()
    val shown = remember(apps, look.hiddenApps) { apps.filter { it.key !in look.hiddenApps }.sortedBy { it.label.lowercase() } }
    val accent = CarColors.Accent
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shown.size, key = { shown[it].key }) { i ->
            val app = shown[i]
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .height(150.dp)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .drawBehind { if (pressed) bottomGlow(deep(accent), 0.9f) }
                        .border(BorderStroke(1.dp, accent))
                        .combinedClickable(interactionSource = interaction, indication = null, onClick = { actions.launch(app.component) }, onLongClick = { onAppMenu(app) })
                        .padding(8.dp),
            ) {
                Image(app.icon, null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(10.dp))
                Text(app.label, color = LIGHT, fontSize = 19.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// --- Options pop-up ------------------------------------------------------------------------------------------------

internal class AmbientOption(
    val label: Int,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** What the Options key offers on each screen; empty: the key is greyed. */
@Composable
private fun ambientOptions(
    screen: Screen,
    actions: LauncherActions,
    onSources: () -> Unit,
): List<AmbientOption> {
    val context = LocalContext.current
    return when (screen) {
        Screen.HOME ->
            listOf(
                AmbientOption(R.string.ambient_edit_home) { actions.show(Screen.APPS) },
                AmbientOption(R.string.ambient_change_theme) { actions.show(Screen.SETTINGS) },
                AmbientOption(R.string.ambient_date_time) { startOrNote(context, android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS)) },
                AmbientOption(R.string.ambient_black_screen) { actions.standby() },
            )
        Screen.MEDIA ->
            listOf(
                AmbientOption(R.string.ambient_sources, onClick = onSources),
                AmbientOption(R.string.ambient_open_player) { actions.mediaOpen() },
                AmbientOption(R.string.ambient_sound) { startOrNote(context, android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) },
            )
        else -> emptyList()
    }
}

/**
 * The pop-up menu: a column of choices over the right of the bottom bar, each line framed in the accent colour on
 * night blue, the screen dimmed behind. A touch outside closes it.
 */
@Composable
internal fun AmbientOptionsPopup(
    items: List<AmbientOption>,
    onDismiss: () -> Unit,
) {
    val accent = CarColors.Accent
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 62.dp)
                .fillMaxWidth(0.5f)
                .background(Color(0xF00E1626))
                .border(BorderStroke(1.dp, accent))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            items.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(accent))
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .drawBehind { if (pressed) drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent))) }
                        .then(
                            if (item.enabled) {
                                Modifier.clickable(interactionSource = interaction, indication = null) {
                                    onDismiss()
                                    item.onClick()
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(item.label), color = if (item.enabled) LIGHT else DIM.copy(alpha = 0.6f), fontSize = 21.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}

/** A white icon on a soft disc of the accent colour. */
@Composable
private fun GlowIcon(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp,
) {
    val accent = CarColors.Accent
    Box(
        Modifier.size(size).drawBehind {
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent)), radius = this.size.minDimension * 0.62f)
        },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = LIGHT, modifier = Modifier.size(size * 0.7f)) }
}

// --- Multimedia ----------------------------------------------------------------------------------------------------

/** Multimedia: the cover on the left, title, artist and progress on the right, the controls under them. */
@Composable
private fun AmbientMedia(
    media: MediaRepository,
    actions: LauncherActions,
) {
    val access by media.hasAccess.collectAsStateWithLifecycle()
    val np by media.nowPlaying.collectAsStateWithLifecycle()
    Frame(Modifier.fillMaxSize()) {
        val p = np
        when {
            !access ->
                Text(
                    stringResource(R.string.factory_no_access),
                    color = DIM,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.Center).clickable { actions.grantMediaAccess() },
                )
            p == null -> Text(stringResource(R.string.factory_no_media), color = DIM, fontSize = 22.sp, modifier = Modifier.align(Alignment.Center))
            else ->
                Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(p, Modifier.fillMaxHeight(0.9f).aspectRatio(1f))
                    Spacer(Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.MusicNote, null, tint = DIM, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(p.title, color = LIGHT, fontSize = 30.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (p.subtitle.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Person, null, tint = DIM, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(p.subtitle, color = DIM, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (p.durationMs > 0) {
                            Spacer(Modifier.height(22.dp))
                            ProgressLine(p)
                        }
                        Spacer(Modifier.height(22.dp))
                        Transport(p.playing, actions, small = false)
                    }
                }
        }
    }
}

@Composable
private fun Cover(
    np: NowPlaying?,
    modifier: Modifier,
) {
    val art = remember(np?.art) { np?.art?.asImageBitmap() }
    val accent = CarColors.Accent
    Box(
        modifier
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width / 2, size.height), radius = size.width * 0.7f))
            }.border(BorderStroke(1.dp, Color(0xFF5A6575))),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF111824)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MusicNote, null, tint = DIM, modifier = Modifier.fillMaxSize(0.45f))
            }
        }
    }
}

/** Progress of the track (shown only; seeking stays in the player and the media widgets). */
@Composable
private fun ProgressLine(np: NowPlaying) {
    var position by remember { mutableLongStateOf(np.positionNow()) }
    LaunchedEffect(np) {
        position = np.positionNow()
        while (np.playing) {
            delay(1000L - position % 1000L)
            position = np.positionNow()
        }
    }
    val f = (position.toFloat() / np.durationMs).coerceIn(0f, 1f)
    Column {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2A3442))) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(CarColors.Accent))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(time(position), color = DIM, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text(time(np.durationMs), color = DIM, fontSize = 15.sp)
        }
    }
}

/** Previous, play / pause, next: round brushed buttons, the middle one lit by the accent colour. */
@Composable
private fun Transport(
    playing: Boolean,
    actions: LauncherActions,
    small: Boolean,
) {
    val s = if (small) 44.dp else 64.dp
    Row(horizontalArrangement = Arrangement.spacedBy(if (small) 14.dp else 24.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Outlined.SkipPrevious, s, false, actions.mediaPrevious)
        RoundButton(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, s * 1.2f, true, actions.mediaToggle)
        RoundButton(Icons.Outlined.SkipNext, s, false, actions.mediaNext)
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp,
    lit: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(0f to SHEEN, 0.2f to BAR_TOP, 0.5f to BAR_BOTTOM, 1f to Color.Black))
            .border(BorderStroke(if (lit || pressed) 2.dp else 1.dp, if (lit || pressed) CarColors.Accent else Color(0xFF4A515B)), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = LIGHT, modifier = Modifier.size(size * 0.55f)) }
}

/** Sources: radio, disc, Android Auto, CarPlay and the music apps, in a lit frame. */
@Composable
private fun AmbientSources(
    actions: LauncherActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val list = remember { mediaSources(context) }
    Dialog(onDismissRequest = onDismiss) {
        Frame(Modifier.width(520.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(R.string.ambient_sources), color = LIGHT, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.component.flattenToString() }) { src ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        actions.launch(src.component)
                                    }.padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            when {
                                src.icon != null -> GlowIcon(src.icon, 40.dp)
                                src.image != null -> Image(src.image, null, modifier = Modifier.size(40.dp))
                                else -> GlowIcon(Icons.Outlined.PhoneAndroid, 40.dp)
                            }
                            Spacer(Modifier.width(18.dp))
                            Text(src.label, color = LIGHT, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Color(0xFF243040)))
                    }
                }
            }
        }
    }
}

/** The current time, updated on each minute. */
@Composable
private fun rememberMinute(): java.util.Date {
    var now by remember { mutableStateOf(java.util.Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.util.Date()
            delay(1000L * (60 - (System.currentTimeMillis() / 1000) % 60))
        }
    }
    return now
}
