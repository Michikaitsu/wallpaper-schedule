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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var darkModeSwitch: SwitchMaterial
    private lateinit var currentLanguageText: TextView

    companion object {
        const val GITHUB_URL = "https://github.com/KaiTooast/wallpaper-schedule"
        const val HELP_URL = "https://kaitooast.github.io/wallpaper-schedule"
        const val PREF_DARK_MODE = "dark_mode"
        const val PREF_LANGUAGE = "language"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        applyTheme()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupToolbar()
        setupViews()
        setupListeners()
    }

    private fun applyTheme() {
        val isDarkMode = prefs.getBoolean(PREF_DARK_MODE, true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViews() {
        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        currentLanguageText = findViewById(R.id.currentLanguage)

        darkModeSwitch.isChecked = prefs.getBoolean(PREF_DARK_MODE, true)
        updateLanguageText()

        // Version
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            findViewById<TextView>(R.id.versionText).text = "Version $versionName"
        } catch (e: Exception) {
            findViewById<TextView>(R.id.versionText).text = "Version 1.0"
        }
    }

    private fun setupListeners() {
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_DARK_MODE, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES 
                else AppCompatDelegate.MODE_NIGHT_NO
            )
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
                
                // Restart to apply language
                if (selectedCode != currentLang) {
                    recreate()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
