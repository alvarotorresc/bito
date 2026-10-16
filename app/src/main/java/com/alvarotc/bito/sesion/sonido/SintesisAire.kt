package com.alvarotc.bito.sesion.sonido

import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.domain.model.BreathingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * El sonido que respira, muestra a muestra (receta `SND.respirar` del mock, spec §8.3). Puro: sin
 * Android ni reloj, lo llena quien tenga el `AudioTrack`.
 *
 * Tono guía (siempre): seno a 196 Hz × (1 + 0,5·llenado), τ 50 ms; ganancia 0,045 al inhalar y
 * exhalar y 0,03 al mantener, τ 80 ms; paso bajo a 900 Hz. Música (opcional): acorde de re
 * (146,83 Hz triángulo a 0,30; 220, 293,66 y 369,99 Hz senos a 0,22), cada nota doble a −4 y +5
 * cents; grupo a 0,02 + 0,06·llenado con paso bajo de 380 + 1500·llenado Hz (Q 0,4), τ 80 ms.
 * Parar: tono a 0 con τ 150 ms, música con 250 ms.
 *
 * Cada parámetro imita `setTargetAtTime`: se acerca a su objetivo con `1 − e^(−Δt/τ)` por muestra,
 * así que nunca hay un salto. Los filtros son los `BiquadFilter` paso bajo de Web Audio, con la Q en
 * decibelios como allí (el del tono no la fija: vale 1, la de Web Audio por defecto).
 */
class SintesisAire {
    /** Ganancia del canal ambiente por el factor del foco de audio; se sigue con τ [TAU_VOLUMEN]. */
    @Volatile
    var volumen: Float = 0f

    private var modo: BreathingMode? = null
    private var ciclos = 0
    private var musicaEncendida = false
    private var parado = false

    // Objetivos.
    private var objFrecuencia = FRECUENCIA_BASE
    private var objGuia = 0.0
    private var objMusica = 0.0
    private var objCorte = CORTE_INICIAL

    // Valores que se acercan a los objetivos.
    private var frecuencia = FRECUENCIA_BASE
    private var guia = 0.0
    private var musicaG = 0.0
    private var corte = CORTE_INICIAL
    private var vol = 0.0

    private var faseGuia = 0.0
    private val fasesAcorde = DoubleArray(ACORDE.size)
    private val pasosAcorde = DoubleArray(ACORDE.size) { 2 * PI * ACORDE[it].hercios / MUESTREO }
    private val filtroGuia = PasoBajo().also { it.ajustar(CORTE_GUIA, Q_GUIA_DB) }
    private val filtroMusica = PasoBajo().also { it.ajustar(CORTE_INICIAL, Q_MUSICA_DB) }
    private var hastaAjuste = 0

    /** Ganancia actual del tono guía (para los tests). */
    internal val gananciaGuia: Float get() = guia.toFloat()

    /** Frecuencia actual del tono guía, en Hz. */
    internal val frecuenciaGuia: Float get() = frecuencia.toFloat()

    /** Ganancia actual del grupo de la música. */
    internal val gananciaMusica: Float get() = musicaG.toFloat()

    /** Corte actual del paso bajo de la música, en Hz. */
    internal val corteMusica: Float get() = corte.toFloat()

    /** Parado y ya sin nada que se oiga: el hilo puede cerrar. */
    val callado: Boolean get() = parado && guia < UMBRAL_CALLADO && musicaG < UMBRAL_CALLADO

    /** Empieza (o retoma tras [parar]) una respiración: [llenar] seguirá su ritmo. */
    fun respirar(
        modo: BreathingMode,
        ciclos: Int,
        musica: Boolean,
    ) {
        this.modo = modo
        this.ciclos = ciclos
        musicaEncendida = musica
        parado = false
    }

    fun musica(encendida: Boolean) {
        musicaEncendida = encendida
        if (!parado) objMusica = if (encendida) musicaDe(llenadoObjetivo) else 0.0
    }

    private var llenadoObjetivo = 0.0

    /** Los objetivos de la receta para [llenado] (0–1) y [fase]. */
    fun fijar(
        llenado: Float,
        fase: BreathPhase,
    ) {
        val l = llenado.coerceIn(0f, 1f).toDouble()
        llenadoObjetivo = l
        objFrecuencia = FRECUENCIA_BASE * (1 + SUBIDA * l)
        objCorte = CORTE_MUSICA + CORTE_POR_LLENADO * l
        if (parado) return
        objGuia = if (fase == BreathPhase.HOLD) GUIA_MANTEN else GUIA_RESPIRA
        objMusica = if (musicaEncendida) musicaDe(l) else 0.0
    }

    /** Tono a 0 con τ 150 ms y música con 250 ms. */
    fun parar() {
        parado = true
        objGuia = 0.0
        objMusica = 0.0
    }

    /**
     * Un bloque siguiendo el ritmo: el llenado sale de `BreathingRhythm.at(modo, desdeMs, ciclos)`.
     * Al acabar los ciclos para solo. Devuelve el punto (null si no hay respiración).
     */
    fun llenar(
        buffer: FloatArray,
        desdeMs: Long,
    ): BreathingRhythm.Point? {
        val m = modo
        val punto = m?.let { BreathingRhythm.at(it, desdeMs, ciclos) }
        if (punto != null && !parado) {
            if (punto.finished) parar() else fijar(punto.fill, punto.phase)
        }
        generar(buffer)
        return punto
    }

    fun generar(buffer: FloatArray) {
        val tauGuia = if (parado) K_PARAR_TONO else K_80
        val tauMusica = if (parado) K_PARAR_MUSICA else K_80
        val objVol = volumen.toDouble()
        for (i in buffer.indices) {
            frecuencia += (objFrecuencia - frecuencia) * K_50
            guia += (objGuia - guia) * tauGuia
            musicaG += (objMusica - musicaG) * tauMusica
            corte += (objCorte - corte) * K_80
            vol += (objVol - vol) * K_VOLUMEN
            if (hastaAjuste-- <= 0) {
                filtroMusica.ajustar(corte, Q_MUSICA_DB)
                hastaAjuste = MUESTRAS_POR_AJUSTE
            }

            faseGuia += 2 * PI * frecuencia / MUESTREO
            if (faseGuia >= 2 * PI) faseGuia -= 2 * PI
            val tono = filtroGuia.paso(sin(faseGuia) * guia)

            var acorde = 0.0
            for (n in ACORDE.indices) {
                var f = fasesAcorde[n] + pasosAcorde[n]
                if (f >= 2 * PI) f -= 2 * PI
                fasesAcorde[n] = f
                val nota = ACORDE[n]
                acorde += nota.ganancia * if (nota.triangulo) triangulo(f) else sin(f)
            }
            val musica = filtroMusica.paso(acorde * musicaG)

            buffer[i] = ((tono + musica) * vol).toFloat()
        }
    }

    /** El paso bajo `BiquadFilter` de Web Audio (forma directa transpuesta II). */
    private class PasoBajo {
        private var b0 = 0.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var a1 = 0.0
        private var a2 = 0.0
        private var z1 = 0.0
        private var z2 = 0.0

        fun ajustar(
            corte: Double,
            qDb: Double,
        ) {
            val w0 = 2 * PI * corte / MUESTREO
            val alfa = sin(w0) / (2 * 10.0.pow(qDb / 20))
            val c = cos(w0)
            val a0 = 1 + alfa
            b0 = (1 - c) / 2 / a0
            b1 = (1 - c) / a0
            b2 = b0
            a1 = -2 * c / a0
            a2 = (1 - alfa) / a0
        }

        fun paso(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    private class Nota(
        base: Double,
        cents: Int,
        val ganancia: Double,
        val triangulo: Boolean,
    ) {
        val hercios = base * 2.0.pow(cents / 1200.0)
    }

    companion object {
        const val MUESTREO = 44_100

        /** 10 ms de audio. */
        const val BLOQUE = MUESTREO / 100

        private const val FRECUENCIA_BASE = 196.0
        private const val SUBIDA = 0.5
        private const val GUIA_RESPIRA = 0.045
        private const val GUIA_MANTEN = 0.03
        private const val CORTE_GUIA = 900.0
        private const val Q_GUIA_DB = 1.0
        private const val MUSICA_BASE = 0.02
        private const val MUSICA_POR_LLENADO = 0.06
        private const val CORTE_MUSICA = 380.0
        private const val CORTE_POR_LLENADO = 1500.0

        /** El mock crea el filtro de la música a 400 Hz antes del primer objetivo. */
        private const val CORTE_INICIAL = 400.0
        private const val Q_MUSICA_DB = 0.4

        /** El acorde de re: la fundamental en triángulo a 0,30 y el resto en senos a 0,22. */
        private const val GANANCIA_FUNDAMENTAL = 0.30
        private const val GANANCIA_NOTA = 0.22
        private val DESAFINES = listOf(-4, 5)
        private val ACORDE =
            listOf(146.83, 220.0, 293.66, 369.99).flatMapIndexed { i, f ->
                DESAFINES.map { Nota(f, it, if (i == 0) GANANCIA_FUNDAMENTAL else GANANCIA_NOTA, triangulo = i == 0) }
            }

        private const val TAU_FRECUENCIA = 0.05
        private const val TAU_GANANCIA = 0.08
        private const val TAU_PARAR_TONO = 0.15
        private const val TAU_PARAR_MUSICA = 0.25

        /** Fundido del volumen (canal y foco de audio): la spec no fija τ; se usa el de parar el tono. */
        private const val TAU_VOLUMEN = TAU_PARAR_TONO

        private val K_50 = coeficiente(TAU_FRECUENCIA)
        private val K_80 = coeficiente(TAU_GANANCIA)
        private val K_PARAR_TONO = coeficiente(TAU_PARAR_TONO)
        private val K_PARAR_MUSICA = coeficiente(TAU_PARAR_MUSICA)
        private val K_VOLUMEN = coeficiente(TAU_VOLUMEN)

        private const val MUESTRAS_POR_AJUSTE = 32
        private const val UMBRAL_CALLADO = 1e-5

        /** `1 − e^(−Δt/τ)` con Δt una muestra. */
        private fun coeficiente(tau: Double) = 1 - exp(-1.0 / (MUESTREO * tau))

        private fun musicaDe(llenado: Double) = MUSICA_BASE + MUSICA_POR_LLENADO * llenado

        /** Triángulo de −1 a 1 que empieza en 0 subiendo, como el de Web Audio. */
        private fun triangulo(fase: Double): Double {
            val t = fase / (2 * PI)
            return 4 * abs(((t + 0.75) % 1.0) - 0.5) - 1
        }
    }
}
