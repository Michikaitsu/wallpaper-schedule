package com.wallpaperscheduler

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WallpaperWorker"
        private const val WORK_NAME = "wallpaper_scheduler_work"
        
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
                15, TimeUnit.MINUTES // Minimum interval für PeriodicWork
            ).build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "WorkManager scheduled")
        }
        
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "WorkManager cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker ausgeführt")
        
        val wallpaperManager = WallpaperSchedulerManager(applicationContext)
        
        if (!wallpaperManager.isSchedulerEnabled()) {
            Log.d(TAG, "Scheduler ist deaktiviert, überspringe")
            return Result.success()
        }
        
        try {
            wallpaperManager.checkAndUpdateWallpaper()
            Log.d(TAG, "Wallpaper erfolgreich überprüft")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Wallpaper-Update: ${e.message}")
            return Result.retry()
        }
        
        return Result.success()
    }
}
