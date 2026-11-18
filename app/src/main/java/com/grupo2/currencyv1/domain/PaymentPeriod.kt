package com.grupo2.currencyv1.domain

/** Periodo de pago del salario y frecuencia de los gastos fijos. */
enum class PaymentPeriod { QUINCENAL, MENSUAL }

/**
 * RC-07: lleva un gasto fijo al periodo de pago del salario.
 * - Mismo periodo: sin cambio.
 * - Gasto mensual con salario quincenal: monto / 2 (HALF_UP).
 * - Gasto quincenal con salario mensual: monto * 2.
 */
fun normalizeToPeriod(
    amount: Money,
    expenseFrequency: PaymentPeriod,
    salaryPeriod: PaymentPeriod
): Money = when {
    expenseFrequency == salaryPeriod -> amount
    expenseFrequency == PaymentPeriod.MENSUAL -> amount.dividedBy(2)
    else -> amount * 2
}

data class FixedExpenseAmount(val amount: Money, val frequency: PaymentPeriod)

/** Suma de los gastos fijos, cada uno normalizado al periodo de pago. */
fun totalFixedExpenses(
    expenses: List<FixedExpenseAmount>,
    salaryPeriod: PaymentPeriod
): Money = expenses.fold(Money.ZERO) { total, expense ->
    total + normalizeToPeriod(expense.amount, expense.frequency, salaryPeriod)
}
