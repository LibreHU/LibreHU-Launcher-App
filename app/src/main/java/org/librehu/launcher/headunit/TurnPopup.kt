package org.librehu.launcher.headunit

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.librehu.launcher.data.AaManeuver
import org.librehu.launcher.data.AndroidAutoLink
import org.librehu.launcher.data.ThemeStore
import org.librehu.launcher.ui.CarColors

/**
 * Android Auto's directions in a popup over the other screens (FM, music, home…): each new direction, then once more
 * when it comes close ([REMIND_METERS]), for [SHOW_MS]. Never over Android Auto itself, which shows the maneuver
 * already: Open Headunit LibreHU says with each NAVIGATION_UPDATE whether its projection is in front
 * ([AaManeuver.projectionOnScreen]). A touch brings Android Auto to the front. Switched in Settings → Look
 * ([org.librehu.launcher.data.ThemeSettings.aaTurnPopup]); drawn as an overlay window, which needs the
 * SYSTEM_ALERT_WINDOW app-op: granted by root (`appops set`) or by the user in Android's settings.
 */
object TurnPopup {
    private const val TAG = "LibreHU-TurnPopup"
    private const val SHOW_MS = 6_000L
    private const val REMIND_METERS = 200

    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var lastKey: String? = null
    private var reminded = false
    private var view: LinearLayout? = null
    private var arrow: TextView? = null
    private var title: TextView? = null
    private var subtitle: TextView? = null
    private val hide = Runnable { remove() }

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        if (ThemeStore.get(app).settings.value.aaTurnPopup) ensurePermission(app, ask = false)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            AndroidAutoLink.maneuver.collect { onManeuver(app, it) }
        }
    }

    private fun onManeuver(
        context: Context,
        m: AaManeuver?,
    ) {
        if (m == null) {
            lastKey = null
            remove()
            return
        }
        val key = "${m.action}|${m.road}|${m.side}"
        val show =
            if (key != lastKey) {
                lastKey = key
                // Already close when announced: the announcement is the reminder.
                reminded = m.distanceMeters in 0..REMIND_METERS
                true
            } else if (!reminded && m.distanceMeters in 0..REMIND_METERS) {
                reminded = true
                true
            } else {
                false
            }
        if (m.projectionOnScreen) {
            remove()
            return
        }
        // The distance of a popup on screen follows the updates.
        if (view != null) fill(m)
        if (show && ThemeStore.get(context).settings.value.aaTurnPopup && canDraw(context)) show(context, m)
    }

    private fun show(
        context: Context,
        m: AaManeuver,
    ) {
        val v = view ?: create(context) ?: return
        fill(m)
        main.removeCallbacks(hide)
        main.postDelayed(hide, SHOW_MS)
        v.visibility = View.VISIBLE
    }

    private fun create(context: Context): LinearLayout? {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
        val palette = CarColors.palette
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(12), dp(24), dp(12))
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(28).toFloat()
                        setColor(palette.surface.toArgb())
                        setStroke(dp(2), palette.accent.toArgb())
                    }
                elevation = dp(8).toFloat()
                setOnClickListener {
                    AndroidAutoLink.show(context)
                    remove()
                }
            }
        arrow =
            TextView(context).apply {
                setTextColor(palette.accent.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, dp(16), 0)
            }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        title =
            TextView(context).apply {
                setTextColor(palette.text.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        subtitle =
            TextView(context).apply {
                setTextColor(palette.textDim.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        texts.addView(title)
        texts.addView(subtitle)
        root.addView(arrow)
        root.addView(texts)
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(20)
                windowAnimations = android.R.style.Animation_Toast
            }
        // Texts at most 60 % of the screen width, so a long road name does not cover it all.
        root.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        texts.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        title?.maxWidth = (context.resources.displayMetrics.widthPixels * 0.6).toInt()
        subtitle?.maxWidth = (context.resources.displayMetrics.widthPixels * 0.6).toInt()
        return try {
            wm.addView(root, params)
            view = root
            root
        } catch (e: RuntimeException) {
            Log.w(TAG, "overlay refused: ${e.message}")
            null
        }
    }

    private fun fill(m: AaManeuver) {
        arrow?.text =
            when (m.side) {
                1 -> "↰"
                2 -> "↱"
                else -> "↑"
            }
        title?.text = listOf(distance(m.distanceMeters), m.action).filter { it.isNotEmpty() }.joinToString(" · ")
        val sub = listOf(m.road, m.arrival.takeIf { it.isNotEmpty() }?.let { "→ $it" }.orEmpty()).filter { it.isNotEmpty() }
        subtitle?.text = sub.joinToString(" · ")
        subtitle?.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun remove() {
        main.removeCallbacks(hide)
        val v = view ?: return
        view = null
        arrow = null
        title = null
        subtitle = null
        runCatching { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
    }

    private fun distance(meters: Int): String =
        when {
            meters < 0 -> ""
            meters < 1000 -> "${(meters / 10) * 10} m"
            else -> String.format(java.util.Locale.getDefault(), "%.1f km", meters / 1000f)
        }

    private fun canDraw(context: Context) = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    /**
     * Gets the overlay permission: by root first (`appops set … SYSTEM_ALERT_WINDOW allow`), else, when [ask], Android's
     * screen for it.
     */
    fun ensurePermission(
        context: Context,
        ask: Boolean,
    ) {
        val app = context.applicationContext
        if (canDraw(app)) return
        Thread({
            val granted =
                runCatching {
                    val p = ProcessBuilder("su", "-c", "appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow").redirectErrorStream(true).start()
                    p.inputStream.readBytes()
                    p.waitFor() == 0
                }.getOrDefault(false)
            if ((!granted || !canDraw(app)) && ask) {
                main.post {
                    runCatching {
                        app.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${app.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
        }, "turn-popup-permission").start()
    }
}
