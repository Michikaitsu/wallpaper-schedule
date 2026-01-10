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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
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
    private var isMorningSelection: Boolean = true
    private var selectedDaysForBulk: MutableList<Int> = mutableListOf()
    private var isBulkSelection: Boolean = false
    private var isBulkTimeSelection: Boolean = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleImageSelection(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        wallpaperManager = WallpaperSchedulerManager(this)

        initViews()
        setupRecyclerView()
        setupListeners()
        checkPermissions()
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(SettingsActivity.PREF_DARK_MODE, true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
            onMorningClick = { schedule ->
                currentDay = schedule.dayOfWeek
                isMorningSelection = true
                isBulkSelection = false
                openImagePicker()
            },
            onEveningClick = { schedule ->
                currentDay = schedule.dayOfWeek
                isMorningSelection = false
                isBulkSelection = false
                openImagePicker()
            },
            onTimeClick = { schedule, isMorning ->
                showTimePicker(schedule, isMorning)
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
                wallpaperManager.checkAndUpdateWallpaper()
                Toast.makeText(this, getString(R.string.scheduler_activated), Toast.LENGTH_SHORT).show()
            } else {
                stopWallpaperService()
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
        }, currentHour, currentMinute, true).show()
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun handleImageSelection(uri: Uri) {
        try {
            if (isBulkSelection) {
                val firstDay = selectedDaysForBulk.first()
                val savedPath = wallpaperManager.copyWallpaperToStorage(uri, firstDay, isMorningSelection)
                
                savedPath?.let { path ->
                    wallpaperManager.applyWallpaperForDays(selectedDaysForBulk, isMorningSelection, path)
                    Toast.makeText(this, getString(R.string.wallpaper_set_days, selectedDaysForBulk.size), Toast.LENGTH_SHORT).show()
                }
            } else {
                val savedPath = wallpaperManager.copyWallpaperToStorage(uri, currentDay, isMorningSelection)
                
                savedPath?.let { path ->
                    val schedules = wallpaperManager.getDaySchedules()
                    val schedule = schedules.find { it.dayOfWeek == currentDay } ?: return
                    
                    if (isMorningSelection) {
                        schedule.morningWallpaper = path
                    } else {
                        schedule.eveningWallpaper = path
                    }
                    wallpaperManager.saveDaySchedule(schedule)
                    Toast.makeText(this, getString(R.string.wallpaper_set), Toast.LENGTH_SHORT).show()
                }
            }

            adapter.updateSchedules(wallpaperManager.getDaySchedules())

            if (wallpaperManager.isSchedulerEnabled()) {
                wallpaperManager.checkAndUpdateWallpaper()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_message, e.message), Toast.LENGTH_LONG).show()
        }
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
        startService(serviceIntent)
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
        adapter.updateSchedules(wallpaperManager.getDaySchedules())
    }
}
