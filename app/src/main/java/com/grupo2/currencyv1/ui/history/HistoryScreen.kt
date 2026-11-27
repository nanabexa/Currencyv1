package com.grupo2.currencyv1.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grupo2.currencyv1.CurrencyApplication
import com.grupo2.currencyv1.domain.HistoryView

/** Punto de entrada: crea el ViewModel con las dependencias de la app. */
@Composable
fun HistoryRoute() {
    val container = (LocalContext.current.applicationContext as CurrencyApplication).container
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(container.historyRepository) }
        }
    )
    HistoryScreen(viewModel)
}

private fun HistoryView.label(): String = when (this) {
    HistoryView.HOY -> "Hoy"
    HistoryView.SEMANAL -> "Semanal"
    HistoryView.MENSUAL -> "Mensual"
    HistoryView.ANUAL -> "Anual"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Historial") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Botones superiores: Hoy, Semanal, Mensual, Anual (RN-01).
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryView.values().forEach { view ->
                    FilterChip(
                        selected = state.view == view,
                        onClick = { viewModel.onViewSelect(view) },
                        label = { Text(view.label()) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.loaded) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(state.rangeText, style = MaterialTheme.typography.labelLarge)
                            if (state.items.isNotEmpty()) {
                                Text("Total: B/. ${state.total}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    if (state.items.isEmpty()) {
                        item {
                            // Excepción 4e del caso de uso, o aviso de que aún no hay gastos (precondición).
                            Text(
                                if (state.hasAnyExpense) {
                                    "Sin registros en este periodo"
                                } else {
                                    "Aún no has registrado gastos. Registra uno en la pestaña Gastos."
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    items(state.items, key = { it.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(item.categoryName, style = MaterialTheme.typography.titleMedium)
                                    Text(item.detail, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("B/. ${item.amount}", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
