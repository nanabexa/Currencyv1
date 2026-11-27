package com.grupo2.currencyv1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.grupo2.currencyv1.ui.AppRoot
import com.grupo2.currencyv1.ui.theme.CurrencyV1Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CurrencyV1Theme {
                AppRoot()
            }
        }

        val container = (application as CurrencyApplication).container
        lifecycleScope.launch {
            // Este bloque se ejecuta cada vez que la app vuelve a primer plano (y se cancela al salir).
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1) Descuentos de gastos fijos pendientes y cierre de periodos.
                container.runPeriodicChecks()
                // 2) Mientras la app esté visible, cada cambio del resumen se revisa para avisar
                //    (presupuesto bajo, excedente).
                container.budgetRepository.observeOverview().collect { container.alertEngine.evaluate(it) }
            }
        }
    }
}
