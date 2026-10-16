package com.alvarotc.bito.sesion

import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.PlanSesion
import com.alvarotc.bito.domain.sesion.RegistroSesion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface Conflicto {
    data class OtraEnMarcha(
        val objetivo: Objetivo,
        val restaMillis: Long,
        val minutosPlan: Int,
    ) : Conflicto
}

data class ResultadoAccion(
    val bitos: Int,
    val celebraciones: Boolean,
    val aviso: AvisoSesion?,
)

/** Los avisos breves al volver a Hoy. */
sealed interface AvisoSesion {
    data class SigueEnMarcha(val resta: String) : AvisoSesion

    data class Hecho(val nombre: String) : AvisoSesion

    /** 0 minutos: «Otra vez será.» */
    data class LoDejamos(val minutos: Int) : AvisoSesion

    data object QuedaParaLuego : AvisoSesion

    data class ComoIntento(val nombre: String) : AvisoSesion
}

/** El único dueño de la sesión viva: cambia el registro y manda al sonido, a lo de fuera y a No molestar. */
interface MotorSesion {
    /** null = no hay sesión. */
    val instante: StateFlow<Instante?>
    val registro: StateFlow<RegistroSesion?>

    /** Lo que enseña Preparar. */
    val borrador: StateFlow<PlanSesion?>

    suspend fun preparar(desde: Destino?)

    fun cambiarBorrador(plan: PlanSesion)

    suspend fun empezar(): Conflicto?

    suspend fun seguirConLaViva()

    suspend fun dejarlaYEmpezar(): ResultadoAccion

    suspend fun pausar()

    suspend fun seguir()

    suspend fun sumar(minutos: Int)

    /** Respirar o Descanso. */
    suspend fun saltar()

    suspend fun empezarDescanso()

    suspend fun heTerminado(): ResultadoAccion

    suspend fun loDejo(): ResultadoAccion

    suspend fun siTerminado(): ResultadoAccion

    suspend fun sigoLuego(): ResultadoAccion

    suspend fun otraDeCinco()

    /**
     * Atrás desde Respirar para entrar: la sesión que acaba de empezar se descarta (sin fila, sin intento, sin aviso,
     * el aire se calla y No molestar se devuelve) y Preparar vuelve con el mismo plan. Fuera de Respirar no hace nada.
     * Solo lo hace el motor real: por defecto no hace nada.
     */
    suspend fun volverAPreparar() = Unit

    /** Al abrir: lee el store y cierra por cambio de día. */
    suspend fun recuperar()
}

/** El audio continuo de la sesión y de respirar (canal ambiente, USAGE_MEDIA). */
interface Aire {
    val sonando: StateFlow<Boolean>

    fun respirar(
        modo: BreathingMode,
        anclaElapsed: Long,
        ciclos: Int,
        musica: Boolean,
    )

    fun musica(encendida: Boolean)

    /** Tono 150 ms, música 250 ms. */
    fun parar()
}

interface Fondo {
    /** null = callado o foco perdido para siempre. */
    val sonando: StateFlow<SessionBackground?>

    /** Entra con tau 400 ms; NONE = callar. */
    fun sonar(tipo: SessionBackground)

    /** Tau 300 ms (30 ms en seco). */
    fun callar(seco: Boolean = false)

    /** 2200 ms y se funde. */
    fun muestra(tipo: SessionBackground)
}

/** Lo que pasa fuera de la pantalla: aviso fijo, alarma de la siguiente frontera y servicio. */
interface FueraDeLaApp {
    /** null = sesión cerrada: cancela todo y suelta los avisos retenidos. */
    suspend fun alCambiar(
        r: RegistroSesion?,
        sonando: Boolean,
    )

    /** Respirar suelta con la pantalla apagada. */
    fun respirando(
        activo: Boolean,
        finElapsed: Long?,
    )
}

object AireMudo : Aire {
    override val sonando: StateFlow<Boolean> = MutableStateFlow(false)

    override fun respirar(
        modo: BreathingMode,
        anclaElapsed: Long,
        ciclos: Int,
        musica: Boolean,
    ) = Unit

    override fun musica(encendida: Boolean) = Unit

    override fun parar() = Unit
}

object FondoMudo : Fondo {
    override val sonando: StateFlow<SessionBackground?> = MutableStateFlow(null)

    override fun sonar(tipo: SessionBackground) = Unit

    override fun callar(seco: Boolean) = Unit

    override fun muestra(tipo: SessionBackground) = Unit
}

object FueraMuda : FueraDeLaApp {
    override suspend fun alCambiar(
        r: RegistroSesion?,
        sonando: Boolean,
    ) = Unit

    override fun respirando(
        activo: Boolean,
        finElapsed: Long?,
    ) = Unit
}
