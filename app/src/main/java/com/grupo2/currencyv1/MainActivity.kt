package com.grupo2.currencyv1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
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
    }

    /**
     * Cada vez que la app vuelve a primer plano se aplican los descuentos de gastos fijos que ya
     * llegaron (incluidos los atrasados si el teléfono estaba apagado). La iteración 8 lo
     * complementa con WorkManager para que ocurra también en segundo plano.
     */
    override fun onStart() {
        super.onStart()
        val container = (application as CurrencyApplication).container
        lifecycleScope.launch { container.expenseRepository.processPending() }
    }
}
