package com.grupo2.currencyv1.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import com.grupo2.currencyv1.CurrencyApplication
import com.grupo2.currencyv1.ui.budget.BudgetRoute
import com.grupo2.currencyv1.ui.expenses.ExpensesRoute
import com.grupo2.currencyv1.ui.history.HistoryRoute
import com.grupo2.currencyv1.ui.home.HomeRoute
import com.grupo2.currencyv1.ui.incomes.IncomesRoute

private const val TAB_HOME = 0
private const val TAB_INCOMES = 1
private const val TAB_EXPENSES = 2
private const val TAB_BUDGET = 3
private const val TAB_HISTORY = 4

/**
 * Raíz de la app: barra inferior con las secciones, cambiadas con un estado simple
 * (sin librería de navegación, porque todas son pantallas de primer nivel).
 */
@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    val context = LocalContext.current
    val container = (context.applicationContext as CurrencyApplication).container

    // Desde Android 13 las notificaciones necesitan el permiso del usuario. Si lo niega, los avisos
    // se muestran dentro de la app (CU-07, flujo alterno 6a).
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Aviso dentro de la app cuando no se pudo mostrar la notificación del sistema.
    val inAppAlert by container.alertEngine.inAppAlert.collectAsState()
    inAppAlert?.let { message ->
        AlertDialog(
            onDismissRequest = container.alertEngine::dismissInAppAlert,
            title = { Text("Aviso de tu presupuesto") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = container.alertEngine::dismissInAppAlert) { Text("Entendido") }
            }
        )
    }

    // Cada pantalla trae su propia barra superior, que ya maneja el espacio de la barra de estado.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    TAB_HOME to "Principal",
                    TAB_INCOMES to "Ingresos",
                    TAB_EXPENSES to "Gastos",
                    TAB_BUDGET to "Presupuesto",
                    TAB_HISTORY to "Historial"
                )
                items.forEach { (index, label) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {},
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
                TAB_BUDGET -> BudgetRoute()
                else -> HistoryRoute()
            }
        }
    }
}
