package com.alvarotc.bito.domain.habla

/** Los números de las reglas de Habi (spec §5), con nombre para poder afinarlos sin tocar las reglas. */
object Ritmo {
    /** §5.2: propuestas con botones al día. */
    const val TOPE_PROPUESTAS_DIA = 4
    const val ESPERA_TRAS_RESPONDER_MIN = 90
    const val TOPE_ACEPTADOS_DIA = 2

    /** §5.6: días lógicos sin actividad para la vuelta (el umbral de DRAMATIC). */
    const val DIAS_AUSENCIA = 3
    const val DIAS_AUSENCIA_SEMANAL = 14
    const val PERMISO_CADA_DIAS = 7

    /** §5.4: lo gordo. */
    const val GORDO_HASTA_MIN = 13 * 60
    const val GORDO_ESPERA_MIN = 120
    const val GORDO_TARDE_MIN = 12 * 60

    /** §5.1: hora habitual y caducidad. */
    const val HORA_HABITUAL_VENTANA_MIN = 60
    const val HORA_HABITUAL_CADUCA_MIN = 90
    const val CADUCA_ANTES_NOCHE_MIN = 30

    /** §5.1 n.º 10: día flojo. */
    const val DIA_FLOJO_SEMANAS = 6
    const val DIA_FLOJO_APARICIONES = 8
    const val DIA_FLOJO_MIN_JUZGADAS = 5
    const val DIA_FLOJO_PCT_MAX = 50
    const val DIA_FLOJO_PUNTOS = 25
    const val DIA_FLOJO_CADA_DIAS = 14

    /** §5.1 n.º 9: hitos de racha y a cuántos periodos se avisa. */
    val HITOS = listOf(7, 30, 100, 365)
    const val HITO_A_PERIODOS = 2

    /** §5.7: la nota de la ficha. */
    const val FI_OBJETIVO_PCT = 40
    const val FI_OBJETIVO_JUZGADOS = 14
    const val FI_NO_HACE_FALTA_DIAS = 7
    const val FI_MEJOR_DIA_PUNTOS = 15
    const val FI_NUEVO_DIAS = 7
    const val FI_RACHA_MIN = 3

    /** §5.3: la hora del «Vale, a las…». */
    const val HORA_MIN_DELANTE = 30
    const val HORA_ANTES_NOCHE = 15
    const val HORA_HABITUAL_REGISTROS = 5
    const val HORA_HABITUAL_DIAS = 30

    /** §5.1 n.º 9 y §5.6: racha redonda (múltiplo de) y racha recuperada. */
    const val RACHA_REDONDA_CADA = 5
    const val RACHA_RECUPERADA = 8
    const val RACHA_ROTA_MIN = 7

    /** §4.3: cuántos periodos juzgados cuentan para el cumplimiento reciente. */
    const val PERIODOS_JUZGADOS = 30

    /** §6.1: «Más tarde» en un aviso aceptado. */
    const val MAS_TARDE_MIN = 30
    const val MAS_TARDE_VECES = 2

    /** §5.5: propuestas con botones en un día duro. */
    const val DIA_DURO_TOPE = 2

    /** §5.11: recuerdos. */
    const val RECUERDO_MAX_DIAS = 90
    const val PATRON_ANIMO_CADA_DIAS = 30

    /** §5.12: anti-repetición. */
    const val NUCLEO_DIAS = 7
    const val APERTURA_CIERRE_USOS = 5
    const val FRASE_DIAS = 30
    const val PROB_APERTURA_TARJETA = 0.5
    const val PROB_CIERRE = 0.4

    /** §4.6: cuánto se guarda. */
    const val MEMORIA_DIAS = 400
    const val USOS_DIAS = 60
}
