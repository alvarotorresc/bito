package com.alvarotc.bito.ui.sistema.sonido

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Lo mínimo de `SoundPool`, para probar el motor sin Android. */
interface Reproductor {
    fun cargar(recurso: Int): Int

    fun listo(id: Int): Boolean

    fun play(
        id: Int,
        volumen: Float,
        bucle: Boolean,
    ): Int

    fun volumen(
        stream: Int,
        volumen: Float,
    )

    fun parar(stream: Int)
}

/**
 * El motor real (spec §4.4.1). Precarga todas las muestras al crearse; [play] nunca bloquea ni espera:
 * una muestra sin cargar se descarta.
 */
class MotorSonidoAndroid(
    private val reproductor: Reproductor,
    private val recursoDe: (Snd) -> Int,
    private val ajustes: () -> AjustesSonido,
    private val timbreNormal: () -> Boolean,
    private val scope: CoroutineScope,
    precargaEn: CoroutineDispatcher? = null,
    /** Las rampas de los bucles y su estado viven en un solo hilo (Main.immediate en producción). */
    private val scopeBucles: CoroutineScope = scope,
) : MotorSonido {
    private val ids = java.util.concurrent.ConcurrentHashMap<Snd, Int>()
    private val bucles = java.util.concurrent.ConcurrentHashMap<Snd, BucleSoundPool>()

    init {
        val cargar = { Snd.entries.forEach { ids[it] = reproductor.cargar(recursoDe(it)) } }
        if (precargaEn == null) cargar() else scope.launch(precargaEn) { cargar() }
    }

    override fun play(
        snd: Snd,
        intensidad: Float,
    ) {
        val id = ids[snd] ?: return
        if (!reproductor.listo(id)) return
        val v = Ganancia.de(snd, intensidad, ajustes(), timbreNormal())
        if (v > 0f) reproductor.play(id, v, bucle = false)
    }

    override fun bucle(snd: Snd): Bucle =
        bucles.getOrPut(snd) {
            BucleSoundPool(reproductor, { ids[snd] ?: 0 }, { ganancia(snd.canal) }, scopeBucles)
        }

    override fun probar(
        canal: Canal,
        nivel: Int,
    ) {
        val a = ajustes()
        if (!a.general || !timbreNormal()) return
        val snd =
            when (canal) {
                Canal.HABI -> Snd.HABI
                Canal.EFECTOS -> Snd.BROTE
                Canal.AMBIENTE -> Snd.AMBIENTE
            }
        val id = ids[snd] ?: return
        if (!reproductor.listo(id)) return
        val v = Ganancia.deNivel(nivel)
        if (v > 0f) reproductor.play(id, v, bucle = false)
    }

    override fun ganancia(canal: Canal): Float = Ganancia.canal(canal, ajustes(), timbreNormal())

    override fun segundoPlano() {
        bucles.values.forEach { it.callar() }
    }
}
