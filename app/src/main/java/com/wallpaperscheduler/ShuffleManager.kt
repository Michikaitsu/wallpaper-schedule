package com.wallpaperscheduler

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject

data class ShuffleConfig(
    val dayOfWeek: Int,
    val slotLabel: String,
    val folderUri: String,
    val isEnabled: Boolean = true
)

class ShuffleManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("shuffle_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "ShuffleManager"
    }
    
    fun setShuffleFolder(dayOfWeek: Int, slotLabel: String, folderUri: String?) {
        val key = "shuffle_${dayOfWeek}_${slotLabel}"
        if (folderUri != null) {
            prefs.edit().putString(key, folderUri).apply()
        } else {
            prefs.edit().remove(key).apply()
        }
    }
    
    fun getShuffleFolder(dayOfWeek: Int, slotLabel: String): String? {
        return prefs.getString("shuffle_${dayOfWeek}_${slotLabel}", null)
    }
    
    fun isShuffleEnabled(dayOfWeek: Int, slotLabel: String): Boolean {
        return getShuffleFolder(dayOfWeek, slotLabel) != null
    }
    
    fun getRandomWallpaperFromFolder(folderUri: String): String? {
        try {
            val uri = Uri.parse(folderUri)
            val documentFile = DocumentFile.fromTreeUri(context, uri) ?: return null
            
            val imageFiles = documentFile.listFiles().filter { file ->
                file.isFile && file.type?.startsWith("image/") == true
            }
            
            if (imageFiles.isEmpty()) {
                Log.d(TAG, "No images found in folder")
                return null
            }
            
            val randomFile = imageFiles.random()
            Log.d(TAG, "Selected random image: ${randomFile.name}")
            
            return randomFile.uri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting random wallpaper: ${e.message}")
            return null
        }
    }
    
    fun getAllShuffleConfigs(): List<ShuffleConfig> {
        val configs = mutableListOf<ShuffleConfig>()
        
        for (day in 1..7) {
            val allKeys = prefs.all.keys.filter { it.startsWith("shuffle_${day}_") }
            allKeys.forEach { key ->
                val slotLabel = key.removePrefix("shuffle_${day}_")
                val folderUri = prefs.getString(key, null)
                if (folderUri != null) {
                    configs.add(ShuffleConfig(day, slotLabel, folderUri))
                }
            }
        }
        
        return configs
    }
    
    fun clearAllShuffleConfigs() {
        prefs.edit().clear().apply()
    }
}
