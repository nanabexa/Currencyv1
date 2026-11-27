package com.grupo2.currencyv1.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grupo2.currencyv1.CurrencyApplication

/** Punto de entrada: crea el ViewModel con las dependencias de la app. */
@Composable
fun BudgetRoute() {
    val container = (LocalContext.current.applicationContext as CurrencyApplication).container
    val viewModel: BudgetViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BudgetViewModel(container.budgetRepository) }
        }
    )
    BudgetScreen(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(viewModel: BudgetViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Presupuesto") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.loaded && !state.hasSalary) {
                item {
                    Text(
                        "Primero registra tu salario en la pestaña Ingresos.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (state.hasSalary) {
                // Paso 2: salario, gastos fijos y lo que queda.
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AmountRow("Salario total", "B/. ${state.salarioTotal}")
                            AmountRow("Gastos fijos", "B/. ${state.fijos} (${state.fijosPercent} %)")
                            AmountRow(
                                "Te queda después de los fijos",
                                "B/. ${state.remaining} (${state.remainingPercent} %)"
                            )
                        }
                    }
                }

                if (state.fijosBlock) {
                    item {
                        Text(
                            "Tus gastos fijos son iguales o mayores a tu salario. " +
                                    "Corrígelos en la pestaña Gastos para poder configurar el presupuesto.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    // Paso 3: el usuario decide cuánto ahorra.
                    item {
                        OutlinedTextField(
                            value = state.ahorroText,
                            onValueChange = viewModel::onAhorroChange,
                            label = { Text("Porcentaje de ahorro (%)") },
                            supportingText = {
                                Text("Máximo permitido: ${state.maxAhorro ?: "0.00"} %")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Paso 4: vista previa en dinero y en porcentaje.
                    state.preview?.let { preview ->
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AmountRow("Ahorro", "B/. ${preview.ahorro} (${preview.ahorroPercent} %)")
                                    AmountRow(
                                        "Para gastos variables",
                                        "B/. ${preview.variables} (${preview.variablesPercent} %)"
                                    )
                                }
                            }
                        }
                    }

                    // Paso 5: confirmar.
                    item {
                        Button(onClick = viewModel::save) {
                            Text(if (state.isConfigured) "Actualizar presupuesto" else "Guardar presupuesto")
                        }
                    }
                }

                state.message?.let { msg ->
                    item {
                        Text(
                            msg.text,
                            color = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // CU-07: el límite se configura desde la pantalla de presupuesto.
                if (state.isConfigured && !state.fijosBlock) {
                    item { LimitSection() }
                }
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
