package org.librehu.launcher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Collator

data class LauncherApp(
    val component: ComponentName,
    val label: String,
    val icon: ImageBitmap,
) {
    val key: String get() = component.flattenToString()
}

/** Launchable apps, refreshed when packages are installed, removed or changed. */
class AppsRepository(
    private val context: Context,
) {
    private val _apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val apps: StateFlow<List<LauncherApp>> = _apps.asStateFlow()

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                intent: Intent,
            ) = refresh()
        }

    fun start() {
        refresh()
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun stop() = context.unregisterReceiver(receiver)

    fun refresh() {
        val pm = context.packageManager
        val collator = Collator.getInstance()
        val iconPx = (ICON_DP * context.resources.displayMetrics.density).toInt()
        _apps.value =
            pm
                .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .map {
                    val ai = it.activityInfo
                    LauncherApp(
                        ComponentName(ai.packageName, ai.name),
                        it.loadLabel(pm).toString(),
                        it.loadIcon(pm).toBitmap(iconPx, iconPx).asImageBitmap(),
                    )
                }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun launch(app: LauncherApp) = launch(context, app.component)

    companion object {
        private const val ICON_DP = 64

        fun launch(
            context: Context,
            component: ComponentName,
        ) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                )
            } catch (_: Exception) {
            }
        }

        /** Default activity for an intent (navigation, music, dialer…), null if none or only a chooser. */
        fun resolve(
            context: Context,
            intent: Intent,
        ): ComponentName? {
            val ri = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
            val pkg = ri.activityInfo?.packageName ?: return null
            if (pkg == "android") return null // resolver / chooser
            return context.packageManager.getLaunchIntentForPackage(pkg)?.component
        }
    }
}
