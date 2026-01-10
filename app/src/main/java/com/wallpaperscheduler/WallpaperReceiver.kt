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
            val serviceIntent = Intent(context, WallpaperService::class.java)
            context.startService(serviceIntent)
            Log.d(TAG, "WallpaperService gestartet")
        }
    }
}