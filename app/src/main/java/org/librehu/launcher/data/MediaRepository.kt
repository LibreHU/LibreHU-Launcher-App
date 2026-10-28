package org.librehu.launcher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val packageName: String,
    val title: String,
    val subtitle: String,
    /** Album, when the player gives one besides the artist (empty otherwise). */
    val album: String = "",
    val art: Bitmap?,
    val playing: Boolean,
    /** Track length in ms, 0 when unknown (radio, streams). */
    val durationMs: Long = 0,
    /** Position in ms at [positionAt] (elapsedRealtime), advancing at [speed] while playing. */
    val positionMs: Long = 0,
    val positionAt: Long = 0,
    val speed: Float = 1f,
    /** The app accepts seekTo, or (Bluetooth) it is reached by fast forward / rewind steps. */
    val canSeek: Boolean = false,
    /** Seeking is approximate (Bluetooth: AVRCP has no "go to position", only fast forward / rewind). */
    val seekApprox: Boolean = false,
    /** The player opens a disc tray (LibreHU Disc's eject action). */
    val canEject: Boolean = false,
) {
    /** Position now, extrapolated like Android's media notifications. */
    fun positionNow(now: Long = android.os.SystemClock.elapsedRealtime()): Long {
        val p = if (playing && positionAt > 0) positionMs + ((now - positionAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** Current media session (radio, music, phone projection…) for the dashboard card. */
class MediaRepository(
    private val context: Context,
) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var controllers: List<MediaController> = emptyList()
    private var started = false

    private val controllerCallback =
        object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

            override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

            override fun onSessionDestroyed() = publish()
        }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> setControllers(list.orEmpty()) }

    fun start() {
        _hasAccess.value = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (!_hasAccess.value || started) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
            setControllers(manager.getActiveSessions(listener))
            started = true
        } catch (_: SecurityException) {
            _hasAccess.value = false
        }
    }

    fun stop() {
        if (!started) return
        manager.removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = emptyList()
        started = false
    }

    /** The playing session first, else the most recent one. */
    val current: MediaController?
        get() = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()

    // Last toggle: some players (YouTube, Tidal over Bluetooth) report their new state late or not at all, so a
    // second press within a few seconds would send the same command again ("press twice to resume").
    private var toggledAt = 0L
    private var toggledToPlay = false
    private var reportedAtToggle = false
    private var toggledSession: android.media.session.MediaSession.Token? = null

    fun togglePlay() {
        val c = current ?: return
        val reported = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val now = android.os.SystemClock.uptimeMillis()
        val stale = toggledSession == c.sessionToken && now - toggledAt < TOGGLE_TRUST_MS && reported == reportedAtToggle
        // Not updated since the last toggle: the player is where that toggle sent it.
        val playing = if (stale) toggledToPlay else reported
        toggledAt = now
        toggledSession = c.sessionToken
        reportedAtToggle = reported
        toggledToPlay = !playing
        if (playing) {
            c.transportControls.pause()
        } else {
            // prepare() first: the Bluetooth player takes the audio focus with it; without the focus Android 9's A2DP
            // sink pauses the phone again right away (A2dpSinkStreamHandler). Harmless for the other players.
            c.transportControls.prepare()
            c.transportControls.play()
        }
    }

    fun next() = current?.transportControls?.skipToNext()

    /** Moves the current track to [positionMs] (apps that accept it, see [NowPlaying.canSeek]). */
    fun seekTo(positionMs: Long) {
        val c = current ?: return
        if (c.playbackState.hasSeekTo() || !isBluetooth(c)) {
            c.transportControls.seekTo(positionMs)
        } else {
            stepTo(c, positionMs)
        }
    }

    private var stepping: Runnable? = null

    /**
     * Bluetooth (AVRCP controller): no "go to position" in AVRCP, only the fast forward / rewind buttons of the phone.
     * They are pressed one at a time, the position being asked again after each, until it is within a few seconds of
     * the target; stops when the phone does not move (its player ignores them) or goes past the target twice.
     */
    private fun stepTo(
        c: MediaController,
        target: Long,
    ) {
        stepping?.let { main.removeCallbacks(it) }
        var steps = 0
        var stuck = 0
        var flips = 0
        var lastPos = -1L
        var lastDir = 0
        val r =
            object : Runnable {
                override fun run() {
                    if (current?.sessionToken != c.sessionToken) return
                    val pos = _nowPlaying.value?.positionNow() ?: return
                    val diff = target - pos
                    if (kotlin.math.abs(diff) <= SEEK_TOLERANCE_MS || steps >= SEEK_MAX_STEPS || stuck >= 3 || flips >= 2) {
                        stepping = null
                        return
                    }
                    val dir = if (diff > 0) 1 else -1
                    if (lastDir != 0 && dir != lastDir) flips++
                    stuck = if (lastPos >= 0 && kotlin.math.abs(pos - lastPos) < 1000) stuck + 1 else 0
                    lastPos = pos
                    lastDir = dir
                    steps++
                    if (dir > 0) c.transportControls.fastForward() else c.transportControls.rewind()
                    // The Bluetooth app asks the phone for its position again (AOSP custom action).
                    c.transportControls.sendCustomAction(BT_PLAY_STATUS, null)
                    main.postDelayed(this, SEEK_STEP_MS)
                }
            }
        stepping = r
        main.post(r)
    }

    private fun isBluetooth(c: MediaController) = c.packageName == BT_PACKAGE

    private fun PlaybackState?.hasSeekTo() = this != null && actions and PlaybackState.ACTION_SEEK_TO != 0L

    fun previous() = current?.transportControls?.skipToPrevious()

    /** Opens the tray of the disc player (sessions that offer [EJECT_ACTION]). */
    fun eject() = current?.transportControls?.sendCustomAction(EJECT_ACTION, null)

    fun open() {
        val c = current ?: return
        try {
            c.sessionActivity?.send() ?: launchPackage(c.packageName)
        } catch (_: Exception) {
            launchPackage(c.packageName)
        }
    }

    private fun launchPackage(pkg: String) {
        context.packageManager
            .getLaunchIntentForPackage(pkg)
            ?.component
            ?.let { AppsRepository.launch(context, it) }
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = list
        list.forEach { it.registerCallback(controllerCallback, main) }
        publish()
    }

    private fun publish() {
        val c = current
        val md = c?.metadata
        _nowPlaying.value =
            if (c == null || md == null) {
                null
            } else {
                NowPlaying(
                    packageName = c.packageName,
                    title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    subtitle =
                        md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                    album = if (md.getString(MediaMetadata.METADATA_KEY_ARTIST) != null) md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty() else "",
                    art =
                        md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
                    playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                    durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
                    positionMs = c.playbackState?.position?.takeIf { it >= 0 } ?: 0,
                    positionAt = c.playbackState?.lastPositionUpdateTime ?: 0,
                    speed = c.playbackState?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                    canSeek =
                        (c.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L ||
                            (isBluetooth(c) && md.getLong(MediaMetadata.METADATA_KEY_DURATION) > 0),
                    seekApprox = isBluetooth(c) && (c.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO == 0L,
                    canEject = c.playbackState?.customActions.orEmpty().any { it.action == EJECT_ACTION },
                )
            }
    }
}

private const val TOGGLE_TRUST_MS = 4000L

/** Custom action of LibreHU Disc's media session: stops playback and opens the tray. */
private const val EJECT_ACTION = "org.librehu.disc.action.EJECT"
private const val BT_PACKAGE = "com.android.bluetooth"
private const val BT_PLAY_STATUS = "com.android.bluetooth.a2dpsink.mbs.CUSTOM_ACTION_GET_PLAY_STATUS_NATIVE"
private const val SEEK_TOLERANCE_MS = 5000L
private const val SEEK_MAX_STEPS = 40
private const val SEEK_STEP_MS = 600L
