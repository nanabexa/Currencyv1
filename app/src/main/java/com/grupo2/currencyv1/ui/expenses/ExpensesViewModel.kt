package com.grupo2.currencyv1.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo2.currencyv1.data.AddExpenseResult
import com.grupo2.currencyv1.data.ChangeRejection
import com.grupo2.currencyv1.data.ChangeResult
import com.grupo2.currencyv1.data.ExpenseRejection
import com.grupo2.currencyv1.data.ExpenseRepository
import com.grupo2.currencyv1.domain.EffectiveDateResult
import com.grupo2.currencyv1.domain.ExpenseType
import com.grupo2.currencyv1.domain.FieldError
import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.PaymentPeriod
import com.grupo2.currencyv1.domain.Validation
import com.grupo2.currencyv1.domain.formatEffectiveDate
import com.grupo2.currencyv1.domain.parseEffectiveDate
import com.grupo2.currencyv1.domain.validateFixedExpense
import com.grupo2.currencyv1.domain.validateVariableExpense
import com.grupo2.currencyv1.ui.common.userMessage
import com.grupo2.currencyv1.ui.incomes.payScheduleText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

data class CategoryItem(val id: Long, val name: String)

/** Regla de un gasto fijo, como la ve el usuario en la lista. */
data class FixedRuleItem(
    val id: Long,
    val categoryId: Long,
    val categoryName: String,
    val amount: Money,
    val frequency: PaymentPeriod,
    val payDays: List<Int>,
    val schedule: String,
    /** true si ya hay un cambio con fecha futura pendiente. */
    val scheduledChange: Boolean
)

/** Un gasto ocurrido (variable o descuento automático de un fijo). */
data class ExpenseItem(
    val id: Long,
    val categoryId: Long,
    val categoryName: String,
    val amount: Money,
    val detail: String,
    /** Solo los gastos variables se editan aquí; los descuentos automáticos cambian editando su regla. */
    val editable: Boolean
)

data class ExpensesUiState(
    val loaded: Boolean = false,
    val hasSalary: Boolean = false,
    val hasBudget: Boolean = false,
    val categories: List<CategoryItem> = emptyList(),
    val fixedRules: List<FixedRuleItem> = emptyList(),
    val expenses: List<ExpenseItem> = emptyList()
)

/** Estado del formulario de alta de gasto (null = cerrado). */
data class ExpenseFormState(
    val type: ExpenseType? = null,
    val categoryId: Long? = null,
    val amountText: String = "",
    val frequency: PaymentPeriod? = null,
    val dayTexts: List<String> = emptyList(),
    val errors: List<FieldError> = emptyList(),
    /** Mensaje general (bloqueos como "configura tu presupuesto" o "los fijos exceden el salario"). */
    val message: String? = null
)

/** Estado del diálogo de edición o eliminación de un gasto (null = cerrado). CU-08. */
data class ExpenseEditState(
    /** Id de la regla (si es fijo) o del gasto (si es variable). */
    val id: Long,
    val type: ExpenseType,
    val categoryId: Long?,
    val amountText: String,
    val frequency: PaymentPeriod?,
    val dayTexts: List<String>,
    /** Solo gastos fijos: fecha desde la que rige el cambio (dd/MM/aaaa). Por defecto, hoy. */
    val effectiveDateText: String,
    val confirmingDelete: Boolean = false,
    val errors: List<FieldError> = emptyList(),
    val message: String? = null
)

private const val NO_BUDGET_MESSAGE = "Configura tu presupuesto antes de registrar gastos variables."
private const val NO_SALARY_MESSAGE = "Primero registra tu salario en la pestaña Ingresos."
private const val NO_TYPE_MESSAGE = "Selecciona el tipo de gasto."

class ExpensesViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val uiState: StateFlow<ExpensesUiState> = combine(
        repository.observeCategories(),
        repository.observeFixedRules(),
        repository.observeMonthExpenses(),
        repository.observeHasBudget(),
        repository.observeHasSalary()
    ) { categories, rules, expenses, hasBudget, hasSalary ->
        val names = categories.associate { it.id to it.nombre }
        ExpensesUiState(
            loaded = true,
            hasSalary = hasSalary,
            hasBudget = hasBudget,
            categories = categories.map { CategoryItem(it.id, it.nombre) },
            fixedRules = rules.map {
                FixedRuleItem(
                    id = it.id,
                    categoryId = it.categoriaId,
                    categoryName = names[it.categoriaId] ?: "",
                    amount = Money(it.montoCentavos),
                    frequency = it.frecuencia,
                    payDays = it.diasDescuento,
                    schedule = payScheduleText(it.frecuencia, it.diasDescuento),
                    scheduledChange = it.vigenteHasta != null
                )
            },
            expenses = expenses.map {
                val isFixed = it.gasto.tipo == ExpenseType.FIJO
                val tipo = if (isFixed) "Fijo (descuento automático)" else "Variable"
                ExpenseItem(
                    id = it.gasto.id,
                    categoryId = it.gasto.categoriaId,
                    categoryName = it.categoriaNombre,
                    amount = Money(it.gasto.montoCentavos),
                    detail = "$tipo · ${it.gasto.fecha.format(DATE_FORMAT)}",
                    editable = !isFixed
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    private val _form = MutableStateFlow<ExpenseFormState?>(null)
    val form: StateFlow<ExpenseFormState?> = _form.asStateFlow()

    private val _edit = MutableStateFlow<ExpenseEditState?>(null)
    val edit: StateFlow<ExpenseEditState?> = _edit.asStateFlow()

    // =================== Alta (CU-02) ===================

    fun openForm() {
        _form.value = ExpenseFormState()
    }

    fun dismissForm() {
        _form.value = null
    }

    /** CU-02 paso 3. Un gasto variable sin presupuesto se bloquea desde aquí (flujo de excepción 3e). */
    fun onTypeSelect(type: ExpenseType) = _form.update {
        it?.copy(
            type = type,
            errors = emptyList(),
            message = if (type == ExpenseType.VARIABLE && !uiState.value.hasBudget) NO_BUDGET_MESSAGE else null
        )
    }

    fun onCategorySelect(id: Long) = _form.update { it?.copy(categoryId = id, errors = emptyList()) }

    fun onAmountChange(value: String) =
        _form.update { it?.copy(amountText = value, errors = emptyList(), message = null) }

    /** Al elegir la frecuencia se sugieren los días: mensual 15; quincenal 15 y 30. */
    fun onFrequencySelect(frequency: PaymentPeriod) = _form.update {
        it?.copy(frequency = frequency, dayTexts = defaultDays(frequency), errors = emptyList())
    }

    fun onDayChange(index: Int, value: String) = _form.update { current ->
        current?.copy(
            dayTexts = current.dayTexts.toMutableList().also { if (index in it.indices) it[index] = value },
            errors = emptyList()
        )
    }

    fun submit() {
        val form = _form.value ?: return
        val type = form.type
        if (type == null) {
            _form.update { it?.copy(message = NO_TYPE_MESSAGE) }
            return
        }
        if (type == ExpenseType.VARIABLE && !uiState.value.hasBudget) {
            _form.update { it?.copy(message = NO_BUDGET_MESSAGE) }
            return
        }

        viewModelScope.launch {
            when (type) {
                ExpenseType.FIJO -> {
                    val days = form.dayTexts.mapNotNull { it.trim().toIntOrNull() }
                    when (val v = validateFixedExpense(form.categoryId, form.amountText, form.frequency, days)) {
                        is Validation.Valid -> handleAdd(repository.addFixed(v.value))
                        is Validation.Invalid -> _form.update { it?.copy(errors = v.errors, message = null) }
                    }
                }
                ExpenseType.VARIABLE -> {
                    when (val v = validateVariableExpense(form.categoryId, form.amountText)) {
                        is Validation.Valid -> handleAdd(repository.addVariable(v.value))
                        is Validation.Invalid -> _form.update { it?.copy(errors = v.errors, message = null) }
                    }
                }
            }
        }
    }

    private fun handleAdd(result: AddExpenseResult) {
        when (result) {
            AddExpenseResult.Saved -> _form.value = null
            is AddExpenseResult.Rejected -> _form.update {
                it?.copy(message = addRejectionMessage(result.reason, result.maxFixed))
            }
        }
    }

    private fun addRejectionMessage(reason: ExpenseRejection, maxFixed: Money?): String = when (reason) {
        ExpenseRejection.NO_SALARY -> NO_SALARY_MESSAGE
        ExpenseRejection.NO_BUDGET -> NO_BUDGET_MESSAGE
        ExpenseRejection.FIXED_EXCEEDS_SALARY -> fixedExceedsMessage(maxFixed)
    }

    private fun fixedExceedsMessage(maxFixed: Money?): String =
        "Los gastos fijos más el ahorro no pueden superar tu salario. " +
                "Te quedan B/. ${maxFixed ?: Money.ZERO} por periodo de pago para gastos fijos."

    // =================== Modificar y eliminar (CU-08) ===================

    /** CU-08 pasos 1 a 3 con un gasto fijo: se edita su regla. */
    fun openEditFixed(rule: FixedRuleItem) {
        _edit.value = ExpenseEditState(
            id = rule.id,
            type = ExpenseType.FIJO,
            categoryId = rule.categoryId,
            amountText = rule.amount.toString(),
            frequency = rule.frequency,
            dayTexts = rule.payDays.map { it.toString() },
            effectiveDateText = formatEffectiveDate(repository.today())
        )
    }

    /** CU-08 pasos 1 a 3 con un gasto variable. */
    fun openEditVariable(expense: ExpenseItem) {
        _edit.value = ExpenseEditState(
            id = expense.id,
            type = ExpenseType.VARIABLE,
            categoryId = expense.categoryId,
            amountText = expense.amount.toString(),
            frequency = null,
            dayTexts = emptyList(),
            effectiveDateText = formatEffectiveDate(repository.today())
        )
    }

    fun dismissEdit() {
        _edit.value = null
    }

    fun onEditCategorySelect(id: Long) =
        _edit.update { it?.copy(categoryId = id, errors = emptyList(), message = null) }

    fun onEditAmountChange(value: String) =
        _edit.update { it?.copy(amountText = value, errors = emptyList(), message = null) }

    fun onEditFrequencySelect(frequency: PaymentPeriod) = _edit.update {
        if (it == null || it.frequency == frequency) {
            it
        } else {
            it.copy(frequency = frequency, dayTexts = defaultDays(frequency), errors = emptyList(), message = null)
        }
    }

    fun onEditDayChange(index: Int, value: String) = _edit.update { current ->
        current?.copy(
            dayTexts = current.dayTexts.toMutableList().also { if (index in it.indices) it[index] = value },
            errors = emptyList(),
            message = null
        )
    }

    fun onEditDateChange(value: String) = _edit.update { it?.copy(effectiveDateText = value, message = null) }

    fun askDelete() = _edit.update { it?.copy(confirmingDelete = true, message = null) }

    fun cancelDelete() = _edit.update { it?.copy(confirmingDelete = false, message = null) }

    /** CU-08 pasos 4 a 6. */
    fun saveEdit() {
        val edit = _edit.value ?: return
        viewModelScope.launch {
            when (edit.type) {
                ExpenseType.VARIABLE -> when (val v = validateVariableExpense(edit.categoryId, edit.amountText)) {
                    is Validation.Valid -> handleChange(repository.updateVariable(edit.id, v.value))
                    is Validation.Invalid -> _edit.update { it?.copy(errors = v.errors, message = null) }
                }
                ExpenseType.FIJO -> saveFixedEdit(edit)
            }
        }
    }

    private suspend fun saveFixedEdit(edit: ExpenseEditState) {
        val days = edit.dayTexts.mapNotNull { it.trim().toIntOrNull() }
        val validation = validateFixedExpense(edit.categoryId, edit.amountText, edit.frequency, days)
        val problems = mutableListOf<String>()
        val effectiveFrom = when (val date = parseEffectiveDate(edit.effectiveDateText, repository.today())) {
            is EffectiveDateResult.Ok -> date.date
            is EffectiveDateResult.Error -> {
                problems += date.reason.userMessage()
                null
            }
        }

        if (validation !is Validation.Valid || effectiveFrom == null) {
            val fieldErrors = (validation as? Validation.Invalid)?.errors ?: emptyList()
            _edit.update { it?.copy(errors = fieldErrors, message = problems.joinToString("\n").ifEmpty { null }) }
            return
        }
        handleChange(repository.updateFixed(edit.id, validation.value, effectiveFrom))
    }

    /** CU-08, flujo alterno 3a: eliminar, ya confirmado por el usuario. */
    fun confirmDelete() {
        val edit = _edit.value ?: return
        viewModelScope.launch {
            when (edit.type) {
                ExpenseType.VARIABLE -> handleChange(repository.deleteVariable(edit.id))
                ExpenseType.FIJO -> when (val date = parseEffectiveDate(edit.effectiveDateText, repository.today())) {
                    is EffectiveDateResult.Ok -> handleChange(repository.deleteFixed(edit.id, date.date))
                    is EffectiveDateResult.Error -> _edit.update { it?.copy(message = date.reason.userMessage()) }
                }
            }
        }
    }

    private fun handleChange(result: ChangeResult) {
        when (result) {
            ChangeResult.Saved -> _edit.value = null
            is ChangeResult.Rejected -> _edit.update {
                it?.copy(message = changeRejectionMessage(result), confirmingDelete = false)
            }
        }
    }

    private fun changeRejectionMessage(result: ChangeResult.Rejected): String = when (result.reason) {
        ChangeRejection.NOT_FOUND -> "No se encontró este gasto."
        ChangeRejection.FIXED_EXCEEDS_SALARY -> fixedExceedsMessage(result.maxFixed)
        ChangeRejection.CHANGE_ALREADY_SCHEDULED ->
            "Este gasto ya tiene un cambio con fecha futura. Espera a que entre en vigencia para modificarlo de nuevo."
        ChangeRejection.ONLY_SALARY, ChangeRejection.BUDGET_DOES_NOT_FIT -> "No se pudo aplicar el cambio."
    }
}

private fun defaultDays(frequency: PaymentPeriod): List<String> =
    if (frequency == PaymentPeriod.MENSUAL) listOf("15") else listOf("15", "30")

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
