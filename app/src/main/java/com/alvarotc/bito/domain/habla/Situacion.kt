package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * Las situaciones de Habi (spec §5.1, §5.7, §5.8, §5.9), cada una con su ámbito y la bolsa de su
 * núcleo. [conBotones]: la propuesta lleva acciones. [cuentaTope]: cuenta para el tope de
 * propuestas con botones del día (§5.2); nunca sin botones, y nunca las cinco exentas
 * (`CERRAR_DIA`, `TODO_HECHO`, `CONFIRMA_VALE`, `GORDO_ELEGIDO`, `PERMISO_AVISOS`).
 */
enum class Situacion(
    val ambito: Ambito,
    val nucleo: Bolsa?,
    val conBotones: Boolean,
    val cuentaTope: Boolean,
) {
    // HOY, por prioridad (§5.1)
    PERMISO_AVISOS(Ambito.HOY, Bolsa.NU_PERMISO_AVISOS, conBotones = true, cuentaTope = false),
    VUELTA(Ambito.HOY, Bolsa.NU_VUELTA, conBotones = true, cuentaTope = true),
    DIA_DURO(Ambito.HOY, Bolsa.NU_DIA_DURO, conBotones = true, cuentaTope = true),
    CERRAR_DIA(Ambito.HOY, Bolsa.NU_CERRAR_DIA, conBotones = true, cuentaTope = false),
    TODO_HECHO(Ambito.HOY, Bolsa.NU_TODO_HECHO, conBotones = true, cuentaTope = false),
    ELEGIR_GORDO(Ambito.HOY, Bolsa.NU_ELEGIR_GORDO, conBotones = true, cuentaTope = true),
    GORDO_SIN_EMPEZAR(Ambito.HOY, Bolsa.NU_GORDO_SIN_EMPEZAR, conBotones = true, cuentaTope = true),
    AYER_SE_ESCAPO(Ambito.HOY, Bolsa.NU_AYER_ESCAPO, conBotones = true, cuentaTope = true),
    RACHA_CERCA(Ambito.HOY, Bolsa.NU_RACHA_CERCA, conBotones = true, cuentaTope = true),
    DIA_FLOJO(Ambito.HOY, Bolsa.NU_DIA_FLOJO, conBotones = true, cuentaTope = true),
    HORA_HABITUAL(Ambito.HOY, Bolsa.NU_HORA_HABITUAL, conBotones = true, cuentaTope = true),
    TAREA_VENCE(Ambito.HOY, Bolsa.NU_TAREA_VENCE, conBotones = true, cuentaTope = true),
    SALUDO(Ambito.HOY, Bolsa.NU_SALUDO, conBotones = false, cuentaTope = false),

    // HOY, como respuesta (§5.1, segunda tabla)
    CONFIRMA_VALE(Ambito.HOY, Bolsa.NU_CONFIRMA_VALE, conBotones = true, cuentaTope = false),
    GORDO_ELEGIDO(Ambito.HOY, Bolsa.NU_GORDO_ELEGIDO, conBotones = true, cuentaTope = false),
    TRAS_HOY_NO(Ambito.HOY, Bolsa.NU_TRAS_HOY_NO, conBotones = false, cuentaTope = false),
    NOCHE(Ambito.HOY, Bolsa.NU_NOCHE, conBotones = false, cuentaTope = false),

    // FICHA (§5.7)
    FI_PAUSA(Ambito.FICHA, Bolsa.FI_PAUSA, conBotones = false, cuentaTope = false),
    FI_HOY_FALTA(Ambito.FICHA, Bolsa.FI_HOY_FALTA, conBotones = true, cuentaTope = true),
    FI_OBJETIVO(Ambito.FICHA, Bolsa.FI_OBJETIVO, conBotones = true, cuentaTope = true),
    FI_NUEVO(Ambito.FICHA, Bolsa.FI_NUEVO, conBotones = false, cuentaTope = false),
    FI_RACHA_VS_MEJOR(Ambito.FICHA, Bolsa.FI_RACHA_VS_MEJOR, conBotones = false, cuentaTope = false),
    FI_LIMPIO(Ambito.FICHA, Bolsa.FI_LIMPIO, conBotones = false, cuentaTope = false),
    FI_MEJOR_DIA(Ambito.FICHA, Bolsa.FI_MEJOR_DIA, conBotones = false, cuentaTope = false),
    FI_CONSTANCIA(Ambito.FICHA, Bolsa.FI_CONSTANCIA, conBotones = false, cuentaTope = false),

    // CAMINO (§5.8)
    CA_RADIANTE(Ambito.CAMINO, Bolsa.CA_RADIANTE, conBotones = false, cuentaTope = false),
    CA_NORMAL(Ambito.CAMINO, Bolsa.CA_NORMAL, conBotones = false, cuentaTope = false),
    CA_MUSTIO(Ambito.CAMINO, Bolsa.CA_MUSTIO, conBotones = false, cuentaTope = false),
    CA_AUSENCIA(Ambito.CAMINO, Bolsa.CA_AUSENCIA, conBotones = false, cuentaTope = false),

    // CIERRE (§5.9)
    CIE_GORDO_HECHO(Ambito.CIERRE, Bolsa.CIE_GORDO_HECHO, conBotones = false, cuentaTope = false),
    CIE_LOGRO(Ambito.CIERRE, Bolsa.CIE_LOGRO, conBotones = false, cuentaTope = false),
    CIE_RACHA_AVANZA(Ambito.CIERRE, Bolsa.CIE_RACHA_AVANZA, conBotones = false, cuentaTope = false),
    CIE_NADA_PENDIENTE(Ambito.CIERRE, Bolsa.CIE_NADA_PENDIENTE, conBotones = false, cuentaTope = false),
    CIE_SENCILLO(Ambito.CIERRE, Bolsa.CIE_SENCILLO, conBotones = false, cuentaTope = false),
}

/** Lo que una regla propone antes de redactarlo (spec §4.3). [acciones]: la primera, principal. */
data class Candidata(
    val situacion: Situacion,
    /** Id de hábito o de tarea. */
    val ref: String?,
    val huecos: Map<Hueco, ValorHueco>,
    val acciones: List<Respuesta>,
    val destino: Destino? = null,
    val hora: Int? = null,
    /** Desempate dentro de la situación: mayor gana. */
    val peso: Int = 0,
    /** Otra bolsa de núcleo cuando la situación tiene variantes (`nu_regular`, `nu_permiso_alarmas`…). */
    val nucleo: Bolsa? = situacion.nucleo,
)

/** Una regla por situación (spec §4.3): las candidatas que salen de los hechos. */
fun interface Regla {
    fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata>
}

/**
 * El id estable de una propuesta, «SITUACION:ref:día» con ref vacío si no hay (spec §4.2). Es el
 * `ref` de un `RESPUESTA` en la memoria (§5.11), que guarda en `valor` el ordinal de la respuesta.
 */
fun idPropuesta(
    situacion: Situacion,
    ref: String?,
    dia: LogicalDay,
): String = "${situacion.name}:${ref.orEmpty()}:$dia"
