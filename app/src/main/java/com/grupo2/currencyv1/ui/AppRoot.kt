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
import com.grupo2.currencyv1.ui.expenses.ExpensesRoute
import com.grupo2.currencyv1.ui.incomes.IncomesRoute

/**
 * Raíz de la app: barra inferior con las secciones. Las pestañas de la v1 se cambian con un
 * estado simple (sin librería de navegación). Principal, Presupuesto e Historial se agregan
 * en las iteraciones 5 a 7.
 */
@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Cada pantalla trae su propia barra superior, que ya maneja el espacio de la barra de estado.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = {},
                    label = { Text("Ingresos") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {},
                    label = { Text("Gastos") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                0 -> IncomesRoute()
                else -> ExpensesRoute()
            }
        }
    }
}
