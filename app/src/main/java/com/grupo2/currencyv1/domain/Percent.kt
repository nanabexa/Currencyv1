package com.grupo2.currencyv1.domain

import java.math.BigDecimal

/** Porcentaje en centésimas. 12.50 % se guarda como 1250; 100 % = 10000. */
@JvmInline
value class Percent(val centesimas: Int) : Comparable<Percent> {

    override fun compareTo(other: Percent): Int = centesimas.compareTo(other.centesimas)

    /** Siempre con 2 decimales: "12.50". */
    override fun toString(): String = BigDecimal.valueOf(centesimas.toLong(), 2).toPlainString()

    companion object {
        val ZERO = Percent(0)
        val HUNDRED = Percent(10_000)
    }
}

enum class PercentError { EMPTY, NOT_NUMERIC, NEGATIVE, TOO_MANY_DECIMALS, ABOVE_100 }

sealed interface PercentResult {
    data class Ok(val percent: Percent) : PercentResult
    data class Error(val reason: PercentError) : PercentResult
}

private val PERCENT_FORMAT = Regex("""-?\d+(\.\d+)?""")

/**
 * Convierte el texto del % de ahorro en un Percent (CU-04, RN-01).
 * Acepta de 0 a 100 con hasta 2 decimales. El máximo según los gastos fijos
 * se valida aparte, en calculateBudget.
 */
fun parsePercent(input: String): PercentResult {
    val text = input.trim()
    if (text.isEmpty()) return PercentResult.Error(PercentError.EMPTY)
    if (!PERCENT_FORMAT.matches(text)) return PercentResult.Error(PercentError.NOT_NUMERIC)

    val value = BigDecimal(text)
    if (value.signum() < 0) return PercentResult.Error(PercentError.NEGATIVE)
    if (value.scale() > 2) return PercentResult.Error(PercentError.TOO_MANY_DECIMALS)
    if (value > BigDecimal(100)) return PercentResult.Error(PercentError.ABOVE_100)

    return PercentResult.Ok(Percent(value.movePointRight(2).intValueExact()))
}
