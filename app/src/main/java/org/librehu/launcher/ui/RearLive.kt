package org.librehu.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max

/** Rear view widget of LibreHU-service: shown live by the launcher instead of its pictures (see [RearLive]). */
val REAR_WIDGET = ComponentName("org.librehu.service", "org.librehu.service.car.ReverseWidget")

/** What LibreHU-service sends back about the rear camera (its RearPreviewService). */
private data class RearInfo(
    val width: Int,
    val height: Int,
    val mirror: Boolean,
    val flip: Boolean,
    val rotation: Int,
    val zoom: Int,
    val offsetX: Int,
    val offsetY: Int,
)

/**
 * Live rear camera over the rear view widget of LibreHU-service. An app widget cannot show a video stream, so the
 * launcher binds to the service's RearPreviewService, puts a TextureView here and hands its surface over: the camera
 * draws straight into it, at full rate. Until the stream runs (service missing, live picture off, camera busy…)
 * nothing is drawn and the widget underneath shows (its pictures or its button). A touch opens or closes the full
 * rear view.
 */
@Composable
fun RearLive(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<RearInfo?>(null) }
    var live by remember { mutableStateOf(false) }
    val link = remember { RearLink(context.applicationContext, onInfo = { info = it }, onLive = { live = it }) }
    DisposableEffect(Unit) {
        link.bind()
        onDispose { link.unbind() }
    }
    val i = info ?: return
    Box(modifier.clickable { link.toggle() }) {
        AndroidView(
            factory = { c -> link.textureView(c, i) },
            modifier = Modifier.fillMaxSize(),
            update = { it.alpha = if (live) 1f else 0f },
        )
    }
}

/** Connection to RearPreviewService and the TextureView whose surface it draws into. */
private class RearLink(
    private val context: Context,
    private val onInfo: (RearInfo?) -> Unit,
    private val onLive: (Boolean) -> Unit,
) {
    private var service: Messenger? = null
    private var surface: Surface? = null
    private var bound = false
    private val replies =
        Messenger(
            object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        MSG_INFO -> {
                            val d = msg.data
                            onInfo(
                                if (d.getBoolean("enabled")) {
                                    RearInfo(
                                        d.getInt("width"),
                                        d.getInt("height"),
                                        d.getBoolean("mirror"),
                                        d.getBoolean("flip"),
                                        d.getInt("rotation"),
                                        d.getInt("zoom", 100),
                                        d.getInt("offsetX"),
                                        d.getInt("offsetY"),
                                    )
                                } else {
                                    null
                                },
                            )
                        }

                        MSG_STATE -> {
                            onLive(msg.arg1 == STATE_LIVE)
                        }
                    }
                }
            },
        )

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                service = Messenger(binder)
                send(MSG_QUERY)
                surface?.let(::attach)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
                onLive(false)
            }
        }

    fun bind() {
        bound =
            runCatching {
                context.bindService(Intent(ACTION).setPackage(REAR_WIDGET.packageName), connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
    }

    fun unbind() {
        send(MSG_DETACH)
        surface?.release()
        surface = null
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        service = null
    }

    fun toggle() = send(MSG_TOGGLE)

    fun textureView(
        c: Context,
        info: RearInfo,
    ): TextureView {
        val tv = TextureView(c)
        tv.surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    st: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    // The camera picks its output size from the buffer size: one of its supported sizes.
                    st.setDefaultBufferSize(info.width, info.height)
                    val s = Surface(st)
                    surface = s
                    transform(tv, info)
                    attach(s)
                }

                override fun onSurfaceTextureSizeChanged(
                    st: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) = transform(tv, info)

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    send(MSG_DETACH)
                    onLive(false)
                    surface?.release()
                    surface = null
                    return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        tv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> transform(tv, info) }
        return tv
    }

    private fun attach(s: Surface) {
        val m = service ?: return
        runCatching {
            m.send(
                Message.obtain(null, MSG_ATTACH).apply {
                    data = Bundle().apply { putParcelable("surface", s) }
                    replyTo = replies
                },
            )
        }
    }

    private fun send(what: Int) {
        val m = service ?: return
        runCatching { m.send(Message.obtain(null, what).apply { replyTo = replies }) }
    }

    /** Fills the card keeping the picture's proportions, then the rear view's rotation, mirror, flip, zoom and shift. */
    private fun transform(
        tv: TextureView,
        i: RearInfo,
    ) {
        val w = tv.width.toFloat()
        val h = tv.height.toFloat()
        if (w <= 0 || h <= 0 || i.width <= 0 || i.height <= 0) return
        val rot = ((i.rotation % 360 + 360) % 360 / 90) * 90
        val turned = rot % 180 != 0
        val iw = (if (turned) i.height else i.width).toFloat()
        val ih = (if (turned) i.width else i.height).toFloat()
        val scale = max(w / iw, h / ih) * i.zoom.coerceIn(50, 400) / 100f
        val cx = w / 2
        val cy = h / 2
        val m = Matrix()
        m.setScale(i.width * scale / w, i.height * scale / h, cx, cy)
        if (rot != 0) m.postRotate(rot.toFloat(), cx, cy)
        if (i.mirror) m.postScale(-1f, 1f, cx, cy)
        if (i.flip) m.postScale(1f, -1f, cx, cy)
        m.postTranslate(i.offsetX / 100f * w, i.offsetY / 100f * h)
        tv.setTransform(m)
    }

    private companion object {
        const val ACTION = "org.librehu.service.REAR_PREVIEW"
        const val MSG_QUERY = 1
        const val MSG_ATTACH = 2
        const val MSG_DETACH = 3
        const val MSG_TOGGLE = 4
        const val MSG_INFO = 10
        const val MSG_STATE = 11
        const val STATE_LIVE = 1
    }
}
