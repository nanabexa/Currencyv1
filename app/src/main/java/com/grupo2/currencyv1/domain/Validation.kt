package com.grupo2.currencyv1.domain

enum class FieldError {
    NAME_EMPTY,
    AMOUNT_EMPTY,
    AMOUNT_NOT_NUMERIC,
    AMOUNT_BELOW_MINIMUM,
    AMOUNT_TOO_MANY_DECIMALS,
    AMOUNT_TOO_LARGE,
    PERIOD_MISSING,
    PERIOD_MISMATCH,
    PAY_DAYS_INVALID_COUNT,
    PAY_DAY_OUT_OF_RANGE,
    PAY_DAYS_DUPLICATED,
    CATEGORY_MISSING,
    FREQUENCY_MISSING
}

sealed interface Validation<out T> {
    data class Valid<T>(val value: T) : Validation<T>
    data class Invalid(val errors: List<FieldError>) : Validation<Nothing>
}

data class SalaryDraft(
    val name: String,
    val amount: Money,
    val period: PaymentPeriod,
    val payDays: List<Int>
)

data class ExtraIncomeDraft(val name: String, val amount: Money)

data class FixedExpenseDraft(
    val categoryId: Long,
    val amount: Money,
    val frequency: PaymentPeriod,
    val payDays: List<Int>
)

data class VariableExpenseDraft(val categoryId: Long, val amount: Money)

private fun MoneyError.toFieldError(): FieldError = when (this) {
    MoneyError.EMPTY -> FieldError.AMOUNT_EMPTY
    MoneyError.NOT_NUMERIC -> FieldError.AMOUNT_NOT_NUMERIC
    MoneyError.BELOW_MINIMUM -> FieldError.AMOUNT_BELOW_MINIMUM
    MoneyError.TOO_MANY_DECIMALS -> FieldError.AMOUNT_TOO_MANY_DECIMALS
    MoneyError.TOO_LARGE -> FieldError.AMOUNT_TOO_LARGE
}

private fun PayDayError.toFieldError(): FieldError = when (this) {
    PayDayError.WRONG_COUNT -> FieldError.PAY_DAYS_INVALID_COUNT
    PayDayError.OUT_OF_RANGE -> FieldError.PAY_DAY_OUT_OF_RANGE
    PayDayError.DUPLICATED -> FieldError.PAY_DAYS_DUPLICATED
}

/** Lee el monto; si es inválido agrega el error y devuelve null. */
private fun readAmount(text: String, errors: MutableList<FieldError>): Money? =
    when (val result = parseMoney(text)) {
        is MoneyResult.Ok -> result.money
        is MoneyResult.Error -> {
            errors += result.reason.toFieldError()
            null
        }
    }

/**
 * CU-01: registro de un salario.
 * [existingPeriod] es el periodo ya definido por salarios anteriores (null si es el primero).
 * Todas las fuentes deben compartir el mismo periodo (RN-04).
 */
fun validateSalary(
    name: String,
    amountText: String,
    period: PaymentPeriod?,
    payDays: List<Int>,
    existingPeriod: PaymentPeriod?
): Validation<SalaryDraft> {
    val errors = mutableListOf<FieldError>()
    if (name.isBlank()) errors += FieldError.NAME_EMPTY
    val amount = readAmount(amountText, errors)
    if (period == null) {
        errors += FieldError.PERIOD_MISSING
    } else {
        if (existingPeriod != null && existingPeriod != period) errors += FieldError.PERIOD_MISMATCH
        validatePayDays(period, payDays)?.let { errors += it.toFieldError() }
    }

    return if (errors.isNotEmpty() || amount == null || period == null) {
        Validation.Invalid(errors)
    } else {
        Validation.Valid(SalaryDraft(name.trim(), amount, period, payDays))
    }
}

/** CU-01, flujo alterno 1a: ingreso extra (nombre y monto). */
fun validateExtraIncome(name: String, amountText: String): Validation<ExtraIncomeDraft> {
    val errors = mutableListOf<FieldError>()
    if (name.isBlank()) errors += FieldError.NAME_EMPTY
    val amount = readAmount(amountText, errors)

    return if (errors.isNotEmpty() || amount == null) {
        Validation.Invalid(errors)
    } else {
        Validation.Valid(ExtraIncomeDraft(name.trim(), amount))
    }
}

/** CU-02: gasto fijo (categoría, monto, frecuencia y día(s) de descuento). */
fun validateFixedExpense(
    categoryId: Long?,
    amountText: String,
    frequency: PaymentPeriod?,
    payDays: List<Int>
): Validation<FixedExpenseDraft> {
    val errors = mutableListOf<FieldError>()
    if (categoryId == null) errors += FieldError.CATEGORY_MISSING
    val amount = readAmount(amountText, errors)
    if (frequency == null) {
        errors += FieldError.FREQUENCY_MISSING
    } else {
        validatePayDays(frequency, payDays)?.let { errors += it.toFieldError() }
    }

    return if (errors.isNotEmpty() || categoryId == null || amount == null || frequency == null) {
        Validation.Invalid(errors)
    } else {
        Validation.Valid(FixedExpenseDraft(categoryId, amount, frequency, payDays))
    }
}

/** CU-02: gasto variable (categoría y monto; la fecha es automática). */
fun validateVariableExpense(categoryId: Long?, amountText: String): Validation<VariableExpenseDraft> {
    val errors = mutableListOf<FieldError>()
    if (categoryId == null) errors += FieldError.CATEGORY_MISSING
    val amount = readAmount(amountText, errors)

    return if (errors.isNotEmpty() || categoryId == null || amount == null) {
        Validation.Invalid(errors)
    } else {
        Validation.Valid(VariableExpenseDraft(categoryId, amount))
    }
}
