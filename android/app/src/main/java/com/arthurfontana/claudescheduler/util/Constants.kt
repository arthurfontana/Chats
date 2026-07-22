package com.arthurfontana.claudescheduler.util

object Constants {
    /**
     * Nomes de pacote candidatos para o app oficial do Claude. Ajuste esta lista
     * se o pacote instalado no seu aparelho for diferente (verifique em
     * Configurações > Apps > Claude > detalhes do app).
     */
    val CLAUDE_PACKAGE_NAMES = listOf(
        "com.anthropic.claude"
    )

    const val PREFS_NAME = "claude_scheduler_prefs"
    const val KEY_SCHEDULES = "schedules_json"

    const val PENDING_PREFS_NAME = "claude_scheduler_pending"
    const val KEY_PENDING_MESSAGE = "pending_message"
    const val KEY_PENDING_TIMESTAMP = "pending_timestamp"

    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"

    const val WORK_NAME_RESYNC = "claude_scheduler_resync"

    /** Mensagens pendentes mais velhas que isso são descartadas (evita disparo tardio). */
    const val PENDING_MESSAGE_MAX_AGE_MS = 5 * 60 * 1000L

    /** Espera após a janela do Claude aparecer antes de tentar digitar (deixa a UI assentar). */
    const val INJECT_DELAY_MS = 900L
}
