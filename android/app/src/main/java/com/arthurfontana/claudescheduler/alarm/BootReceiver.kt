package com.arthurfontana.claudescheduler.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarmes do AlarmManager não sobrevivem a reboot — precisam ser recriados aqui. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}
