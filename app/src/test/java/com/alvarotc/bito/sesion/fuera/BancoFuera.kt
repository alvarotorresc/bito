package com.alvarotc.bito.sesion.fuera

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.alvarotc.bito.R
import com.alvarotc.bito.data.sesion.SesionStore
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.sesion.ANCLA_ELAPSED
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.INICIO_PARED
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.domain.sesion.registroSesion
import com.alvarotc.bito.sesion.Aire
import com.alvarotc.bito.sesion.Fondo
import com.alvarotc.bito.sesion.NoMolestar
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Un [Fondo] que apunta lo que le piden, en orden. */
class FondoGrabado : Fondo {
    val llamadas = mutableListOf<String>()
    override val sonando: StateFlow<SessionBackground?> = MutableStateFlow(null)

    override fun sonar(tipo: SessionBackground) {
        llamadas += "sonar:${tipo.name}"
    }

    override fun callar(seco: Boolean) {
        llamadas += "callar"
    }

    override fun muestra(tipo: SessionBackground) {
        llamadas += "muestra:${tipo.name}"
    }
}

/** Un [Aire] que apunta lo que le piden, en orden. */
class AireGrabado : Aire {
    val llamadas = mutableListOf<String>()
    override val sonando: StateFlow<Boolean> = MutableStateFlow(false)

    override fun respirar(
        modo: BreathingMode,
        anclaElapsed: Long,
        ciclos: Int,
        musica: Boolean,
    ) {
        llamadas += "respirar"
    }

    override fun musica(encendida: Boolean) {
        llamadas += "musica:$encendida"
    }

    override fun parar() {
        llamadas += "parar"
    }
}

/** El mismo plan de siempre en los tests: directo, dos bloques de 25 con 5 de descanso. Fronteras a 25, 30 y 55 min. */
fun registroDeDosBloques(
    entrada: Entrada = Entrada.DIRECTO,
    filtroPrevio: Int? = null,
    objetivo: Objetivo = Objetivo.Tarea("t1"),
    eventos: List<com.alvarotc.bito.domain.sesion.Evento> = emptyList(),
): RegistroSesion {
    val base = registroSesion(plan = planSesion(bloques = 2, entrada = entrada, objetivo = objetivo), eventos = eventos, arranques = 1)
    return base.copy(filtroPrevio = filtroPrevio)
}

const val MIN = 60_000L

/** Dos relojes `ms` después de Empezar, en el mismo arranque (contador 1). */
fun ahoraA(ms: Long): Ahora = Ahora(INICIO_PARED + ms, ANCLA_ELAPSED + ms, 1)

/** Todo lo que necesitan los tests de fuera de la app, con grabadores y un reloj que se mueve a mano. */
internal class BancoFuera(val context: Context, carpeta: File) {
    private val job = Job()
    val store =
        SesionStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { File(carpeta, "focus.preferences_pb") },
        )
    val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    val noMolestar = NoMolestar(context)
    val fondo = FondoGrabado()
    val aire = AireGrabado()
    val sonido = SonidoGrabado()
    val tacto = TactoGrabado()
    var visible = false
    var ahora: Ahora = ahoraA(0)

    val entorno =
        EntornoFuera(
            context = context,
            store = store,
            noMolestar = noMolestar,
            voz = { Personality.NEUTRA },
            titulo = { o ->
                when (o) {
                    is Objetivo.Tarea -> "Leer"
                    is Objetivo.Habito -> "Estirar"
                    Objetivo.Libre -> context.getString(R.string.ses_solo_foco)
                }
            },
            visible = { visible },
            ahora = { ahora },
            tacto = { tacto },
        )
    val audio = AudioFrontera(fondo, aire, sonido)

    fun cerrar() = job.cancel()
}
