package com.maisonchahyn.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.RemoteViews
import com.maisonchahyn.app.R

/** Widget horloge en verre pour l'écran d'accueil Samsung (One UI Home) ou l'écran verrouillé. */
class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        for (id in ids) {
            val rv = RemoteViews(context.packageName, R.layout.widget_clock)
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            manager.updateAppWidget(id, rv)
        }
    }
}
