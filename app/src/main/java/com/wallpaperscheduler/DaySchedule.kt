package com.wallpaperscheduler

data class DaySchedule(
    val dayOfWeek: Int,
    val dayName: String,
    var morningWallpaper: String? = null,
    var eveningWallpaper: String? = null,
    var morningHour: Int = 8,
    var morningMinute: Int = 0,
    var eveningHour: Int = 20,
    var eveningMinute: Int = 0,
    var isEnabled: Boolean = true
)
