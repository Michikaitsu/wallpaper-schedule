package com.wallpaperscheduler

data class DaySchedule(
    val dayOfWeek: Int,
    val dayName: String,
    var timeSlots: MutableList<TimeSlot> = mutableListOf(
        TimeSlot(hour = 8, minute = 0, label = "morning"),
        TimeSlot(hour = 20, minute = 0, label = "evening")
    ),
    var isEnabled: Boolean = true
) {
    // Kompatibilität mit altem Code
    var morningWallpaper: String?
        get() = timeSlots.find { it.label == "morning" }?.wallpaperHome
        set(value) { timeSlots.find { it.label == "morning" }?.wallpaperHome = value }
    
    var eveningWallpaper: String?
        get() = timeSlots.find { it.label == "evening" }?.wallpaperHome
        set(value) { timeSlots.find { it.label == "evening" }?.wallpaperHome = value }
    
    var morningHour: Int
        get() = timeSlots.find { it.label == "morning" }?.hour ?: 8
        set(value) { timeSlots.find { it.label == "morning" }?.hour = value }
    
    var morningMinute: Int
        get() = timeSlots.find { it.label == "morning" }?.minute ?: 0
        set(value) { timeSlots.find { it.label == "morning" }?.minute = value }
    
    var eveningHour: Int
        get() = timeSlots.find { it.label == "evening" }?.hour ?: 20
        set(value) { timeSlots.find { it.label == "evening" }?.hour = value }
    
    var eveningMinute: Int
        get() = timeSlots.find { it.label == "evening" }?.minute ?: 0
        set(value) { timeSlots.find { it.label == "evening" }?.minute = value }
}

data class TimeSlot(
    var hour: Int,
    var minute: Int,
    var label: String,
    var wallpaperHome: String? = null,
    var wallpaperLock: String? = null
) {
    fun getTimeInMinutes(): Int = hour * 60 + minute
    
    fun getFormattedTime(): String = String.format("%02d:%02d", hour, minute)
}
