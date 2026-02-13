package com.wallpaperscheduler

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

class QuickSwitchWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_SWITCH = "com.wallpaperscheduler.QUICK_SWITCH"
        const val EXTRA_SLOT = "slot_label"
        
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, QuickSwitchWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, QuickSwitchWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_SWITCH) {
            val slotLabel = intent.getStringExtra(EXTRA_SLOT) ?: return
            switchToSlot(context, slotLabel)
        }
    }
    
    private fun switchToSlot(context: Context, slotLabel: String) {
        val wallpaperManager = WallpaperSchedulerManager(context)
        
        // Get today's schedule
        val calendar = Calendar.getInstance()
        var dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        dayOfWeek = if (dayOfWeek == 1) 7 else dayOfWeek - 1
        
        val schedule = wallpaperManager.getDaySchedules().find { it.dayOfWeek == dayOfWeek } ?: return
        val slot = schedule.timeSlots.find { it.label == slotLabel } ?: return
        
        // Apply wallpaper
        slot.wallpaperHome?.let { path ->
            try {
                val uri = android.net.Uri.parse(path)
                val file = java.io.File(uri.path ?: return@let)
                if (file.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    val wm = android.app.WallpaperManager.getInstance(context)
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        wm.setBitmap(bitmap, null, true, android.app.WallpaperManager.FLAG_SYSTEM)
                    } else {
                        wm.setBitmap(bitmap)
                    }
                }
            } catch (e: Exception) { }
        }
        
        slot.wallpaperLock?.let { path ->
            try {
                val uri = android.net.Uri.parse(path)
                val file = java.io.File(uri.path ?: return@let)
                if (file.exists() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    val wm = android.app.WallpaperManager.getInstance(context)
                    wm.setBitmap(bitmap, null, true, android.app.WallpaperManager.FLAG_LOCK)
                }
            } catch (e: Exception) { }
        }
        
        // Update widgets
        updateAllWidgets(context)
        NextChangeWidget.updateAllWidgets(context)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_switch)
        
        // Morning button
        val morningIntent = Intent(context, QuickSwitchWidget::class.java).apply {
            action = ACTION_SWITCH
            putExtra(EXTRA_SLOT, "morning")
        }
        views.setOnClickPendingIntent(
            R.id.btnMorning,
            PendingIntent.getBroadcast(context, 1, morningIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        
        // Evening button
        val eveningIntent = Intent(context, QuickSwitchWidget::class.java).apply {
            action = ACTION_SWITCH
            putExtra(EXTRA_SLOT, "evening")
        }
        views.setOnClickPendingIntent(
            R.id.btnEvening,
            PendingIntent.getBroadcast(context, 2, eveningIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        
        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
