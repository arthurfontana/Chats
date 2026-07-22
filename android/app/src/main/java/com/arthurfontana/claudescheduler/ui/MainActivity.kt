package com.arthurfontana.claudescheduler.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.arthurfontana.claudescheduler.accessibility.ClaudeAccessibilityService
import com.arthurfontana.claudescheduler.alarm.AlarmScheduler
import com.arthurfontana.claudescheduler.data.ScheduleRepository
import com.arthurfontana.claudescheduler.databinding.ActivityMainBinding
import com.arthurfontana.claudescheduler.model.ScheduledMessage
import com.arthurfontana.claudescheduler.util.Constants

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScheduleRepository
    private lateinit var adapter: ScheduleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ScheduleRepository(this)

        adapter = ScheduleAdapter(
            onToggle = { schedule, enabled -> onToggleSchedule(schedule, enabled) },
            onClick = { schedule -> openEditor(schedule.id) }
        )
        binding.recyclerSchedules.layoutManager = LinearLayoutManager(this)
        binding.recyclerSchedules.adapter = adapter

        binding.fabAdd.setOnClickListener { openEditor(null) }
        binding.bannerAccessibilityAction.setOnClickListener { openAccessibilitySettings() }
        binding.bannerAlarmAction.setOnClickListener { openExactAlarmSettings() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        updateBanners()
    }

    private fun refreshList() {
        val schedules = repository.getAll().sortedWith(compareBy({ it.hour }, { it.minute }))
        adapter.submitList(schedules)
        binding.emptyState.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onToggleSchedule(schedule: ScheduledMessage, enabled: Boolean) {
        schedule.enabled = enabled
        repository.save(schedule)
        if (enabled) {
            AlarmScheduler.scheduleNext(this, schedule)
        } else {
            AlarmScheduler.cancel(this, schedule.id)
        }
    }

    private fun openEditor(scheduleId: Long?) {
        val intent = Intent(this, AddEditScheduleActivity::class.java)
        if (scheduleId != null) intent.putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
        startActivity(intent)
    }

    private fun updateBanners() {
        binding.bannerAccessibility.visibility = if (isAccessibilityServiceEnabled()) View.GONE else View.VISIBLE

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val needsAlarmPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()
        binding.bannerAlarm.visibility = if (needsAlarmPermission) View.VISIBLE else View.GONE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${ClaudeAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedComponent, ignoreCase = true)) return true
        }
        return false
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }
}
