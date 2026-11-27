package com.grupo2.currencyv1.data

import com.grupo2.currencyv1.domain.BudgetBreakdown
import com.grupo2.currencyv1.domain.BudgetError
import com.grupo2.currencyv1.domain.BudgetOverview
import com.grupo2.currencyv1.domain.BudgetResult
import com.grupo2.currencyv1.domain.FixedExpenseAmount
import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.PayPeriodRange
import com.grupo2.currencyv1.domain.Percent
import com.grupo2.currencyv1.domain.buildOverview
import com.grupo2.currencyv1.domain.calculateBudget
import com.grupo2.currencyv1.domain.currentPayPeriod
import com.grupo2.currencyv1.domain.totalFixedExpenses
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** Datos para la pantalla de configuración del presupuesto (CU-04, pasos 1 y 2). */
data class BudgetSetup(
    val hasSalary: Boolean = false,
    val salarioTotal: Money = Money.ZERO,
    /** Gastos fijos ya llevados al periodo de pago (RC-07). */
    val fijos: Money = Money.ZERO,
    val savedAhorro: Percent? = null
)

sealed interface SaveBudgetResult {
    data object Saved : SaveBudgetResult
    data object NoSalary : SaveBudgetResult
    data class Rejected(val reason: BudgetError, val maxAhorro: Percent?) : SaveBudgetResult
}

sealed interface SaveLimitResult {
    data object Saved : SaveLimitResult
    data object NoBudget : SaveLimitResult

    /** CU-07, excepción 4f: el límite supera el presupuesto total ([max]). */
    data class TooHigh(val max: Money) : SaveLimitResult
}

/** Presupuesto (CU-04), límite (CU-07), resumen del periodo (CU-10) y cierre de periodos (RC-09). */
class BudgetRepository(
    private val ingresoDao: IngresoDao,
    private val gastoFijoDao: GastoFijoDao,
    private val gastoDao: GastoDao,
    private val presupuestoDao: PresupuestoDao,
    private val clock: Clock
) {
    private fun today(): LocalDate = LocalDate.now(clock)

    private fun List<IngresoEntity>.total(): Money =
        fold(Money.ZERO) { total, s -> total + Money(s.montoCentavos) }

    private fun List<GastoFijoEntity>.asAmounts(): List<FixedExpenseAmount> =
        map { FixedExpenseAmount(Money(it.montoCentavos), it.frecuencia) }

    // ---------------- CU-04 ----------------

    fun observeSetup(): Flow<BudgetSetup> {
        val hoy = today()
        return combine(
            ingresoDao.observeSalariosVigentes(hoy),
            gastoFijoDao.observeVigentes(hoy),
            presupuestoDao.observe()
        ) { salaries, rules, presupuesto ->
            val period = salaries.firstOrNull()?.periodo
            if (period == null) {
                BudgetSetup()
            } else {
                BudgetSetup(
                    hasSalary = true,
                    salarioTotal = salaries.total(),
                    fijos = totalFixedExpenses(rules.asAmounts(), period),
                    savedAhorro = presupuesto?.let { Percent(it.ahorroCentesimas) }
                )
            }
        }
    }

    /** % de ahorro ya guardado, para rellenar el campo al abrir la pantalla. */
    suspend fun savedAhorro(): Percent? = presupuestoDao.get()?.let { Percent(it.ahorroCentesimas) }

    /**
     * Valida con las reglas de dominio y guarda. Al modificar un presupuesto existente se conservan
     * su límite, su ahorro acumulado y su periodo de inicio. El cambio rige de inmediato.
     */
    suspend fun saveBudget(ahorro: Percent): SaveBudgetResult {
        val hoy = today()
        val salaries = ingresoDao.getSalariosVigentes(hoy)
        val first = salaries.firstOrNull() ?: return SaveBudgetResult.NoSalary
        val period = first.periodo ?: return SaveBudgetResult.NoSalary

        val fijos = totalFixedExpenses(gastoFijoDao.getVigentes(hoy).asAmounts(), period)
        return when (val result = calculateBudget(salaries.total(), fijos, ahorro)) {
            is BudgetResult.Error -> SaveBudgetResult.Rejected(result.reason, result.maxAhorro)
            is BudgetResult.Ok -> {
                val existing = presupuestoDao.get()
                presupuestoDao.save(
                    existing?.copy(ahorroCentesimas = ahorro.centesimas)
                        ?: PresupuestoEntity(
                            ahorroCentesimas = ahorro.centesimas,
                            periodoInicio = currentPayPeriod(first.diasAcreditacion, hoy).start
                        )
                )
                SaveBudgetResult.Saved
            }
        }
    }

    // ---------------- CU-10 ----------------

    /**
     * Resumen del periodo en curso. null si no hay salario. La fecha de "hoy" se toma al llamar,
     * así que el periodo se actualiza al reabrir la app.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeOverview(): Flow<BudgetOverview?> {
        val hoy = today()
        return ingresoDao.observeSalariosVigentes(hoy).flatMapLatest<List<IngresoEntity>, BudgetOverview?> { salaries ->
            val first = salaries.firstOrNull()
            val period = first?.periodo
            if (first == null || period == null) {
                flowOf<BudgetOverview?>(null)
            } else {
                val range = currentPayPeriod(first.diasAcreditacion, hoy)
                combine(
                    gastoFijoDao.observeVigentes(hoy),
                    presupuestoDao.observe(),
                    ingresoDao.observeTotalExtras(range.start, range.end),
                    gastoDao.observeTotalVariables(range.start, range.end)
                ) { rules, presupuesto, extras, variables ->
                    buildOverview(
                        salarios = salaries.map { Money(it.montoCentavos) },
                        period = period,
                        range = range,
                        fixed = rules.asAmounts(),
                        ahorro = presupuesto?.let { Percent(it.ahorroCentesimas) },
                        extras = Money(extras),
                        gastosVariables = Money(variables),
                        ahorroAcumulado = Money(presupuesto?.ahorroAcumuladoCentavos ?: 0)
                    )
                }
            }
        }
    }

    // ---------------- CU-07: límite ----------------

    /** Límite configurado, o null si no hay. */
    fun observeLimit(): Flow<Money?> =
        presupuestoDao.observe().map { presupuesto -> presupuesto?.limiteCentavos?.let { Money(it) } }

    /**
     * Guarda el límite si no supera el presupuesto total (RN-02). Al guardar se vuelve a armar el aviso,
     * así que si el disponible ya está por debajo del límite se notifica de inmediato (flujo 4a).
     */
    suspend fun saveLimit(limit: Money): SaveLimitResult {
        val presupuesto = presupuestoDao.get() ?: return SaveLimitResult.NoBudget
        val total = observeOverview().first()?.budget?.variables ?: return SaveLimitResult.NoBudget
        if (limit > total) return SaveLimitResult.TooHigh(total)

        presupuestoDao.save(presupuesto.copy(limiteCentavos = limit.centavos, limiteNotificado = false))
        return SaveLimitResult.Saved
    }

    // ---------------- RC-09: cierre de periodos ----------------

    /**
     * Si ya empezó un periodo nuevo, cierra los anteriores: el sobrante positivo de cada uno pasa al
     * ahorro acumulado y el periodo de inicio avanza. Un disponible negativo no se arrastra.
     * Es seguro llamarla muchas veces. Se ejecuta cada vez que se abre la app.
     */
    suspend fun closeElapsedPeriods() {
        val presupuesto = presupuestoDao.get() ?: return
        val hoy = today()
        val days = ingresoDao.getSalariosVigentes(hoy).firstOrNull()?.diasAcreditacion ?: return

        val currentStart = currentPayPeriod(days, hoy).start
        var inicio = presupuesto.periodoInicio
        var acumulado = presupuesto.ahorroAcumuladoCentavos
        while (inicio < currentStart) {
            val range = currentPayPeriod(days, inicio)
            acumulado += leftoverOf(range, presupuesto).centavos
            inicio = range.end.plusDays(1)
        }

        if (inicio != presupuesto.periodoInicio) {
            presupuestoDao.save(presupuesto.copy(periodoInicio = inicio, ahorroAcumuladoCentavos = acumulado))
        }
    }

    /**
     * Sobrante de un periodo ya terminado. Usa los salarios y gastos fijos vigentes al final de ese
     * periodo (aproximación: si hubo cambios intermedios, no se reconstruyen).
     */
    private suspend fun leftoverOf(range: PayPeriodRange, presupuesto: PresupuestoEntity): Money {
        val salaries = ingresoDao.getSalariosVigentes(range.end)
        val period = salaries.firstOrNull()?.periodo ?: return Money.ZERO

        val fijos = totalFixedExpenses(gastoFijoDao.getVigentes(range.end).asAmounts(), period)
        val breakdown: BudgetBreakdown =
            (calculateBudget(salaries.total(), fijos, Percent(presupuesto.ahorroCentesimas)) as? BudgetResult.Ok)
                ?.breakdown ?: return Money.ZERO

        val extras = Money(ingresoDao.totalExtras(range.start, range.end))
        val gastado = Money(gastoDao.totalVariables(range.start, range.end))
        val disponible = breakdown.variables + extras - gastado
        return if (disponible > Money.ZERO) disponible else Money.ZERO
    }
}
