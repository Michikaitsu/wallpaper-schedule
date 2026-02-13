package com.wallpaperscheduler

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var wallpaperManager: WallpaperSchedulerManager
    private lateinit var adapter: DayScheduleAdapter
    private lateinit var enableSwitch: SwitchMaterial
    private lateinit var statusText: TextView

    private var currentDay: Int = 1
    private var currentSlot: TimeSlot? = null
    private var isMorningSelection: Boolean = true
    private var selectedDaysForBulk: MutableList<Int> = mutableListOf()
    private var isBulkSelection: Boolean = false
    private var isBulkTimeSelection: Boolean = false
    private var selectedWallpaperTarget: WallpaperTarget = WallpaperTarget.BOTH
    private var pendingImageUri: Uri? = null
    
    private var currentTheme: String = ""
    private var currentLanguage: String = ""

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val REQUEST_FAVORITES = 102
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Persist permission
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                currentSlot?.let { slot ->
                    wallpaperManager.getShuffleManager().setShuffleFolder(currentDay, slot.label, uri.toString())
                    Toast.makeText(this, getString(R.string.shuffle_folder_set), Toast.LENGTH_SHORT).show()
                    adapter.updateSchedules(wallpaperManager.getDaySchedules())
                }
            }
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                pendingImageUri = uri
                showWallpaperTargetDialog()
            }
        }
    }
    
    private fun showWallpaperTargetDialog() {
        val uri = pendingImageUri ?: return
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wallpaper_type, null)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.homeScreenCard).setOnClickListener {
            selectedWallpaperTarget = WallpaperTarget.HOME
            dialog.dismiss()
            pendingImageUri?.let { handleImageSelection(it) }
        }
        
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.lockScreenCard).setOnClickListener {
            selectedWallpaperTarget = WallpaperTarget.LOCK
            dialog.dismiss()
            pendingImageUri?.let { handleImageSelection(it) }
        }
        
        dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.bothScreenCard).setOnClickListener {
            selectedWallpaperTarget = WallpaperTarget.BOTH
            dialog.dismiss()
            pendingImageUri?.let { handleImageSelection(it) }
        }
        
        dialog.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Save current settings to detect changes later
        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        currentTheme = prefs.getString(SettingsActivity.PREF_THEME, SettingsActivity.THEME_DARK) ?: SettingsActivity.THEME_DARK
        currentLanguage = prefs.getString(SettingsActivity.PREF_LANGUAGE, "system") ?: "system"
        
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        wallpaperManager = WallpaperSchedulerManager(this)

        applyAmoledColors()
        initViews()
        setupRecyclerView()
        setupListeners()
        checkPermissions()
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
            findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.rootLayout)?.setBackgroundColor(amoledBg)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_favorites -> {
                startActivity(Intent(this, FavoritesActivity::class.java))
                true
            }
            R.id.action_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initViews() {
        enableSwitch = findViewById(R.id.enableSwitch)
        statusText = findViewById(R.id.statusText)

        enableSwitch.isChecked = wallpaperManager.isSchedulerEnabled()
        updateStatus()
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.daysRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = DayScheduleAdapter(
            context = this,
            schedules = wallpaperManager.getDaySchedules(),
            onDayToggle = { schedule, enabled ->
                schedule.isEnabled = enabled
                wallpaperManager.saveDaySchedule(schedule)
            },
            onSlotWallpaperClick = { schedule, slot ->
                currentDay = schedule.dayOfWeek
                currentSlot = slot
                isMorningSelection = slot.label == "morning"
                isBulkSelection = false
                openImagePicker()
            },
            onSlotTimeClick = { schedule, slot ->
                showSlotTimePicker(schedule, slot)
            },
            onAddSlotClick = { schedule ->
                showAddSlotDialog(schedule)
            },
            onSlotLongClick = { schedule, slot ->
                showSlotOptionsDialog(schedule, slot)
            }
        )

        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            wallpaperManager.setSchedulerEnabled(isChecked)
            updateStatus()

            if (isChecked) {
                startWallpaperService()
                WallpaperWorker.schedule(this)
                WallpaperAlarmManager(this).scheduleNextAlarm()
                wallpaperManager.checkAndUpdateWallpaper()
                Toast.makeText(this, getString(R.string.scheduler_activated), Toast.LENGTH_SHORT).show()
            } else {
                stopWallpaperService()
                WallpaperWorker.cancel(this)
                WallpaperAlarmManager(this).cancelAllAlarms()
                Toast.makeText(this, getString(R.string.scheduler_deactivated), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.applyAllDaysButton).setOnClickListener {
            showDaySelectionDialog(listOf(1, 2, 3, 4, 5, 6, 7), forWallpaper = true)
        }

        findViewById<MaterialButton>(R.id.applyWeekdaysButton).setOnClickListener {
            showDaySelectionDialog(listOf(1, 2, 3, 4, 5), forWallpaper = true)
        }

        findViewById<MaterialButton>(R.id.applyWeekendsButton).setOnClickListener {
            showDaySelectionDialog(listOf(6, 7), forWallpaper = true)
        }

        findViewById<MaterialButton>(R.id.applyTimeButton).setOnClickListener {
            showDaySelectionDialog(listOf(1, 2, 3, 4, 5, 6, 7), forWallpaper = false)
        }
    }

    private fun showDaySelectionDialog(preselectedDays: List<Int>, forWallpaper: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_days, null)
        
        val chips = listOf(
            dialogView.findViewById<Chip>(R.id.chipMonday) to 1,
            dialogView.findViewById<Chip>(R.id.chipTuesday) to 2,
            dialogView.findViewById<Chip>(R.id.chipWednesday) to 3,
            dialogView.findViewById<Chip>(R.id.chipThursday) to 4,
            dialogView.findViewById<Chip>(R.id.chipFriday) to 5,
            dialogView.findViewById<Chip>(R.id.chipSaturday) to 6,
            dialogView.findViewById<Chip>(R.id.chipSunday) to 7
        )

        chips.forEach { (chip, day) ->
            chip.isChecked = preselectedDays.contains(day)
        }

        // Update dialog text based on purpose
        if (!forWallpaper) {
            dialogView.findViewById<TextView>(R.id.dialogTitle).text = getString(R.string.set_times_for_days)
            dialogView.findViewById<TextView>(R.id.dialogDesc).text = getString(R.string.select_days_desc)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.selectImageButton).setOnClickListener {
            selectedDaysForBulk.clear()
            chips.forEach { (chip, day) ->
                if (chip.isChecked) selectedDaysForBulk.add(day)
            }

            if (selectedDaysForBulk.isEmpty()) {
                Toast.makeText(this, getString(R.string.select_one_day), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            
            if (forWallpaper) {
                showMorningEveningChoice()
            } else {
                showTimeTypeChoice()
            }
        }

        dialog.show()
    }

    private fun showMorningEveningChoice() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.wallpaper_type))
            .setMessage(getString(R.string.which_time))
            .setPositiveButton("☀️ ${getString(R.string.morning)}") { _, _ ->
                isMorningSelection = true
                isBulkSelection = true
                isBulkTimeSelection = false
                openImagePicker()
            }
            .setNegativeButton("🌙 ${getString(R.string.evening)}") { _, _ ->
                isMorningSelection = false
                isBulkSelection = true
                isBulkTimeSelection = false
                openImagePicker()
            }
            .setNeutralButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showTimeTypeChoice() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_time_type))
            .setMessage(getString(R.string.which_time_to_set))
            .setPositiveButton("☀️ ${getString(R.string.morning)}") { _, _ ->
                isMorningSelection = true
                isBulkTimeSelection = true
                showBulkTimePicker()
            }
            .setNegativeButton("🌙 ${getString(R.string.evening)}") { _, _ ->
                isMorningSelection = false
                isBulkTimeSelection = true
                showBulkTimePicker()
            }
            .setNeutralButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showBulkTimePicker() {
        val defaultHour = if (isMorningSelection) 8 else 20
        
        TimePickerDialog(this, { _, hour, minute ->
            // Apply to all selected days
            selectedDaysForBulk.forEach { dayNum ->
                val schedules = wallpaperManager.getDaySchedules()
                val schedule = schedules.find { it.dayOfWeek == dayNum } ?: return@forEach
                
                if (isMorningSelection) {
                    schedule.morningHour = hour
                    schedule.morningMinute = minute
                } else {
                    schedule.eveningHour = hour
                    schedule.eveningMinute = minute
                }
                wallpaperManager.saveDaySchedule(schedule)
            }
            
            adapter.updateSchedules(wallpaperManager.getDaySchedules())
            Toast.makeText(this, getString(R.string.times_set_days, selectedDaysForBulk.size), Toast.LENGTH_SHORT).show()
            
            // Alarme neu planen wenn Scheduler aktiv
            if (wallpaperManager.isSchedulerEnabled()) {
                WallpaperAlarmManager(this).scheduleNextAlarm()
            }
        }, defaultHour, 0, true).show()
    }

    private fun showTimePicker(schedule: DaySchedule, isMorning: Boolean) {
        val currentHour = if (isMorning) schedule.morningHour else schedule.eveningHour
        val currentMinute = if (isMorning) schedule.morningMinute else schedule.eveningMinute

        TimePickerDialog(this, { _, hour, minute ->
            if (isMorning) {
                schedule.morningHour = hour
                schedule.morningMinute = minute
            } else {
                schedule.eveningHour = hour
                schedule.eveningMinute = minute
            }
            wallpaperManager.saveDaySchedule(schedule)
            adapter.updateSchedules(wallpaperManager.getDaySchedules())
            
            // Alarme neu planen wenn Scheduler aktiv
            if (wallpaperManager.isSchedulerEnabled()) {
                WallpaperAlarmManager(this).scheduleNextAlarm()
            }
        }, currentHour, currentMinute, true).show()
    }
    
    private fun showSlotTimePicker(schedule: DaySchedule, slot: TimeSlot) {
        TimePickerDialog(this, { _, hour, minute ->
            slot.hour = hour
            slot.minute = minute
            wallpaperManager.saveDaySchedule(schedule)
            adapter.updateSchedules(wallpaperManager.getDaySchedules())
            
            if (wallpaperManager.isSchedulerEnabled()) {
                WallpaperAlarmManager(this).scheduleNextAlarm()
            }
        }, slot.hour, slot.minute, true).show()
    }
    
    private fun showSlotOptionsDialog(schedule: DaySchedule, slot: TimeSlot) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_slot_options, null)
        
        val shuffleManager = wallpaperManager.getShuffleManager()
        val isShuffleEnabled = shuffleManager.isShuffleEnabled(schedule.dayOfWeek, slot.label)
        
        // Set time
        dialogView.findViewById<TextView>(R.id.slotTimeText).text = slot.getFormattedTime()
        
        // Load wallpaper preview
        val previewImage = dialogView.findViewById<ImageView>(R.id.wallpaperPreview)
        val noWallpaperText = dialogView.findViewById<TextView>(R.id.noWallpaperText)
        val previewCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.previewCard)
        
        val wallpaperPath = slot.wallpaperHome
        if (wallpaperPath != null) {
            try {
                val uri = android.net.Uri.parse(wallpaperPath)
                val file = java.io.File(uri.path ?: "")
                if (file.exists()) {
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                    previewImage.setImageBitmap(bitmap)
                    previewImage.visibility = View.VISIBLE
                    noWallpaperText.visibility = View.GONE
                    
                    // Click to show fullscreen preview
                    previewCard.setOnClickListener {
                        showFullscreenPreview(file.absolutePath)
                    }
                } else {
                    previewImage.visibility = View.GONE
                    noWallpaperText.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                previewImage.visibility = View.GONE
                noWallpaperText.visibility = View.VISIBLE
            }
        } else if (isShuffleEnabled) {
            previewImage.visibility = View.GONE
            noWallpaperText.text = "🔀 ${getString(R.string.shuffle_enabled)}"
            noWallpaperText.visibility = View.VISIBLE
        } else {
            previewImage.visibility = View.GONE
            noWallpaperText.visibility = View.VISIBLE
        }
        
        // Update shuffle button text
        val btnShuffle = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShuffle)
        btnShuffle.text = if (isShuffleEnabled) getString(R.string.shuffle_disabled) else getString(R.string.shuffle_mode)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        
        // Change wallpaper
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnChangeWallpaper).setOnClickListener {
            dialog.dismiss()
            currentDay = schedule.dayOfWeek
            currentSlot = slot
            isBulkSelection = false
            openImagePicker()
        }
        
        // Shuffle toggle
        btnShuffle.setOnClickListener {
            dialog.dismiss()
            if (isShuffleEnabled) {
                shuffleManager.setShuffleFolder(schedule.dayOfWeek, slot.label, null)
                Toast.makeText(this, getString(R.string.shuffle_disabled), Toast.LENGTH_SHORT).show()
                adapter.updateSchedules(wallpaperManager.getDaySchedules())
            } else {
                currentDay = schedule.dayOfWeek
                currentSlot = slot
                openFolderPicker()
            }
        }
        
        // Change time
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnChangeTime).setOnClickListener {
            dialog.dismiss()
            showSlotTimePicker(schedule, slot)
        }
        
        // Delete
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDelete).setOnClickListener {
            dialog.dismiss()
            if (schedule.timeSlots.size > 1) {
                schedule.timeSlots.remove(slot)
                shuffleManager.setShuffleFolder(schedule.dayOfWeek, slot.label, null)
                wallpaperManager.saveDaySchedule(schedule)
                adapter.updateSchedules(wallpaperManager.getDaySchedules())
                Toast.makeText(this, getString(R.string.slot_deleted), Toast.LENGTH_SHORT).show()
                
                if (wallpaperManager.isSchedulerEnabled()) {
                    WallpaperAlarmManager(this).scheduleNextAlarm()
                }
            } else {
                Toast.makeText(this, getString(R.string.min_one_slot), Toast.LENGTH_SHORT).show()
            }
        }
        
        // Favorite toggle
        val favoritesManager = FavoritesManager(this)
        val favoriteIcon = dialogView.findViewById<ImageView>(R.id.favoriteIcon)
        
        if (wallpaperPath != null) {
            val isFavorite = favoritesManager.isFavorite(wallpaperPath)
            favoriteIcon.setImageResource(if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
            favoriteIcon.visibility = View.VISIBLE
            
            favoriteIcon.setOnClickListener {
                val nowFavorite = favoritesManager.toggleFavorite(wallpaperPath)
                favoriteIcon.setImageResource(if (nowFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
                Toast.makeText(this, 
                    if (nowFavorite) getString(R.string.added_to_favorites) else getString(R.string.removed_from_favorites),
                    Toast.LENGTH_SHORT).show()
            }
        } else {
            favoriteIcon.visibility = View.GONE
        }
        
        // From Favorites button
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFromFavorites).setOnClickListener {
            dialog.dismiss()
            currentDay = schedule.dayOfWeek
            currentSlot = slot
            startActivityForResult(Intent(this, FavoritesActivity::class.java), REQUEST_FAVORITES)
        }
        
        // Effects button
        val btnEffects = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEffects)
        if (wallpaperPath != null) {
            btnEffects.visibility = View.VISIBLE
            btnEffects.setOnClickListener {
                dialog.dismiss()
                showEffectsDialog(schedule, slot, wallpaperPath)
            }
        } else {
            btnEffects.visibility = View.GONE
        }
        
        dialog.show()
    }
    
    private fun showEffectsDialog(schedule: DaySchedule, slot: TimeSlot, wallpaperPath: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wallpaper_effects, null)
        
        val uri = android.net.Uri.parse(wallpaperPath)
        val file = java.io.File(uri.path ?: return)
        if (!file.exists()) return
        
        val originalBitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        val previewImage = dialogView.findViewById<ImageView>(R.id.effectPreview)
        previewImage.setImageBitmap(originalBitmap)
        
        val blurSlider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.blurSlider)
        val dimSlider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.dimSlider)
        val saturationSlider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.saturationSlider)
        val warmthSlider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.warmthSlider)
        
        var currentEffect = WallpaperEffect()
        
        val updatePreview = {
            currentEffect = WallpaperEffect(
                blur = blurSlider.value,
                dim = dimSlider.value,
                saturation = saturationSlider.value,
                warmth = warmthSlider.value
            )
            
            lifecycleScope.launch(Dispatchers.Default) {
                val previewBitmap = originalBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                val effectedBitmap = WallpaperEffects.applyEffects(this@MainActivity, previewBitmap, currentEffect)
                withContext(Dispatchers.Main) {
                    previewImage.setImageBitmap(effectedBitmap)
                }
            }
        }
        
        blurSlider.addOnChangeListener { _, _, _ -> updatePreview() }
        dimSlider.addOnChangeListener { _, _, _ -> updatePreview() }
        saturationSlider.addOnChangeListener { _, _, _ -> updatePreview() }
        warmthSlider.addOnChangeListener { _, _, _ -> updatePreview() }
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.resetButton).setOnClickListener {
            blurSlider.value = 0f
            dimSlider.value = 0f
            saturationSlider.value = 1f
            warmthSlider.value = 0f
            previewImage.setImageBitmap(originalBitmap)
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.applyButton).setOnClickListener {
            dialog.dismiss()
            applyEffectsToWallpaper(schedule, slot, wallpaperPath, currentEffect)
        }
        
        dialog.show()
    }
    
    private fun applyEffectsToWallpaper(schedule: DaySchedule, slot: TimeSlot, wallpaperPath: String, effect: WallpaperEffect) {
        val loadingToast = Toast.makeText(this, getString(R.string.loading), Toast.LENGTH_SHORT)
        loadingToast.show()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uri = android.net.Uri.parse(wallpaperPath)
                val file = java.io.File(uri.path ?: return@launch)
                val originalBitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                
                val effectedBitmap = WallpaperEffects.applyEffects(this@MainActivity, originalBitmap, effect)
                
                // Save effected bitmap
                val effectFileName = "wallpaper_day${schedule.dayOfWeek}_${slot.label}_effected.jpg"
                val outputFile = java.io.File(filesDir, effectFileName)
                outputFile.outputStream().use { out ->
                    effectedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                }
                
                val newPath = android.net.Uri.fromFile(outputFile).toString()
                slot.wallpaperHome = newPath
                slot.wallpaperLock = newPath
                wallpaperManager.saveDaySchedule(schedule)
                
                withContext(Dispatchers.Main) {
                    adapter.updateSchedules(wallpaperManager.getDaySchedules())
                    Toast.makeText(this@MainActivity, getString(R.string.wallpaper_set), Toast.LENGTH_SHORT).show()
                }
                
                if (wallpaperManager.isSchedulerEnabled()) {
                    wallpaperManager.checkAndUpdateWallpaper()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.error_message, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        folderPickerLauncher.launch(intent)
    }
    
    private fun showFullscreenPreview(imagePath: String) {
        val previewDialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val previewView = LayoutInflater.from(this).inflate(R.layout.dialog_wallpaper_preview, null)
        
        val rootView = previewView.findViewById<android.widget.FrameLayout>(R.id.previewRoot)
        val previewImage = previewView.findViewById<ImageView>(R.id.fullscreenPreview)
        val closeButton = previewView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.closeButton)
        
        // Load full resolution image
        val bitmap = android.graphics.BitmapFactory.decodeFile(imagePath)
        previewImage.setImageBitmap(bitmap)
        
        // Fade in animation
        rootView.alpha = 0f
        rootView.animate().alpha(1f).setDuration(200).start()
        
        val dismissWithAnimation = {
            rootView.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction { previewDialog.dismiss() }
                .start()
        }
        
        // Close on button click
        closeButton.setOnClickListener { dismissWithAnimation() }
        
        // Close on image click
        previewImage.setOnClickListener { dismissWithAnimation() }
        
        previewDialog.setContentView(previewView)
        previewDialog.show()
    }
    
    private fun showAddSlotDialog(schedule: DaySchedule) {
        // Direkt TimePicker öffnen
        TimePickerDialog(this, { _, hour, minute ->
            // Generiere ein eindeutiges Label basierend auf der Zeit
            val label = "slot_${hour}_${minute}"
            
            // Prüfe ob diese Zeit schon existiert
            val existingSlot = schedule.timeSlots.find { it.hour == hour && it.minute == minute }
            if (existingSlot != null) {
                Toast.makeText(this, getString(R.string.slot_exists), Toast.LENGTH_SHORT).show()
                return@TimePickerDialog
            }
            
            val newSlot = TimeSlot(hour, minute, label)
            schedule.timeSlots.add(newSlot)
            schedule.timeSlots.sortBy { it.getTimeInMinutes() }
            wallpaperManager.saveDaySchedule(schedule)
            adapter.updateSchedules(wallpaperManager.getDaySchedules())
            
            Toast.makeText(this, getString(R.string.slot_added), Toast.LENGTH_SHORT).show()
            
            if (wallpaperManager.isSchedulerEnabled()) {
                WallpaperAlarmManager(this).scheduleNextAlarm()
            }
            
            // Optional: Direkt Wallpaper-Auswahl öffnen
            currentDay = schedule.dayOfWeek
            currentSlot = newSlot
            isBulkSelection = false
            openImagePicker()
            
        }, 12, 0, true).show()
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun handleImageSelection(uri: Uri) {
        val loadingToast = Toast.makeText(this, getString(R.string.loading), Toast.LENGTH_SHORT)
        loadingToast.show()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val slotLabel = currentSlot?.label ?: if (isMorningSelection) "morning" else "evening"
                
                if (isBulkSelection) {
                    val firstDay = selectedDaysForBulk.first()
                    val savedPath = wallpaperManager.copyWallpaperToStorage(uri, firstDay, slotLabel, selectedWallpaperTarget)
                    
                    savedPath?.let { path ->
                        selectedDaysForBulk.forEach { day ->
                            saveWallpaperToSlot(day, slotLabel, path)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.wallpaper_set_days, selectedDaysForBulk.size), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val savedPath = wallpaperManager.copyWallpaperToStorage(uri, currentDay, slotLabel, selectedWallpaperTarget)
                    
                    savedPath?.let { path ->
                        saveWallpaperToSlot(currentDay, slotLabel, path)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.wallpaper_set), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    adapter.updateSchedules(wallpaperManager.getDaySchedules())
                }

                if (wallpaperManager.isSchedulerEnabled()) {
                    wallpaperManager.checkAndUpdateWallpaper()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.error_message, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun saveWallpaperToSlot(day: Int, slotLabel: String, path: String) {
        val schedule = wallpaperManager.getDaySchedules().find { it.dayOfWeek == day } ?: return
        val slot = schedule.timeSlots.find { it.label == slotLabel } ?: return
        
        when (selectedWallpaperTarget) {
            WallpaperTarget.HOME -> slot.wallpaperHome = path
            WallpaperTarget.LOCK -> slot.wallpaperLock = path
            WallpaperTarget.BOTH -> {
                slot.wallpaperHome = path
                slot.wallpaperLock = path
            }
        }
        wallpaperManager.saveDaySchedule(schedule)
    }

    private fun updateStatus() {
        statusText.text = if (wallpaperManager.isSchedulerEnabled()) {
            getString(R.string.active)
        } else {
            getString(R.string.inactive)
        }
    }

    private fun startWallpaperService() {
        val serviceIntent = Intent(this, WallpaperService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopWallpaperService() {
        val serviceIntent = Intent(this, WallpaperService::class.java)
        stopService(serviceIntent)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.SET_WALLPAPER)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
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

    override fun onResume() {
        super.onResume()
        
        // Check if theme or language changed
        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val newTheme = prefs.getString(SettingsActivity.PREF_THEME, SettingsActivity.THEME_DARK) ?: SettingsActivity.THEME_DARK
        val newLanguage = prefs.getString(SettingsActivity.PREF_LANGUAGE, "system") ?: "system"
        
        if (newTheme != currentTheme || newLanguage != currentLanguage) {
            recreate()
            return
        }
        
        adapter.updateSchedules(wallpaperManager.getDaySchedules())
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_FAVORITES && resultCode == Activity.RESULT_OK) {
            val selectedPath = data?.getStringExtra("selected_path")
            if (selectedPath != null && currentSlot != null) {
                val schedule = wallpaperManager.getDaySchedules().find { it.dayOfWeek == currentDay }
                schedule?.let {
                    val slot = it.timeSlots.find { s -> s.label == currentSlot?.label }
                    slot?.let { s ->
                        s.wallpaperHome = selectedPath
                        s.wallpaperLock = selectedPath
                    }
                    wallpaperManager.saveDaySchedule(it)
                    adapter.updateSchedules(wallpaperManager.getDaySchedules())
                    Toast.makeText(this, getString(R.string.wallpaper_set), Toast.LENGTH_SHORT).show()
                    
                    if (wallpaperManager.isSchedulerEnabled()) {
                        wallpaperManager.checkAndUpdateWallpaper()
                    }
                }
            }
        }
    }
}
