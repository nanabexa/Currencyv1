package com.grupo2.currencyv1.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class BudgetBreakdown(
    val salario: Money,
    val fijos: Money,
    val ahorro: Money,
    val variables: Money,
    val fijosPercent: Percent,
    val ahorroPercent: Percent,
    val variablesPercent: Percent
)

enum class BudgetError { FIJOS_IGUAL_O_MAYOR_AL_SALARIO, AHORRO_EXCEDE_MAXIMO }

sealed interface BudgetResult {
    data class Ok(val breakdown: BudgetBreakdown) : BudgetResult

    /** [maxAhorro] viene informado cuando el error es AHORRO_EXCEDE_MAXIMO, para mostrarlo al usuario. */
    data class Error(val reason: BudgetError, val maxAhorro: Percent? = null) : BudgetResult
}

private val BASIS = BigDecimal(10_000)

/** Monto que representa un porcentaje del total, redondeado HALF_UP a 2 decimales (RN-02). */
fun amountOfPercent(total: Money, percent: Percent): Money =
    Money(
        BigDecimal(total.centavos)
            .multiply(BigDecimal(percent.centesimas))
            .divide(BASIS, 0, RoundingMode.HALF_UP)
            .longValueExact()
    )

/** Porcentaje que representa una parte del total, redondeado HALF_UP. Solo para presentación (RC-06). */
fun percentOf(part: Money, total: Money): Percent =
    Percent(
        BigDecimal(part.centavos)
            .multiply(BASIS)
            .divide(BigDecimal(total.centavos), 0, RoundingMode.HALF_UP)
            .intValueExact()
    )

/**
 * Máximo % de ahorro permitido: (100 % - % de fijos), truncado a 2 decimales.
 * Devuelve null si los fijos son iguales o mayores al salario (RN-04).
 */
fun maxAhorroPercent(salario: Money, fijos: Money): Percent? {
    if (fijos >= salario) return null
    val remaining = salario - fijos
    return Percent(
        BigDecimal(remaining.centavos)
            .multiply(BASIS)
            .divide(BigDecimal(salario.centavos), 0, RoundingMode.DOWN)
            .intValueExact()
    )
}

/**
 * CU-04. Calcula la distribución del presupuesto.
 * - ahorro = salario x %ahorro (HALF_UP).
 * - variables = salario - fijos - ahorro (absorbe cualquier centavo de diferencia, RC-04).
 * - fijos + ahorro + variables = salario, siempre (RC-05).
 */
fun calculateBudget(salario: Money, fijos: Money, ahorroPercent: Percent): BudgetResult {
    val max = maxAhorroPercent(salario, fijos)
        ?: return BudgetResult.Error(BudgetError.FIJOS_IGUAL_O_MAYOR_AL_SALARIO)
    if (ahorroPercent > max) {
        return BudgetResult.Error(BudgetError.AHORRO_EXCEDE_MAXIMO, max)
    }

    val ahorro = amountOfPercent(salario, ahorroPercent)
    val variables = salario - fijos - ahorro

    return BudgetResult.Ok(
        BudgetBreakdown(
            salario = salario,
            fijos = fijos,
            ahorro = ahorro,
            variables = variables,
            fijosPercent = percentOf(fijos, salario),
            ahorroPercent = ahorroPercent,
            variablesPercent = percentOf(variables, salario)
        )
    )
}
