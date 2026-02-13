package com.wallpaperscheduler

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File

class DayScheduleAdapter(
    private val context: Context,
    private var schedules: List<DaySchedule>,
    private val onDayToggle: (DaySchedule, Boolean) -> Unit,
    private val onSlotWallpaperClick: (DaySchedule, TimeSlot) -> Unit,
    private val onSlotTimeClick: (DaySchedule, TimeSlot) -> Unit,
    private val onAddSlotClick: ((DaySchedule) -> Unit)? = null,
    private val onSlotLongClick: ((DaySchedule, TimeSlot) -> Unit)? = null
) : RecyclerView.Adapter<DayScheduleAdapter.ViewHolder>() {

    // Legacy constructor for compatibility
    constructor(
        context: Context,
        schedules: List<DaySchedule>,
        onDayToggle: (DaySchedule, Boolean) -> Unit,
        onMorningClick: (DaySchedule) -> Unit,
        onEveningClick: (DaySchedule) -> Unit,
        onTimeClick: (DaySchedule, Boolean) -> Unit,
        onAddSlotClick: ((DaySchedule) -> Unit)? = null
    ) : this(
        context,
        schedules,
        onDayToggle,
        onSlotWallpaperClick = { schedule, slot ->
            if (slot.label == "morning") onMorningClick(schedule)
            else onEveningClick(schedule)
        },
        onSlotTimeClick = { schedule, slot ->
            onTimeClick(schedule, slot.label == "morning")
        },
        onAddSlotClick
    )

    private val isAmoled: Boolean
    private val shuffleManager: ShuffleManager
    private val slotColors = listOf(
        R.color.accent_orange,
        R.color.accent_purple,
        R.color.button_blue,
        R.color.button_green,
        R.color.accent_pink,
        R.color.accent_teal
    )

    init {
        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        isAmoled = prefs.getString(SettingsActivity.PREF_THEME, SettingsActivity.THEME_DARK) == SettingsActivity.THEME_AMOLED
        shuffleManager = ShuffleManager(context)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rootCard: MaterialCardView = view as MaterialCardView
        val dayName: TextView = view.findViewById(R.id.dayName)
        val daySwitch: SwitchMaterial = view.findViewById(R.id.daySwitch)
        val slotsContainer: LinearLayout = view.findViewById(R.id.slotsContainer)
        val addSlotButton: com.google.android.material.button.MaterialButton = view.findViewById(R.id.addSlotButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = schedules[position]
        
        if (isAmoled) {
            val amoledCard = ContextCompat.getColor(context, R.color.amoled_card)
            holder.rootCard.setCardBackgroundColor(amoledCard)
        }
        
        holder.dayName.text = schedule.dayName
        holder.daySwitch.isChecked = schedule.isEnabled
        holder.slotsContainer.alpha = if (schedule.isEnabled) 1f else 0.5f
        
        holder.daySwitch.setOnCheckedChangeListener { _, isChecked ->
            onDayToggle(schedule, isChecked)
            holder.slotsContainer.alpha = if (isChecked) 1f else 0.5f
        }

        // Clear and rebuild slots
        holder.slotsContainer.removeAllViews()
        
        val sortedSlots = schedule.timeSlots.sortedBy { it.getTimeInMinutes() }
        
        sortedSlots.forEachIndexed { index, slot ->
            val slotView = LayoutInflater.from(context)
                .inflate(R.layout.item_time_slot, holder.slotsContainer, false)
            
            val accentColor = ContextCompat.getColor(context, slotColors[index % slotColors.size])
            
            // Time label at top
            val timeLabel = slotView.findViewById<TextView>(R.id.slotTimeLabel)
            val isShuffleEnabled = shuffleManager.isShuffleEnabled(schedule.dayOfWeek, slot.label)
            timeLabel.text = if (isShuffleEnabled) "🔀 ${slot.getFormattedTime()}" else slot.getFormattedTime()
            timeLabel.setTextColor(accentColor)
            
            // Wallpaper preview
            val wallpaperCard = slotView.findViewById<MaterialCardView>(R.id.slotWallpaperCard)
            val preview = slotView.findViewById<ImageView>(R.id.slotPreview)
            val placeholder = slotView.findViewById<TextView>(R.id.slotPlaceholder)
            
            loadWallpaperPreview(slot.wallpaperHome, preview, placeholder)
            
            wallpaperCard.setOnClickListener { onSlotWallpaperClick(schedule, slot) }
            wallpaperCard.setOnLongClickListener { 
                onSlotLongClick?.invoke(schedule, slot)
                true
            }
            
            // Time edit card
            val timeCard = slotView.findViewById<MaterialCardView>(R.id.slotTimeCard)
            val timeText = slotView.findViewById<TextView>(R.id.slotTime)
            
            timeCard.setCardBackgroundColor(accentColor)
            timeCard.strokeWidth = 0
            timeText.text = slot.getFormattedTime()
            timeText.setTextColor(ContextCompat.getColor(context, R.color.white))
            
            // Set change time label to white for better contrast
            val changeTimeLabel = slotView.findViewById<TextView>(R.id.slotTime)
            changeTimeLabel.parent?.let { parent ->
                if (parent is LinearLayout) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChildAt(i)
                        if (child is TextView && child.id != R.id.slotTime) {
                            child.setTextColor(ContextCompat.getColor(context, R.color.white))
                        }
                    }
                }
            }
            
            timeCard.setOnClickListener { onSlotTimeClick(schedule, slot) }
            
            // AMOLED styling
            if (isAmoled) {
                val amoledCardSecondary = ContextCompat.getColor(context, R.color.amoled_card_secondary)
                wallpaperCard.setCardBackgroundColor(amoledCardSecondary)
                timeCard.setCardBackgroundColor(amoledCardSecondary)
            }
            
            holder.slotsContainer.addView(slotView)
        }
        
        // Add slot button
        holder.addSlotButton.setOnClickListener { 
            onAddSlotClick?.invoke(schedule)
        }
    }
    
    private fun loadWallpaperPreview(wallpaperPath: String?, imageView: ImageView, placeholder: TextView) {
        if (wallpaperPath != null) {
            try {
                val uri = Uri.parse(wallpaperPath)
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 4
                    }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        imageView.visibility = View.VISIBLE
                        placeholder.visibility = View.GONE
                        return
                    }
                }
            } catch (e: Exception) { }
        }
        
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        placeholder.text = "+"
        placeholder.visibility = View.VISIBLE
    }

    override fun getItemCount() = schedules.size

    fun updateSchedules(newSchedules: List<DaySchedule>) {
        schedules = newSchedules
        notifyDataSetChanged()
    }
}
