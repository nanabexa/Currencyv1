package com.grupo2.currencyv1.ui.incomes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grupo2.currencyv1.CurrencyApplication
import com.grupo2.currencyv1.domain.FieldError
import com.grupo2.currencyv1.domain.IncomeType
import com.grupo2.currencyv1.domain.PaymentPeriod

/** Punto de entrada: crea el ViewModel con las dependencias de la app. */
@Composable
fun IncomesRoute() {
    val container = (LocalContext.current.applicationContext as CurrencyApplication).container
    val viewModel: IncomesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { IncomesViewModel(container.incomeRepository) }
        }
    )
    IncomesScreen(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomesScreen(viewModel: IncomesViewModel) {
    val state by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()

    // CU-01 paso 1-2: la primera vez, el sistema pide el salario sin que el usuario lo busque.
    LaunchedEffect(state.loaded, state.salaries.isEmpty()) {
        if (state.loaded && state.salaries.isEmpty() && viewModel.form.value == null) {
            viewModel.openSalaryForm()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Ingresos") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.loaded && state.salaries.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Registra tu salario para comenzar.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(onClick = viewModel::openSalaryForm) { Text("Registrar salario") }
                    }
                }
            }

            if (state.salaries.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Salario total", style = MaterialTheme.typography.labelLarge)
                            Text("B/. ${state.salaryTotal}", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                payScheduleText(state.period, state.payDays),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                item { Text("Fuentes de salario", style = MaterialTheme.typography.titleMedium) }
                items(state.salaries, key = { "s${it.id}" }) { IncomeRow(it) }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::openSalaryForm) { Text("Agregar otra fuente") }
                        Button(onClick = viewModel::openExtraForm) { Text("Agregar ingreso extra") }
                    }
                }

                if (state.extras.isNotEmpty()) {
                    item { Text("Ingresos extra", style = MaterialTheme.typography.titleMedium) }
                    items(state.extras, key = { "e${it.id}" }) { IncomeRow(it) }
                }
            }
        }
    }

    form?.let { IncomeFormDialog(it, viewModel) }
}

@Composable
private fun IncomeRow(item: IncomeItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(item.detail, style = MaterialTheme.typography.bodySmall)
            }
            Text("B/. ${item.amount}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val amountErrors = setOf(
    FieldError.AMOUNT_EMPTY,
    FieldError.AMOUNT_NOT_NUMERIC,
    FieldError.AMOUNT_BELOW_MINIMUM,
    FieldError.AMOUNT_TOO_MANY_DECIMALS,
    FieldError.AMOUNT_TOO_LARGE
)
private val periodErrors = setOf(FieldError.PERIOD_MISSING, FieldError.PERIOD_MISMATCH)
private val dayErrors = setOf(
    FieldError.PAY_DAYS_INVALID_COUNT,
    FieldError.PAY_DAY_OUT_OF_RANGE,
    FieldError.PAY_DAYS_DUPLICATED
)

private fun List<FieldError>.firstIn(group: Set<FieldError>): FieldError? = firstOrNull { it in group }

@Composable
private fun IncomeFormDialog(form: IncomeFormState, viewModel: IncomesViewModel) {
    val title = when {
        form.type == IncomeType.EXTRA -> "Agregar ingreso extra"
        form.needsPeriod -> "Registrar salario"
        else -> "Agregar otra fuente"
    }

    AlertDialog(
        onDismissRequest = viewModel::dismissForm,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormField(
                    label = "Nombre",
                    value = form.name,
                    onValueChange = viewModel::onNameChange,
                    error = if (FieldError.NAME_EMPTY in form.errors) FieldError.NAME_EMPTY.message() else null
                )
                FormField(
                    label = "Monto (B/.)",
                    value = form.amountText,
                    onValueChange = viewModel::onAmountChange,
                    error = form.errors.firstIn(amountErrors)?.message(),
                    keyboardType = KeyboardType.Decimal
                )

                if (form.needsPeriod) {
                    Text("Periodo de pago", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = form.period == PaymentPeriod.QUINCENAL,
                            onClick = { viewModel.onPeriodSelect(PaymentPeriod.QUINCENAL) },
                            label = { Text("Quincenal") }
                        )
                        FilterChip(
                            selected = form.period == PaymentPeriod.MENSUAL,
                            onClick = { viewModel.onPeriodSelect(PaymentPeriod.MENSUAL) },
                            label = { Text("Mensual") }
                        )
                    }
                    form.errors.firstIn(periodErrors)?.let { ErrorText(it.message()) }

                    if (form.period != null) {
                        val labels = if (form.period == PaymentPeriod.MENSUAL) {
                            listOf("Día de acreditación (1 a 30)")
                        } else {
                            listOf("Primer día (1 a 30)", "Segundo día (1 a 30)")
                        }
                        labels.forEachIndexed { index, label ->
                            FormField(
                                label = label,
                                value = form.dayTexts.getOrElse(index) { "" },
                                onValueChange = { viewModel.onDayChange(index, it) },
                                error = null,
                                keyboardType = KeyboardType.Number
                            )
                        }
                        form.errors.firstIn(dayErrors)?.let { ErrorText(it.message()) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::submit) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = viewModel::dismissForm) { Text("Cancelar") } }
    )
}

@Composable
private fun FormField(
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
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

/** Mensajes para el usuario (CU-01, flujos de excepción 7e a 7h). */
private fun FieldError.message(): String = when (this) {
    FieldError.NAME_EMPTY -> "Escribe un nombre."
    FieldError.AMOUNT_EMPTY -> "Escribe un monto."
    FieldError.AMOUNT_NOT_NUMERIC -> "El monto debe ser un número. Usa punto para los decimales."
    FieldError.AMOUNT_BELOW_MINIMUM -> "El monto debe ser de B/. 0.01 o más."
    FieldError.AMOUNT_TOO_MANY_DECIMALS -> "Usa máximo 2 decimales."
    FieldError.AMOUNT_TOO_LARGE -> "El monto es demasiado grande."
    FieldError.PERIOD_MISSING -> "Selecciona el periodo de pago."
    FieldError.PERIOD_MISMATCH -> "Todas las fuentes deben tener el mismo periodo de pago."
    FieldError.PAY_DAYS_INVALID_COUNT -> "Indica los días de acreditación (uno si es mensual, dos si es quincenal)."
    FieldError.PAY_DAY_OUT_OF_RANGE -> "Los días deben estar entre 1 y 30."
    FieldError.PAY_DAYS_DUPLICATED -> "Los dos días deben ser distintos."
    FieldError.CATEGORY_MISSING -> "Selecciona una categoría."
    FieldError.FREQUENCY_MISSING -> "Selecciona la frecuencia."
}
