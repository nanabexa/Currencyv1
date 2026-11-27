package com.grupo2.currencyv1.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.grupo2.currencyv1.CurrencyApplication

/**
 * Proceso en segundo plano: aplica los descuentos de gastos fijos que ya llegaron, cierra los
 * periodos terminados y revisa si hay que avisar. Funciona sin internet (RNF-05).
 */
class PeriodicChecksWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CurrencyApplication).container
        container.runBackgroundChecks()
        return Result.success()
    }
}
