package com.arthurfontana.claudescheduler.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arthurfontana.claudescheduler.data.PendingMessageStore
import com.arthurfontana.claudescheduler.data.ScheduleRepository
import com.arthurfontana.claudescheduler.util.Constants

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(Constants.EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId == -1L) return

        val repo = ScheduleRepository(context)
        val schedule = repo.getById(scheduleId) ?: return
        if (!schedule.enabled) return

        PendingMessageStore(context).setPending(schedule.message)
        launchClaudeApp(context)

        if (schedule.daysOfWeek.isEmpty()) {
            schedule.enabled = false
            repo.save(schedule)
        } else {
            AlarmScheduler.scheduleNext(context, schedule)
        }
    }

    private fun launchClaudeApp(context: Context) {
        val packageManager = context.packageManager
        val launchIntent = Constants.CLAUDE_PACKAGE_NAMES
            .firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
            ?: return

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(launchIntent)
    }
}
