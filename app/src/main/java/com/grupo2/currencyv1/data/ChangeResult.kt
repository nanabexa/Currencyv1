package com.grupo2.currencyv1.data

import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.Percent

enum class ChangeRejection {
    NOT_FOUND,
    /** Un gasto fijo (nuevo o editado) haría que fijos + ahorro superen el salario (CU-02, 7h). */
    FIXED_EXCEEDS_SALARY,
    /** RC-11: no se puede eliminar el único salario. */
    ONLY_SALARY,
    /** El cambio dejaría el presupuesto inválido (fijos + ahorro mayores al salario). */
    BUDGET_DOES_NOT_FIT,
    /** Ya hay un cambio con fecha futura pendiente; hay que esperar a que entre en vigencia. */
    CHANGE_ALREADY_SCHEDULED
}

sealed interface ChangeResult {
    data object Saved : ChangeResult

    data class Rejected(
        val reason: ChangeRejection,
        val maxFixed: Money? = null,
        val maxAhorro: Percent? = null
    ) : ChangeResult
}
