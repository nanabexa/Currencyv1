package com.grupo2.currencyv1.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.grupo2.currencyv1.domain.ExpenseType
import com.grupo2.currencyv1.domain.IncomeType
import com.grupo2.currencyv1.domain.NotificationType
import com.grupo2.currencyv1.domain.PaymentPeriod
import java.time.LocalDate

/*
 * Convenciones:
 * - Todo el dinero se guarda en centavos (Long): B/. 440.00 = 44000.
 * - Los porcentajes se guardan en centésimas (Int): 12.50 % = 1250.
 * - Las fechas son LocalDate (se convierten con Converters).
 */

/** Categoría de gasto. En la v1 solo existen las predeterminadas (RF-03); RF-11 agregará las propias. */
@Entity(
    tableName = "categorias",
    indices = [Index(value = ["nombre"], unique = true)]
)
data class CategoriaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val esPredeterminada: Boolean = true
)

/**
 * Ingreso. Puede ser una fuente de salario o un extra.
 *
 * - SALARIO: lleva periodo, días de acreditación y vigencia. Un cambio de sueldo cierra la fila
 *   anterior (vigenteHasta) y crea otra que rige desde la siguiente acreditación (RC-10).
 * - EXTRA: solo usa nombre, monto y fecha; periodo y vigencia quedan vacíos.
 */
@Entity(tableName = "ingresos", indices = [Index("tipo"), Index("fecha")])
data class IngresoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val montoCentavos: Long,
    val tipo: IncomeType,
    val periodo: PaymentPeriod? = null,
    val diasAcreditacion: List<Int> = emptyList(),
    val fecha: LocalDate,
    val vigenteDesde: LocalDate? = null,
    val vigenteHasta: LocalDate? = null
)

/**
 * Regla de un gasto fijo (la obligación). Cada descuento aplicado se guarda como una fila
 * de [GastoEntity]. Igual que el salario, un cambio se maneja con vigencia (RC-10).
 */
@Entity(
    tableName = "gastos_fijos",
    foreignKeys = [
        ForeignKey(
            entity = CategoriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("categoriaId")]
)
data class GastoFijoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoriaId: Long,
    val montoCentavos: Long,
    val frecuencia: PaymentPeriod,
    val diasDescuento: List<Int>,
    val vigenteDesde: LocalDate,
    val vigenteHasta: LocalDate? = null
)

/**
 * Un gasto que ya ocurrió: aparece en el historial y descuenta el presupuesto.
 * - VARIABLE: lo registra el usuario; la fecha es la de hoy.
 * - FIJO: lo genera el sistema el día de descuento ([gastoFijoId] apunta a su regla).
 *
 * El índice único (gastoFijoId, fecha, diaProgramado) evita aplicar dos veces el mismo
 * descuento si el proceso automático corre más de una vez. Los variables tienen
 * gastoFijoId = null, y SQLite permite varios nulos en un índice único.
 */
@Entity(
    tableName = "gastos",
    foreignKeys = [
        ForeignKey(
            entity = CategoriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = GastoFijoEntity::class,
            parentColumns = ["id"],
            childColumns = ["gastoFijoId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("categoriaId"),
        Index("fecha"),
        Index(value = ["gastoFijoId", "fecha", "diaProgramado"], unique = true)
    ]
)
data class GastoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: ExpenseType,
    val categoriaId: Long,
    val montoCentavos: Long,
    val fecha: LocalDate,
    val gastoFijoId: Long? = null,
    val diaProgramado: Int? = null
)

/** Presupuesto del usuario. Siempre hay una sola fila (id = 1). */
@Entity(tableName = "presupuesto")
data class PresupuestoEntity(
    @PrimaryKey val id: Int = 1,
    val ahorroCentesimas: Int,
    val limiteCentavos: Long? = null,
    /** true si ya se avisó que el disponible cruzó el límite; vuelve a false al subir de nuevo (CU-07). */
    val limiteNotificado: Boolean = false,
    /** Sobrantes de periodos anteriores que pasaron al ahorro (RC-09). */
    val ahorroAcumuladoCentavos: Long = 0,
    val periodoInicio: LocalDate
)

/** Registro de avisos enviados, para no repetirlos (por ejemplo, un descuento fijo = un aviso). */
@Entity(
    tableName = "notificaciones",
    indices = [Index(value = ["tipo", "referenciaId", "fecha"], unique = true)]
)
data class NotificacionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: NotificationType,
    val referenciaId: Long,
    val fecha: LocalDate
)
