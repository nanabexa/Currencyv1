package com.grupo2.currencyv1.ui.incomes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo2.currencyv1.data.IncomeRepository
import com.grupo2.currencyv1.data.IngresoEntity
import com.grupo2.currencyv1.domain.FieldError
import com.grupo2.currencyv1.domain.IncomeType
import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.PaymentPeriod
import com.grupo2.currencyv1.domain.Validation
import com.grupo2.currencyv1.domain.validateExtraIncome
import com.grupo2.currencyv1.domain.validateSalary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/** Una fila de la lista de ingresos. */
data class IncomeItem(
    val id: Long,
    val name: String,
    val amount: Money,
    val detail: String
)

data class IncomesUiState(
    /** false hasta que Room entrega los primeros datos (evita mostrar "sin salario" por un instante). */
    val loaded: Boolean = false,
    val salaries: List<IncomeItem> = emptyList(),
    val extras: List<IncomeItem> = emptyList(),
    /** RN-08: suma de todas las fuentes, mostrada como un único salario. */
    val salaryTotal: Money = Money.ZERO,
    /** Periodo y días ya definidos por la primera fuente (null si aún no hay salario). */
    val period: PaymentPeriod? = null,
    val payDays: List<Int> = emptyList()
)

/** Estado del formulario de ingreso (null = formulario cerrado). */
data class IncomeFormState(
    val type: IncomeType,
    /** true solo para el primer salario: pide periodo y días. Las demás fuentes los heredan. */
    val needsPeriod: Boolean,
    val name: String = "",
    val amountText: String = "",
    val period: PaymentPeriod? = null,
    val dayTexts: List<String> = emptyList(),
    val errors: List<FieldError> = emptyList()
)

class IncomesViewModel(private val repository: IncomeRepository) : ViewModel() {

    val uiState: StateFlow<IncomesUiState> =
        combine(repository.observeSalaries(), repository.observeExtras()) { salaries, extras ->
            val first = salaries.firstOrNull()
            IncomesUiState(
                loaded = true,
                salaries = salaries.map { it.toItem() },
                extras = extras.map { it.toItem() },
                salaryTotal = salaries.fold(Money.ZERO) { total, s -> total + Money(s.montoCentavos) },
                period = first?.periodo,
                payDays = first?.diasAcreditacion ?: emptyList()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomesUiState())

    private val _form = MutableStateFlow<IncomeFormState?>(null)
    val form: StateFlow<IncomeFormState?> = _form.asStateFlow()

    // ---- Abrir y cerrar el formulario ----

    /** Primer salario (pide periodo y días) o "Agregar otra fuente" (los hereda). */
    fun openSalaryForm() {
        val firstSalary = uiState.value.salaries.isEmpty()
        _form.value = IncomeFormState(type = IncomeType.SALARIO, needsPeriod = firstSalary)
    }

    /** Ingreso extra: solo nombre y monto (CU-01, flujo alterno 1a). */
    fun openExtraForm() {
        _form.value = IncomeFormState(type = IncomeType.EXTRA, needsPeriod = false)
    }

    fun dismissForm() {
        _form.value = null
    }

    // ---- Cambios en los campos (al editar se limpian los errores mostrados) ----

    fun onNameChange(value: String) = _form.update { it?.copy(name = value, errors = emptyList()) }

    fun onAmountChange(value: String) = _form.update { it?.copy(amountText = value, errors = emptyList()) }

    /** Al elegir el periodo se sugieren los días típicos: mensual 30; quincenal 15 y 30. */
    fun onPeriodSelect(period: PaymentPeriod) = _form.update {
        it?.copy(
            period = period,
            dayTexts = if (period == PaymentPeriod.MENSUAL) listOf("30") else listOf("15", "30"),
            errors = emptyList()
        )
    }

    fun onDayChange(index: Int, value: String) = _form.update { current ->
        current?.copy(
            dayTexts = current.dayTexts.toMutableList().also { if (index in it.indices) it[index] = value },
            errors = emptyList()
        )
    }

    // ---- Guardar ----

    fun submit() {
        val form = _form.value ?: return
        val state = uiState.value

        viewModelScope.launch {
            when (form.type) {
                IncomeType.SALARIO -> {
                    // Una fuente adicional hereda el periodo y los días ya definidos.
                    val period = if (form.needsPeriod) form.period else state.period
                    val days = if (form.needsPeriod) {
                        form.dayTexts.mapNotNull { it.trim().toIntOrNull() }
                    } else {
                        state.payDays
                    }
                    when (val result = validateSalary(form.name, form.amountText, period, days, state.period)) {
                        is Validation.Valid -> {
                            repository.addSalary(result.value)
                            _form.value = null
                        }
                        is Validation.Invalid -> _form.update { it?.copy(errors = result.errors) }
                    }
                }
                IncomeType.EXTRA -> {
                    when (val result = validateExtraIncome(form.name, form.amountText)) {
                        is Validation.Valid -> {
                            repository.addExtra(result.value)
                            _form.value = null
                        }
                        is Validation.Invalid -> _form.update { it?.copy(errors = result.errors) }
                    }
                }
            }
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun IngresoEntity.toItem(): IncomeItem = IncomeItem(
    id = id,
    name = nombre,
    amount = Money(montoCentavos),
    detail = if (tipo == IncomeType.SALARIO) payScheduleText(periodo, diasAcreditacion) else fecha.format(DATE_FORMAT)
)

/** "Quincenal · días 15 y 30" / "Mensual · día 30". */
fun payScheduleText(period: PaymentPeriod?, days: List<Int>): String = when (period) {
    PaymentPeriod.MENSUAL -> "Mensual · día ${days.joinToString()}"
    PaymentPeriod.QUINCENAL -> "Quincenal · días ${days.joinToString(" y ")}"
    null -> ""
}
