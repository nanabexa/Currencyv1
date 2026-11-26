package com.grupo2.currencyv1

import android.app.Application
import android.content.Context
import com.grupo2.currencyv1.data.AppDatabase
import com.grupo2.currencyv1.data.BudgetRepository
import com.grupo2.currencyv1.data.ExpenseRepository
import com.grupo2.currencyv1.data.IncomeRepository
import java.time.Clock

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
}

class CurrencyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}