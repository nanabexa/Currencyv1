package com.grupo2.currencyv1

import android.app.Application
import android.content.Context
import com.grupo2.currencyv1.data.AppDatabase
import java.time.Clock

/**
 * Crea las dependencias a mano (sin Hilt) para que sean fáciles de reemplazar en pruebas.
 * El [clock] es la única fuente de "hoy" de la app (ver decisión de Clock inyectable).
 */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val clock: Clock = Clock.systemDefaultZone()
}

class CurrencyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
