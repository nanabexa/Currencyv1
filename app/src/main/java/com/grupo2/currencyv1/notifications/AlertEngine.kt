package com.grupo2.currencyv1.notifications

import com.grupo2.currencyv1.data.CategoriaDao
import com.grupo2.currencyv1.data.GastoDao
import com.grupo2.currencyv1.data.NotificacionDao
import com.grupo2.currencyv1.data.NotificacionEntity
import com.grupo2.currencyv1.data.PresupuestoDao
import com.grupo2.currencyv1.domain.BudgetOverview
import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decide cuándo avisar al usuario:
 * - Presupuesto bajo (CU-07): una vez al llegar al límite; se vuelve a armar si el disponible sube.
 * - Excedente (CU-02 7a, CU-04 RN-05): una vez por periodo, al pasarse del presupuesto total.
 * - Gasto fijo descontado (CU-02 8a): una vez por cada descuento.
 *
 * Si no se puede mostrar la notificación del sistema, deja el aviso en [inAppAlert] para que la
 * pantalla lo muestre dentro de la app (CU-07, flujo alterno 6a).
 */
class AlertEngine(
    private val presupuestoDao: PresupuestoDao,
    private val notificacionDao: NotificacionDao,
    private val gastoDao: GastoDao,
    private val categoriaDao: CategoriaDao,
    private val notifier: Notifier
) {
    // Evita avisos duplicados si la app y el proceso en segundo plano evalúan al mismo tiempo.
    private val mutex = Mutex()

    private val _inAppAlert = MutableStateFlow<String?>(null)
    val inAppAlert: StateFlow<String?> = _inAppAlert.asStateFlow()

    fun dismissInAppAlert() {
        _inAppAlert.value = null
    }

    /** Revisa el resumen del periodo y avisa si corresponde. Se llama cada vez que el resumen cambia. */
    suspend fun evaluate(overview: BudgetOverview?) = mutex.withLock {
        if (overview == null || overview.budget == null) return@withLock
        val disponible = overview.disponible ?: return@withLock
        val presupuesto = presupuestoDao.get() ?: return@withLock

        // Excedente: una sola vez por periodo. El registro de avisos hace de candado.
        if (overview.excedido) {
            val start = overview.range.start
            val inserted = notificacionDao.insertIfAbsent(
                NotificacionEntity(
                    tipo = NotificationType.EXCEDENTE,
                    referenciaId = start.toEpochDay(),
                    fecha = start
                )
            )
            if (inserted > 0) {
                val excedente = Money.ZERO - disponible
                deliver(
                    kind = NotificationType.EXCEDENTE,
                    id = 0,
                    title = "Te pasaste del presupuesto",
                    message = "Superaste tu presupuesto de este periodo por B/. $excedente."
                )
            }
        }

        // Presupuesto bajo (RN-03 de CU-07): disponible menor o igual al límite.
        val limite = presupuesto.limiteCentavos?.let { Money(it) } ?: return@withLock
        if (disponible <= limite) {
            if (!presupuesto.limiteNotificado) {
                presupuestoDao.save(presupuesto.copy(limiteNotificado = true))
                deliver(
                    kind = NotificationType.LIMITE_BAJO,
                    id = 0,
                    title = "Presupuesto bajo",
                    message = "Tu presupuesto disponible es de B/. $disponible, igual o por debajo de tu límite de B/. $limite."
                )
            }
        } else if (presupuesto.limiteNotificado) {
            // El disponible volvió a subir por encima del límite: se rearma el aviso.
            presupuestoDao.save(presupuesto.copy(limiteNotificado = false))
        }
    }

    /** CU-02, flujo 8a: una notificación por cada descuento de gasto fijo recién aplicado. */
    suspend fun notifyAppliedFixedExpenses(expenseIds: List<Long>) = mutex.withLock {
        for (id in expenseIds) {
            val gasto = gastoDao.getById(id) ?: continue
            val inserted = notificacionDao.insertIfAbsent(
                NotificacionEntity(
                    tipo = NotificationType.GASTO_FIJO_DESCONTADO,
                    referenciaId = id,
                    fecha = gasto.fecha
                )
            )
            if (inserted <= 0) continue

            val category = categoriaDao.getById(gasto.categoriaId)?.nombre ?: "gasto fijo"
            deliver(
                kind = NotificationType.GASTO_FIJO_DESCONTADO,
                id = id.toInt(),
                title = "Gasto fijo descontado",
                message = "Se descontó tu gasto fijo de $category: B/. ${Money(gasto.montoCentavos)}."
            )
        }
    }

    private fun deliver(kind: NotificationType, id: Int, title: String, message: String) {
        val shown = notifier.show(kind, id, title, message)
        if (!shown) _inAppAlert.value = message
    }
}
