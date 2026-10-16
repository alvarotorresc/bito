package com.alvarotc.bito.domain.habla

/** El orden de prioridad de cada ámbito, en un solo sitio (spec §4.3). Las respuestas no están: salen como respuesta. */
object Prioridades {
    /** §5.1, de 1 a 13. */
    val HOY: List<Situacion> =
        listOf(
            Situacion.PERMISO_AVISOS,
            Situacion.VUELTA,
            Situacion.DIA_DURO,
            Situacion.CERRAR_DIA,
            Situacion.TODO_HECHO,
            Situacion.ELEGIR_GORDO,
            Situacion.GORDO_SIN_EMPEZAR,
            Situacion.AYER_SE_ESCAPO,
            Situacion.RACHA_CERCA,
            Situacion.DIA_FLOJO,
            Situacion.HORA_HABITUAL,
            Situacion.TAREA_VENCE,
            Situacion.SALUDO,
        )

    /** §5.7. */
    val FICHA: List<Situacion> =
        listOf(
            Situacion.FI_PAUSA,
            Situacion.FI_HOY_FALTA,
            Situacion.FI_OBJETIVO,
            Situacion.FI_NUEVO,
            Situacion.FI_RACHA_VS_MEJOR,
            Situacion.FI_LIMPIO,
            Situacion.FI_MEJOR_DIA,
            Situacion.FI_CONSTANCIA,
        )

    /** §5.8: la ausencia manda sobre el ánimo de la semana. */
    val CAMINO: List<Situacion> =
        listOf(Situacion.CA_AUSENCIA, Situacion.CA_RADIANTE, Situacion.CA_MUSTIO, Situacion.CA_NORMAL)

    /** §5.9.3. */
    val CIERRE: List<Situacion> =
        listOf(
            Situacion.CIE_GORDO_HECHO,
            Situacion.CIE_LOGRO,
            Situacion.CIE_RACHA_AVANZA,
            Situacion.CIE_NADA_PENDIENTE,
            Situacion.CIE_SENCILLO,
        )
}
