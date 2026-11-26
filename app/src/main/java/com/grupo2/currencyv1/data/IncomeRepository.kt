package com.grupo2.currencyv1.data

import com.grupo2.currencyv1.domain.ExtraIncomeDraft
import com.grupo2.currencyv1.domain.IncomeType
import com.grupo2.currencyv1.domain.SalaryDraft
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate

/**
 * Guarda y lee ingresos (CU-01). Recibe los datos ya validados (SalaryDraft, ExtraIncomeDraft):
 * las reglas viven en domain, aquí solo se persiste.
 * "Hoy" siempre sale del [clock] inyectado.
 */
class IncomeRepository(
    private val dao: IngresoDao,
    private val clock: Clock
) {
    private fun today(): LocalDate = LocalDate.now(clock)

    /** Salarios vigentes hoy. La fecha se toma al llamar, así que se vuelve a pedir al reabrir la pantalla. */
    fun observeSalaries(): Flow<List<IngresoEntity>> = dao.observeSalariosVigentes(today())

    fun observeExtras(): Flow<List<IngresoEntity>> = dao.observeExtras()

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
}
