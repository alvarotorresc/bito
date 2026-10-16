package com.alvarotc.bito.ui.sesion.respirar

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habla.CatalogoRecursos
import com.alvarotc.bito.data.habla.MemoriaRepository
import com.alvarotc.bito.data.repo.BreathingRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.sesion.Aire
import com.alvarotc.bito.sesion.FueraDeLaApp
import com.alvarotc.bito.ui.sesion.EstadoCamaraSesion
import com.alvarotc.bito.ui.sesion.rememberEstadoCamaraSesion
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.TemporaryFolder
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

internal class AireGrabado : Aire {
    override val sonando: StateFlow<Boolean> = MutableStateFlow(false)
    val arranques = mutableListOf<Arranque>()
    val musicas = mutableListOf<Boolean>()
    var paradas = 0

    data class Arranque(val modo: BreathingMode, val ancla: Long, val ciclos: Int, val musica: Boolean)

    override fun respirar(
        modo: BreathingMode,
        anclaElapsed: Long,
        ciclos: Int,
        musica: Boolean,
    ) {
        arranques += Arranque(modo, anclaElapsed, ciclos, musica)
    }

    override fun musica(encendida: Boolean) {
        musicas += encendida
    }

    override fun parar() {
        paradas++
    }
}

internal class FueraGrabada : FueraDeLaApp {
    val llamadas = mutableListOf<Pair<Boolean, Long?>>()

    override suspend fun alCambiar(
        r: RegistroSesion?,
        sonando: Boolean,
        fondoPendiente: Boolean,
    ) = Unit

    override fun respirando(
        activo: Boolean,
        finElapsed: Long?,
    ) {
        llamadas += activo to finElapsed
    }
}

/** 2026-10-14 (miércoles) a las 12:00 en UTC: mitad de semana, lejos de cualquier frontera. */
internal const val PARED_BASE = 1_791_979_200_000L

/** Todo lo que necesita `RespirarSuelta`: Room en memoria, ajustes en un DataStore temporal y relojes a mano. */
internal class Montaje(
    carpeta: TemporaryFolder,
    val reducido: Boolean = false,
) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: BitoDatabase = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).allowMainThreadQueries().build()
    val settings =
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined)) {
                carpeta.newFile("ajustes-" + java.util.UUID.randomUUID() + ".preferences_pb")
            },
        )
    val breathing = BreathingRepository(db)
    val memoria = MemoriaRepository(db)
    val estado = DomainStateRepository(db).observe()
    val aire = AireGrabado()
    val fuera = FueraGrabada()
    val sonido = SonidoGrabado()
    val tacto = TactoGrabado()
    val gestos = GestosHabi()
    val elapsed = AtomicLong(5_000_000L)
    val respiracion = mutableStateOf<(() -> Float)?>(null)
    var volvioAHoy = 0
    lateinit var camara: EstadoCamaraSesion
    val entorno =
        EntornoSuelta(
            elapsed = { elapsed.get() },
            pared = { PARED_BASE + (elapsed.get() - 5_000_000L) },
            semilla = { 7L },
            zona = { ZoneId.of("UTC") },
            animada = false,
        )

    init {
        runBlocking {
            settings.update { it.copy(userName = "Álvaro", personality = Personality.NEUTRA) }
        }
    }

    fun ajustes() = runBlocking { settings.settings.first() }

    fun montar(compose: AndroidComposeTestRule<*, ComponentActivity>) {
        compose.setContent {
            ConSistema(sonido, tacto, reducido) {
                camara = rememberEstadoCamaraSesion()
                RespirarSuelta(
                    camara = camara,
                    aire = aire,
                    fuera = fuera,
                    breathing = breathing,
                    memoria = memoria,
                    settings = settings,
                    alVolverAHoy = { volvioAHoy++ },
                    respiracion = respiracion,
                    gestos = gestos,
                    entorno = entorno,
                    estado = estado,
                    catalogo = CatalogoRecursos { context.resources },
                )
            }
        }
    }

    fun avanzar(
        compose: AndroidComposeTestRule<*, ComponentActivity>,
        ms: Long,
    ) {
        var resto = ms
        while (resto > 0) {
            val paso = minOf(resto, 100L)
            elapsed.addAndGet(paso)
            compose.mainClock.advanceTimeBy(paso)
            resto -= paso
        }
    }

    fun cerrar() = db.close()
}

internal fun nuevaRegla() = createAndroidComposeRule<ComponentActivity>()
