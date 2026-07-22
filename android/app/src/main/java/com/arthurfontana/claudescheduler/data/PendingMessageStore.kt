package com.arthurfontana.claudescheduler.data

import android.content.Context
import com.arthurfontana.claudescheduler.util.Constants

/**
 * Canal simples entre o [android.app.AlarmManager] (que só consegue abrir o app do Claude)
 * e o AccessibilityService (que roda em processo/contexto próprio e precisa saber qual
 * mensagem digitar quando a janela do Claude aparecer).
 */
class PendingMessageStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Constants.PENDING_PREFS_NAME, Context.MODE_PRIVATE)

    fun setPending(message: String) {
        prefs.edit()
            .putString(Constants.KEY_PENDING_MESSAGE, message)
            .putLong(Constants.KEY_PENDING_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /** Lê a mensagem pendente sem removê-la, ou null se não houver uma "fresca". */
    fun peekPending(): String? {
        val message = prefs.getString(Constants.KEY_PENDING_MESSAGE, null) ?: return null
        val timestamp = prefs.getLong(Constants.KEY_PENDING_TIMESTAMP, 0L)
        val age = System.currentTimeMillis() - timestamp
        if (age !in 0..Constants.PENDING_MESSAGE_MAX_AGE_MS) {
            clear()
            return null
        }
        return message
    }

    fun hasPending(): Boolean = peekPending() != null

    fun clear() {
        prefs.edit().remove(Constants.KEY_PENDING_MESSAGE).remove(Constants.KEY_PENDING_TIMESTAMP).apply()
    }
}
