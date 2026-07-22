package com.arthurfontana.claudescheduler.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * [daysOfWeek] usa as constantes de [java.util.Calendar] (SUNDAY=1 .. SATURDAY=7).
 * Vazio significa "envio único" — depois de disparar, o agendamento é desativado.
 */
data class ScheduledMessage(
    val id: Long,
    var hour: Int,
    var minute: Int,
    var message: String,
    var daysOfWeek: Set<Int> = emptySet(),
    var enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("message", message)
        put("days", JSONArray(daysOfWeek.toList()))
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): ScheduledMessage {
            val daysArray = json.optJSONArray("days") ?: JSONArray()
            val days = mutableSetOf<Int>()
            for (i in 0 until daysArray.length()) {
                days.add(daysArray.getInt(i))
            }
            return ScheduledMessage(
                id = json.getLong("id"),
                hour = json.getInt("hour"),
                minute = json.getInt("minute"),
                message = json.getString("message"),
                daysOfWeek = days,
                enabled = json.optBoolean("enabled", true)
            )
        }
    }
}
