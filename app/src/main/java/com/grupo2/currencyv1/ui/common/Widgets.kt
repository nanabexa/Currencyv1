package com.grupo2.currencyv1.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.grupo2.currencyv1.domain.EffectiveDateError
import com.grupo2.currencyv1.domain.FieldError
import com.grupo2.currencyv1.domain.PercentError

/** Campo de texto con mensaje de error debajo. Lo usan todos los formularios. */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

/** Mensajes para el usuario de los errores de validación de formularios. */
fun FieldError.userMessage(): String = when (this) {
    FieldError.NAME_EMPTY -> "Escribe un nombre."
    FieldError.AMOUNT_EMPTY -> "Escribe un monto."
    FieldError.AMOUNT_NOT_NUMERIC -> "El monto debe ser un número. Usa punto para los decimales."
    FieldError.AMOUNT_BELOW_MINIMUM -> "El monto debe ser de B/. 0.01 o más."
    FieldError.AMOUNT_TOO_MANY_DECIMALS -> "Usa máximo 2 decimales."
    FieldError.AMOUNT_TOO_LARGE -> "El monto es demasiado grande."
    FieldError.PERIOD_MISSING -> "Selecciona el periodo de pago."
    FieldError.PERIOD_MISMATCH -> "Todas las fuentes deben tener el mismo periodo de pago."
    FieldError.PAY_DAYS_INVALID_COUNT -> "Indica los días (uno si es mensual, dos si es quincenal)."
    FieldError.PAY_DAY_OUT_OF_RANGE -> "Los días deben estar entre 1 y 30."
    FieldError.PAY_DAYS_DUPLICATED -> "Los dos días deben ser distintos."
    FieldError.CATEGORY_MISSING -> "Selecciona una categoría."
    FieldError.FREQUENCY_MISSING -> "Selecciona la frecuencia."
}

fun EffectiveDateError.userMessage(): String = when (this) {
    EffectiveDateError.EMPTY -> "Escribe la fecha desde la que rige el cambio."
    EffectiveDateError.INVALID_FORMAT -> "Escribe la fecha como dd/MM/aaaa, por ejemplo 15/12/2026."
    EffectiveDateError.IN_THE_PAST -> "La fecha no puede ser anterior a hoy."
}

fun PercentError.userMessage(): String = when (this) {
    PercentError.EMPTY -> "Escribe el porcentaje de ahorro."
    PercentError.NOT_NUMERIC -> "El porcentaje debe ser un número. Usa punto para los decimales."
    PercentError.NEGATIVE -> "El porcentaje no puede ser negativo."
    PercentError.TOO_MANY_DECIMALS -> "Usa máximo 2 decimales."
    PercentError.ABOVE_100 -> "El porcentaje no puede pasar de 100."
}
