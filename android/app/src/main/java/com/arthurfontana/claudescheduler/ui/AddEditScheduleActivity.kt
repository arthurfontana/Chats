package com.arthurfontana.claudescheduler.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arthurfontana.claudescheduler.R
import com.arthurfontana.claudescheduler.alarm.AlarmScheduler
import com.arthurfontana.claudescheduler.data.ScheduleRepository
import com.arthurfontana.claudescheduler.databinding.ActivityEditScheduleBinding
import com.arthurfontana.claudescheduler.model.ScheduledMessage
import com.arthurfontana.claudescheduler.util.Constants
import com.google.android.material.chip.Chip
import java.util.Calendar

class AddEditScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditScheduleBinding
    private lateinit var repository: ScheduleRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = ScheduleRepository(this)

        val id = intent.getLongExtra(Constants.EXTRA_SCHEDULE_ID, -1L).takeIf { it != -1L }
        val existing = id?.let { repository.getById(it) }

        binding.toolbar.title = getString(
            if (existing != null) R.string.action_edit_schedule else R.string.action_new_schedule
        )

        val now = Calendar.getInstance()
        binding.timePicker.setIs24HourView(true)
        setTimePickerValue(existing?.hour ?: now.get(Calendar.HOUR_OF_DAY), existing?.minute ?: now.get(Calendar.MINUTE))

        binding.editMessage.setText(existing?.message ?: "")
        binding.switchEnabled.isChecked = existing?.enabled ?: true
        setSelectedDays(existing?.daysOfWeek ?: emptySet())

        binding.buttonDelete.visibility = if (existing != null) View.VISIBLE else View.GONE
        binding.buttonDelete.setOnClickListener { existing?.let { deleteAndFinish(it) } }

        binding.buttonCancel.setOnClickListener { finish() }
        binding.buttonSave.setOnClickListener { save(existing) }
    }

    private fun setTimePickerValue(hour: Int, minute: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.timePicker.hour = hour
            binding.timePicker.minute = minute
        } else {
            @Suppress("DEPRECATION")
            binding.timePicker.currentHour = hour
            @Suppress("DEPRECATION")
            binding.timePicker.currentMinute = minute
        }
    }

    private fun getTimePickerValue(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        binding.timePicker.hour to binding.timePicker.minute
    } else {
        @Suppress("DEPRECATION")
        (binding.timePicker.currentHour to binding.timePicker.currentMinute)
    }

    private fun setSelectedDays(days: Set<Int>) {
        dayChips().forEach { (calendarDay, chip) -> chip.isChecked = days.contains(calendarDay) }
    }

    private fun selectedDays(): Set<Int> =
        dayChips().filter { (_, chip) -> chip.isChecked }.map { (day, _) -> day }.toSet()

    private fun dayChips(): List<Pair<Int, Chip>> = listOf(
        Calendar.SUNDAY to binding.chipSun,
        Calendar.MONDAY to binding.chipMon,
        Calendar.TUESDAY to binding.chipTue,
        Calendar.WEDNESDAY to binding.chipWed,
        Calendar.THURSDAY to binding.chipThu,
        Calendar.FRIDAY to binding.chipFri,
        Calendar.SATURDAY to binding.chipSat
    )

    private fun save(existing: ScheduledMessage?) {
        val message = binding.editMessage.text?.toString()?.trim().orEmpty()
        if (message.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_message, Toast.LENGTH_SHORT).show()
            return
        }
        val (hour, minute) = getTimePickerValue()
        val schedule = ScheduledMessage(
            id = existing?.id ?: System.currentTimeMillis(),
            hour = hour,
            minute = minute,
            message = message,
            daysOfWeek = selectedDays(),
            enabled = binding.switchEnabled.isChecked
        )
        repository.save(schedule)
        if (schedule.enabled) {
            AlarmScheduler.scheduleNext(this, schedule)
        } else {
            AlarmScheduler.cancel(this, schedule.id)
        }
        finish()
    }

    private fun deleteAndFinish(schedule: ScheduledMessage) {
        AlarmScheduler.cancel(this, schedule.id)
        repository.delete(schedule.id)
        finish()
    }
}
