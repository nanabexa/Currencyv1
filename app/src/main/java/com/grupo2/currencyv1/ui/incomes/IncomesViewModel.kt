package com.grupo2.currencyv1.ui.incomes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo2.currencyv1.data.ChangeRejection
import com.grupo2.currencyv1.data.ChangeResult
import com.grupo2.currencyv1.data.IncomeRepository
import com.grupo2.currencyv1.data.IngresoEntity
import com.grupo2.currencyv1.domain.EffectiveDateResult
import com.grupo2.currencyv1.domain.FieldError
import com.grupo2.currencyv1.domain.IncomeType
import com.grupo2.currencyv1.domain.Money
import com.grupo2.currencyv1.domain.Percent
import com.grupo2.currencyv1.domain.PaymentPeriod
import com.grupo2.currencyv1.domain.PercentResult
import com.grupo2.currencyv1.domain.Validation
import com.grupo2.currencyv1.domain.formatEffectiveDate
import com.grupo2.currencyv1.domain.parseEffectiveDate
import com.grupo2.currencyv1.domain.parsePercent
import com.grupo2.currencyv1.domain.validateExtraIncome
import com.grupo2.currencyv1.domain.validateSalary
import com.grupo2.currencyv1.ui.common.userMessage
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
    val detail: String,
    val type: IncomeType,
    val period: PaymentPeriod?,
    val payDays: List<Int>,
    /** true si ya hay un cambio de salario con fecha futura pendiente. */
    val scheduledChange: Boolean
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

/** Estado del formulario de alta de ingreso (null = cerrado). */
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

/** Estado del diálogo de edición o eliminación de un ingreso (null = cerrado). CU-09. */
data class IncomeEditState(
    val id: Long,
    val type: IncomeType,
    val name: String,
    val amountText: String,
    val period: PaymentPeriod?,
    val dayTexts: List<String>,
    /** Fecha desde la que rige el cambio de un salario (dd/MM/aaaa). Por defecto, hoy. */
    val effectiveDateText: String,
    /** % de ahorro actual; null si todavía no hay presupuesto (entonces no se pregunta por el ahorro). */
    val currentAhorro: Percent? = null,
    val keepSavings: Boolean = true,
    val newAhorroText: String = "",
    val confirmingDelete: Boolean = false,
    val errors: List<FieldError> = emptyList(),
    val message: String? = null
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

    private val _edit = MutableStateFlow<IncomeEditState?>(null)
    val edit: StateFlow<IncomeEditState?> = _edit.asStateFlow()

    // =================== Alta (CU-01) ===================

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

    fun onNameChange(value: String) = _form.update { it?.copy(name = value, errors = emptyList()) }

    fun onAmountChange(value: String) = _form.update { it?.copy(amountText = value, errors = emptyList()) }

    /** Al elegir el periodo se sugieren los días típicos: mensual 30; quincenal 15 y 30. */
    fun onPeriodSelect(period: PaymentPeriod) = _form.update {
        it?.copy(period = period, dayTexts = defaultDays(period), errors = emptyList())
    }

    fun onDayChange(index: Int, value: String) = _form.update { current ->
        current?.copy(
            dayTexts = current.dayTexts.toMutableList().also { if (index in it.indices) it[index] = value },
            errors = emptyList()
        )
    }

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

    // =================== Modificar y eliminar (CU-09) ===================

    /** CU-09 pasos 1 a 3: al tocar un ingreso se abre su edición con los datos actuales. */
    fun openEdit(item: IncomeItem) {
        _edit.value = IncomeEditState(
            id = item.id,
            type = item.type,
            name = item.name,
            amountText = item.amount.toString(),
            period = item.period,
            dayTexts = item.payDays.map { it.toString() },
            effectiveDateText = formatEffectiveDate(repository.today())
        )
        if (item.type == IncomeType.SALARIO) {
            // Paso 5: si hay presupuesto, se pregunta si se conserva el % de ahorro.
            viewModelScope.launch {
                val ahorro = repository.currentAhorro()
                _edit.update { it?.copy(currentAhorro = ahorro) }
            }
        }
    }

    fun dismissEdit() {
        _edit.value = null
    }

    fun onEditNameChange(value: String) = _edit.update { it?.copy(name = value, errors = emptyList(), message = null) }

    fun onEditAmountChange(value: String) =
        _edit.update { it?.copy(amountText = value, errors = emptyList(), message = null) }

    fun onEditPeriodSelect(period: PaymentPeriod) = _edit.update {
        if (it == null || it.period == period) {
            it
        } else {
            it.copy(period = period, dayTexts = defaultDays(period), errors = emptyList(), message = null)
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

    fun onKeepSavingsChange(keep: Boolean) = _edit.update { it?.copy(keepSavings = keep, message = null) }

    fun onNewAhorroChange(value: String) = _edit.update { it?.copy(newAhorroText = value, message = null) }

    fun askDelete() = _edit.update { it?.copy(confirmingDelete = true, message = null) }

    fun cancelDelete() = _edit.update { it?.copy(confirmingDelete = false, message = null) }

    /** CU-09 pasos 4 a 8. */
    fun saveEdit() {
        val edit = _edit.value ?: return
        viewModelScope.launch {
            when (edit.type) {
                IncomeType.EXTRA -> when (val v = validateExtraIncome(edit.name, edit.amountText)) {
                    is Validation.Valid -> handle(repository.updateExtra(edit.id, v.value))
                    is Validation.Invalid -> _edit.update { it?.copy(errors = v.errors, message = null) }
                }
                IncomeType.SALARIO -> saveSalaryEdit(edit)
            }
        }
    }

    private suspend fun saveSalaryEdit(edit: IncomeEditState) {
        val days = edit.dayTexts.mapNotNull { it.trim().toIntOrNull() }
        val validation = validateSalary(edit.name, edit.amountText, edit.period, days, null)
        val problems = mutableListOf<String>()

        val effectiveFrom = parseEffectiveDate(edit.effectiveDateText, repository.today())
            .let { result ->
                when (result) {
                    is EffectiveDateResult.Ok -> result.date
                    is EffectiveDateResult.Error -> {
                        problems += result.reason.userMessage()
                        null
                    }
                }
            }

        var newAhorro: Percent? = null
        if (edit.currentAhorro != null && !edit.keepSavings) {
            when (val parsed = parsePercent(edit.newAhorroText)) {
                is PercentResult.Ok -> newAhorro = parsed.percent
                is PercentResult.Error -> problems += parsed.reason.userMessage()
            }
        }

        if (validation !is Validation.Valid || effectiveFrom == null || problems.isNotEmpty()) {
            val fieldErrors = (validation as? Validation.Invalid)?.errors ?: emptyList()
            _edit.update { it?.copy(errors = fieldErrors, message = problems.joinToString("\n").ifEmpty { null }) }
            return
        }
        handle(repository.updateSalary(edit.id, validation.value, effectiveFrom, newAhorro))
    }

    /** CU-09, flujo alterno 3a: eliminar, ya confirmado por el usuario. */
    fun confirmDelete() {
        val edit = _edit.value ?: return
        viewModelScope.launch {
            when (edit.type) {
                IncomeType.EXTRA -> handle(repository.deleteExtra(edit.id))
                IncomeType.SALARIO -> when (val date = parseEffectiveDate(edit.effectiveDateText, repository.today())) {
                    is EffectiveDateResult.Ok -> handle(repository.deleteSalary(edit.id, date.date))
                    is EffectiveDateResult.Error ->
                        _edit.update { it?.copy(message = date.reason.userMessage()) }
                }
            }
        }
    }

    private fun handle(result: ChangeResult) {
        when (result) {
            ChangeResult.Saved -> _edit.value = null
            is ChangeResult.Rejected -> _edit.update {
                it?.copy(message = rejectionMessage(result), confirmingDelete = false)
            }
        }
    }

    private fun rejectionMessage(result: ChangeResult.Rejected): String = when (result.reason) {
        ChangeRejection.NOT_FOUND -> "No se encontró este ingreso."
        ChangeRejection.ONLY_SALARY -> "No puedes eliminar tu único salario. Puedes editarlo."
        ChangeRejection.CHANGE_ALREADY_SCHEDULED ->
            "Ya tienes un cambio de salario con fecha futura. Espera a que entre en vigencia para modificarlo de nuevo."
        ChangeRejection.BUDGET_DOES_NOT_FIT ->
            "Con este cambio tus gastos fijos y tu ahorro superarían tu salario. " +
                    "El ahorro máximo permitido sería ${result.maxAhorro ?: "0.00"} %. " +
                    "Elige \"Definir uno nuevo\" y baja el porcentaje de ahorro."
        ChangeRejection.FIXED_EXCEEDS_SALARY -> "Los gastos fijos superarían tu salario."
    }
}

private fun defaultDays(period: PaymentPeriod): List<String> =
    if (period == PaymentPeriod.MENSUAL) listOf("30") else listOf("15", "30")

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun IngresoEntity.toItem(): IncomeItem = IncomeItem(
    id = id,
    name = nombre,
    amount = Money(montoCentavos),
    detail = if (tipo == IncomeType.SALARIO) payScheduleText(periodo, diasAcreditacion) else fecha.format(DATE_FORMAT),
    type = tipo,
    period = periodo,
    payDays = diasAcreditacion,
    scheduledChange = vigenteHasta != null
)

/** "Quincenal · días 15 y 30" / "Mensual · día 30". */
fun payScheduleText(period: PaymentPeriod?, days: List<Int>): String = when (period) {
    PaymentPeriod.MENSUAL -> "Mensual · día ${days.joinToString()}"
    PaymentPeriod.QUINCENAL -> "Quincenal · días ${days.joinToString(" y ")}"
    null -> ""
}
