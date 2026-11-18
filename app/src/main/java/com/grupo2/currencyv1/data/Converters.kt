package com.grupo2.currencyv1.data

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room solo guarda tipos simples. Estos conversores traducen:
 * - LocalDate  <-> Long (días desde 1970, "epochDay")
 * - List<Int>  <-> String ("15,30"), para los días de acreditación o descuento
 * Los enums (PaymentPeriod, IncomeType...) los guarda Room por su nombre, sin conversor.
 */
class Converters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? = epochDay?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun fromDays(days: List<Int>?): String? = days?.joinToString(",")

    @TypeConverter
    fun toDays(text: String?): List<Int>? = text?.let { value ->
        if (value.isEmpty()) emptyList() else value.split(",").map { it.toInt() }
    }
}
