package com.grupo2.currencyv1.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo2.currencyv1.data.BudgetRepository
import com.grupo2.currencyv1.domain.BudgetOverview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loaded: Boolean = false,
    /** null si todavía no hay salario registrado. */
    val overview: BudgetOverview? = null
)

class HomeViewModel(repository: BudgetRepository) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = repository.observeOverview()
        .map { HomeUiState(loaded = true, overview = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
