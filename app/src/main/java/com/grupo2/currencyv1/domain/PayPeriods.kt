package com.grupo2.currencyv1.domain

import java.time.LocalDate
import java.time.YearMonth

/** Un periodo del presupuesto: desde una acreditación hasta el día antes de la siguiente. */
data class PayPeriodRange(val start: LocalDate, val end: LocalDate)

/**
 * RC-09: el periodo en curso empieza en la última acreditación y termina el día antes de la siguiente.
 * Ejemplo con días 15 y 30, hoy 25-nov: del 15-nov al 29-nov.
 * Si no hay días configurados, usa el mes calendario.
 */
fun currentPayPeriod(payDays: List<Int>, today: LocalDate): PayPeriodRange {
    val month = YearMonth.from(today)
    if (payDays.isEmpty()) return PayPeriodRange(month.atDay(1), month.atEndOfMonth())

    // Fechas de acreditación del mes anterior, el actual y el siguiente (sin repetidas).
    val dates = (-1L..1L)
        .flatMap { offset -> payDays.map { resolvePayDate(month.plusMonths(offset), it) } }
        .toSortedSet()
    val start = dates.last { it <= today }
    val next = dates.first { it > start }
    return PayPeriodRange(start, next.minusDays(1))
}
