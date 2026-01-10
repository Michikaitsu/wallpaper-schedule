package com.wallpaperscheduler

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File

class DayScheduleAdapter(
    private val context: Context,
    private var schedules: List<DaySchedule>,
    private val onDayToggle: (DaySchedule, Boolean) -> Unit,
    private val onMorningClick: (DaySchedule) -> Unit,
    private val onEveningClick: (DaySchedule) -> Unit,
    private val onTimeClick: (DaySchedule, Boolean) -> Unit
) : RecyclerView.Adapter<DayScheduleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayName: TextView = view.findViewById(R.id.dayName)
        val daySwitch: SwitchMaterial = view.findViewById(R.id.daySwitch)
        val contentLayout: LinearLayout = view.findViewById(R.id.contentLayout)
        val morningCard: MaterialCardView = view.findViewById(R.id.morningCard)
        val eveningCard: MaterialCardView = view.findViewById(R.id.eveningCard)
        val morningPreview: ImageView = view.findViewById(R.id.morningPreview)
        val eveningPreview: ImageView = view.findViewById(R.id.eveningPreview)
        val morningPlaceholder: TextView = view.findViewById(R.id.morningPlaceholder)
        val eveningPlaceholder: TextView = view.findViewById(R.id.eveningPlaceholder)
        val morningTime: TextView = view.findViewById(R.id.morningTime)
        val eveningTime: TextView = view.findViewById(R.id.eveningTime)
        val morningTimeCard: MaterialCardView = view.findViewById(R.id.morningTimeCard)
        val eveningTimeCard: MaterialCardView = view.findViewById(R.id.eveningTimeCard)
        val morningLabel: TextView = view.findViewById(R.id.morningLabel)
        val eveningLabel: TextView = view.findViewById(R.id.eveningLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = schedules[position]
        
        holder.dayName.text = schedule.dayName
        holder.daySwitch.isChecked = schedule.isEnabled
        holder.contentLayout.alpha = if (schedule.isEnabled) 1f else 0.5f
        
        holder.morningLabel.text = "☀️ ${context.getString(R.string.morning)}"
        holder.eveningLabel.text = "🌙 ${context.getString(R.string.evening)}"
        
        holder.daySwitch.setOnCheckedChangeListener { _, isChecked ->
            onDayToggle(schedule, isChecked)
            holder.contentLayout.alpha = if (isChecked) 1f else 0.5f
        }

        // Morning wallpaper
        if (schedule.morningWallpaper != null) {
            val uri = Uri.parse(schedule.morningWallpaper)
            val file = File(uri.path ?: "")
            if (file.exists()) {
                holder.morningPreview.setImageURI(null)
                holder.morningPreview.setImageURI(uri)
                holder.morningPreview.visibility = View.VISIBLE
                holder.morningPlaceholder.visibility = View.GONE
            }
        } else {
            holder.morningPreview.setImageDrawable(null)
            holder.morningPreview.visibility = View.GONE
            holder.morningPlaceholder.visibility = View.VISIBLE
        }

        // Evening wallpaper
        if (schedule.eveningWallpaper != null) {
            val uri = Uri.parse(schedule.eveningWallpaper)
            val file = File(uri.path ?: "")
            if (file.exists()) {
                holder.eveningPreview.setImageURI(null)
                holder.eveningPreview.setImageURI(uri)
                holder.eveningPreview.visibility = View.VISIBLE
                holder.eveningPlaceholder.visibility = View.GONE
            }
        } else {
            holder.eveningPreview.setImageDrawable(null)
            holder.eveningPreview.visibility = View.GONE
            holder.eveningPlaceholder.visibility = View.VISIBLE
        }

        // Time with minutes
        holder.morningTime.text = String.format("%02d:%02d", schedule.morningHour, schedule.morningMinute)
        holder.eveningTime.text = String.format("%02d:%02d", schedule.eveningHour, schedule.eveningMinute)

        holder.morningCard.setOnClickListener { onMorningClick(schedule) }
        holder.eveningCard.setOnClickListener { onEveningClick(schedule) }
        holder.morningTimeCard.setOnClickListener { onTimeClick(schedule, true) }
        holder.eveningTimeCard.setOnClickListener { onTimeClick(schedule, false) }
    }

    override fun getItemCount() = schedules.size

    fun updateSchedules(newSchedules: List<DaySchedule>) {
        schedules = newSchedules
        notifyDataSetChanged()
    }
}
