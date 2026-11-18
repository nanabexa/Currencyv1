package com.grupo2.currencyv1.domain

import java.time.LocalDate
import java.time.YearMonth

const val MIN_PAY_DAY = 1
const val MAX_PAY_DAY = 30

enum class PayDayError { WRONG_COUNT, OUT_OF_RANGE, DUPLICATED }

/**
 * Valida los días de acreditación (salario) o de descuento (gasto fijo).
 * - Mensual: 1 día. Quincenal: 2 días distintos.
 * - Cada día entre 1 y 30 (el 31 no se ofrece, RN-07).
 * Devuelve null si son válidos.
 */
fun validatePayDays(period: PaymentPeriod, days: List<Int>): PayDayError? {
    val expectedCount = if (period == PaymentPeriod.MENSUAL) 1 else 2
    if (days.size != expectedCount) return PayDayError.WRONG_COUNT
    if (days.any { it < MIN_PAY_DAY || it > MAX_PAY_DAY }) return PayDayError.OUT_OF_RANGE
    if (days.toSet().size != days.size) return PayDayError.DUPLICATED
    return null
}

/**
 * RC-08: fecha real de un día elegido en un mes concreto.
 * Si el mes tiene menos días que el elegido (febrero), se usa el último día del mes.
 */
fun resolvePayDate(month: YearMonth, day: Int): LocalDate =
    month.atDay(minOf(day, month.lengthOfMonth()))
