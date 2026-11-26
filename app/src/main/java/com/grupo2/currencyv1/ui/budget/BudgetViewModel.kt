package com.grupo2.currencyv1.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo2.currencyv1.data.BudgetRepository
import com.grupo2.currencyv1.data.SaveBudgetResult
import com.grupo2.currencyv1.domain.BudgetBreakdown
import com.grupo2.currencyv1.domain.BudgetError
import com.grupo2.currencyv1.domain.BudgetResult
import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.Percent
import com.grupo2.currencyv1.domain.PercentError
import com.grupo2.currencyv1.domain.PercentResult
import com.grupo2.currencyv1.domain.calculateBudget
import com.grupo2.currencyv1.domain.maxAhorroPercent
import com.grupo2.currencyv1.domain.parsePercent
import com.grupo2.currencyv1.domain.percentOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FormMessage(val text: String, val isError: Boolean)

data class BudgetUiState(
    val loaded: Boolean = false,
    val hasSalary: Boolean = false,
    val salarioTotal: Money = Money.ZERO,
    val fijos: Money = Money.ZERO,
    val fijosPercent: Percent = Percent.ZERO,
    /** Lo que queda del salario después de los gastos fijos (CU-04, paso 2). */
    val remaining: Money = Money.ZERO,
    val remainingPercent: Percent = Percent.ZERO,
    /** true si los fijos igualan o superan el salario: no se puede configurar (excepción 6g). */
    val fijosBlock: Boolean = false,
    val maxAhorro: Percent? = null,
    val isConfigured: Boolean = false,
    val ahorroText: String = "",
    /** Vista previa en tiempo real (CU-04, paso 4); null si el texto aún no es un ahorro válido. */
    val preview: BudgetBreakdown? = null,
    val message: FormMessage? = null
)

class BudgetViewModel(private val repository: BudgetRepository) : ViewModel() {

    private val ahorroText = MutableStateFlow("")
    private val message = MutableStateFlow<FormMessage?>(null)

    val uiState: StateFlow<BudgetUiState> = combine(
        repository.observeSetup(),
        ahorroText,
        message
    ) { setup, text, msg ->
        if (!setup.hasSalary) {
            BudgetUiState(loaded = true, ahorroText = text, message = msg)
        } else {
            val max = maxAhorroPercent(setup.salarioTotal, setup.fijos)
            val remaining =
                if (max == null) Money.ZERO else setup.salarioTotal - setup.fijos
            val preview = (parsePercent(text) as? PercentResult.Ok)?.let { parsed ->
                (calculateBudget(setup.salarioTotal, setup.fijos, parsed.percent) as? BudgetResult.Ok)?.breakdown
            }
            BudgetUiState(
                loaded = true,
                hasSalary = true,
                salarioTotal = setup.salarioTotal,
                fijos = setup.fijos,
                fijosPercent = percentOf(setup.fijos, setup.salarioTotal),
                remaining = remaining,
                remainingPercent = percentOf(remaining, setup.salarioTotal),
                fijosBlock = max == null,
                maxAhorro = max,
                isConfigured = setup.savedAhorro != null,
                ahorroText = text,
                preview = preview,
                message = msg
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    init {
        // CU-04, flujo alterno 2a: si ya hay presupuesto, se muestra su porcentaje actual.
        viewModelScope.launch {
            repository.savedAhorro()?.let { if (ahorroText.value.isEmpty()) ahorroText.value = it.toString() }
        }
    }

    fun onAhorroChange(value: String) {
        ahorroText.value = value
        message.value = null
    }

    /** CU-04, pasos 5 y 6. */
    fun save() {
        when (val parsed = parsePercent(ahorroText.value)) {
            is PercentResult.Error -> message.value = FormMessage(percentMessage(parsed.reason), isError = true)
            is PercentResult.Ok -> viewModelScope.launch {
                message.value = when (val result = repository.saveBudget(parsed.percent)) {
                    SaveBudgetResult.Saved -> FormMessage("Presupuesto guardado.", isError = false)
                    SaveBudgetResult.NoSalary ->
                        FormMessage("Primero registra tu salario en la pestaña Ingresos.", isError = true)
                    is SaveBudgetResult.Rejected -> FormMessage(budgetMessage(result), isError = true)
                }
            }
        }
    }

    private fun percentMessage(reason: PercentError): String = when (reason) {
        PercentError.EMPTY -> "Escribe el porcentaje de ahorro."
        PercentError.NOT_NUMERIC -> "El porcentaje debe ser un número. Usa punto para los decimales."
        PercentError.NEGATIVE -> "El porcentaje no puede ser negativo."
        PercentError.TOO_MANY_DECIMALS -> "Usa máximo 2 decimales."
        PercentError.ABOVE_100 -> "El porcentaje no puede pasar de 100."
    }

    private fun budgetMessage(result: SaveBudgetResult.Rejected): String = when (result.reason) {
        BudgetError.FIJOS_IGUAL_O_MAYOR_AL_SALARIO ->
            "Tus gastos fijos son iguales o mayores a tu salario. " +
                "Corrígelos en la pestaña Gastos antes de configurar el presupuesto."
        BudgetError.AHORRO_EXCEDE_MAXIMO ->
            "El ahorro máximo permitido es ${result.maxAhorro ?: Percent.ZERO} %."
    }
}
