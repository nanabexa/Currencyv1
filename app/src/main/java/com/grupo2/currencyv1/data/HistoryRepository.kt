package com.grupo2.currencyv1.data

import com.grupo2.currencyv1.domain.HistoryView
import com.grupo2.currencyv1.domain.rangeFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** Historial de gastos (CU-06): solo lectura. */
class HistoryRepository(
    private val gastoDao: GastoDao,
    private val clock: Clock
) {
    private fun today(): LocalDate = LocalDate.now(clock)

    /** Periodo actual de una vista (RN-04). Se calcula cada vez, así que respeta el cambio de día. */
    fun rangeOf(view: HistoryView): ClosedRange<LocalDate> = rangeFor(view, today())

    /**
     * Gastos del periodo actual de la vista, del más reciente al más antiguo. Incluye los descuentos
     * automáticos de gastos fijos (RN-05).
     */
    fun observe(view: HistoryView): Flow<List<GastoConCategoria>> {
        val range = rangeOf(view)
        return gastoDao.observeBetween(range.start, range.endInclusive)
    }

    /** ¿Hay al menos un gasto registrado? (precondición de CU-06). */
    fun observeHasAnyExpense(): Flow<Boolean> = gastoDao.observeCount().map { it > 0 }
}
