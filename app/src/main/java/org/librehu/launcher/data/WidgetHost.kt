package org.librehu.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context

/** Hosts the dashboard widgets (e.g. the LibreHU FM widget). */
class WidgetHost(
    private val context: Context,
) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, HOST_ID)

    val providers: List<AppWidgetProviderInfo> get() = manager.installedProviders

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)

    fun createView(appWidgetId: Int): AppWidgetHostView? {
        val info = info(appWidgetId) ?: return null
        return host.createView(context.applicationContext, appWidgetId, info)
    }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun delete(appWidgetId: Int) {
        if (appWidgetId >= 0) host.deleteAppWidgetId(appWidgetId)
    }

    /** True when the launcher may bind without asking (privileged install, or the user said "always"). */
    fun bindIfAllowed(
        appWidgetId: Int,
        provider: ComponentName,
    ): Boolean =
        try {
            manager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        } catch (_: Exception) {
            false
        }

    fun fmWidget(): ComponentName? = providers.firstOrNull { it.provider.packageName == LauncherPrefs.FM_PACKAGE }?.provider

    companion object {
        const val HOST_ID = 0x4C48 // "LH"
    }
}
