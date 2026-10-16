package com.alvarotc.bito.sesion.fuera

import android.content.Context
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.data.sesion.SesionStore
import com.alvarotc.bito.data.sesion.ahoraDelSistema
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.ui.notifications.ReminderReceiver
import java.time.ZoneId

/**
 * Los avisos de Bito que llegan con una sesión viva (spec pieza 5, decisión 8): esperan a que acabe la parte con tiempo
 * y entonces salen una vez, solo si siguen teniendo sentido ese día. Se guarda qué aviso (clase y clave de su alarma) y
 * el día lógico; al soltar se vuelve a evaluar contra el estado de entonces, así que un hábito ya hecho o una propuesta
 * caducada no sale. «Se acabó el tiempo» nunca pasa por aquí.
 *
 * [sesionViva] y [diaDeHoy] salen del [SesionStore] con [RelojSesion] y de los ajustes, nunca del motor.
 */
internal class AvisosRetenidos(
    context: Context,
    private val sesionViva: suspend () -> Boolean,
    private val diaDeHoy: suspend () -> LogicalDay,
    private val publicar: suspend (clase: String, clave: String) -> Unit,
) {
    private val prefs = context.applicationContext.getSharedPreferences(ARCHIVO_FUERA, Context.MODE_PRIVATE)

    /** true = con una sesión viva, el aviso queda apuntado y quien llama no lo publica. */
    suspend fun retenerSiHaySesion(
        clase: String,
        clave: String,
    ): Boolean {
        if (!sesionViva()) return false
        val dia = diaDeHoy()
        synchronized(CANDADO) {
            val actuales = prefs.getStringSet(CLAVE, emptySet()).orEmpty()
            prefs.edit().putStringSet(CLAVE, actuales + "$dia$SEP$clase$SEP$clave").commit()
        }
        return true
    }

    /** Publica cada retenido de hoy una vez y descarta el resto. */
    suspend fun soltar() {
        val retenidos =
            synchronized(CANDADO) {
                val actuales = prefs.getStringSet(CLAVE, emptySet()).orEmpty().toSet()
                if (actuales.isNotEmpty()) prefs.edit().remove(CLAVE).commit()
                actuales
            }
        if (retenidos.isEmpty()) return
        val hoy = diaDeHoy()
        retenidos.sorted().forEach { entrada ->
            val (dia, clase, clave) = entrada.split(SEP, limit = 3)
            if (dia.toIntOrNull() == hoy) publicar(clase, clave)
        }
    }

    private companion object {
        const val CLAVE = "avisos_retenidos"
        const val SEP = "|"
        val CANDADO = Any()
    }
}

/** Los retenidos reales: la sesión viva por el almacén, el día por el corte de los ajustes y la salida por el receptor. */
internal fun avisosRetenidosReales(
    context: Context,
    store: SesionStore,
    corteDia: suspend () -> Int,
    ahora: () -> Ahora = { ahoraDelSistema(context) },
): AvisosRetenidos {
    val app = context.applicationContext
    return AvisosRetenidos(
        context = app,
        sesionViva = { store.leer()?.let { RelojSesion.en(it, ahora()).actual != null } == true },
        diaDeHoy = { LogicalDays.logicalDayOf(ahora().paredMillis, corteDia(), ZoneId.systemDefault()) },
        publicar = { clase, clave ->
            (app as? BitoApp)?.container?.let { ReminderReceiver.reevaluar(app, it, clase, clave) }
        },
    )
}
