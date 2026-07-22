package com.arthurfontana.claudescheduler.data

import android.content.Context
import com.arthurfontana.claudescheduler.model.ScheduledMessage
import com.arthurfontana.claudescheduler.util.Constants
import org.json.JSONArray

/** Persistência simples em SharedPreferences (JSON) — sem banco de dados, de propósito. */
class ScheduleRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<ScheduledMessage> {
        val raw = prefs.getString(Constants.KEY_SCHEDULES, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { ScheduledMessage.fromJson(array.getJSONObject(it)) }
    }

    fun getById(id: Long): ScheduledMessage? = getAll().find { it.id == id }

    fun save(schedule: ScheduledMessage) {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == schedule.id }
        if (index >= 0) all[index] = schedule else all.add(schedule)
        persist(all)
    }

    fun delete(id: Long) {
        persist(getAll().filterNot { it.id == id })
    }

    private fun persist(list: List<ScheduledMessage>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(Constants.KEY_SCHEDULES, array.toString()).apply()
    }
}
