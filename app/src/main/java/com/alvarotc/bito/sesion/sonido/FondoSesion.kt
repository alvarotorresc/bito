package com.alvarotc.bito.sesion.sonido

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.sesion.Fondo
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp

/** Lo que suena de un fondo ya abierto: se le sube y baja el volumen y se cierra. */
interface FuenteFondo {
    fun volumen(v: Float)

    fun cerrar()
}

/** De dónde sale el sonido del fondo; en los tests, un grabador. */
interface SalidaFondo {
    /** El bucle corto de 4 s de [snd], en una instancia propia de `SoundPool`. `null` si aún no está cargado. */
    fun bucleCorto(snd: Snd): FuenteFondo?

    /** La pista larga [recurso] sin hueco entre vueltas. `null` si no se puede abrir. */
    fun pista(recurso: Int): FuenteFondo?
}

/** Pistas largas por fondo (spec §9.4). `null`: se usa el bucle corto de [Snd] (el ruido marrón es ruido, no necesita pista). */
val PISTAS: Map<SessionBackground, Int?> =
    mapOf(
        SessionBackground.RAIN to R.raw.fondo_lluvia,
        SessionBackground.CAFE to R.raw.fondo_cafe,
        SessionBackground.BROWN to null,
        SessionBackground.NONE to null,
    )

private val BUCLES_CORTOS =
    mapOf(
        SessionBackground.RAIN to Snd.FONDO_LLUVIA,
        SessionBackground.BROWN to Snd.FONDO_MARRON,
        SessionBackground.CAFE to Snd.FONDO_CAFE,
    )

/**
 * El sonido de fondo del foco (spec §9.3): entra con constante de 400 ms y sale con 300 ms (30 ms en
 * seco), por el canal ambiente y con el foco de audio compartido ([FocoAudio]). Con Bito callado
 * (interruptor general o canal a 0) no pide el foco. Una pérdida temporal lo funde a 0 y vuelve; una
 * permanente lo para y [sonando] pasa a `null` (el reloj sigue). No depende del `MotorSonido`: no se
 * para al salir de la app, que es lo que hace el `Bucle` de ese motor.
 */
class FondoSesion(
    private val salida: SalidaFondo,
    private val foco: FocoAudio,
    /** Ganancia del canal ambiente, ya con sus silencios. */
    private val ganancia: () -> Float,
    private val scope: CoroutineScope,
    private val pistas: Map<SessionBackground, Int?> = PISTAS,
) : Fondo,
    FocoAudio.Usuario {
    private val _sonando = MutableStateFlow<SessionBackground?>(null)
    override val sonando: StateFlow<SessionBackground?> = _sonando.asStateFlow()
    private val _pendiente = MutableStateFlow(false)
    override val pendiente: StateFlow<Boolean> = _pendiente.asStateFlow()

    private val cerrojo = Any()
    private var querido: SessionBackground? = null
    private var abierto: SessionBackground? = null
    private var fuente: FuenteFondo? = null
    private var volumen = 0f
    private var tau = TAU_ENTRA
    private var conFoco = false
    private var perdido = false
    private var ultimoFactor = 1f
    private var latiendo = false
    private var muestra: Job? = null

    // Fuentes de un fondo anterior que se funden en seco (30 ms) mientras entra el nuevo.
    private class Saliente(
        val fuente: FuenteFondo,
        var volumen: Float,
    )

    private val salientes = mutableListOf<Saliente>()

    override fun sonar(tipo: SessionBackground) {
        if (tipo == SessionBackground.NONE) {
            callar(seco = false)
            return
        }
        synchronized(cerrojo) {
            muestra?.cancel()
            muestra = null
            empezar(tipo)
            // Con Bito callado no pedirá el foco ni sonará: el pedido se resuelve ya como «no suena».
            _pendiente.value = _sonando.value == null && ganancia() > 0f
        }
    }

    override fun callar(seco: Boolean) {
        synchronized(cerrojo) {
            muestra?.cancel()
            muestra = null
            apagar(seco)
        }
    }

    override fun muestra(tipo: SessionBackground) {
        if (tipo == SessionBackground.NONE) return
        synchronized(cerrojo) {
            // Un sonar en curso manda: la muestra solo suena si el fondo está callado o es otra muestra.
            if (querido != null && muestra == null) return
            muestra?.cancel()
            empezar(tipo)
            muestra =
                scope.launch {
                    delay(MUESTRA_MS)
                    synchronized(cerrojo) {
                        muestra = null
                        apagar(seco = false)
                    }
                }
        }
    }

    /** Pérdida permanente del foco: se para y deja de contar como sonando. */
    override fun alPerder() =
        synchronized(cerrojo) {
            perdido = true
            conFoco = false
            muestra?.cancel()
            muestra = null
            _sonando.value = null
            _pendiente.value = false
            tau = TAU_FOCO
            arrancarBucle()
        }

    /** El factor del foco cambió: si el bucle estaba aparcado, se despierta. */
    override fun alCambiarFactor() = despertar()

    /** La ganancia del canal cambió (ajustes): si el bucle estaba aparcado, se despierta. */
    fun alCambiarGanancia() = despertar()

    private fun despertar() =
        synchronized(cerrojo) {
            if (querido != null || fuente != null || salientes.isNotEmpty()) arrancarBucle()
        }

    // Siempre con el cerrojo. El bucle se aparca cuando no hay nada que mover y vuelve con estas llamadas.
    private fun arrancarBucle() {
        if (latiendo) return
        latiendo = true
        scope.launch { latir() }
    }

    // Siempre con el cerrojo.
    private fun empezar(tipo: SessionBackground) {
        if (abierto != tipo) {
            fuente?.let { salientes += Saliente(it, volumen) }
            fuente = null
            abierto = null
            volumen = 0f
        }
        querido = tipo
        perdido = false
        tau = TAU_ENTRA
        ultimoFactor = foco.factor
        arrancarBucle()
    }

    // Siempre con el cerrojo.
    private fun apagar(seco: Boolean) {
        if (querido == null && fuente == null) return
        querido = null
        tau = if (seco) TAU_SECO else TAU_SALE
        _sonando.value = null
        _pendiente.value = false
        arrancarBucle()
    }

    private suspend fun latir() {
        while (true) {
            if (paso()) return
            delay(PASO_MS)
        }
    }

    // Siempre con el cerrojo.
    private fun aparcar(): Boolean {
        latiendo = false
        return true
    }

    // Siempre con el cerrojo.
    private fun fundirSalientes() {
        val k = 1f - exp(-PASO_MS / TAU_SECO)
        val it = salientes.iterator()
        while (it.hasNext()) {
            val v = it.next()
            v.volumen -= v.volumen * k
            if (v.volumen < SILENCIO) {
                v.fuente.cerrar()
                it.remove()
            } else {
                v.fuente.volumen(v.volumen)
            }
        }
    }

    /** Un paso de [PASO_MS]. Devuelve si ya no queda nada que hacer. */
    private fun paso(): Boolean =
        synchronized(cerrojo) {
            val tipo = querido
            if (tipo != null && !perdido) {
                if (!conFoco && ganancia() > 0f) {
                    if (foco.tomar(this)) {
                        conFoco = true
                        ultimoFactor = foco.factor
                    } else {
                        perdido = true
                    }
                }
                if (conFoco && fuente == null) abrir(tipo)
                _sonando.value = if (conFoco && fuente != null) tipo else null
            }
            // Ya suena, o no sonará (sin foco, o Bito callado): el pedido deja de estar pendiente.
            if (_pendiente.value && (_sonando.value != null || perdido || ganancia() <= 0f)) _pendiente.value = false
            if (foco.factor != ultimoFactor) {
                ultimoFactor = foco.factor
                tau = TAU_FOCO
            }
            val objetivo = if (tipo != null && conFoco && !perdido) ganancia() * foco.factor else 0f
            volumen += (objetivo - volumen) * (1f - exp(-PASO_MS / tau))
            if (abs(objetivo - volumen) < SILENCIO) volumen = objetivo
            fuente?.volumen(volumen)
            fundirSalientes()
            val callado = volumen < SILENCIO
            val reposo = tipo != null && !perdido && volumen == objetivo && foco.factor == ultimoFactor && salientes.isEmpty()
            when {
                // Callado del todo, o perdido para siempre: se cierra y se suelta el foco.
                callado && (tipo == null || perdido) -> {
                    cerrarFuente()
                    soltarFoco()
                    querido = null
                    if (salientes.isEmpty()) aparcar() else false
                }
                // Bito callado: sin volumen no se retiene el foco; se vuelve a pedir si el volumen sube.
                callado && conFoco && ganancia() == 0f -> {
                    cerrarFuente()
                    soltarFoco()
                    false
                }
                // En su volumen y sin nada que cambiar: no despierta cada 16 ms toda la sesión.
                reposo && (fuente != null || (!conFoco && ganancia() == 0f)) -> aparcar()
                else -> false
            }
        }

    private fun abrir(tipo: SessionBackground) {
        val nueva =
            pistas[tipo]?.let { salida.pista(it) }
                ?: BUCLES_CORTOS[tipo]?.let { salida.bucleCorto(it) }
                ?: return
        fuente = nueva
        abierto = tipo
        nueva.volumen(volumen)
    }

    private fun cerrarFuente() {
        fuente?.cerrar()
        fuente = null
        abierto = null
        volumen = 0f
    }

    private fun soltarFoco() {
        foco.soltar(this)
        conFoco = false
    }

    companion object {
        const val TAU_ENTRA = 400f
        const val TAU_SALE = 300f
        const val TAU_SECO = 30f

        /** Al perder o recuperar el foco, la misma constante que el tono (spec §9.1). */
        const val TAU_FOCO = 150f
        const val MUESTRA_MS = 2200L
        const val PASO_MS = 16L
        private const val SILENCIO = 0.002f
    }
}

/**
 * Una pista larga sin hueco: dos reproductores encadenados con `setNextMediaPlayer`. Al acabar el
 * primero ya suena el segundo; se prepara un tercero y se encadena al segundo, y así siempre.
 */
class PistaEncadenada private constructor(
    private val crear: () -> Jugador?,
    private var actual: Jugador,
    private var siguiente: Jugador,
) : FuenteFondo {
    /** Lo mínimo de `MediaPlayer`, para probar el encadenado. */
    interface Jugador {
        fun volumen(v: Float)

        fun encadenar(siguiente: Jugador)

        fun alAcabar(f: () -> Unit)

        fun arrancar()

        fun liberar()
    }

    private val cerrojo = Any()
    private var vol = 0f
    private var cerrada = false

    init {
        actual.encadenar(siguiente)
        actual.alAcabar(::avanzar)
        actual.arrancar()
    }

    override fun volumen(v: Float) =
        synchronized(cerrojo) {
            vol = v
            actual.volumen(v)
            siguiente.volumen(v)
        }

    override fun cerrar() =
        synchronized(cerrojo) {
            cerrada = true
            actual.liberar()
            siguiente.liberar()
        }

    private fun avanzar() =
        synchronized(cerrojo) {
            if (cerrada) return
            val viejo = actual
            actual = siguiente
            val nuevo = crear()
            if (nuevo == null) {
                // Sin un tercero no hay vuelta: el segundo acaba y la pista se queda en silencio.
                viejo.liberar()
                return
            }
            nuevo.volumen(vol)
            siguiente = nuevo
            // Un jugador liberado en una carrera con `cerrar` no debe tumbar el hilo principal: la pista se queda en silencio.
            runCatching {
                actual.encadenar(nuevo)
                actual.alAcabar(::avanzar)
            }
            viejo.liberar()
        }

    companion object {
        fun abrir(crear: () -> Jugador?): PistaEncadenada? {
            val a = crear() ?: return null
            val b = crear()
            if (b == null) {
                a.liberar()
                return null
            }
            return PistaEncadenada(crear, a, b)
        }
    }
}

/** La salida de verdad: `SoundPool` propio y `MediaPlayer`, los dos como `USAGE_MEDIA`. */
class SalidaFondoAndroid(
    private val context: Context,
) : SalidaFondo {
    private val atributos =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    private val pool: SoundPool =
        SoundPool.Builder().setMaxStreams(1).setAudioAttributes(atributos).build()
    private val listos = ConcurrentHashMap.newKeySet<Int>()
    private val ids = ConcurrentHashMap<Snd, Int>()

    init {
        pool.setOnLoadCompleteListener { _, id, estado -> if (estado == 0) listos += id }
        BUCLES_CORTOS.values.forEach { snd -> ids[snd] = pool.load(context, recursoDe(snd), 1) }
    }

    override fun bucleCorto(snd: Snd): FuenteFondo? {
        val id = ids[snd]?.takeIf { it in listos } ?: return null
        val stream = pool.play(id, 0f, 0f, 1, -1, 1f)
        if (stream == 0) return null
        return object : FuenteFondo {
            override fun volumen(v: Float) = pool.setVolume(stream, v, v)

            override fun cerrar() = pool.stop(stream)
        }
    }

    override fun pista(recurso: Int): FuenteFondo? = runCatching { PistaEncadenada.abrir { jugador(recurso) } }.getOrNull()

    private fun jugador(recurso: Int): PistaEncadenada.Jugador? =
        runCatching {
            val mp = MediaPlayer()
            context.resources.openRawResourceFd(recurso).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setAudioAttributes(atributos)
            mp.setVolume(0f, 0f)
            mp.prepare()
            JugadorAndroid(mp)
        }.getOrNull()

    private fun recursoDe(snd: Snd): Int =
        when (snd) {
            Snd.FONDO_LLUVIA -> R.raw.snd_fondo_lluvia
            Snd.FONDO_MARRON -> R.raw.snd_fondo_marron
            else -> R.raw.snd_fondo_cafe
        }
}

private class JugadorAndroid(
    val mp: MediaPlayer,
) : PistaEncadenada.Jugador {
    override fun volumen(v: Float) = mp.setVolume(v, v)

    override fun encadenar(siguiente: PistaEncadenada.Jugador) = mp.setNextMediaPlayer((siguiente as JugadorAndroid).mp)

    override fun alAcabar(f: () -> Unit) = mp.setOnCompletionListener { f() }

    override fun arrancar() = mp.start()

    override fun liberar() = mp.release()
}
