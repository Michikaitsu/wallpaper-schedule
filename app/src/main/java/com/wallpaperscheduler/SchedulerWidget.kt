package com.wallpaperscheduler

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SchedulerWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.wallpaperscheduler.TOGGLE_SCHEDULER"
        
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, SchedulerWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, SchedulerWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_TOGGLE) {
            val wallpaperManager = WallpaperSchedulerManager(context)
            val newState = !wallpaperManager.isSchedulerEnabled()
            wallpaperManager.setSchedulerEnabled(newState)
            
            val serviceIntent = Intent(context, WallpaperService::class.java)
            if (newState) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                WallpaperWorker.schedule(context)
                WallpaperAlarmManager(context).scheduleNextAlarm()
            } else {
                context.stopService(serviceIntent)
                WallpaperWorker.cancel(context)
                WallpaperAlarmManager(context).cancelAllAlarms()
            }
            
            updateAllWidgets(context)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val wallpaperManager = WallpaperSchedulerManager(context)
        val isEnabled = wallpaperManager.isSchedulerEnabled()
        
        val views = RemoteViews(context.packageName, R.layout.widget_scheduler)
        
        // Status Text & Color
        views.setTextViewText(
            R.id.widgetStatus,
            if (isEnabled) context.getString(R.string.active) else context.getString(R.string.inactive)
        )
        views.setTextColor(
            R.id.widgetStatus,
            if (isEnabled) 0xFF69F0AE.toInt() else 0xFF888888.toInt()
        )
        
        // Toggle Icon
        views.setImageViewResource(
            R.id.widgetToggleIcon,
            if (isEnabled) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        
        // Click Intent für Toggle
        val toggleIntent = Intent(context, SchedulerWidget::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            context, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetToggle, togglePendingIntent)
        
        // Click Intent für App öffnen
        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openPendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
