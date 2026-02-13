package com.wallpaperscheduler

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FavoritesActivity : AppCompatActivity() {
    
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var adapter: FavoritesAdapter
    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        
        favoritesManager = FavoritesManager(this)
        
        emptyView = findViewById(R.id.emptyView)
        recyclerView = findViewById(R.id.favoritesRecyclerView)
        
        setupRecyclerView()
        applyAmoledColors()
    }
    
    private fun applyTheme() {
        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val theme = prefs.getString(SettingsActivity.PREF_THEME, SettingsActivity.THEME_DARK) ?: SettingsActivity.THEME_DARK
        when (theme) {
            SettingsActivity.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            SettingsActivity.THEME_DARK, SettingsActivity.THEME_AMOLED -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
    
    private fun applyAmoledColors() {
        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val theme = prefs.getString(SettingsActivity.PREF_THEME, SettingsActivity.THEME_DARK) ?: SettingsActivity.THEME_DARK
        
        if (theme == SettingsActivity.THEME_AMOLED) {
            val amoledBg = ContextCompat.getColor(this, R.color.amoled_background)
            window.decorView.setBackgroundColor(amoledBg)
            window.statusBarColor = amoledBg
            window.navigationBarColor = amoledBg
            findViewById<View>(R.id.rootLayout)?.setBackgroundColor(amoledBg)
        }
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        
        adapter = FavoritesAdapter(
            favorites = favoritesManager.getFavorites(),
            onItemClick = { favorite ->
                // Return selected wallpaper
                setResult(Activity.RESULT_OK, Intent().apply {
                    putExtra("selected_path", favorite.path)
                })
                finish()
            },
            onItemLongClick = { favorite ->
                showFavoriteOptions(favorite)
            }
        )
        
        recyclerView.adapter = adapter
        updateEmptyState()
    }
    
    private fun showFavoriteOptions(favorite: FavoriteWallpaper) {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val addedDate = dateFormat.format(Date(favorite.addedAt))
        
        MaterialAlertDialogBuilder(this)
            .setTitle(favorite.name ?: getString(R.string.favorite))
            .setMessage(getString(R.string.added_on, addedDate))
            .setPositiveButton(getString(R.string.use_wallpaper)) { _, _ ->
                setResult(Activity.RESULT_OK, Intent().apply {
                    putExtra("selected_path", favorite.path)
                })
                finish()
            }
            .setNegativeButton(getString(R.string.remove_favorite)) { _, _ ->
                favoritesManager.removeFavorite(favorite.path)
                adapter.updateFavorites(favoritesManager.getFavorites())
                updateEmptyState()
                Toast.makeText(this, getString(R.string.removed_from_favorites), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    override fun onResume() {
        super.onResume()
        adapter.updateFavorites(favoritesManager.getFavorites())
        updateEmptyState()
    }
}

class FavoritesAdapter(
    private var favorites: List<FavoriteWallpaper>,
    private val onItemClick: (FavoriteWallpaper) -> Unit,
    private val onItemLongClick: (FavoriteWallpaper) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.wallpaperImage)
        val favoriteIcon: ImageView = view.findViewById(R.id.favoriteIcon)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_wallpaper, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val favorite = favorites[position]
        
        try {
            val uri = Uri.parse(favorite.path)
            val file = File(uri.path ?: "")
            if (file.exists()) {
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                holder.imageView.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            holder.imageView.setImageResource(R.drawable.ic_launcher_foreground)
        }
        
        holder.favoriteIcon.visibility = View.VISIBLE
        
        holder.itemView.setOnClickListener { onItemClick(favorite) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(favorite)
            true
        }
    }
    
    override fun getItemCount() = favorites.size
    
    fun updateFavorites(newFavorites: List<FavoriteWallpaper>) {
        favorites = newFavorites
        notifyDataSetChanged()
    }
}
