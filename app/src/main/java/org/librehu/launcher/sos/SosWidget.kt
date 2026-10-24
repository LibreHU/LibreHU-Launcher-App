package org.librehu.launcher.sos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.librehu.launcher.R
import org.librehu.launcher.data.SegmentViews
import org.librehu.launcher.data.WidgetLook

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
        val views = SegmentViews(context, R.layout.widget_sos, WidgetLook(context).segment).apply { setOnClickPendingIntent(R.id.sos_root, open) }
        manager.updateAppWidget(appWidgetIds, views)
    }

    companion object {
        /** Drawn again (the theme's font changed). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, SosWidget::class.java))
            if (ids.isNotEmpty()) SosWidget().onUpdate(context, manager, ids)
        }
    }
}
