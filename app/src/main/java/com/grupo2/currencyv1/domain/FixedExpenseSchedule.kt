package com.grupo2.currencyv1.domain

import java.time.LocalDate
import java.time.YearMonth

/** Un descuento de gasto fijo que corresponde aplicar: la fecha real y el día configurado. */
data class ScheduledDeduction(val date: LocalDate, val day: Int)

/**
 * Fechas de descuento de un gasto fijo entre [from] y [to] (ambas incluidas).
 * Usa resolvePayDate, así que un día que no existe en el mes (febrero) cae en el último día (RC-08).
 * Si el teléfono estuvo apagado, devuelve también los descuentos atrasados.
 */
fun dueDeductions(days: List<Int>, from: LocalDate, to: LocalDate): List<ScheduledDeduction> {
    if (to < from) return emptyList()
    val result = mutableListOf<ScheduledDeduction>()
    var month = YearMonth.from(from)
    val lastMonth = YearMonth.from(to)
    while (month <= lastMonth) {
        for (day in days) {
            val date = resolvePayDate(month, day)
            if (date >= from && date <= to) result += ScheduledDeduction(date, day)
        }
        month = month.plusMonths(1)
    }
    return result.sortedWith(compareBy({ it.date }, { it.day }))
}

/**
 * CU-02, excepción 7h. Cuánto queda disponible para gastos fijos (ya normalizados al periodo
 * de pago): salario - fijos actuales - ahorro. Nunca negativo.
 */
fun remainingForFixed(salario: Money, fijosActuales: Money, ahorro: Money): Money {
    val remaining = salario - fijosActuales - ahorro
    return if (remaining < Money.ZERO) Money.ZERO else remaining
}
