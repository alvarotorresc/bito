package com.alvarotc.bito.sesion

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habla.LoGordoRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.sesion.SesionStore
import com.alvarotc.bito.data.sesion.SesionesRepository
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.sesion.ANCLA_ELAPSED
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.INICIO_PARED
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.ui.notifications.QuickActionUseCase
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.sonido.Bucle
import com.alvarotc.bito.ui.sistema.sonido.BucleMudo
import com.alvarotc.bito.ui.sistema.sonido.Canal
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.Executors

const val MINUTO = 60_000L

/** Los dos relojes a mano: avanzar mueve los dos a la vez, como el tiempo de verdad. */
class RelojFalso(
    var pared: Long = INICIO_PARED,
    var elapsed: Long = ANCLA_ELAPSED,
) {
    fun ahora() = Ahora(pared, elapsed)

    fun avanzar(ms: Long) {
        pared += ms
        elapsed += ms
    }
}

class SonidoGrabado : MotorSonido {
    val sonidos: MutableList<Snd> = Collections.synchronizedList(mutableListOf())

    override fun play(
        snd: Snd,
        intensidad: Float,
    ) {
        sonidos += snd
    }

    override fun bucle(snd: Snd): Bucle = BucleMudo

    override fun probar(
        canal: Canal,
        nivel: Int,
    ) = Unit

    override fun ganancia(canal: Canal): Float = 1f

    override fun segundoPlano() = Unit
}

class TactoGrabado : Tacto {
    val hapticas: MutableList<Haptica> = Collections.synchronizedList(mutableListOf())

    override fun haptica(h: Haptica) {
        hapticas += h
    }

    override fun probar(nivel: Int) = Unit
}

class AireGrabado : Aire {
    val llamadas: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val _sonando = MutableStateFlow(false)
    override val sonando: StateFlow<Boolean> = _sonando

    override fun respirar(
        modo: BreathingMode,
        anclaElapsed: Long,
        ciclos: Int,
        musica: Boolean,
    ) {
        llamadas += "respirar:$modo:$ciclos"
        _sonando.value = true
    }

    override fun musica(encendida: Boolean) {
        llamadas += "musica:$encendida"
    }

    override fun parar() {
        llamadas += "parar"
        _sonando.value = false
    }
}

class FondoGrabado : Fondo {
    val llamadas: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val _sonando = MutableStateFlow<SessionBackground?>(null)
    override val sonando: StateFlow<SessionBackground?> = _sonando

    override fun sonar(tipo: SessionBackground) {
        llamadas += "sonar:$tipo"
        _sonando.value = tipo.takeIf { it != SessionBackground.NONE }
    }

    override fun callar(seco: Boolean) {
        llamadas += if (seco) "callar:seco" else "callar"
        _sonando.value = null
    }

    override fun muestra(tipo: SessionBackground) = Unit
}

class FueraGrabada : FueraDeLaApp {
    val cambios: MutableList<RegistroSesion?> = Collections.synchronizedList(mutableListOf())

    override suspend fun alCambiar(
        r: RegistroSesion?,
        sonando: Boolean,
    ) {
        cambios += r
    }

    override fun respirando(
        activo: Boolean,
        finElapsed: Long?,
    ) = Unit
}

/** Lo que un motor manda fuera de sí: sonido, tacto, aire, fondo y lo de fuera de la app. */
class Grabadores {
    val sonido = SonidoGrabado()
    val tacto = TactoGrabado()
    val aire = AireGrabado()
    val fondo = FondoGrabado()
    val fuera = FueraGrabada()
}

/**
 * Un filtro de interrupciones que, con [retirado], falla como el sistema cuando se quita el permiso a
 * mitad: dice que hay permiso (lo comprobado antes) pero leer o poner el filtro lanza.
 */
class FiltroRetirable : FiltroAndroid {
    var retirado = false
    var fallaAlPoner = false
    var filtro = NotificationManager.INTERRUPTION_FILTER_ALL

    override fun permitido(): Boolean = true

    override fun actual(): Int {
        if (retirado) throw SecurityException("permiso retirado")
        return filtro
    }

    override fun poner(filtro: Int) {
        if (retirado || fallaAlPoner) throw SecurityException("permiso retirado")
        this.filtro = filtro
    }
}

/**
 * Base en memoria, Ajustes y `SesionStore` en archivos temporales, reloj falso y No molestar real
 * sobre la sombra de Robolectric (o el que se pase). Cada [motor] es un proceso nuevo sobre los mismos datos.
 */
class BancoSesion(
    carpeta: File,
    noMolestar: NoMolestar? = null,
) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val reloj = RelojFalso()
    val zona = ZoneOffset.UTC

    /**
     * Hilos propios, no los compartidos: en la suite completa los colectores que deja vivos otra
     * BitoApp del mismo proceso pueden acaparar Dispatchers.Default/IO y retrasar decenas de segundos
     * las escrituras de los DataStore (el mismo molde que FocusSyncTest).
     */
    val hilos: ExecutorCoroutineDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    private val alcance = CoroutineScope(SupervisorJob() + hilos)
    val db: BitoDatabase = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
    val sesiones = SesionesRepository(db)
    val tasks = TasksRepository(db, sesiones)
    val habits = HabitsRepository(db, sesiones)
    val domainState = DomainStateRepository(db)
    val rewards = RewardsRepository(db)
    val reconciler = PointsReconciler(domainState, rewards)
    val loGordo = LoGordoRepository(db) { reloj.pared }
    val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta, "settings.preferences_pb") })
    val store = SesionStore(PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta, "focus.preferences_pb") })
    val acciones =
        QuickActionUseCase(JournalRepository(db), reconciler, domainState, habits, settings, now = { reloj.pared }, zone = { zona })
    val noMolestar = noMolestar ?: NoMolestar(context)
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** El día lógico de hoy según el reloj falso (corte a medianoche, UTC). */
    val hoy: Int get() = LogicalDays.logicalDayOf(reloj.pared, 0, zona)

    var delante = true

    fun permisoNoMolestar(concedido: Boolean) = shadowOf(manager).setNotificationPolicyAccessGranted(concedido)

    fun filtro(): Int = manager.currentInterruptionFilter

    fun ponerFiltro(filtro: Int) = manager.setInterruptionFilter(filtro)

    fun motor(g: Grabadores = Grabadores()): MotorSesionAndroid =
        MotorSesionAndroid(
            store = store,
            efectos = EfectosSesion(tasks, habits, domainState, sesiones, reconciler, acciones),
            estado = domainState,
            loGordo = loGordo,
            acciones = acciones,
            settings = settings,
            aire = g.aire,
            fondo = g.fondo,
            fuera = g.fuera,
            noMolestar = noMolestar,
            sonido = g.sonido,
            tacto = g.tacto,
            reloj = { reloj.ahora() },
            alcance = alcance,
            zona = { zona },
            delante = { delante },
            conTicker = false,
        )

    fun cerrar() {
        db.close()
        alcance.cancel()
        hilos.close()
    }
}
