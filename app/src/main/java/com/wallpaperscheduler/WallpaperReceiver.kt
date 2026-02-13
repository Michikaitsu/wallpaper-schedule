package com.wallpaperscheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WallpaperReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "WallpaperReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Broadcast empfangen: ${intent.action}")
        
        val wallpaperManager = WallpaperSchedulerManager(context)
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Gerät gestartet - Service wird gestartet")
                startServiceIfEnabled(context, wallpaperManager)
            }
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.d(TAG, "Zeit/Zeitzone geändert - Wallpaper wird überprüft")
                if (wallpaperManager.isSchedulerEnabled()) {
                    wallpaperManager.checkAndUpdateWallpaper()
                    startServiceIfEnabled(context, wallpaperManager)
                }
            }
        }
    }
    
    private fun startServiceIfEnabled(context: Context, wallpaperManager: WallpaperSchedulerManager) {
        if (wallpaperManager.isSchedulerEnabled()) {
            // Foreground Service starten
            val serviceIntent = Intent(context, WallpaperService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "WallpaperService gestartet")
            
            // WorkManager als Backup starten
            WallpaperWorker.schedule(context)
            Log.d(TAG, "WallpaperWorker geplant")
            
            // AlarmManager für exakte Zeiten
            WallpaperAlarmManager(context).scheduleNextAlarm()
            Log.d(TAG, "Alarm geplant")
        }
    }
}