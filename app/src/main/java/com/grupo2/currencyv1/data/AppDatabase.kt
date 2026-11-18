package com.grupo2.currencyv1.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.grupo2.currencyv1.domain.DEFAULT_CATEGORY_NAMES

/**
 * Base de datos local (RNF-04, RNF-05): vive en el dispositivo y no usa red.
 * exportSchema = true guarda una copia del esquema en app/schemas (se sube a Git).
 */
@Database(
    entities = [
        CategoriaEntity::class,
        IngresoEntity::class,
        GastoFijoEntity::class,
        GastoEntity::class,
        PresupuestoEntity::class,
        NotificacionEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoriaDao(): CategoriaDao
    abstract fun ingresoDao(): IngresoDao
    abstract fun gastoFijoDao(): GastoFijoDao
    abstract fun gastoDao(): GastoDao
    abstract fun presupuestoDao(): PresupuestoDao
    abstract fun notificacionDao(): NotificacionDao

    companion object {
        private const val DB_NAME = "currency.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addCallback(SeedCallback)
                .build()

        /** Base en memoria: se borra al cerrarla. Útil para pruebas instrumentadas. */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
                .addCallback(SeedCallback)
                .build()
    }
}

/** Al crear la base por primera vez, inserta las 8 categorías predeterminadas (RF-03). */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DEFAULT_CATEGORY_NAMES.forEach { nombre ->
            db.execSQL(
                "INSERT INTO categorias (nombre, esPredeterminada) VALUES (?, 1)",
                arrayOf<Any?>(nombre)
            )
        }
    }
}
