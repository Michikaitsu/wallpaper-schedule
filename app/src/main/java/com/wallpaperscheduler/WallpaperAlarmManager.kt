package com.wallpaperscheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

class WallpaperAlarmManager(private val context: Context) {

    companion object {
        private const val TAG = "WallpaperAlarmManager"
        private const val ACTION_CHANGE_WALLPAPER = "com.wallpaperscheduler.CHANGE_WALLPAPER"
        private const val REQUEST_CODE_BASE = 2000
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleNextAlarm() {
        val wallpaperManager = WallpaperSchedulerManager(context)
        if (!wallpaperManager.isSchedulerEnabled()) {
            Log.d(TAG, "Scheduler deaktiviert, keine Alarme geplant")
            return
        }

        val nextAlarmTime = calculateNextAlarmTime(wallpaperManager)
        if (nextAlarmTime != null) {
            scheduleAlarm(nextAlarmTime)
        }
    }

    private fun calculateNextAlarmTime(wallpaperManager: WallpaperSchedulerManager): Long? {
        val now = Calendar.getInstance()
        val schedules = wallpaperManager.getDaySchedules()
        
        var nearestTime: Long? = null
        
        // Prüfe die nächsten 7 Tage
        for (dayOffset in 0..7) {
            val checkDay = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            
            var dayOfWeek = checkDay.get(Calendar.DAY_OF_WEEK)
            dayOfWeek = if (dayOfWeek == 1) 7 else dayOfWeek - 1
            
            val schedule = schedules.find { it.dayOfWeek == dayOfWeek } ?: continue
            if (!schedule.isEnabled) continue
            
            // Morgen-Zeit prüfen
            val morningTime = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, schedule.morningHour)
                set(Calendar.MINUTE, schedule.morningMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            if (morningTime.timeInMillis > now.timeInMillis) {
                if (nearestTime == null || morningTime.timeInMillis < nearestTime) {
                    nearestTime = morningTime.timeInMillis
                }
            }
            
            // Abend-Zeit prüfen
            val eveningTime = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, schedule.eveningHour)
                set(Calendar.MINUTE, schedule.eveningMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            if (eveningTime.timeInMillis > now.timeInMillis) {
                if (nearestTime == null || eveningTime.timeInMillis < nearestTime) {
                    nearestTime = eveningTime.timeInMillis
                }
            }
            
            // Wenn wir einen Alarm für heute oder morgen gefunden haben, reicht das
            if (nearestTime != null && dayOffset <= 1) break
        }
        
        return nearestTime
    }

    private fun scheduleAlarm(triggerTime: Long) {
        val intent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
            action = ACTION_CHANGE_WALLPAPER
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Bestehende Alarme abbrechen
        alarmManager.cancel(pendingIntent)
        
        // Neuen Alarm setzen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // Fallback wenn keine exakten Alarme erlaubt
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
        
        val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }
        Log.d(TAG, "Alarm geplant für: ${cal.time}")
    }

    fun cancelAllAlarms() {
        val intent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
            action = ACTION_CHANGE_WALLPAPER
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Alle Alarme abgebrochen")
    }
}

class WallpaperAlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "WallpaperAlarmReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm empfangen: ${intent.action}")
        
        val wallpaperManager = WallpaperSchedulerManager(context)
        
        if (wallpaperManager.isSchedulerEnabled()) {
            // Wallpaper wechseln
            wallpaperManager.checkAndUpdateWallpaper()
            Log.d(TAG, "Wallpaper aktualisiert")
            
            // Nächsten Alarm planen
            WallpaperAlarmManager(context).scheduleNextAlarm()
        }
    }
}
