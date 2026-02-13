package com.wallpaperscheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class WallpaperService : Service() {
    
    private lateinit var wallpaperManager: WallpaperSchedulerManager
    private lateinit var handler: Handler
    private lateinit var checkRunnable: Runnable
    
    companion object {
        private const val TAG = "WallpaperService"
        private const val CHECK_INTERVAL = 60000L // 1 Minute
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "wallpaper_scheduler_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        wallpaperManager = WallpaperSchedulerManager(this)
        handler = Handler(Looper.getMainLooper())
        
        checkRunnable = object : Runnable {
            override fun run() {
                Log.d(TAG, "Überprüfe Wallpaper...")
                wallpaperManager.checkAndUpdateWallpaper()
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        }
        
        Log.d(TAG, "WallpaperService erstellt")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WallpaperService gestartet")
        
        // Als Foreground Service starten
        startForegroundService()
        
        // Sofortige Überprüfung
        wallpaperManager.checkAndUpdateWallpaper()
        
        // Regelmäßige Überprüfung starten
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
        
        return START_STICKY
    }
    
    private fun startForegroundService() {
        createNotificationChannel()
        
        // Prüfe ob Notification angezeigt werden soll
        val settingsPrefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val showNotification = settingsPrefs.getBoolean("show_notification", true)
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
        
        if (showNotification) {
            builder.setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_service_running))
                .setPriority(NotificationCompat.PRIORITY_LOW)
        } else {
            // Minimale unsichtbare Notification
            builder.setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        }
        
        startForeground(NOTIFICATION_ID, builder.build())
        Log.d(TAG, "Foreground Service gestartet (Notification sichtbar: $showNotification)")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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