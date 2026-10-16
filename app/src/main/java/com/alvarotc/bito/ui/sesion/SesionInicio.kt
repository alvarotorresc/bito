package com.alvarotc.bito.ui.sesion

import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.habla.CatalogoHabla
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.sesion.Aire
import com.alvarotc.bito.sesion.Fondo
import com.alvarotc.bito.sesion.FondoMudo
import com.alvarotc.bito.sesion.MotorSesion
import com.alvarotc.bito.sesion.NoMolestar
import kotlinx.coroutines.flow.StateFlow

/** El nombre de un objetivo y, si lo tiene, su primer paso (los hábitos no tienen). */
data class DatosObjetivo(
    val nombre: String,
    val primerPaso: String?,
)

/**
 * Lo que el Inicio necesita para vivir la sesión y lo que los bloques leen de él por [LocalSesionInicio]: el motor,
 * el aire (la música de la cabecera), los ajustes (nombre y voz de quien usa la app), el fondo que suena, No molestar,
 * el nombre y primer paso del objetivo, el catálogo de frases y el contexto del día.
 */
class SesionInicio(
    val motor: MotorSesion,
    val aire: Aire,
    val ajustes: SettingsRepository,
    val fondo: Fondo = FondoMudo,
    private val noMolestar: NoMolestar? = null,
    private val objetivos: suspend (Objetivo) -> DatosObjetivo? = { null },
    val catalogo: CatalogoHabla? = null,
    private val contextoDelDia: suspend () -> Contexto? = { null },
) {
    /** El fondo que suena ahora; null con Bito callado o con el foco perdido. */
    val fondoSonando: StateFlow<SessionBackground?> get() = fondo.sonando

    /** No molestar está puesto y lo puso Bito: lo dice el `filtroPrevio` del [registro], que sobrevive a matar la app. */
    fun noMolestarActivo(registro: RegistroSesion?): Boolean = noMolestar?.activo(registro?.filtroPrevio) ?: false

    suspend fun objetivo(objetivo: Objetivo): DatosObjetivo? = objetivos(objetivo)

    suspend fun contexto(): Contexto? = contextoDelDia()
}
