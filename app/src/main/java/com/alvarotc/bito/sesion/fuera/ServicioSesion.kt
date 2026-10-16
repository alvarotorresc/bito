package com.alvarotc.bito.sesion.fuera

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.alvarotc.bito.ui.notifications.Notifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * El servicio en primer plano (`mediaPlayback`) que mantiene vivo el sonido continuo de la sesión con la app fuera
 * (spec pieza 5 §11.2). Usa como suya la notificación fija de la sesión. Solo lo arranca quien tiene la app delante
 * (Empezar, Respirar): con targetSdk 36 un receptor no puede arrancarlo desde segundo plano, y por eso ningún
 * receptor lo hace. Se suelta al cerrar la sesión o cuando ya no suena nada.
 */
class ServicioSesion : Service() {
    private val manejador = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        vivo.set(true)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val aviso = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_AVISO, Notification::class.java) }
        if (aviso == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(this, Notifier.FOCUS_ID, aviso, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        manejador.removeCallbacksAndMessages(null)
        val fin = intent.getLongExtra(EXTRA_FIN_ELAPSED, SIN_FIN)
        // La respiración suelta se suelta sola al acabar; la sesión la suelta quien la cierra.
        if (fin != SIN_FIN) manejador.postDelayed({ stopSelf() }, (fin - SystemClock.elapsedRealtime() + MARGEN_MILLIS).coerceAtLeast(0L))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        manejador.removeCallbacksAndMessages(null)
        // El aviso lo cancela quien cierra la sesión: si el servicio se lo llevara, «Se acabó el tiempo» desaparecería.
        stopForeground(STOP_FOREGROUND_DETACH)
        vivo.set(false)
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_AVISO = "aviso"
        internal const val EXTRA_FIN_ELAPSED = "fin_elapsed"
        private const val SIN_FIN = -1L
        internal const val MARGEN_MILLIS = 2_000L

        internal val vivo = AtomicBoolean(false)

        fun arrancar(
            context: Context,
            aviso: Notification,
            finElapsed: Long?,
        ) {
            val intent =
                Intent(context, ServicioSesion::class.java)
                    .putExtra(EXTRA_AVISO, aviso)
                    .putExtra(EXTRA_FIN_ELAPSED, finElapsed ?: SIN_FIN)
            ContextCompat.startForegroundService(context, intent)
        }

        fun parar(context: Context) {
            context.stopService(Intent(context, ServicioSesion::class.java))
        }
    }
}

/** Lo que [FueraAndroid] necesita del servicio; los tests lo sustituyen por un grabador. */
internal interface ControlServicio {
    fun vivo(): Boolean

    fun arrancar(
        aviso: Notification,
        finElapsed: Long?,
    )

    fun parar()
}

internal class ServicioAndroid(private val context: Context) : ControlServicio {
    private val arrancado = AtomicBoolean(false)

    override fun vivo(): Boolean = arrancado.get()

    override fun arrancar(
        aviso: Notification,
        finElapsed: Long?,
    ) {
        // Arrancar desde segundo plano no está permitido: si falla, la sesión sigue con su aviso y sus alarmas.
        runCatching { ServicioSesion.arrancar(context, aviso, finElapsed) }.onSuccess { arrancado.set(true) }
    }

    override fun parar() {
        arrancado.set(false)
        runCatching { ServicioSesion.parar(context) }
    }
}

/** El `PARTIAL_WAKE_LOCK` de respirar, limitado a la duración del ejercicio: el hilo de audio y la vibración no se duermen. */
internal class RetenDespierto(context: Context) {
    private val energia = context.applicationContext.getSystemService(PowerManager::class.java)
    private var cerrojo: PowerManager.WakeLock? = null

    @Synchronized
    fun retener(millis: Long) {
        liberar()
        cerrojo =
            energia.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, ETIQUETA).apply {
                setReferenceCounted(false)
                acquire(millis + ServicioSesion.MARGEN_MILLIS)
            }
    }

    @Synchronized
    fun liberar() {
        cerrojo?.takeIf { it.isHeld }?.release()
        cerrojo = null
    }

    private companion object {
        const val ETIQUETA = "bito:respirar"
    }
}
