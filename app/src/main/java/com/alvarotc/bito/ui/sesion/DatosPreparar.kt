package com.alvarotc.bito.ui.sesion

import androidx.compose.runtime.Stable
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.sesion.Fondo
import com.alvarotc.bito.sesion.FondoMudo
import com.alvarotc.bito.sesion.NoMolestar
import com.alvarotc.bito.ui.habi.HabiSpec

/** Cómo se llama lo que se va a hacer y su primer paso (las tareas lo tienen; un hábito no). */
data class ObjetivoUi(
    val nombre: String,
    val primerPaso: String?,
)

/** Una opción de la hoja de tarea: las tareas de hoy. */
data class OpcionTarea(
    val id: String,
    val nombre: String,
    val primerPaso: String?,
)

/**
 * Lo que Preparar necesita aparte del motor: las tareas de hoy, cómo es Habi, el fondo (el mismo que suena en la
 * sesión), No molestar y dónde dar un aviso. Solo agrupa parámetros: quien monta Preparar lo rellena.
 */
@Stable
class DatosPreparar(
    val tareas: List<OpcionTarea> = emptyList(),
    val spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
    val fondo: Fondo = FondoMudo,
    val noMolestar: NoMolestar? = null,
    val aviso: (String) -> Unit = {},
    /** El nombre de un objetivo que no está entre [tareas] (un hábito, una tarea que no toca hoy). */
    val objetivo: suspend (Objetivo) -> ObjetivoUi? = { null },
)
