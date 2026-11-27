package com.grupo2.currencyv1.notifications

import com.grupo2.currencyv1.domain.NotificationType

/** Quien muestra los avisos. Es una interfaz para poder reemplazarla en pruebas. */
interface Notifier {
    /**
     * Muestra una notificación del sistema.
     * Devuelve false si no se pudo mostrar (por ejemplo, el usuario negó el permiso); en ese caso
     * la app muestra el aviso dentro de ella (CU-07, flujo alterno 6a).
     */
    fun show(kind: NotificationType, id: Int, title: String, message: String): Boolean
}
