package com.grupo2.currencyv1.domain

/** Tipo de ingreso: fuente periódica ("salario") o eventual ("extra"). */
enum class IncomeType { SALARIO, EXTRA }

/** Tipo de gasto: obligación con día de descuento (fijo) o registrado a mano (variable). */
enum class ExpenseType { FIJO, VARIABLE }

/** Tipos de aviso que la app puede enviar (se usan en la iteración 8). */
enum class NotificationType { GASTO_FIJO_DESCONTADO, LIMITE_BAJO, EXCEDENTE }
