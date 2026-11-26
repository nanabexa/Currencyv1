package com.grupo2.currencyv1.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.grupo2.currencyv1.domain.ExpenseType
import com.grupo2.currencyv1.domain.FieldError
import com.grupo2.currencyv1.domain.PaymentPeriod
import com.grupo2.currencyv1.ui.common.ErrorText
import com.grupo2.currencyv1.ui.common.FormField
import com.grupo2.currencyv1.ui.common.userMessage

/** Punto de entrada: crea el ViewModel con las dependencias de la app. */
@Composable
fun ExpensesRoute() {
    val container = (LocalContext.current.applicationContext as CurrencyApplication).container
    val viewModel: ExpensesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ExpensesViewModel(container.expenseRepository) }
        }
    )
    ExpensesScreen(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(viewModel: ExpensesViewModel) {
    val state by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()
    val edit by viewModel.edit.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Gastos") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.loaded && !state.hasSalary) {
                item {
                    Text(
                        "Primero registra tu salario en la pestaña Ingresos.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                item {
                    Button(onClick = viewModel::openForm, enabled = state.loaded) { Text("Registrar gasto") }
                }
            }

            if (state.fixedRules.isNotEmpty()) {
                item { Text("Gastos fijos (toca uno para editarlo)", style = MaterialTheme.typography.titleMedium) }
                items(state.fixedRules, key = { "f${it.id}" }) { rule ->
                    ItemCard(
                        title = rule.categoryName,
                        subtitle = rule.schedule,
                        amount = "B/. ${rule.amount}",
                        note = if (rule.scheduledChange) "Cambio programado" else null,
                        onClick = { viewModel.openEditFixed(rule) }
                    )
                }
            }

            if (state.expenses.isNotEmpty()) {
                item { Text("Gastos de este mes", style = MaterialTheme.typography.titleMedium) }
                items(state.expenses, key = { "g${it.id}" }) { expense ->
                    ItemCard(
                        title = expense.categoryName,
                        subtitle = expense.detail,
                        amount = "B/. ${expense.amount}",
                        note = null,
                        onClick = if (expense.editable) {
                            { viewModel.openEditVariable(expense) }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    form?.let { ExpenseFormDialog(it, state, viewModel) }
    edit?.let { ExpenseEditDialog(it, state, viewModel) }
}

@Composable
private fun ItemCard(title: String, subtitle: String, amount: String, note: String?, onClick: (() -> Unit)?) {
    Card(
        modifier = if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        } else {
            Modifier.fillMaxWidth()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(amount, style = MaterialTheme.typography.titleMedium)
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
private val dayErrors = setOf(
    FieldError.PAY_DAYS_INVALID_COUNT,
    FieldError.PAY_DAY_OUT_OF_RANGE,
    FieldError.PAY_DAYS_DUPLICATED
)

private fun List<FieldError>.firstIn(group: Set<FieldError>): FieldError? = firstOrNull { it in group }

private fun dayLabels(frequency: PaymentPeriod): List<String> =
    if (frequency == PaymentPeriod.MENSUAL) {
        listOf("Día de descuento (1 a 30)")
    } else {
        listOf("Primer día de descuento (1 a 30)", "Segundo día de descuento (1 a 30)")
    }

// ---------------- Alta (CU-02) ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseFormDialog(form: ExpenseFormState, state: ExpensesUiState, viewModel: ExpensesViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissForm,
        title = { Text("Registrar gasto") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Paso 2-3: tipo de gasto.
                Text("Tipo de gasto", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.type == ExpenseType.FIJO,
                        onClick = { viewModel.onTypeSelect(ExpenseType.FIJO) },
                        label = { Text("Fijo") }
                    )
                    FilterChip(
                        selected = form.type == ExpenseType.VARIABLE,
                        onClick = { viewModel.onTypeSelect(ExpenseType.VARIABLE) },
                        label = { Text("Variable") }
                    )
                }

                // Paso 4-5: categoría (solo las predeterminadas en la v1).
                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = form.categoryId == category.id,
                            onClick = { viewModel.onCategorySelect(category.id) },
                            label = { Text(category.name) }
                        )
                    }
                }
                if (FieldError.CATEGORY_MISSING in form.errors) {
                    ErrorText(FieldError.CATEGORY_MISSING.userMessage())
                }

                FormField(
                    label = "Monto (B/.)",
                    value = form.amountText,
                    onValueChange = viewModel::onAmountChange,
                    error = form.errors.firstIn(amountErrors)?.userMessage(),
                    keyboardType = KeyboardType.Decimal
                )

                // Solo los gastos fijos llevan frecuencia y día de descuento.
                if (form.type == ExpenseType.FIJO) {
                    Text("Frecuencia", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = form.frequency == PaymentPeriod.QUINCENAL,
                            onClick = { viewModel.onFrequencySelect(PaymentPeriod.QUINCENAL) },
                            label = { Text("Quincenal") }
                        )
                        FilterChip(
                            selected = form.frequency == PaymentPeriod.MENSUAL,
                            onClick = { viewModel.onFrequencySelect(PaymentPeriod.MENSUAL) },
                            label = { Text("Mensual") }
                        )
                    }
                    if (FieldError.FREQUENCY_MISSING in form.errors) {
                        ErrorText(FieldError.FREQUENCY_MISSING.userMessage())
                    }

                    if (form.frequency != null) {
                        dayLabels(form.frequency).forEachIndexed { index, label ->
                            FormField(
                                label = label,
                                value = form.dayTexts.getOrElse(index) { "" },
                                onValueChange = { viewModel.onDayChange(index, it) },
                                error = null,
                                keyboardType = KeyboardType.Number
                            )
                        }
                        form.errors.firstIn(dayErrors)?.let { ErrorText(it.userMessage()) }
                    }
                }

                form.message?.let { ErrorText(it) }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::submit) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = viewModel::dismissForm) { Text("Cancelar") } }
    )
}

// ---------------- Modificar y eliminar (CU-08) ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseEditDialog(edit: ExpenseEditState, state: ExpensesUiState, viewModel: ExpensesViewModel) {
    val isFixed = edit.type == ExpenseType.FIJO

    if (edit.confirmingDelete) {
        // CU-08, flujo alterno 3a: confirmar antes de eliminar.
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Eliminar gasto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isFixed) {
                            "¿Seguro que quieres eliminar este gasto fijo? Dejará de descontarse desde la fecha " +
                                    "que indiques; los descuentos anteriores se conservan en el historial."
                        } else {
                            "¿Seguro que quieres eliminar este gasto?"
                        }
                    )
                    if (isFixed) {
                        FormField(
                            label = "Rige desde (dd/MM/aaaa)",
                            value = edit.effectiveDateText,
                            onValueChange = viewModel::onEditDateChange,
                            error = null
                        )
                    }
                    edit.message?.let { ErrorText(it) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Sí, eliminar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancelar") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = viewModel::dismissEdit,
        title = { Text(if (isFixed) "Editar gasto fijo" else "Editar gasto") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = edit.categoryId == category.id,
                            onClick = { viewModel.onEditCategorySelect(category.id) },
                            label = { Text(category.name) }
                        )
                    }
                }
                if (FieldError.CATEGORY_MISSING in edit.errors) {
                    ErrorText(FieldError.CATEGORY_MISSING.userMessage())
                }

                FormField(
                    label = "Monto (B/.)",
                    value = edit.amountText,
                    onValueChange = viewModel::onEditAmountChange,
                    error = edit.errors.firstIn(amountErrors)?.userMessage(),
                    keyboardType = KeyboardType.Decimal
                )

                if (isFixed) {
                    Text("Frecuencia", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = edit.frequency == PaymentPeriod.QUINCENAL,
                            onClick = { viewModel.onEditFrequencySelect(PaymentPeriod.QUINCENAL) },
                            label = { Text("Quincenal") }
                        )
                        FilterChip(
                            selected = edit.frequency == PaymentPeriod.MENSUAL,
                            onClick = { viewModel.onEditFrequencySelect(PaymentPeriod.MENSUAL) },
                            label = { Text("Mensual") }
                        )
                    }
                    if (FieldError.FREQUENCY_MISSING in edit.errors) {
                        ErrorText(FieldError.FREQUENCY_MISSING.userMessage())
                    }
                    if (edit.frequency != null) {
                        dayLabels(edit.frequency).forEachIndexed { index, label ->
                            FormField(
                                label = label,
                                value = edit.dayTexts.getOrElse(index) { "" },
                                onValueChange = { viewModel.onEditDayChange(index, it) },
                                error = null,
                                keyboardType = KeyboardType.Number
                            )
                        }
                        edit.errors.firstIn(dayErrors)?.let { ErrorText(it.userMessage()) }
                    }

                    // RC-10: el usuario elige desde cuándo rige el cambio (hoy o una fecha futura).
                    FormField(
                        label = "El cambio rige desde (dd/MM/aaaa)",
                        value = edit.effectiveDateText,
                        onValueChange = viewModel::onEditDateChange,
                        error = null
                    )
                }

                edit.message?.let { ErrorText(it) }
                TextButton(onClick = viewModel::askDelete) { Text("Eliminar") }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveEdit) { Text("Guardar cambios") } },
        dismissButton = { TextButton(onClick = viewModel::dismissEdit) { Text("Cancelar") } }
    )
}
