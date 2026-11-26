package com.grupo2.currencyv1.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo2.currencyv1.data.AddExpenseResult
import com.grupo2.currencyv1.data.ExpenseRejection
import com.grupo2.currencyv1.data.ExpenseRepository
import com.grupo2.currencyv1.domain.ExpenseType
import com.grupo2.currencyv1.domain.FieldError
import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.PaymentPeriod
import com.grupo2.currencyv1.domain.Validation
import com.grupo2.currencyv1.domain.validateFixedExpense
import com.grupo2.currencyv1.domain.validateVariableExpense
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
data class FixedRuleItem(val id: Long, val categoryName: String, val amount: Money, val schedule: String)

/** Un gasto ocurrido (variable o descuento automático de un fijo). */
data class ExpenseItem(val id: Long, val categoryName: String, val amount: Money, val detail: String)

data class ExpensesUiState(
    val loaded: Boolean = false,
    val hasSalary: Boolean = false,
    val hasBudget: Boolean = false,
    val categories: List<CategoryItem> = emptyList(),
    val fixedRules: List<FixedRuleItem> = emptyList(),
    val expenses: List<ExpenseItem> = emptyList()
)

/** Estado del formulario de gasto (null = cerrado). */
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
                    categoryName = names[it.categoriaId] ?: "",
                    amount = Money(it.montoCentavos),
                    schedule = payScheduleText(it.frecuencia, it.diasDescuento)
                )
            },
            expenses = expenses.map {
                val tipo = if (it.gasto.tipo == ExpenseType.FIJO) "Fijo (descuento automático)" else "Variable"
                ExpenseItem(
                    id = it.gasto.id,
                    categoryName = it.categoriaNombre,
                    amount = Money(it.gasto.montoCentavos),
                    detail = "$tipo · ${it.gasto.fecha.format(DATE_FORMAT)}"
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    private val _form = MutableStateFlow<ExpenseFormState?>(null)
    val form: StateFlow<ExpenseFormState?> = _form.asStateFlow()

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
        it?.copy(
            frequency = frequency,
            dayTexts = if (frequency == PaymentPeriod.MENSUAL) listOf("15") else listOf("15", "30"),
            errors = emptyList()
        )
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
                        is Validation.Valid -> handle(repository.addFixed(v.value))
                        is Validation.Invalid -> _form.update { it?.copy(errors = v.errors, message = null) }
                    }
                }
                ExpenseType.VARIABLE -> {
                    when (val v = validateVariableExpense(form.categoryId, form.amountText)) {
                        is Validation.Valid -> handle(repository.addVariable(v.value))
                        is Validation.Invalid -> _form.update { it?.copy(errors = v.errors, message = null) }
                    }
                }
            }
        }
    }

    private fun handle(result: AddExpenseResult) {
        when (result) {
            AddExpenseResult.Saved -> _form.value = null
            is AddExpenseResult.Rejected -> _form.update {
                it?.copy(message = rejectionMessage(result.reason, result.maxFixed))
            }
        }
    }

    private fun rejectionMessage(reason: ExpenseRejection, maxFixed: Money?): String = when (reason) {
        ExpenseRejection.NO_SALARY -> NO_SALARY_MESSAGE
        ExpenseRejection.NO_BUDGET -> NO_BUDGET_MESSAGE
        ExpenseRejection.FIXED_EXCEEDS_SALARY ->
            "Los gastos fijos más el ahorro no pueden superar tu salario. " +
                "Te quedan B/. ${maxFixed ?: Money.ZERO} por periodo de pago para gastos fijos."
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
