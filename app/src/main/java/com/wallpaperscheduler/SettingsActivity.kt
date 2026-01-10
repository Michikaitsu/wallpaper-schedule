package com.wallpaperscheduler

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var currentThemeText: TextView
    private lateinit var currentLanguageText: TextView

    companion object {
        const val GITHUB_URL = "https://github.com/KaiTooast/wallpaper-schedule"
        const val HELP_URL = "https://kaitooast.github.io/wallpaper-schedule"
        const val PREF_THEME = "theme"
        const val PREF_LANGUAGE = "language"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_AMOLED = "amoled"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        applyTheme()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        applyThemeColors()
        setupToolbar()
        setupViews()
        setupListeners()
    }

    private fun applyTheme() {
        val theme = prefs.getString(PREF_THEME, THEME_DARK) ?: THEME_DARK
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK, THEME_AMOLED -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun applyThemeColors() {
        val theme = prefs.getString(PREF_THEME, THEME_DARK) ?: THEME_DARK
        
        if (theme == THEME_AMOLED) {
            val amoledBg = ContextCompat.getColor(this, R.color.amoled_background)
            window.decorView.setBackgroundColor(amoledBg)
            window.statusBarColor = amoledBg
            window.navigationBarColor = amoledBg
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViews() {
        currentThemeText = findViewById(R.id.currentTheme)
        currentLanguageText = findViewById(R.id.currentLanguage)

        updateThemeText()
        updateLanguageText()

        // Apply AMOLED colors to cards if needed
        applyAmoledToCards()

        // Version
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            findViewById<TextView>(R.id.versionText).text = "Version $versionName"
        } catch (e: Exception) {
            findViewById<TextView>(R.id.versionText).text = "Version 1.0"
        }
    }

    private fun applyAmoledToCards() {
        val theme = prefs.getString(PREF_THEME, THEME_DARK) ?: THEME_DARK
        if (theme == THEME_AMOLED) {
            val amoledBg = ContextCompat.getColor(this, R.color.amoled_background)
            val amoledCard = ContextCompat.getColor(this, R.color.amoled_card)
            
            findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.rootLayout)?.setBackgroundColor(amoledBg)
            
            // Find all cards and set AMOLED background
            listOf(R.id.appearanceCard, R.id.linksCard, R.id.aboutCard).forEach { cardId ->
                findViewById<MaterialCardView>(cardId)?.setCardBackgroundColor(amoledCard)
            }
        }
    }

    private fun setupListeners() {
        findViewById<LinearLayout>(R.id.themeOption).setOnClickListener {
            showThemeDialog()
        }

        findViewById<LinearLayout>(R.id.languageOption).setOnClickListener {
            showLanguageDialog()
        }

        findViewById<LinearLayout>(R.id.helpOption).setOnClickListener {
            openUrl(HELP_URL)
        }

        findViewById<LinearLayout>(R.id.githubOption).setOnClickListener {
            openUrl(GITHUB_URL)
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_amoled)
        )
        val themeCodes = arrayOf(THEME_LIGHT, THEME_DARK, THEME_AMOLED)
        val currentTheme = prefs.getString(PREF_THEME, THEME_DARK) ?: THEME_DARK
        val currentIndex = themeCodes.indexOf(currentTheme).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.theme))
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                val selectedTheme = themeCodes[which]
                prefs.edit().putString(PREF_THEME, selectedTheme).apply()
                dialog.dismiss()
                
                if (selectedTheme != currentTheme) {
                    // Apply theme and recreate
                    applyTheme()
                    recreate()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            getString(R.string.lang_system),
            getString(R.string.lang_english),
            getString(R.string.lang_german)
        )
        val languageCodes = arrayOf("system", "en", "de")
        val currentLang = prefs.getString(PREF_LANGUAGE, "system") ?: "system"
        val currentIndex = languageCodes.indexOf(currentLang).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.language))
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val selectedCode = languageCodes[which]
                prefs.edit().putString(PREF_LANGUAGE, selectedCode).apply()
                updateLanguageText()
                dialog.dismiss()
                
                if (selectedCode != currentLang) {
                    recreate()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateThemeText() {
        val theme = prefs.getString(PREF_THEME, THEME_DARK) ?: THEME_DARK
        currentThemeText.text = when (theme) {
            THEME_LIGHT -> getString(R.string.theme_light)
            THEME_AMOLED -> getString(R.string.theme_amoled)
            else -> getString(R.string.theme_dark)
        }
    }

    private fun updateLanguageText() {
        val langCode = prefs.getString(PREF_LANGUAGE, "system") ?: "system"
        currentLanguageText.text = when (langCode) {
            "en" -> getString(R.string.lang_english)
            "de" -> getString(R.string.lang_german)
            else -> getString(R.string.lang_system)
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val langCode = prefs.getString(PREF_LANGUAGE, "system") ?: "system"
        
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
