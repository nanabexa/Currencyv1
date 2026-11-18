package com.grupo2.currencyv1.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Un gasto junto al nombre de su categoría, para mostrarlo en listas e historial. */
data class GastoConCategoria(
    @Embedded val gasto: GastoEntity,
    val categoriaNombre: String
)

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categorias ORDER BY id")
    fun observeAll(): Flow<List<CategoriaEntity>>

    @Query("SELECT * FROM categorias WHERE id = :id")
    suspend fun getById(id: Long): CategoriaEntity?
}

@Dao
interface IngresoDao {
    @Insert
    suspend fun insert(ingreso: IngresoEntity): Long

    @Update
    suspend fun update(ingreso: IngresoEntity)

    @Delete
    suspend fun delete(ingreso: IngresoEntity)

    @Query("SELECT * FROM ingresos WHERE id = :id")
    suspend fun getById(id: Long): IngresoEntity?

    /** Salarios que rigen en la fecha dada (la vigencia incluye ambos extremos). */
    @Query(
        """SELECT * FROM ingresos
           WHERE tipo = 'SALARIO' AND vigenteDesde <= :fecha
             AND (vigenteHasta IS NULL OR vigenteHasta >= :fecha)
           ORDER BY id"""
    )
    fun observeSalariosVigentes(fecha: LocalDate): Flow<List<IngresoEntity>>

    @Query(
        """SELECT * FROM ingresos
           WHERE tipo = 'SALARIO' AND vigenteDesde <= :fecha
             AND (vigenteHasta IS NULL OR vigenteHasta >= :fecha)
           ORDER BY id"""
    )
    suspend fun getSalariosVigentes(fecha: LocalDate): List<IngresoEntity>

    @Query("SELECT * FROM ingresos WHERE tipo = 'EXTRA' ORDER BY fecha DESC, id DESC")
    fun observeExtras(): Flow<List<IngresoEntity>>

    /** Total de ingresos extra recibidos entre dos fechas (ambas incluidas), en centavos. */
    @Query(
        """SELECT COALESCE(SUM(montoCentavos), 0) FROM ingresos
           WHERE tipo = 'EXTRA' AND fecha BETWEEN :desde AND :hasta"""
    )
    fun observeTotalExtras(desde: LocalDate, hasta: LocalDate): Flow<Long>
}

@Dao
interface GastoFijoDao {
    @Insert
    suspend fun insert(regla: GastoFijoEntity): Long

    @Update
    suspend fun update(regla: GastoFijoEntity)

    @Query("SELECT * FROM gastos_fijos WHERE id = :id")
    suspend fun getById(id: Long): GastoFijoEntity?

    @Query(
        """SELECT * FROM gastos_fijos
           WHERE vigenteDesde <= :fecha AND (vigenteHasta IS NULL OR vigenteHasta >= :fecha)
           ORDER BY id"""
    )
    fun observeVigentes(fecha: LocalDate): Flow<List<GastoFijoEntity>>

    @Query(
        """SELECT * FROM gastos_fijos
           WHERE vigenteDesde <= :fecha AND (vigenteHasta IS NULL OR vigenteHasta >= :fecha)
           ORDER BY id"""
    )
    suspend fun getVigentes(fecha: LocalDate): List<GastoFijoEntity>
}

@Dao
interface GastoDao {
    @Insert
    suspend fun insert(gasto: GastoEntity): Long

    /**
     * Inserta un descuento de gasto fijo. Si ya existía (mismo gasto, fecha y día programado)
     * no hace nada y devuelve -1, lo que hace seguro ejecutar el proceso automático varias veces.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(gasto: GastoEntity): Long

    @Update
    suspend fun update(gasto: GastoEntity)

    @Delete
    suspend fun delete(gasto: GastoEntity)

    @Query("SELECT * FROM gastos WHERE id = :id")
    suspend fun getById(id: Long): GastoEntity?

    /** Historial: gastos entre dos fechas (ambas incluidas), del más reciente al más antiguo. */
    @Query(
        """SELECT g.*, c.nombre AS categoriaNombre
           FROM gastos g INNER JOIN categorias c ON c.id = g.categoriaId
           WHERE g.fecha BETWEEN :desde AND :hasta
           ORDER BY g.fecha DESC, g.id DESC"""
    )
    fun observeBetween(desde: LocalDate, hasta: LocalDate): Flow<List<GastoConCategoria>>

    /** Total de gastos variables entre dos fechas, en centavos (para el presupuesto disponible). */
    @Query(
        """SELECT COALESCE(SUM(montoCentavos), 0) FROM gastos
           WHERE tipo = 'VARIABLE' AND fecha BETWEEN :desde AND :hasta"""
    )
    fun observeTotalVariables(desde: LocalDate, hasta: LocalDate): Flow<Long>

    @Query("SELECT COUNT(*) FROM gastos")
    fun observeCount(): Flow<Int>
}

@Dao
interface PresupuestoDao {
    @Query("SELECT * FROM presupuesto WHERE id = 1")
    fun observe(): Flow<PresupuestoEntity?>

    @Query("SELECT * FROM presupuesto WHERE id = 1")
    suspend fun get(): PresupuestoEntity?

    /** Crea o reemplaza la única fila del presupuesto. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(presupuesto: PresupuestoEntity)
}

@Dao
interface NotificacionDao {
    /** Devuelve -1 si ese aviso ya se había registrado. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(notificacion: NotificacionEntity): Long
}
