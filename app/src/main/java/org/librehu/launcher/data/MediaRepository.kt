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
    val art: Bitmap?,
    val playing: Boolean,
    /** Track length in ms, 0 when unknown (radio, streams). */
    val durationMs: Long = 0,
    /** Position in ms at [positionAt] (elapsedRealtime), advancing at [speed] while playing. */
    val positionMs: Long = 0,
    val positionAt: Long = 0,
    val speed: Float = 1f,
    /** The app accepts seekTo. */
    val canSeek: Boolean = false,
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
        current?.transportControls?.seekTo(positionMs)
    }

    fun previous() = current?.transportControls?.skipToPrevious()

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
                    art =
                        md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
                    playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                    durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
                    positionMs = c.playbackState?.position?.takeIf { it >= 0 } ?: 0,
                    positionAt = c.playbackState?.lastPositionUpdateTime ?: 0,
                    speed = c.playbackState?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                    canSeek = (c.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L,
                )
            }
    }
}

private const val TOGGLE_TRUST_MS = 4000L
