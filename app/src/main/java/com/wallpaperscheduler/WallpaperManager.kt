package com.wallpaperscheduler

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Calendar

enum class WallpaperTarget {
    HOME, LOCK, BOTH
}

class WallpaperSchedulerManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
    private val wallpaperManager: WallpaperManager = WallpaperManager.getInstance(context)

    private val history = WallpaperHistory(context)
    private val shuffleManager = ShuffleManager(context)

    companion object {
        private const val TAG = "WallpaperManager"
        private const val KEY_ENABLED = "scheduler_enabled"
        private const val KEY_LAST_HOME = "last_home_wallpaper"
        private const val KEY_LAST_LOCK = "last_lock_wallpaper"
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
            val schedule = DaySchedule(
                dayOfWeek = day,
                dayName = dayNames[day - 1],
                isEnabled = prefs.getBoolean("day_${day}_enabled", true)
            )
            
            // Load time slots
            schedule.timeSlots.clear()
            val slotCount = prefs.getInt("day_${day}_slot_count", 0)
            
            if (slotCount > 0) {
                for (i in 0 until slotCount) {
                    val label = prefs.getString("day_${day}_slot_${i}_label", if (i == 0) "morning" else "evening") ?: "slot_$i"
                    val hour = prefs.getInt("day_${day}_slot_${i}_hour", if (i == 0) 8 else 20)
                    val minute = prefs.getInt("day_${day}_slot_${i}_minute", 0)
                    val wallpaperHome = prefs.getString("day_${day}_slot_${i}_home", null)
                    val wallpaperLock = prefs.getString("day_${day}_slot_${i}_lock", null)
                    
                    schedule.timeSlots.add(TimeSlot(hour, minute, label, wallpaperHome, wallpaperLock))
                }
            } else {
                // Default slots oder Legacy-Daten laden
                schedule.timeSlots.add(TimeSlot(
                    hour = prefs.getInt("day_${day}_morning_hour", 8),
                    minute = prefs.getInt("day_${day}_morning_minute", 0),
                    label = "morning",
                    wallpaperHome = prefs.getString("day_${day}_morning", null)
                ))
                schedule.timeSlots.add(TimeSlot(
                    hour = prefs.getInt("day_${day}_evening_hour", 20),
                    minute = prefs.getInt("day_${day}_evening_minute", 0),
                    label = "evening",
                    wallpaperHome = prefs.getString("day_${day}_evening", null)
                ))
            }
            
            Log.d(TAG, "Tag $day geladen: ${schedule.timeSlots.size} Slots, Morning=${schedule.timeSlots.find { it.label == "morning" }?.wallpaperHome}")
            
            schedule
        }
    }

    fun saveDaySchedule(schedule: DaySchedule) {
        prefs.edit().apply {
            putBoolean("day_${schedule.dayOfWeek}_enabled", schedule.isEnabled)
            putInt("day_${schedule.dayOfWeek}_slot_count", schedule.timeSlots.size)
            
            schedule.timeSlots.forEachIndexed { index, slot ->
                putString("day_${schedule.dayOfWeek}_slot_${index}_label", slot.label)
                putInt("day_${schedule.dayOfWeek}_slot_${index}_hour", slot.hour)
                putInt("day_${schedule.dayOfWeek}_slot_${index}_minute", slot.minute)
                putString("day_${schedule.dayOfWeek}_slot_${index}_home", slot.wallpaperHome)
                putString("day_${schedule.dayOfWeek}_slot_${index}_lock", slot.wallpaperLock)
            }
            
            // Kompatibilität mit altem Code
            putString("day_${schedule.dayOfWeek}_morning", schedule.morningWallpaper)
            putString("day_${schedule.dayOfWeek}_evening", schedule.eveningWallpaper)
            putInt("day_${schedule.dayOfWeek}_morning_hour", schedule.morningHour)
            putInt("day_${schedule.dayOfWeek}_morning_minute", schedule.morningMinute)
            putInt("day_${schedule.dayOfWeek}_evening_hour", schedule.eveningHour)
            putInt("day_${schedule.dayOfWeek}_evening_minute", schedule.eveningMinute)
            
            apply()
        }
    }

    fun copyWallpaperToStorage(uri: Uri, day: Int, slotLabel: String, target: WallpaperTarget): String? {
        return try {
            val targetSuffix = when (target) {
                WallpaperTarget.HOME -> "home"
                WallpaperTarget.LOCK -> "lock"
                WallpaperTarget.BOTH -> "both"
            }
            val fileName = "wallpaper_day${day}_${slotLabel}_${targetSuffix}.jpg"
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
    
    // Legacy method for compatibility
    fun copyWallpaperToStorage(uri: Uri, day: Int, isMorning: Boolean): String? {
        return copyWallpaperToStorage(uri, day, if (isMorning) "morning" else "evening", WallpaperTarget.HOME)
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
        if (!isSchedulerEnabled()) {
            Log.d(TAG, "Scheduler ist deaktiviert")
            return
        }

        val calendar = Calendar.getInstance()
        var dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        dayOfWeek = if (dayOfWeek == 1) 7 else dayOfWeek - 1
        
        val currentTimeInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        
        Log.d(TAG, "Prüfe Wallpaper: Tag=$dayOfWeek, Zeit=$currentTimeInMinutes min")
        
        val schedule = getDaySchedules().find { it.dayOfWeek == dayOfWeek } ?: return

        if (!schedule.isEnabled) {
            Log.d(TAG, "Tag $dayOfWeek ist deaktiviert")
            return
        }

        // Finde den aktiven Zeitslot
        val sortedSlots = schedule.timeSlots.sortedBy { it.getTimeInMinutes() }
        var activeSlot: TimeSlot? = null
        
        Log.d(TAG, "Verfügbare Slots: ${sortedSlots.map { "${it.label}@${it.getFormattedTime()}" }}")
        
        for (slot in sortedSlots.reversed()) {
            if (currentTimeInMinutes >= slot.getTimeInMinutes()) {
                activeSlot = slot
                Log.d(TAG, "Aktiver Slot gefunden: ${slot.label} @ ${slot.getFormattedTime()}")
                break
            }
        }
        
        // Wenn kein Slot gefunden (vor dem ersten Slot des Tages), nimm den letzten vom Vortag
        if (activeSlot == null && sortedSlots.isNotEmpty()) {
            activeSlot = sortedSlots.last()
            Log.d(TAG, "Kein Slot für aktuelle Zeit, nutze letzten: ${activeSlot.label}")
        }

        activeSlot?.let { slot ->
            Log.d(TAG, "Setze Wallpaper von Slot: ${slot.label}, Home=${slot.wallpaperHome}, Lock=${slot.wallpaperLock}")
            
            // Check for shuffle mode
            val shuffleFolder = shuffleManager.getShuffleFolder(dayOfWeek, slot.label)
            
            if (shuffleFolder != null) {
                // Shuffle mode - get random wallpaper from folder
                val randomWallpaper = shuffleManager.getRandomWallpaperFromFolder(shuffleFolder)
                randomWallpaper?.let { wallpaper ->
                    setWallpaperFromUri(wallpaper, WallpaperTarget.BOTH)
                    history.addEntry(wallpaper, "both", slot.label)
                    Log.d(TAG, "Shuffle wallpaper applied: $wallpaper")
                }
            } else {
                // Normal mode - Home Wallpaper
                slot.wallpaperHome?.let { path ->
                    setWallpaper(path, WallpaperTarget.HOME)
                    prefs.edit().putString(KEY_LAST_HOME, path).apply()
                    history.addEntry(path, "home", slot.label)
                }
                
                // Lock Wallpaper
                slot.wallpaperLock?.let { path ->
                    setWallpaper(path, WallpaperTarget.LOCK)
                    prefs.edit().putString(KEY_LAST_LOCK, path).apply()
                    history.addEntry(path, "lock", slot.label)
                }
            }
        }
        
        if (activeSlot == null) {
            Log.d(TAG, "Kein aktiver Slot gefunden")
        }
    }

    private fun setWallpaper(uriString: String, target: WallpaperTarget = WallpaperTarget.BOTH) {
        try {
            val uri = Uri.parse(uriString)
            val file = File(uri.path ?: return)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    when (target) {
                        WallpaperTarget.HOME -> {
                            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        }
                        WallpaperTarget.LOCK -> {
                            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        }
                        WallpaperTarget.BOTH -> {
                            wallpaperManager.setBitmap(bitmap)
                        }
                    }
                } else {
                    wallpaperManager.setBitmap(bitmap)
                }
                
                Log.d(TAG, "Wallpaper set ($target): $uriString")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper: ${e.message}")
        }
    }
    
    private fun setWallpaperFromUri(uriString: String, target: WallpaperTarget) {
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    when (target) {
                        WallpaperTarget.HOME -> wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        WallpaperTarget.LOCK -> wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        WallpaperTarget.BOTH -> wallpaperManager.setBitmap(bitmap)
                    }
                } else {
                    wallpaperManager.setBitmap(bitmap)
                }
                
                Log.d(TAG, "Wallpaper set from URI ($target): $uriString")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper from URI: ${e.message}")
        }
    }
    
    fun getShuffleManager(): ShuffleManager = shuffleManager
    
    // Backup & Restore
    fun exportSettings(): String {
        val json = StringBuilder()
        json.append("{")
        json.append("\"enabled\":${isSchedulerEnabled()},")
        json.append("\"schedules\":[")
        
        getDaySchedules().forEachIndexed { index, schedule ->
            if (index > 0) json.append(",")
            json.append("{")
            json.append("\"day\":${schedule.dayOfWeek},")
            json.append("\"enabled\":${schedule.isEnabled},")
            json.append("\"slots\":[")
            
            schedule.timeSlots.forEachIndexed { slotIndex, slot ->
                if (slotIndex > 0) json.append(",")
                json.append("{")
                json.append("\"label\":\"${slot.label}\",")
                json.append("\"hour\":${slot.hour},")
                json.append("\"minute\":${slot.minute}")
                json.append("}")
            }
            
            json.append("]}")
        }
        
        json.append("]}")
        return json.toString()
    }
    
    fun importSettings(json: String) {
        try {
            // Simple JSON parsing without external library
            val enabledMatch = Regex("\"enabled\":(true|false)").find(json)
            if (enabledMatch != null) {
                setSchedulerEnabled(enabledMatch.groupValues[1] == "true")
            }
            
            // Parse schedules
            val schedulesMatch = Regex("\"schedules\":\\[(.+)\\]\\}$").find(json)
            if (schedulesMatch != null) {
                val schedulesJson = schedulesMatch.groupValues[1]
                
                // Parse each day
                val dayPattern = Regex("\\{\"day\":(\\d+),\"enabled\":(true|false),\"slots\":\\[([^\\]]+)\\]\\}")
                dayPattern.findAll(schedulesJson).forEach { dayMatch ->
                    val dayOfWeek = dayMatch.groupValues[1].toInt()
                    val dayEnabled = dayMatch.groupValues[2] == "true"
                    val slotsJson = dayMatch.groupValues[3]
                    
                    prefs.edit().apply {
                        putBoolean("day_${dayOfWeek}_enabled", dayEnabled)
                        
                        // Parse slots
                        val slotPattern = Regex("\\{\"label\":\"([^\"]+)\",\"hour\":(\\d+),\"minute\":(\\d+)\\}")
                        var slotIndex = 0
                        slotPattern.findAll(slotsJson).forEach { slotMatch ->
                            val label = slotMatch.groupValues[1]
                            val hour = slotMatch.groupValues[2].toInt()
                            val minute = slotMatch.groupValues[3].toInt()
                            
                            putString("day_${dayOfWeek}_slot_${slotIndex}_label", label)
                            putInt("day_${dayOfWeek}_slot_${slotIndex}_hour", hour)
                            putInt("day_${dayOfWeek}_slot_${slotIndex}_minute", minute)
                            
                            // Legacy compatibility
                            if (label == "morning") {
                                putInt("day_${dayOfWeek}_morning_hour", hour)
                                putInt("day_${dayOfWeek}_morning_minute", minute)
                            } else if (label == "evening") {
                                putInt("day_${dayOfWeek}_evening_hour", hour)
                                putInt("day_${dayOfWeek}_evening_minute", minute)
                            }
                            
                            slotIndex++
                        }
                        putInt("day_${dayOfWeek}_slot_count", slotIndex)
                        apply()
                    }
                }
            }
            
            Log.d(TAG, "Settings imported successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing settings: ${e.message}")
            throw e
        }
    }
}
