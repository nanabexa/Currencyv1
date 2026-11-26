package com.grupo2.currencyv1.data

import com.grupo2.currencyv1.domain.BudgetResult
import com.grupo2.currencyv1.domain.ExtraIncomeDraft
import com.grupo2.currencyv1.domain.FixedExpenseAmount
import com.grupo2.currencyv1.domain.IncomeType
import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.Percent
import com.grupo2.currencyv1.domain.SalaryDraft
import com.grupo2.currencyv1.domain.calculateBudget
import com.grupo2.currencyv1.domain.maxAhorroPercent
import com.grupo2.currencyv1.domain.totalFixedExpenses
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate

/**
 * Guarda, modifica y elimina ingresos (CU-01 y CU-09). Recibe los datos ya validados: las reglas
 * de formato viven en domain; aquí solo se persiste y se cuida que el presupuesto siga siendo válido.
 * "Hoy" siempre sale del [clock] inyectado.
 */
class IncomeRepository(
    private val dao: IngresoDao,
    private val gastoFijoDao: GastoFijoDao,
    private val presupuestoDao: PresupuestoDao,
    private val clock: Clock
) {
    fun today(): LocalDate = LocalDate.now(clock)

    private fun List<IngresoEntity>.total(): Money =
        fold(Money.ZERO) { total, s -> total + Money(s.montoCentavos) }

    /** Salarios vigentes hoy. La fecha se toma al llamar, así que se vuelve a pedir al reabrir la pantalla. */
    fun observeSalaries(): Flow<List<IngresoEntity>> = dao.observeSalariosVigentes(today())

    fun observeExtras(): Flow<List<IngresoEntity>> = dao.observeExtras()

    /** % de ahorro del presupuesto actual, o null si todavía no hay presupuesto. */
    suspend fun currentAhorro(): Percent? = presupuestoDao.get()?.let { Percent(it.ahorroCentesimas) }

    // ---------------- CU-01 ----------------

    suspend fun addSalary(draft: SalaryDraft): Long {
        val hoy = today()
        return dao.insert(
            IngresoEntity(
                nombre = draft.name,
                montoCentavos = draft.amount.centavos,
                tipo = IncomeType.SALARIO,
                periodo = draft.period,
                diasAcreditacion = draft.payDays,
                fecha = hoy,
                vigenteDesde = hoy
            )
        )
    }

    suspend fun addExtra(draft: ExtraIncomeDraft): Long =
        dao.insert(
            IngresoEntity(
                nombre = draft.name,
                montoCentavos = draft.amount.centavos,
                tipo = IncomeType.EXTRA,
                fecha = today()
            )
        )

    // ---------------- CU-09: ingreso extra (cambio inmediato) ----------------

    suspend fun updateExtra(id: Long, draft: ExtraIncomeDraft): ChangeResult {
        val extra = dao.getById(id)?.takeIf { it.tipo == IncomeType.EXTRA }
            ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)
        dao.update(extra.copy(nombre = draft.name, montoCentavos = draft.amount.centavos))
        return ChangeResult.Saved
    }

    suspend fun deleteExtra(id: Long): ChangeResult {
        val extra = dao.getById(id)?.takeIf { it.tipo == IncomeType.EXTRA }
            ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)
        dao.delete(extra)
        return ChangeResult.Saved
    }

    // ---------------- CU-09: salario (rige desde la fecha que elige el usuario) ----------------

    /**
     * Cambia un salario desde [effectiveFrom] (RC-10). Cada fila afectada se cierra el día anterior y
     * se crea otra con los datos nuevos, así el historial conserva lo que valía antes.
     * Si cambia el periodo o los días, se aplica a todas las fuentes (todas comparten el mismo periodo).
     * [newAhorro]: nuevo % de ahorro elegido por el usuario (null = conservar el actual). El % de
     * ahorro rige de inmediato, por eso se valida contra el salario de hoy y contra el nuevo.
     */
    suspend fun updateSalary(
        id: Long,
        draft: SalaryDraft,
        effectiveFrom: LocalDate,
        newAhorro: Percent?
    ): ChangeResult {
        val salaries = dao.getSalariosVigentes(today())
        val target = salaries.firstOrNull { it.id == id }
            ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)
        val oldPeriod = target.periodo ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)
        if (salaries.any { it.vigenteHasta != null }) {
            return ChangeResult.Rejected(ChangeRejection.CHANGE_ALREADY_SCHEDULED)
        }

        val periodChanged = draft.period != oldPeriod || draft.payDays != target.diasAcreditacion
        val updated = salaries.map { s ->
            when {
                s.id == id -> s.copy(
                    nombre = draft.name,
                    montoCentavos = draft.amount.centavos,
                    periodo = draft.period,
                    diasAcreditacion = draft.payDays
                )
                periodChanged -> s.copy(periodo = draft.period, diasAcreditacion = draft.payDays)
                else -> s
            }
        }

        // El presupuesto debe seguir siendo válido, con el salario de hoy y con el nuevo.
        val presupuesto = presupuestoDao.get()
        val percent = newAhorro ?: presupuesto?.let { Percent(it.ahorroCentesimas) }
        val rules = gastoFijoDao.getVigentes(today()).map {
            FixedExpenseAmount(Money(it.montoCentavos), it.frecuencia)
        }
        val oldTotal = salaries.total()
        val newTotal = updated.total()
        val oldFijos = totalFixedExpenses(rules, oldPeriod)
        val newFijos = totalFixedExpenses(rules, draft.period)
        if (!fits(oldTotal, oldFijos, percent) || !fits(newTotal, newFijos, percent)) {
            return ChangeResult.Rejected(
                ChangeRejection.BUDGET_DOES_NOT_FIT,
                maxAhorro = maxAhorroPercent(newTotal, newFijos)
            )
        }

        for (new in updated) {
            val old = salaries.first { it.id == new.id }
            if (new == old) continue
            dao.update(old.copy(vigenteHasta = effectiveFrom.minusDays(1)))
            dao.insert(new.copy(id = 0, vigenteDesde = effectiveFrom, vigenteHasta = null))
        }
        if (newAhorro != null && presupuesto != null) {
            presupuestoDao.save(presupuesto.copy(ahorroCentesimas = newAhorro.centesimas))
        }
        return ChangeResult.Saved
    }

    /** RC-11: no se puede eliminar el único salario. Si hay varias fuentes, se quita una desde [effectiveFrom]. */
    suspend fun deleteSalary(id: Long, effectiveFrom: LocalDate): ChangeResult {
        val salaries = dao.getSalariosVigentes(today())
        val target = salaries.firstOrNull { it.id == id }
            ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)
        if (salaries.size <= 1) return ChangeResult.Rejected(ChangeRejection.ONLY_SALARY)
        if (salaries.any { it.vigenteHasta != null }) {
            return ChangeResult.Rejected(ChangeRejection.CHANGE_ALREADY_SCHEDULED)
        }

        val remaining = salaries.filter { it.id != id }
        val period = remaining.first().periodo ?: return ChangeResult.Rejected(ChangeRejection.NOT_FOUND)
        val percent = presupuestoDao.get()?.let { Percent(it.ahorroCentesimas) }
        val fijos = totalFixedExpenses(
            gastoFijoDao.getVigentes(today()).map { FixedExpenseAmount(Money(it.montoCentavos), it.frecuencia) },
            period
        )
        if (!fits(remaining.total(), fijos, percent)) {
            return ChangeResult.Rejected(
                ChangeRejection.BUDGET_DOES_NOT_FIT,
                maxAhorro = maxAhorroPercent(remaining.total(), fijos)
            )
        }

        dao.update(target.copy(vigenteHasta = effectiveFrom.minusDays(1)))
        return ChangeResult.Saved
    }

    /** ¿El salario alcanza para los fijos y, si hay presupuesto, para el ahorro? */
    private fun fits(salario: Money, fijos: Money, ahorro: Percent?): Boolean =
        if (ahorro == null) fijos <= salario else calculateBudget(salario, fijos, ahorro) is BudgetResult.Ok
}
