package com.arthurfontana.claudescheduler.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arthurfontana.claudescheduler.R
import com.arthurfontana.claudescheduler.databinding.ItemScheduleBinding
import com.arthurfontana.claudescheduler.model.ScheduledMessage
import java.util.Calendar
import java.util.Locale

class ScheduleAdapter(
    private val onToggle: (ScheduledMessage, Boolean) -> Unit,
    private val onClick: (ScheduledMessage) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    private var items: List<ScheduledMessage> = emptyList()

    fun submitList(list: List<ScheduledMessage>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemScheduleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(schedule: ScheduledMessage) {
            binding.textTime.text = String.format(Locale.getDefault(), "%02d:%02d", schedule.hour, schedule.minute)
            binding.textMessage.text = schedule.message
            binding.textDays.text = formatDays(schedule.daysOfWeek)

            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = schedule.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, checked -> onToggle(schedule, checked) }

            binding.root.setOnClickListener { onClick(schedule) }
        }

        private fun formatDays(days: Set<Int>): String {
            if (days.isEmpty()) return binding.root.context.getString(R.string.one_time_label)
            val labels = mapOf(
                Calendar.SUNDAY to "Dom", Calendar.MONDAY to "Seg", Calendar.TUESDAY to "Ter",
                Calendar.WEDNESDAY to "Qua", Calendar.THURSDAY to "Qui", Calendar.FRIDAY to "Sex",
                Calendar.SATURDAY to "Sáb"
            )
            return days.sorted().mapNotNull { labels[it] }.joinToString(", ")
        }
    }
}
