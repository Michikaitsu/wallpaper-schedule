package com.wallpaperscheduler

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.Calendar

class WallpaperSchedulerManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
    private val wallpaperManager: WallpaperManager = WallpaperManager.getInstance(context)

    companion object {
        private const val TAG = "WallpaperManager"
        private const val KEY_ENABLED = "scheduler_enabled"
    }

    private fun getDayNames(): List<String> {
        return listOf(
            context.getString(R.string.monday),
            context.getString(R.string.tuesday),
            context.getString(R.string.wednesday),
            context.getString(R.string.thursday),
            context.getString(R.string.friday),
            context.getString(R.string.saturday),
            context.getString(R.string.sunday)
        )
    }

    fun isSchedulerEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setSchedulerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getDaySchedules(): List<DaySchedule> {
        val dayNames = getDayNames()
        return (1..7).map { day ->
            DaySchedule(
                dayOfWeek = day,
                dayName = dayNames[day - 1],
                morningWallpaper = prefs.getString("day_${day}_morning", null),
                eveningWallpaper = prefs.getString("day_${day}_evening", null),
                morningHour = prefs.getInt("day_${day}_morning_hour", 8),
                morningMinute = prefs.getInt("day_${day}_morning_minute", 0),
                eveningHour = prefs.getInt("day_${day}_evening_hour", 20),
                eveningMinute = prefs.getInt("day_${day}_evening_minute", 0),
                isEnabled = prefs.getBoolean("day_${day}_enabled", true)
            )
        }
    }

    fun saveDaySchedule(schedule: DaySchedule) {
        prefs.edit().apply {
            putString("day_${schedule.dayOfWeek}_morning", schedule.morningWallpaper)
            putString("day_${schedule.dayOfWeek}_evening", schedule.eveningWallpaper)
            putInt("day_${schedule.dayOfWeek}_morning_hour", schedule.morningHour)
            putInt("day_${schedule.dayOfWeek}_morning_minute", schedule.morningMinute)
            putInt("day_${schedule.dayOfWeek}_evening_hour", schedule.eveningHour)
            putInt("day_${schedule.dayOfWeek}_evening_minute", schedule.eveningMinute)
            putBoolean("day_${schedule.dayOfWeek}_enabled", schedule.isEnabled)
            apply()
        }
    }

    fun copyWallpaperToStorage(uri: Uri, day: Int, isMorning: Boolean): String? {
        return try {
            val fileName = "wallpaper_day${day}_${if (isMorning) "morning" else "evening"}.jpg"
            val outputFile = File(context.filesDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(outputFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error copying: ${e.message}")
            null
        }
    }

    fun applyWallpaperForDays(days: List<Int>, isMorning: Boolean, wallpaperPath: String) {
        days.forEach { day ->
            val schedule = getDaySchedules().find { it.dayOfWeek == day } ?: return@forEach
            if (isMorning) {
                schedule.morningWallpaper = wallpaperPath
            } else {
                schedule.eveningWallpaper = wallpaperPath
            }
            saveDaySchedule(schedule)
        }
    }

    fun checkAndUpdateWallpaper() {
        if (!isSchedulerEnabled()) return

        val calendar = Calendar.getInstance()
        var dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        dayOfWeek = if (dayOfWeek == 1) 7 else dayOfWeek - 1
        
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        
        val schedule = getDaySchedules().find { it.dayOfWeek == dayOfWeek } ?: return

        if (!schedule.isEnabled) return

        val morningTimeInMinutes = schedule.morningHour * 60 + schedule.morningMinute
        val eveningTimeInMinutes = schedule.eveningHour * 60 + schedule.eveningMinute

        val wallpaperPath = if (currentTimeInMinutes >= morningTimeInMinutes && currentTimeInMinutes < eveningTimeInMinutes) {
            schedule.morningWallpaper
        } else {
            schedule.eveningWallpaper
        }

        wallpaperPath?.let { setWallpaper(it) }
    }

    private fun setWallpaper(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            val file = File(uri.path ?: return)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                wallpaperManager.setBitmap(bitmap)
                Log.d(TAG, "Wallpaper set: $uriString")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper: ${e.message}")
        }
    }
}
