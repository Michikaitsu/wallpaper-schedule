package com.wallpaperscheduler

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FavoriteWallpaper(
    val id: String,
    val path: String,
    val addedAt: Long,
    val name: String? = null
)

class FavoritesManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "FavoritesManager"
        private const val KEY_FAVORITES = "favorites_list"
        private const val MAX_FAVORITES = 50
    }
    
    fun getFavorites(): List<FavoriteWallpaper> {
        val json = prefs.getString(KEY_FAVORITES, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val path = obj.getString("path")
                // Only return if file exists
                val file = File(Uri.parse(path).path ?: "")
                if (file.exists()) {
                    FavoriteWallpaper(
                        id = obj.getString("id"),
                        path = path,
                        addedAt = obj.getLong("addedAt"),
                        name = obj.optString("name", null)
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading favorites: ${e.message}")
            emptyList()
        }
    }
    
    fun addFavorite(wallpaperPath: String, name: String? = null): Boolean {
        val favorites = getFavorites().toMutableList()
        
        // Check if already exists
        if (favorites.any { it.path == wallpaperPath }) {
            return false
        }
        
        // Check max limit
        if (favorites.size >= MAX_FAVORITES) {
            // Remove oldest
            favorites.sortBy { it.addedAt }
            favorites.removeAt(0)
        }
        
        val newFavorite = FavoriteWallpaper(
            id = System.currentTimeMillis().toString(),
            path = wallpaperPath,
            addedAt = System.currentTimeMillis(),
            name = name
        )
        
        favorites.add(newFavorite)
        saveFavorites(favorites)
        return true
    }
    
    fun removeFavorite(wallpaperPath: String): Boolean {
        val favorites = getFavorites().toMutableList()
        val removed = favorites.removeAll { it.path == wallpaperPath }
        if (removed) {
            saveFavorites(favorites)
        }
        return removed
    }
    
    fun isFavorite(wallpaperPath: String): Boolean {
        return getFavorites().any { it.path == wallpaperPath }
    }
    
    fun toggleFavorite(wallpaperPath: String): Boolean {
        return if (isFavorite(wallpaperPath)) {
            removeFavorite(wallpaperPath)
            false
        } else {
            addFavorite(wallpaperPath)
            true
        }
    }
    
    private fun saveFavorites(favorites: List<FavoriteWallpaper>) {
        val array = JSONArray()
        favorites.forEach { fav ->
            val obj = JSONObject().apply {
                put("id", fav.id)
                put("path", fav.path)
                put("addedAt", fav.addedAt)
                fav.name?.let { put("name", it) }
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }
}
