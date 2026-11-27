package com.grupo2.currencyv1.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo2.currencyv1.data.BudgetRepository
import com.grupo2.currencyv1.data.SaveLimitResult
import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.MoneyError
import com.grupo2.currencyv1.domain.MoneyResult
import com.grupo2.currencyv1.domain.parseMoney
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LimitUiState(
    val loaded: Boolean = false,
    val hasBudget: Boolean = false,
    /** Presupuesto total de variables del periodo. */
    val total: Money? = null,
    val available: Money? = null,
    val currentLimit: Money? = null,
    val limitText: String = "",
    val message: FormMessage? = null
)

/** CU-07: configurar el límite que dispara el aviso de presupuesto bajo. */
class LimitViewModel(private val repository: BudgetRepository) : ViewModel() {

    private val limitText = MutableStateFlow("")
    private val message = MutableStateFlow<FormMessage?>(null)

    val uiState: StateFlow<LimitUiState> = combine(
        repository.observeOverview(),
        repository.observeLimit(),
        limitText,
        message
    ) { overview, limit, text, msg ->
        LimitUiState(
            loaded = true,
            hasBudget = overview?.budget != null,
            total = overview?.budget?.variables,
            available = overview?.disponible,
            currentLimit = limit,
            limitText = text,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LimitUiState())

    init {
        // Flujo alterno 2a: si ya hay un límite, se muestra su valor actual para modificarlo.
        viewModelScope.launch {
            repository.observeLimit().first()?.let { if (limitText.value.isEmpty()) limitText.value = it.toString() }
        }
    }

    fun onLimitChange(value: String) {
        limitText.value = value
        message.value = null
    }

    /** CU-07, pasos 3 y 4. */
    fun save() {
        when (val parsed = parseMoney(limitText.value)) {
            is MoneyResult.Error -> message.value = FormMessage(moneyMessage(parsed.reason), isError = true)
            is MoneyResult.Ok -> viewModelScope.launch {
                message.value = when (val result = repository.saveLimit(parsed.money)) {
                    SaveLimitResult.Saved -> FormMessage("Límite guardado.", isError = false)
                    SaveLimitResult.NoBudget ->
                        FormMessage("Configura tu presupuesto antes de definir un límite.", isError = true)
                    is SaveLimitResult.TooHigh ->
                        FormMessage(
                            "El límite no puede ser mayor que tu presupuesto total (B/. ${result.max}).",
                            isError = true
                        )
                }
            }
        }
    }

    private fun moneyMessage(reason: MoneyError): String = when (reason) {
        MoneyError.EMPTY -> "Escribe el monto del límite."
        MoneyError.NOT_NUMERIC -> "El límite debe ser un número. Usa punto para los decimales."
        MoneyError.BELOW_MINIMUM -> "El límite debe ser de B/. 0.01 o más."
        MoneyError.TOO_MANY_DECIMALS -> "Usa máximo 2 decimales."
        MoneyError.TOO_LARGE -> "El monto es demasiado grande."
    }
}
