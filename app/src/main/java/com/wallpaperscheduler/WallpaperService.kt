package com.wallpaperscheduler

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class WallpaperService : Service() {
    
    private lateinit var wallpaperManager: WallpaperSchedulerManager
    private lateinit var handler: Handler
    private lateinit var checkRunnable: Runnable
    
    companion object {
        private const val TAG = "WallpaperService"
        private const val CHECK_INTERVAL = 60000L // 1 Minute
    }
    
    override fun onCreate() {
        super.onCreate()
        wallpaperManager = WallpaperSchedulerManager(this)
        handler = Handler(Looper.getMainLooper())
        
        checkRunnable = object : Runnable {
            override fun run() {
                wallpaperManager.checkAndUpdateWallpaper()
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        }
        
        Log.d(TAG, "WallpaperService erstellt")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WallpaperService gestartet")
        
        // Sofortige Überprüfung
        wallpaperManager.checkAndUpdateWallpaper()
        
        // Regelmäßige Überprüfung starten
        handler.post(checkRunnable)
        
        return START_STICKY // Service wird automatisch neu gestartet
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        Log.d(TAG, "WallpaperService gestoppt")
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}