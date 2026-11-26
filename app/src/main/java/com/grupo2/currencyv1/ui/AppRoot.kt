package com.grupo2.currencyv1.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.grupo2.currencyv1.ui.budget.BudgetRoute
import com.grupo2.currencyv1.ui.expenses.ExpensesRoute
import com.grupo2.currencyv1.ui.home.HomeRoute
import com.grupo2.currencyv1.ui.incomes.IncomesRoute

private const val TAB_HOME = 0
private const val TAB_INCOMES = 1
private const val TAB_EXPENSES = 2
private const val TAB_BUDGET = 3

/**
 * Raíz de la app: barra inferior con las secciones, cambiadas con un estado simple.
 * El historial (iteración 7) se agrega como una pestaña más.
 */
@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }

    // Cada pantalla trae su propia barra superior, que ya maneja el espacio de la barra de estado.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    TAB_HOME to "Principal",
                    TAB_INCOMES to "Ingresos",
                    TAB_EXPENSES to "Gastos",
                    TAB_BUDGET to "Presupuesto"
                )
                items.forEach { (index, label) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {},
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                TAB_HOME -> HomeRoute(
                    onGoToIncomes = { tab = TAB_INCOMES },
                    onGoToBudget = { tab = TAB_BUDGET }
                )
                TAB_INCOMES -> IncomesRoute()
                TAB_EXPENSES -> ExpensesRoute()
                else -> BudgetRoute()
            }
        }
    }
}
