package com.grupo2.currencyv1.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.grupo2.currencyv1.domain.BudgetOverview
import com.grupo2.currencyv1.domain.PaymentPeriod
import java.time.format.DateTimeFormatter

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

/** Punto de entrada: crea el ViewModel con las dependencias de la app. */
@Composable
fun HomeRoute(onGoToIncomes: () -> Unit, onGoToBudget: () -> Unit) {
    val container = (LocalContext.current.applicationContext as CurrencyApplication).container
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.budgetRepository) }
        }
    )
    HomeScreen(viewModel, onGoToIncomes, onGoToBudget)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onGoToIncomes: () -> Unit, onGoToBudget: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val overview = state.overview

    Scaffold(topBar = { TopAppBar(title = { Text("Currency v1") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.loaded && overview == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Registra tu salario para empezar.", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onGoToIncomes) { Text("Ir a Ingresos") }
                    }
                }
            }

            if (overview != null) {
                item { AvailableCard(overview) }

                if (overview.budget == null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Aún no configuras tu presupuesto. Define cuánto quieres ahorrar para ver " +
                                    "tu presupuesto disponible.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(onClick = onGoToBudget) { Text("Configurar presupuesto") }
                        }
                    }
                }

                item { DetailCard(overview) }
            }
        }
    }
}

@Composable
private fun AvailableCard(overview: BudgetOverview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Del ${overview.range.start.format(SHORT_DATE)} al ${overview.range.end.format(SHORT_DATE)}",
                style = MaterialTheme.typography.labelLarge
            )
            val disponible = overview.disponible
            if (disponible == null) {
                Text("Presupuesto disponible", style = MaterialTheme.typography.titleMedium)
                Text("Sin configurar", style = MaterialTheme.typography.headlineMedium)
            } else {
                Text("Presupuesto disponible", style = MaterialTheme.typography.titleMedium)
                Text(
                    "B/. $disponible",
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (overview.excedido) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                overview.budget?.let {
                    Text("de B/. ${it.variables} para gastos variables", style = MaterialTheme.typography.bodyMedium)
                }
                if (overview.excedido) {
                    Text(
                        "Te pasaste del presupuesto de este periodo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailCard(overview: BudgetOverview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Salario total", style = MaterialTheme.typography.bodyMedium)
                Text("B/. ${overview.salarioTotal}", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                if (overview.period == PaymentPeriod.MENSUAL) "Se acredita cada mes" else "Se acredita cada quincena",
                style = MaterialTheme.typography.bodySmall
            )
            DetailRow("Gastos fijos", "B/. ${overview.fijos} (${overview.fijosPercent} %)")
            overview.budget?.let {
                DetailRow("Ahorro", "B/. ${it.ahorro} (${it.ahorroPercent} %)")
                DetailRow("Gastos variables", "B/. ${it.variables} (${it.variablesPercent} %)")
            }
            DetailRow("Ingresos extra del periodo", "B/. ${overview.extras}")
            DetailRow("Gastado en variables", "B/. ${overview.gastosVariables}")
            DetailRow("Ahorro acumulado", "B/. ${overview.ahorroAcumulado}")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
