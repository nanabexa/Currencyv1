package com.grupo2.currencyv1.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class HistoryView { HOY, SEMANAL, MENSUAL, ANUAL }

/**
 * CU-06, RN-04: rango del periodo actual de cada vista.
 * La semana va de domingo a sábado; el mes y el año son los calendarios en curso.
 */
fun rangeFor(view: HistoryView, today: LocalDate): ClosedRange<LocalDate> = when (view) {
    HistoryView.HOY -> today..today
    HistoryView.SEMANAL -> {
        val sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        sunday..sunday.plusDays(6)
    }
    HistoryView.MENSUAL ->
        today.withDayOfMonth(1)..today.with(TemporalAdjusters.lastDayOfMonth())
    HistoryView.ANUAL ->
        LocalDate.of(today.year, 1, 1)..LocalDate.of(today.year, 12, 31)
}

/** RN-05: un gasto aparece en una vista si su fecha cae en el periodo actual de esa vista. */
fun isInView(date: LocalDate, view: HistoryView, today: LocalDate): Boolean =
    date in rangeFor(view, today)
