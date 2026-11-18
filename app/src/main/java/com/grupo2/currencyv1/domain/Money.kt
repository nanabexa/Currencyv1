package com.grupo2.currencyv1.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Dinero en centavos. B/. 440.00 se guarda como 44000 (RNF-01). */
@JvmInline
value class Money(val centavos: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(centavos + other.centavos)
    operator fun minus(other: Money) = Money(centavos - other.centavos)
    operator fun times(factor: Int) = Money(centavos * factor)

    /** Divide entre un entero con redondeo HALF_UP (RC-02). */
    fun dividedBy(divisor: Int): Money =
        Money(
            BigDecimal(centavos)
                .divide(BigDecimal(divisor), 0, RoundingMode.HALF_UP)
                .longValueExact()
        )

    override fun compareTo(other: Money): Int = centavos.compareTo(other.centavos)

    /** Siempre con exactamente 2 decimales: "440.00". */
    override fun toString(): String = BigDecimal.valueOf(centavos, 2).toPlainString()

    companion object {
        val ZERO = Money(0)
    }
}

enum class MoneyError { EMPTY, NOT_NUMERIC, BELOW_MINIMUM, TOO_MANY_DECIMALS, TOO_LARGE }

sealed interface MoneyResult {
    data class Ok(val money: Money) : MoneyResult
    data class Error(val reason: MoneyError) : MoneyResult
}

private val MONEY_FORMAT = Regex("""-?\d+(\.\d+)?""")
private val MINIMUM_AMOUNT = BigDecimal("0.01")

/**
 * Convierte el texto que escribe el usuario en dinero.
 * - Solo acepta punto decimal (la coma se rechaza como no numérica).
 * - Mínimo B/. 0.01 (RN-02).
 * - Más de 2 decimales se rechaza, no se redondea (RC-03).
 */
fun parseMoney(input: String): MoneyResult {
    val text = input.trim()
    if (text.isEmpty()) return MoneyResult.Error(MoneyError.EMPTY)
    if (!MONEY_FORMAT.matches(text)) return MoneyResult.Error(MoneyError.NOT_NUMERIC)

    val value = BigDecimal(text)
    if (value.scale() > 2) return MoneyResult.Error(MoneyError.TOO_MANY_DECIMALS)
    if (value < MINIMUM_AMOUNT) return MoneyResult.Error(MoneyError.BELOW_MINIMUM)

    return try {
        MoneyResult.Ok(Money(value.movePointRight(2).longValueExact()))
    } catch (e: ArithmeticException) {
        // Protección técnica (el monto no cabe en un Long). No es un requisito del documento.
        MoneyResult.Error(MoneyError.TOO_LARGE)
    }
}
