package com.arthurfontana.claudescheduler

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.arthurfontana.claudescheduler.alarm.AlarmScheduler
import com.arthurfontana.claudescheduler.alarm.RescheduleWorker
import com.arthurfontana.claudescheduler.util.Constants
import java.util.concurrent.TimeUnit

class SchedulerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmScheduler.scheduleAll(this)

        val periodicWork = PeriodicWorkRequestBuilder<RescheduleWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            Constants.WORK_NAME_RESYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }
}
