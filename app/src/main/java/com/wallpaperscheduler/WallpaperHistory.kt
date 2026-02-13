package com.wallpaperscheduler

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class HistoryEntry(
    val wallpaperPath: String,
    val timestamp: Long,
    val target: String, // "home", "lock", "both"
    val slotLabel: String
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("path", wallpaperPath)
            put("timestamp", timestamp)
            put("target", target)
            put("slot", slotLabel)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): HistoryEntry {
            return HistoryEntry(
                wallpaperPath = json.getString("path"),
                timestamp = json.getLong("timestamp"),
                target = json.optString("target", "both"),
                slotLabel = json.optString("slot", "")
            )
        }
    }
}

class WallpaperHistory(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("wallpaper_history", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_SIZE = 20
    }
    
    fun addEntry(path: String, target: String, slotLabel: String) {
        val history = getHistory().toMutableList()
        
        // Remove duplicate if exists
        history.removeAll { it.wallpaperPath == path }
        
        // Add new entry at the beginning
        history.add(0, HistoryEntry(path, System.currentTimeMillis(), target, slotLabel))
        
        // Limit size
        while (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }
        
        saveHistory(history)
    }
    
    fun getHistory(): List<HistoryEntry> {
        val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val result = mutableListOf<HistoryEntry>()
        
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                result.add(HistoryEntry.fromJson(array.getJSONObject(i)))
            }
        } catch (e: Exception) { }
        
        return result
    }
    
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
    
    private fun saveHistory(history: List<HistoryEntry>) {
        val array = JSONArray()
        history.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }
}
