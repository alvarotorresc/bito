package com.alvarotc.bito.sesion.fuera

import android.content.Context
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.R
import com.alvarotc.bito.data.sesion.SesionStore
import com.alvarotc.bito.data.sesion.ahoraDelSistema
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.sesion.Aire
import com.alvarotc.bito.sesion.Fondo
import com.alvarotc.bito.sesion.NoMolestar
import com.alvarotc.bito.ui.AppVisibility
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import kotlinx.coroutines.flow.first

/**
 * Todo lo que necesita lo de fuera de la app, con las costuras que cambian los tests: el reloj
 * ([ahora], siempre `ahoraDelSistema` en producción), si la app está delante, el nombre del objetivo y la voz.
 */
internal class EntornoFuera(
    val context: Context,
    val store: SesionStore,
    val noMolestar: NoMolestar,
    val voz: suspend () -> Personality,
    val titulo: suspend (Objetivo) -> String,
    val visible: () -> Boolean = { AppVisibility.visible },
    val ahora: () -> Ahora = { ahoraDelSistema(context) },
    val tacto: () -> Tacto,
    val marcas: MarcaFin = MarcaFin(context),
    val servicio: ControlServicio = ServicioAndroid(context),
    val despierto: RetenDespierto = RetenDespierto(context),
    val retenidos: AvisosRetenidos,
)

/** El audio del contenedor, que el receptor mueve en cada frontera con la app fuera. */
internal class AudioFrontera(
    val fondo: Fondo,
    val aire: Aire,
    val sonido: MotorSonido,
)

/** De dónde sacan sus dependencias los receptores. Los tests las sustituyen. */
internal object PuertaFuera {
    @Volatile var entorno: ((Context) -> EntornoFuera)? = null

    @Volatile var audio: ((Context) -> AudioFrontera)? = null

    fun entornoDe(context: Context): EntornoFuera = (entorno ?: ::entornoDelContenedor)(context)

    fun audioDe(context: Context): AudioFrontera = (audio ?: ::audioDelContenedor)(context)

    private fun entornoDelContenedor(context: Context): EntornoFuera {
        val app = context.applicationContext
        val contenedor = (app as BitoApp).container
        return EntornoFuera(
            context = app,
            store = contenedor.sesionStore,
            noMolestar = contenedor.noMolestar,
            voz = { contenedor.settings.settings.first().personality },
            titulo = { tituloDe(app, it) },
            tacto = { contenedor.tacto },
            retenidos = avisosRetenidosReales(app, contenedor.sesionStore, { contenedor.settings.settings.first().dayCutoffMinutes }),
        )
    }

    private fun audioDelContenedor(context: Context): AudioFrontera {
        val contenedor = (context.applicationContext as BitoApp).container
        return AudioFrontera(contenedor.fondo, contenedor.aire, contenedor.sonido)
    }
}

/** El nombre de la tarea o del hábito; «Solo foco» si es libre o ya no existe. */
internal suspend fun tituloDe(
    context: Context,
    objetivo: Objetivo,
): String {
    val contenedor = (context.applicationContext as? BitoApp)?.container
    val nombre =
        when (objetivo) {
            is Objetivo.Tarea -> contenedor?.tasks?.task(objetivo.id)?.title
            is Objetivo.Habito -> contenedor?.habits?.habit(objetivo.id)?.name
            Objetivo.Libre -> null
        }
    return nombre ?: context.getString(R.string.ses_solo_foco)
}

/**
 * Pone lo de fuera de la app al día con el registro de la sesión (spec pieza 5 §11.3, §11.5): el aviso fijo, la
 * alarma de la siguiente frontera y, al acabar, «Se acabó el tiempo». Todo se recalcula desde el [SesionStore] y
 * [RelojSesion]: nunca depende del motor ni de lo que quedara programado.
 */
object SesionSync {
    /** Al arrancar la app y tras reiniciar el móvil. */
    suspend fun recuperar(context: Context) = recuperar(PuertaFuera.entornoDe(context))

    internal suspend fun recuperar(e: EntornoFuera) {
        val r = e.store.leer()
        if (r == null) {
            // Solo el aviso fijo de la sesión: el del foco viejo va por otro canal y no es de aquí.
            AlarmaSesion.cancelar(e.context)
            if (PresenciaSesion.hayAvisoFijo(e.context)) PresenciaSesion.cancelar(e.context)
            e.retenidos.soltar()
            return
        }
        aplicar(e, r, e.ahora(), restaurarDnd = true)
    }

    /**
     * Con la sesión viva: aviso y alarma de la siguiente frontera. Terminada: sin alarma, «Se acabó el tiempo» si no
     * se avisó ya y la app no está delante (con la app delante lo enseña la pantalla) y, con [restaurarDnd], No molestar
     * como estaba. No suena nada: el sonido de la frontera es del receptor.
     */
    internal suspend fun aplicar(
        e: EntornoFuera,
        r: RegistroSesion,
        ahora: Ahora,
        restaurarDnd: Boolean,
    ) {
        val instante = RelojSesion.en(r, ahora)
        if (instante.actual != null) {
            PresenciaSesion.mostrar(e.context, r, ahora, e.titulo(r.plan.objetivo), e.voz())
            AlarmaSesion.programar(e.context, r, ahora)
            return
        }
        AlarmaSesion.cancelar(e.context)
        e.despierto.liberar()
        if (e.visible()) {
            PresenciaSesion.cancelar(e.context)
        } else if (!e.marcas.avisado(r.id)) {
            PresenciaSesion.mostrarFin(e.context, e.titulo(r.plan.objetivo), e.voz())
            e.marcas.marcar(r.id)
            e.tacto().haptica(Haptica.RISE)
        }
        if (restaurarDnd) e.noMolestar.restaurar(r.filtroPrevio)
        // Acabó la parte con tiempo: los avisos de Bito que esperaban salen ahora, no tras la respuesta.
        e.retenidos.soltar()
    }
}

/** Qué sesión ya avisó de su fin: una vez por sesión, aunque la app se abra y se cierre. */
internal class MarcaFin(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun avisado(id: String): Boolean = prefs.getString(CLAVE, null) == id

    fun marcar(id: String) = prefs.edit().putString(CLAVE, id).apply()

    private companion object {
        const val ARCHIVO = "sesion_fuera"
        const val CLAVE = "fin_avisado"
    }
}
