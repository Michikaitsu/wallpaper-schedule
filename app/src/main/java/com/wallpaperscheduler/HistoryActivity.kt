package com.wallpaperscheduler

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var history: WallpaperHistory
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        applyAmoledColors()

        history = WallpaperHistory(this)
        recyclerView = findViewById(R.id.historyRecyclerView)
        emptyState = findViewById(R.id.emptyState)

        recyclerView.layoutManager = LinearLayoutManager(this)
        loadHistory()
    }

    private fun loadHistory() {
        val entries = history.getHistory()
        
        if (entries.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = HistoryAdapter(this, entries) { entry ->
                showReapplyDialog(entry)
            }
        }
    }

    private fun showReapplyDialog(entry: HistoryEntry) {
        val options = arrayOf(
            getString(R.string.home_screen),
            getString(R.string.lock_screen),
            getString(R.string.both_screens)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.reapply_wallpaper))
            .setItems(options) { _, which ->
                val target = when (which) {
                    0 -> WallpaperTarget.HOME
                    1 -> WallpaperTarget.LOCK
                    else -> WallpaperTarget.BOTH
                }
                applyWallpaper(entry.wallpaperPath, target)
            }
            .show()
    }

    private fun applyWallpaper(path: String, target: WallpaperTarget) {
        try {
            val uri = Uri.parse(path)
            val file = File(uri.path ?: return)
            if (!file.exists()) {
                Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
                return
            }

            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val wm = WallpaperManager.getInstance(this)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                when (target) {
                    WallpaperTarget.HOME -> wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    WallpaperTarget.LOCK -> wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    WallpaperTarget.BOTH -> wm.setBitmap(bitmap)
                }
            } else {
                wm.setBitmap(bitmap)
            }

            Toast.makeText(this, getString(R.string.wallpaper_applied), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_message, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        when (prefs.getString(SettingsActivity.PREF_THEME, SettingsActivity.THEME_DARK)) {
            SettingsActivity.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun applyAmoledColors() {
        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        if (prefs.getString(SettingsActivity.PREF_THEME, SettingsActivity.THEME_DARK) == SettingsActivity.THEME_AMOLED) {
            val amoledBg = ContextCompat.getColor(this, R.color.amoled_background)
            window.decorView.setBackgroundColor(amoledBg)
            window.statusBarColor = amoledBg
            window.navigationBarColor = amoledBg
            findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.rootLayout)?.setBackgroundColor(amoledBg)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val langCode = prefs.getString(SettingsActivity.PREF_LANGUAGE, "system") ?: "system"
        
        val context = if (langCode != "system") {
            val locale = Locale(langCode)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        
        super.attachBaseContext(context)
    }
}

class HistoryAdapter(
    private val context: Context,
    private val entries: List<HistoryEntry>,
    private val onReapply: (HistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val isAmoled: Boolean

    init {
        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        isAmoled = prefs.getString(SettingsActivity.PREF_THEME, SettingsActivity.THEME_DARK) == SettingsActivity.THEME_AMOLED
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val thumbnail: ImageView = view.findViewById(R.id.historyThumbnail)
        val time: TextView = view.findViewById(R.id.historyTime)
        val target: TextView = view.findViewById(R.id.historyTarget)
        val reapplyButton: ImageView = view.findViewById(R.id.reapplyButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]

        if (isAmoled) {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.amoled_card))
        }

        holder.time.text = entry.getFormattedTime()
        
        val targetText = when (entry.target) {
            "home" -> context.getString(R.string.home_screen)
            "lock" -> context.getString(R.string.lock_screen)
            else -> context.getString(R.string.both_screens)
        }
        holder.target.text = targetText

        // Load thumbnail
        try {
            val uri = Uri.parse(entry.wallpaperPath)
            val file = File(uri.path ?: "")
            if (file.exists()) {
                val options = BitmapFactory.Options().apply { inSampleSize = 8 }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                holder.thumbnail.setImageBitmap(bitmap)
            }
        } catch (e: Exception) { }

        holder.reapplyButton.setOnClickListener { onReapply(entry) }
        holder.card.setOnClickListener { onReapply(entry) }
    }

    override fun getItemCount() = entries.size
}
