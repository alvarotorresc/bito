package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.Personality

/** Los huecos con nombre que una frase rellena. */
enum class Hueco(
    val clave: String,
) {
    NOMBRE("nombre"),
    HABITO("habito"),
    TAREA("tarea"),
    GORDO("gordo"),
    PRIMER_PASO("primer_paso"),
    HORA("hora"),
    MINUTOS("minutos"),
    N("n"),
    RACHA("racha"),
    FALTAN("faltan"),
    BITOS("bitos"),
    DIA_SEMANA("dia_semana"),
    DIAS_FUERA("dias_fuera"),
    HECHOS("hechos"),
    TOTAL("total"),
    MEJOR("mejor"),
    PCT("pct"),
    OBJETIVO("objetivo"),
    FECHA("fecha"),
    LISTA("lista"),
}

/** Tipos de trozo con los que se compone una frase. */
enum class TipoTrozo { APERTURA, NUCLEO, CIERRE, RECUERDO, SUELTA }

/** Placeholder del contrato: la Tarea 4 lo sustituye por la lista completa de bolsas. */
enum class Bolsa(
    val clave: String,
    val tipo: TipoTrozo,
    val huecos: Set<Hueco>,
    val minimo: Int,
) {
    PROVISIONAL("provisional", TipoTrozo.SUELTA, emptySet(), 0),
}

sealed interface ValorHueco {
    data class Texto(
        val v: String,
    ) : ValorHueco

    data class Numero(
        val v: Int,
    ) : ValorHueco

    data class Hora(
        val minuto: Int,
    ) : ValorHueco

    data class Lista(
        val v: List<String>,
    ) : ValorHueco

    data class DiaSemana(
        val v: java.time.DayOfWeek,
    ) : ValorHueco

    data class Fecha(
        val epochDay: Long,
    ) : ValorHueco
}

/** Se guarda como «BOLSA:VOZ:índice». */
data class ClaveTrozo(
    val bolsa: Bolsa,
    val voz: Personality,
    val indice: Int,
)
