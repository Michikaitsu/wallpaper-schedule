package com.wallpaperscheduler

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var indicatorLayout: LinearLayout
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: MaterialButton
    
    private val pages = mutableListOf<OnboardingPage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        
        // Check if onboarding was already completed
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_completed", false)) {
            startMainActivity()
            return
        }
        
        setContentView(R.layout.activity_onboarding)
        
        applyAmoledColors()
        setupPages()
        setupViews()
    }
    
    private fun setupPages() {
        pages.add(OnboardingPage(
            icon = "🎨",
            title = getString(R.string.onboarding_title_1),
            description = getString(R.string.onboarding_desc_1)
        ))
        pages.add(OnboardingPage(
            icon = "⏰",
            title = getString(R.string.onboarding_title_2),
            description = getString(R.string.onboarding_desc_2)
        ))
        pages.add(OnboardingPage(
            icon = "🔀",
            title = getString(R.string.onboarding_title_3),
            description = getString(R.string.onboarding_desc_3)
        ))
        pages.add(OnboardingPage(
            icon = "🚀",
            title = getString(R.string.onboarding_title_4),
            description = getString(R.string.onboarding_desc_4)
        ))
    }
    
    private fun setupViews() {
        viewPager = findViewById(R.id.viewPager)
        indicatorLayout = findViewById(R.id.indicatorLayout)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)
        
        viewPager.adapter = OnboardingAdapter(pages)
        
        setupIndicators()
        updateIndicators(0)
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                btnNext.text = if (position == pages.size - 1) {
                    getString(R.string.get_started)
                } else {
                    getString(R.string.next)
                }
            }
        })
        
        btnNext.setOnClickListener {
            if (viewPager.currentItem < pages.size - 1) {
                viewPager.currentItem += 1
            } else {
                completeOnboarding()
            }
        }
        
        btnSkip.setOnClickListener {
            completeOnboarding()
        }
    }
    
    private fun setupIndicators() {
        indicatorLayout.removeAllViews()
        pages.forEachIndexed { index, _ ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                    marginStart = if (index == 0) 0 else 16
                }
                background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.indicator_dot)
            }
            indicatorLayout.addView(dot)
        }
    }
    
    private fun updateIndicators(position: Int) {
        for (i in 0 until indicatorLayout.childCount) {
            val dot = indicatorLayout.getChildAt(i)
            dot.alpha = if (i == position) 1f else 0.3f
            dot.scaleX = if (i == position) 1.2f else 1f
            dot.scaleY = if (i == position) 1.2f else 1f
        }
    }
    
    private fun completeOnboarding() {
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .apply()
        startMainActivity()
    }
    
    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
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
            findViewById<View>(R.id.rootLayout)?.setBackgroundColor(amoledBg)
        }
    }
}

data class OnboardingPage(
    val icon: String,
    val title: String,
    val description: String
)

class OnboardingAdapter(
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.pageIcon)
        val title: TextView = view.findViewById(R.id.pageTitle)
        val description: TextView = view.findViewById(R.id.pageDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = pages[position]
        holder.icon.text = page.icon
        holder.title.text = page.title
        holder.description.text = page.description
    }

    override fun getItemCount() = pages.size
}
