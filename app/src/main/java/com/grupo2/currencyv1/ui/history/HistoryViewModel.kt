package com.grupo2.currencyv1.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo2.currencyv1.data.HistoryRepository
import com.grupo2.currencyv1.domain.ExpenseType
import com.grupo2.currencyv1.domain.HistoryView
import com.grupo2.currencyv1.domain.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Una fila del historial: categoría, monto y fecha (CU-06, paso 4). */
data class HistoryItem(
    val id: Long,
    val categoryName: String,
    val amount: Money,
    val detail: String
)

data class HistoryUiState(
    val loaded: Boolean = false,
    val view: HistoryView = HistoryView.HOY,
    val rangeText: String = "",
    val items: List<HistoryItem> = emptyList(),
    val total: Money = Money.ZERO,
    val hasAnyExpense: Boolean = false
)

class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {

    /** Vista seleccionada; por defecto Hoy (CU-06, paso 2). */
    private val selectedView = MutableStateFlow(HistoryView.HOY)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = selectedView.flatMapLatest { view ->
        val range = repository.rangeOf(view)
        combine(repository.observe(view), repository.observeHasAnyExpense()) { expenses, hasAny ->
            HistoryUiState(
                loaded = true,
                view = view,
                rangeText = rangeText(view, range.start, range.endInclusive),
                items = expenses.map {
                    val tipo = if (it.gasto.tipo == ExpenseType.FIJO) "Fijo (descuento automático)" else "Variable"
                    HistoryItem(
                        id = it.gasto.id,
                        categoryName = it.categoriaNombre,
                        amount = Money(it.gasto.montoCentavos),
                        detail = "$tipo · ${it.gasto.fecha.format(DATE_FORMAT)}"
                    )
                },
                total = expenses.fold(Money.ZERO) { total, e -> total + Money(e.gasto.montoCentavos) },
                hasAnyExpense = hasAny
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** CU-06, paso 3 y flujo alterno 3a: cambiar de vista. */
    fun onViewSelect(view: HistoryView) {
        selectedView.value = view
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun rangeText(view: HistoryView, start: LocalDate, end: LocalDate): String =
    if (view == HistoryView.HOY) {
        "Hoy, ${start.format(DATE_FORMAT)}"
    } else {
        "Del ${start.format(DATE_FORMAT)} al ${end.format(DATE_FORMAT)}"
    }
