package com.grupo2.currencyv1.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

enum class EffectiveDateError { EMPTY, INVALID_FORMAT, IN_THE_PAST }

sealed interface EffectiveDateResult {
    data class Ok(val date: LocalDate) : EffectiveDateResult
    data class Error(val reason: EffectiveDateError) : EffectiveDateResult
}

// "uuuu" + STRICT hace que fechas imposibles como 31/02/2026 se rechacen.
private val EFFECTIVE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

/** Fecha como la escribe el usuario: dd/MM/aaaa. */
fun formatEffectiveDate(date: LocalDate): String = date.format(EFFECTIVE_FORMAT)

/**
 * RC-10 y RN-07: la fecha de vigencia de un cambio debe ser hoy o posterior.
 * Una fecha anterior a hoy se rechaza (obligaría a recalcular periodos ya cerrados).
 */
fun parseEffectiveDate(text: String, today: LocalDate): EffectiveDateResult {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return EffectiveDateResult.Error(EffectiveDateError.EMPTY)
    val date = try {
        LocalDate.parse(trimmed, EFFECTIVE_FORMAT)
    } catch (e: DateTimeParseException) {
        return EffectiveDateResult.Error(EffectiveDateError.INVALID_FORMAT)
    }
    if (date.isBefore(today)) return EffectiveDateResult.Error(EffectiveDateError.IN_THE_PAST)
    return EffectiveDateResult.Ok(date)
}
