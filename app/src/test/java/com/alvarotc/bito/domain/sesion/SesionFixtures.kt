package com.alvarotc.bito.domain.sesion

import com.alvarotc.bito.data.settings.SessionBackground

/** Los valores por defecto de la pieza 2: 25 + 5, largo de 15, rondas de 4, cuatro bloques. */
fun planSesion(
    bloques: Int = 4,
    entrada: Entrada = Entrada.RESPIRAR,
    minutosFoco: Int = 25,
    descansoCorto: Int = 5,
    descansoLargo: Int = 15,
    rondas: Int = 4,
    descansoSolo: Boolean = true,
    objetivo: Objetivo = Objetivo.Tarea("t1"),
): PlanSesion =
    PlanSesion(
        objetivo = objetivo,
        minutosFoco = minutosFoco,
        entrada = entrada,
        bloques = bloques,
        descansoCorto = descansoCorto,
        descansoLargo = descansoLargo,
        rondas = rondas,
        descansoSolo = descansoSolo,
        fondo = SessionBackground.RAIN,
        noMolestar = false,
    )

const val INICIO_PARED = 1_800_000_000_000L

/** Unas catorce horas de arranque al Empezar. */
const val ANCLA_ELAPSED = 50_000_000L

fun registroSesion(
    plan: PlanSesion = planSesion(),
    eventos: List<Evento> = emptyList(),
    inicioPared: Long = INICIO_PARED,
    anclaElapsed: Long = ANCLA_ELAPSED,
    arranques: Int? = null,
): RegistroSesion =
    RegistroSesion(
        id = "s1",
        plan = plan,
        eventos = eventos,
        inicioPared = inicioPared,
        anclaElapsed = anclaElapsed,
        arranque = FocusClock.bootSignatureOf(inicioPared, anclaElapsed),
        filtroPrevio = null,
        inicioDia = 20_000,
        arranques = arranques,
    )
