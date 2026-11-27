package com.grupo2.currencyv1

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.grupo2.currencyv1.data.AppDatabase
import com.grupo2.currencyv1.data.BudgetRepository
import com.grupo2.currencyv1.data.ExpenseRepository
import com.grupo2.currencyv1.data.HistoryRepository
import com.grupo2.currencyv1.data.IncomeRepository
import com.grupo2.currencyv1.notifications.AlertEngine
import com.grupo2.currencyv1.notifications.AndroidNotifier
import com.grupo2.currencyv1.notifications.Notifier
import com.grupo2.currencyv1.work.PeriodicChecksWorker
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Crea las dependencias a mano (sin Hilt) para que sean fáciles de reemplazar en pruebas.
 * El [clock] es la única fuente de "hoy" de la app.
 */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val clock: Clock = Clock.systemDefaultZone()

    val incomeRepository = IncomeRepository(
        dao = database.ingresoDao(),
        gastoFijoDao = database.gastoFijoDao(),
        presupuestoDao = database.presupuestoDao(),
        clock = clock
    )

    val expenseRepository = ExpenseRepository(
        categoriaDao = database.categoriaDao(),
        ingresoDao = database.ingresoDao(),
        gastoFijoDao = database.gastoFijoDao(),
        gastoDao = database.gastoDao(),
        presupuestoDao = database.presupuestoDao(),
        clock = clock
    )

    val budgetRepository = BudgetRepository(
        ingresoDao = database.ingresoDao(),
        gastoFijoDao = database.gastoFijoDao(),
        gastoDao = database.gastoDao(),
        presupuestoDao = database.presupuestoDao(),
        clock = clock
    )

    val historyRepository = HistoryRepository(database.gastoDao(), clock)

    val notifier: Notifier = AndroidNotifier(context.applicationContext)

    val alertEngine = AlertEngine(
        presupuestoDao = database.presupuestoDao(),
        notificacionDao = database.notificacionDao(),
        gastoDao = database.gastoDao(),
        categoriaDao = database.categoriaDao(),
        notifier = notifier
    )

    /**
     * Descuentos de gastos fijos que ya llegaron (con su aviso) y cierre de periodos terminados.
     * Se ejecuta al abrir la app y desde el proceso en segundo plano.
     */
    suspend fun runPeriodicChecks() {
        val applied = expenseRepository.processPending()
        alertEngine.notifyAppliedFixedExpenses(applied)
        budgetRepository.closeElapsedPeriods()
    }

    /** Lo mismo, más una revisión del presupuesto por si hay que avisar (se usa sin la app abierta). */
    suspend fun runBackgroundChecks() {
        runPeriodicChecks()
        alertEngine.evaluate(budgetRepository.observeOverview().first())
    }
}

class CurrencyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        schedulePeriodicChecks()
    }

    /**
     * Programa la revisión en segundo plano cada 6 horas. Android decide la hora exacta para
     * ahorrar batería, así que un aviso puede llegar con algo de retraso.
     */
    private fun schedulePeriodicChecks() {
        val request = PeriodicWorkRequestBuilder<PeriodicChecksWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic-checks",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
