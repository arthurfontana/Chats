package com.arthurfontana.claudescheduler.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.arthurfontana.claudescheduler.data.ScheduleRepository
import com.arthurfontana.claudescheduler.model.ScheduledMessage
import com.arthurfontana.claudescheduler.util.Constants
import java.util.Calendar

object AlarmScheduler {

    fun scheduleAll(context: Context) {
        val repo = ScheduleRepository(context)
        repo.getAll().filter { it.enabled }.forEach { scheduleNext(context, it) }
    }

    fun scheduleNext(context: Context, schedule: ScheduledMessage) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis(schedule)
        val pendingIntent = buildPendingIntent(context, schedule.id)

        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, scheduleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, scheduleId))
    }

    private fun buildPendingIntent(context: Context, scheduleId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(Constants.EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Calcula o próximo instante (estritamente no futuro) em que o agendamento deve disparar. */
    fun nextTriggerMillis(schedule: ScheduledMessage, now: Calendar = Calendar.getInstance()): Long {
        val candidate = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, schedule.hour)
            set(Calendar.MINUTE, schedule.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (schedule.daysOfWeek.isEmpty()) {
            if (candidate <= now) candidate.add(Calendar.DAY_OF_YEAR, 1)
            return candidate.timeInMillis
        }

        for (offset in 0..7) {
            val attempt = (candidate.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dayOk = schedule.daysOfWeek.contains(attempt.get(Calendar.DAY_OF_WEEK))
            if (dayOk && attempt > now) {
                return attempt.timeInMillis
            }
        }
        // Não deveria acontecer (daysOfWeek não vazio garante um match em até 7 dias).
        return candidate.apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
    }
}
