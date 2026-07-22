package com.arthurfontana.claudescheduler.alarm

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Rede de segurança: o AlarmManager por si só é confiável, mas alarmes exatos podem
 * ser silenciosamente revogados pelo sistema (ex.: usuário desativa a permissão, ou
 * o app é atualizado). Este worker roda periodicamente e reagenda tudo que estiver ativo.
 */
class RescheduleWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        AlarmScheduler.scheduleAll(applicationContext)
        return Result.success()
    }
}
