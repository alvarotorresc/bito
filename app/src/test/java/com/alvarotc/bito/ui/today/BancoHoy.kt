package com.alvarotc.bito.ui.today

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.habla.AvisosAceptadosRepository
import com.alvarotc.bito.data.habla.LoGordoRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.ui.inicio.NavegacionInicio
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.premios.RegistroAnclas
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

/** Una fuente de propuestas en memoria que apunta lo que se le responde. */
class FuenteFalsa(inicial: Propuesta? = null) : FuentePropuestas {
    val flujo = MutableStateFlow(inicial)
    val respuestas = mutableListOf<Respuesta>()
    val horas = mutableListOf<Int?>()

    /** Lo que devuelve la siguiente respuesta; por defecto, nada que hacer. */
    var efecto: Efecto = Efecto.Ninguno

    override fun propuesta(
        ambito: Ambito,
        habitId: String?,
    ): Flow<Propuesta?> = flujo

    override suspend fun responder(
        id: String,
        respuesta: Respuesta,
        hora: Int?,
    ): Efecto {
        respuestas += respuesta
        horas += hora
        return efecto
    }

    val gordoGuardado = mutableListOf<List<RefGordo>>()

    override suspend fun guardarGordo(refs: List<RefGordo>) {
        gordoGuardado += refs
    }
}

/** HoyLista real sobre Room en memoria, con grabadores de sonido, tacto, anclas y navegación. */
@OptIn(ExperimentalCoroutinesApi::class)
class BancoHoy(private val rule: ComposeContentTestRule, private val tmp: TemporaryFolder) {
    val dispatcher = UnconfinedTestDispatcher()
    val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    val hoy = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    lateinit var db: BitoDatabase
    lateinit var vm: TodayViewModel
    val sonido = SonidoGrabado()
    val tacto = TactoGrabado()
    val registro = RegistroAnclas()
    val avisos = AvisoEstado()
    val llamadas = mutableListOf<String>()

    /** El hábito cuya veta de racha dura ahora (el que Inicio pasa a la lista), si hay. */
    var vetaRachaDe by mutableStateOf<String?>(null)
    var fichaAbierta: String? = null
    val navegar =
        NavegacionInicio(
            abrirFicha = { fichaAbierta = it },
            abrirAjustes = { llamadas += "ajustes" },
            abrirTienda = { llamadas += "tienda" },
            abrirRepaso = { llamadas += "repaso" },
            abrirTareas = { llamadas += "tareas" },
            crearHabito = { llamadas += "crearHabito" },
            crearTarea = { llamadas += "crearTarea" },
            abrirRuta = { llamadas += "ruta:$it" },
        )

    private fun almacen(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher + Job())) { File(tmp.root, "hoy.preferences_pb") }

    fun preparar(
        fuente: FuentePropuestas? = null,
        ticks: Flow<Unit> = flowOf(Unit),
        dispatcherVM: CoroutineDispatcher = dispatcher,
    ) {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        val domainState = DomainStateRepository(db)
        val rewards = RewardsRepository(db)
        vm =
            TodayViewModel(
                domainState,
                HabitsRepository(db),
                JournalRepository(db),
                SettingsRepository(almacen()),
                PointsReconciler(domainState, rewards),
                rewards,
                tasks = TasksRepository(db),
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcherVM,
                propuestas = fuente,
                clockTicks = ticks,
                gordo = LoGordoRepository(db, now = { fixedNow }),
                aceptados = AvisosAceptadosRepository(db),
            )
    }

    fun cerrar() {
        Dispatchers.resetMain()
        if (::db.isInitialized) db.close()
    }

    /** Siembra hábitos del dominio (creados hoy salvo que se diga otra cosa), en el orden dado. */
    fun sembrar(
        vararg habitos: Habit,
        creadosEn: Int = hoy,
    ) = runBlocking {
        habitos.forEachIndexed { i, h ->
            HabitsRepository(db).create(
                habitEntity(
                    id = h.id,
                    name = h.name,
                    metric = h.metric,
                    period = h.period,
                    direction = h.direction,
                    target = h.target,
                    unit = h.unit,
                    logMode = h.logMode,
                    step = h.step,
                    createdOnDay = creadosEn,
                    sortOrder = i,
                ),
            )
        }
    }

    fun entradasDeHoy(id: String): Int = runBlocking { db.entryDao().all().count { it.habitId == id && it.logicalDay == hoy } }

    fun montar(
        creado: String? = null,
        alCreadoVisto: () -> Unit = {},
        reducido: Boolean = false,
        abrirGordo: Boolean = false,
    ) {
        rule.setContent { Contenido(creado, alCreadoVisto, reducido, abrirGordo) }
        rule.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    fun Contenido(
        creado: String? = null,
        alCreadoVisto: () -> Unit = {},
        reducido: Boolean = false,
        abrirGordo: Boolean = false,
    ) {
        ConSistema(sonido, tacto, reducido) {
            CompositionLocalProvider(LocalAnclas provides registro) {
                Box(Modifier.fillMaxSize()) {
                    HoyLista(
                        vm,
                        navegar,
                        rememberLazyListState(),
                        avisos,
                        encabezado = {},
                        creado = creado,
                        alCreadoVisto = alCreadoVisto,
                        abrirGordo = abrirGordo,
                        vetaRachaDe = vetaRachaDe,
                    )
                    AvisoHost(avisos, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    fun esperar(ms: Long = 600) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }
}
