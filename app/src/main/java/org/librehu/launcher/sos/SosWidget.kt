package org.librehu.launcher.sos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.librehu.launcher.R

/** SOS button widget (launcher slots or any home screen): opens the SOS screen and its countdown. */
class SosWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, SosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val views = RemoteViews(context.packageName, R.layout.widget_sos).apply { setOnClickPendingIntent(R.id.sos_root, open) }
        manager.updateAppWidget(appWidgetIds, views)
    }
}
