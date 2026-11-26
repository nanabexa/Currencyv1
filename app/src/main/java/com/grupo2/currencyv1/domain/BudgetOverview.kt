package com.grupo2.currencyv1.domain

/** Todo lo que muestra la pantalla principal (CU-10) para el periodo en curso. */
data class BudgetOverview(
    val salarioTotal: Money,
    val period: PaymentPeriod,
    val range: PayPeriodRange,
    val fijos: Money,
    val fijosPercent: Percent,
    /** null si todavía no hay presupuesto configurado (o los fijos igualan al salario). */
    val budget: BudgetBreakdown?,
    val extras: Money,
    val gastosVariables: Money,
    /** Presupuesto disponible = presupuesto total + extras del periodo - gastos variables del periodo. */
    val disponible: Money?,
    /** Sobrantes de periodos anteriores que pasaron al ahorro (RC-09). */
    val ahorroAcumulado: Money
) {
    /** true si se gastó más que el presupuesto total (CU-10, excepción 2e). */
    val excedido: Boolean get() = disponible != null && disponible < Money.ZERO
}

/**
 * Arma el resumen del periodo. [salarios] no puede estar vacío.
 * Presupuesto total (CU-10, RN-01) = salario total - fijos - ahorro, que es budget.variables.
 */
fun buildOverview(
    salarios: List<Money>,
    period: PaymentPeriod,
    range: PayPeriodRange,
    fixed: List<FixedExpenseAmount>,
    ahorro: Percent?,
    extras: Money,
    gastosVariables: Money,
    ahorroAcumulado: Money
): BudgetOverview {
    val salarioTotal = salarios.fold(Money.ZERO) { total, s -> total + s }
    val fijos = totalFixedExpenses(fixed, period)
    val budget = ahorro?.let { (calculateBudget(salarioTotal, fijos, it) as? BudgetResult.Ok)?.breakdown }

    return BudgetOverview(
        salarioTotal = salarioTotal,
        period = period,
        range = range,
        fijos = fijos,
        fijosPercent = percentOf(fijos, salarioTotal),
        budget = budget,
        extras = extras,
        gastosVariables = gastosVariables,
        disponible = budget?.let { it.variables + extras - gastosVariables },
        ahorroAcumulado = ahorroAcumulado
    )
}
