package com.grupo2.currencyv1.data

import com.grupo2.currencyv1.domain.ExpenseType
import com.grupo2.currencyv1.domain.FixedExpenseAmount
import com.grupo2.currencyv1.domain.FixedExpenseDraft
import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.Percent
import com.grupo2.currencyv1.domain.VariableExpenseDraft
import com.grupo2.currencyv1.domain.amountOfPercent
import com.grupo2.currencyv1.domain.dueDeductions
import com.grupo2.currencyv1.domain.normalizeToPeriod
import com.grupo2.currencyv1.domain.remainingForFixed
import com.grupo2.currencyv1.domain.totalFixedExpenses
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

enum class ExpenseRejection { NO_SALARY, NO_BUDGET, FIXED_EXCEEDS_SALARY }

sealed interface AddExpenseResult {
    data object Saved : AddExpenseResult

    /** [maxFixed]: lo que aún cabe en gastos fijos por periodo de pago (solo en FIXED_EXCEEDS_SALARY). */
    data class Rejected(val reason: ExpenseRejection, val maxFixed: Money? = null) : AddExpenseResult
}

/** Gastos fijos y variables (CU-02) y sus categorías (CU-03). */
class ExpenseRepository(
    private val categoriaDao: CategoriaDao,
    private val ingresoDao: IngresoDao,
    private val gastoFijoDao: GastoFijoDao,
    private val gastoDao: GastoDao,
    private val presupuestoDao: PresupuestoDao,
    private val clock: Clock
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun observeCategories(): Flow<List<CategoriaEntity>> = categoriaDao.observeAll()

    fun observeFixedRules(): Flow<List<GastoFijoEntity>> = gastoFijoDao.observeVigentes(today())

    /** Gastos del mes en curso (variables y descuentos de fijos), del más reciente al más antiguo. */
    fun observeMonthExpenses(): Flow<List<GastoConCategoria>> {
        val month = YearMonth.from(today())
        return gastoDao.observeBetween(month.atDay(1), month.atEndOfMonth())
    }

    fun observeHasBudget(): Flow<Boolean> = presupuestoDao.observe().map { it != null }

    fun observeHasSalary(): Flow<Boolean> =
        ingresoDao.observeSalariosVigentes(today()).map { it.isNotEmpty() }

    /** CU-02 con gasto variable: requiere salario y presupuesto; la fecha es la de hoy. */
    suspend fun addVariable(draft: VariableExpenseDraft): AddExpenseResult {
        if (ingresoDao.getSalariosVigentes(today()).isEmpty()) {
            return AddExpenseResult.Rejected(ExpenseRejection.NO_SALARY)
        }
        if (presupuestoDao.get() == null) {
            return AddExpenseResult.Rejected(ExpenseRejection.NO_BUDGET)
        }
        gastoDao.insert(
            GastoEntity(
                tipo = ExpenseType.VARIABLE,
                categoriaId = draft.categoryId,
                montoCentavos = draft.amount.centavos,
                fecha = today()
            )
        )
        return AddExpenseResult.Saved
    }

    /**
     * CU-02 con gasto fijo: guarda la regla y aplica de inmediato los descuentos que ya correspondan
     * (por ejemplo, si el día de descuento es hoy). Rechaza si fijos + ahorro superarían el salario (7h).
     */
    suspend fun addFixed(draft: FixedExpenseDraft): AddExpenseResult {
        val hoy = today()
        val salaries = ingresoDao.getSalariosVigentes(hoy)
        val period = salaries.firstOrNull()?.periodo
            ?: return AddExpenseResult.Rejected(ExpenseRejection.NO_SALARY)

        val salarioTotal = salaries.fold(Money.ZERO) { total, s -> total + Money(s.montoCentavos) }
        val fijosActuales = totalFixedExpenses(
            gastoFijoDao.getVigentes(hoy).map { FixedExpenseAmount(Money(it.montoCentavos), it.frecuencia) },
            period
        )
        val ahorro = presupuestoDao.get()
            ?.let { amountOfPercent(salarioTotal, Percent(it.ahorroCentesimas)) }
            ?: Money.ZERO

        val nuevo = normalizeToPeriod(draft.amount, draft.frequency, period)
        val disponible = remainingForFixed(salarioTotal, fijosActuales, ahorro)
        if (nuevo > disponible) {
            return AddExpenseResult.Rejected(ExpenseRejection.FIXED_EXCEEDS_SALARY, disponible)
        }

        gastoFijoDao.insert(
            GastoFijoEntity(
                categoriaId = draft.categoryId,
                montoCentavos = draft.amount.centavos,
                frecuencia = draft.frequency,
                diasDescuento = draft.payDays,
                vigenteDesde = hoy
            )
        )
        processPending()
        return AddExpenseResult.Saved
    }

    /**
     * "Procesar pendientes" (CU-02, flujo 8a): para cada gasto fijo registra los descuentos cuya
     * fecha ya llegó. Es seguro llamarla muchas veces: el índice único de la tabla gastos evita
     * duplicados. Devuelve los ids de los descuentos nuevos (la iteración 8 los usará para avisar).
     */
    suspend fun processPending(): List<Long> {
        val hoy = today()
        val applied = mutableListOf<Long>()
        for (rule in gastoFijoDao.getAll()) {
            val hasta = rule.vigenteHasta?.let { minOf(it, hoy) } ?: hoy
            for (due in dueDeductions(rule.diasDescuento, rule.vigenteDesde, hasta)) {
                val id = gastoDao.insertIfAbsent(
                    GastoEntity(
                        tipo = ExpenseType.FIJO,
                        categoriaId = rule.categoriaId,
                        montoCentavos = rule.montoCentavos,
                        fecha = due.date,
                        gastoFijoId = rule.id,
                        diaProgramado = due.day
                    )
                )
                if (id > 0) applied += id
            }
        }
        return applied
    }

    // ---------------- CU-08: modificar y eliminar ----------------

    /** Gasto variable: el cambio se aplica de inmediato. */
    suspend fun updateVariable(id: Long, draft: VariableExpenseDraft): ChangeResult {
        val gasto = gastoDao.getById(id)?.takeIf { it.tipo == ExpenseType.VARIABLE }
            ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)
        gastoDao.update(gasto.copy(categoriaId = draft.categoryId, montoCentavos = draft.amount.centavos))
        return ChangeResult.Saved
    }

    suspend fun deleteVariable(id: Long): ChangeResult {
        val gasto = gastoDao.getById(id)?.takeIf { it.tipo == ExpenseType.VARIABLE }
            ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)
        gastoDao.delete(gasto)
        return ChangeResult.Saved
    }

    /**
     * Gasto fijo: el cambio rige desde [effectiveFrom] (RC-10). La regla actual se cierra el día
     * anterior y se crea otra con los datos nuevos, así los descuentos ya aplicados se conservan.
     */
    suspend fun updateFixed(ruleId: Long, draft: FixedExpenseDraft, effectiveFrom: LocalDate): ChangeResult {
        val rule = gastoFijoDao.getById(ruleId) ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)
        if (rule.vigenteHasta != null) return ChangeResult.Rejected(ChangeRejection.CHANGE_ALREADY_SCHEDULED)

        val hoy = today()
        val salaries = ingresoDao.getSalariosVigentes(hoy)
        val period = salaries.firstOrNull()?.periodo
            ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)

        // CU-02, excepción 7h: fijos (sin contar esta regla) + el nuevo monto + ahorro <= salario.
        val salarioTotal = salaries.fold(Money.ZERO) { total, s -> total + Money(s.montoCentavos) }
        val fijosOtros = totalFixedExpenses(
            gastoFijoDao.getVigentes(hoy)
                .filter { it.id != ruleId }
                .map { FixedExpenseAmount(Money(it.montoCentavos), it.frecuencia) },
            period
        )
        val ahorro = presupuestoDao.get()
            ?.let { amountOfPercent(salarioTotal, Percent(it.ahorroCentesimas)) }
            ?: Money.ZERO
        val nuevo = normalizeToPeriod(draft.amount, draft.frequency, period)
        val disponible = remainingForFixed(salarioTotal, fijosOtros, ahorro)
        if (nuevo > disponible) {
            return ChangeResult.Rejected(ChangeRejection.FIXED_EXCEEDS_SALARY, maxFixed = disponible)
        }

        val start = replacementStart(rule, effectiveFrom)
        gastoFijoDao.update(rule.copy(vigenteHasta = start.minusDays(1)))
        gastoFijoDao.insert(
            GastoFijoEntity(
                categoriaId = draft.categoryId,
                montoCentavos = draft.amount.centavos,
                frecuencia = draft.frequency,
                diasDescuento = draft.payDays,
                vigenteDesde = start
            )
        )
        processPending()
        return ChangeResult.Saved
    }

    /** Eliminar un gasto fijo: deja de aplicarse desde [effectiveFrom]; el historial se conserva. */
    suspend fun deleteFixed(ruleId: Long, effectiveFrom: LocalDate): ChangeResult {
        val rule = gastoFijoDao.getById(ruleId) ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)
        if (rule.vigenteHasta != null) return ChangeResult.Rejected(ChangeRejection.CHANGE_ALREADY_SCHEDULED)

        val start = replacementStart(rule, effectiveFrom)
        gastoFijoDao.update(rule.copy(vigenteHasta = start.minusDays(1)))
        return ChangeResult.Saved
    }

    /**
     * Si la regla ya descontó en la fecha de vigencia (solo puede pasar si es hoy), ese descuento
     * se conserva con el monto anterior y el cambio empieza al día siguiente.
     */
    private suspend fun replacementStart(rule: GastoFijoEntity, effectiveFrom: LocalDate): LocalDate =
        if (gastoDao.countAppliedOn(rule.id, effectiveFrom) > 0) effectiveFrom.plusDays(1) else effectiveFrom
}
