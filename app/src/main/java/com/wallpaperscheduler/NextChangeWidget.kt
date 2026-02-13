package com.wallpaperscheduler

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

class NextChangeWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, NextChangeWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, NextChangeWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_next_change)
        val wallpaperManager = WallpaperSchedulerManager(context)
        
        if (!wallpaperManager.isSchedulerEnabled()) {
            views.setTextViewText(R.id.widgetNextSlot, context.getString(R.string.inactive))
            views.setTextViewText(R.id.widgetCountdown, "—")
        } else {
            val nextChange = getNextChange(context, wallpaperManager)
            if (nextChange != null) {
                views.setTextViewText(R.id.widgetNextSlot, nextChange.slotName)
                views.setTextViewText(R.id.widgetCountdown, nextChange.countdown)
            } else {
                views.setTextViewText(R.id.widgetNextSlot, "—")
                views.setTextViewText(R.id.widgetCountdown, "—")
            }
        }
        
        // Click to open app
        val openIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
        
        appWidgetManager.updateAppWidget(widgetId, views)
    }
    
    private fun getNextChange(context: Context, wallpaperManager: WallpaperSchedulerManager): NextChangeInfo? {
        val calendar = Calendar.getInstance()
        var dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        dayOfWeek = if (dayOfWeek == 1) 7 else dayOfWeek - 1
        
        val currentTimeInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        
        // Check today and next 7 days
        for (dayOffset in 0..7) {
            val checkDay = if (dayOffset == 0) dayOfWeek else ((dayOfWeek + dayOffset - 1) % 7) + 1
            val schedule = wallpaperManager.getDaySchedules().find { it.dayOfWeek == checkDay } ?: continue
            
            if (!schedule.isEnabled) continue
            
            val sortedSlots = schedule.timeSlots.sortedBy { it.getTimeInMinutes() }
            
            for (slot in sortedSlots) {
                val slotTime = slot.getTimeInMinutes()
                
                if (dayOffset == 0 && slotTime <= currentTimeInMinutes) continue
                
                // Found next slot
                val minutesUntil = if (dayOffset == 0) {
                    slotTime - currentTimeInMinutes
                } else {
                    (dayOffset * 24 * 60) + slotTime - currentTimeInMinutes
                }
                
                val hours = minutesUntil / 60
                val mins = minutesUntil % 60
                
                val countdown = when {
                    hours >= 24 -> {
                        val days = hours / 24
                        context.getString(R.string.in_days, days)
                    }
                    hours > 0 -> context.getString(R.string.in_hours_mins, hours, mins)
                    else -> context.getString(R.string.in_mins, mins)
                }
                
                val slotName = getSlotDisplayName(context, slot.label)
                
                return NextChangeInfo(slotName, countdown)
            }
        }
        
        return null
    }
    
    private fun getSlotDisplayName(context: Context, label: String): String {
        return when (label) {
            "dawn" -> context.getString(R.string.slot_dawn)
            "morning" -> context.getString(R.string.slot_morning)
            "noon" -> context.getString(R.string.slot_noon)
            "afternoon" -> context.getString(R.string.slot_afternoon)
            "evening" -> context.getString(R.string.slot_evening)
            "night" -> context.getString(R.string.slot_night)
            else -> label
        }
    }
    
    data class NextChangeInfo(val slotName: String, val countdown: String)
}
