package com.alvarotc.bito.domain.habla

enum class TipoRefGordo { HABITO, TAREA }

/** Una cosa que el usuario ha puesto como «lo gordo» del día. */
data class RefGordo(
    val tipo: TipoRefGordo,
    val id: String,
)
