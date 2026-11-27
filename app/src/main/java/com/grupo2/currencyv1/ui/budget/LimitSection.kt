package com.grupo2.currencyv1.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

/** Sección "Configurar límite" (CU-07), dentro de la pantalla de presupuesto. */
@Composable
fun LimitSection() {
    val container = (LocalContext.current.applicationContext as CurrencyApplication).container
    val viewModel: LimitViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LimitViewModel(container.budgetRepository) }
        }
    )
    val state by viewModel.uiState.collectAsState()
    if (!state.loaded || !state.hasBudget) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Límite de presupuesto bajo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Recibirás un aviso cuando tu presupuesto disponible llegue a este monto o baje de él.",
                style = MaterialTheme.typography.bodySmall
            )
            // Paso 2: se muestra el presupuesto disponible.
            Text(
                "Disponible ahora: B/. ${state.available ?: "0.00"} de B/. ${state.total ?: "0.00"}",
                style = MaterialTheme.typography.bodyMedium
            )

            // Paso 3: el usuario ingresa el límite.
            OutlinedTextField(
                value = state.limitText,
                onValueChange = viewModel::onLimitChange,
                label = { Text("Límite (B/.)") },
                supportingText = {
                    Text(
                        if (state.currentLimit != null) {
                            "Límite actual: B/. ${state.currentLimit}"
                        } else {
                            "Aún no tienes un límite configurado"
                        }
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = viewModel::save) { Text("Guardar límite") }

            state.message?.let { msg ->
                Text(
                    msg.text,
                    color = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
