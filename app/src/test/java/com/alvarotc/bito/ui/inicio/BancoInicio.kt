package com.alvarotc.bito.ui.inicio

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.MarcaLibro
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.ui.camino.CaminoViewModel
import com.alvarotc.bito.ui.garden.LocalSondaJardin
import com.alvarotc.bito.ui.garden.SondaJardin
import com.alvarotc.bito.ui.premios.CelebracionesViewModel
import com.alvarotc.bito.ui.premios.RegistroAnclas
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.today.TodayViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

/**
 * Monta el Inicio real con ViewModels de verdad sobre Room en memoria. Todo (Main, Room y los flujos)
 * corre en un [UnconfinedTestDispatcher] para que cada cambio se asiente al esperar a la composición.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BancoInicio(
    private val tmp: TemporaryFolder,
) {
    val dispatcher = UnconfinedTestDispatcher()
    val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    val hoyLogico = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    lateinit var db: BitoDatabase
    lateinit var habits: HabitsRepository
    lateinit var hoy: TodayViewModel
    lateinit var camino: CaminoViewModel
    lateinit var celebraciones: CelebracionesViewModel
    lateinit var ajustes: SettingsRepository
    val registro = RegistroAnclas()
    val sonido = SonidoGrabado()
    val tacto = TactoGrabado()
    val sonda = SondaJardin()

    val llamadas = mutableListOf<String>()
    val navegar =
        NavegacionInicio(
            abrirFicha = { llamadas += "ficha:$it" },
            abrirAjustes = { llamadas += "ajustes" },
            abrirTienda = { llamadas += "tienda" },
            abrirRepaso = { llamadas += "repaso" },
            abrirTareas = { llamadas += "tareas" },
            crearHabito = { llamadas += "crearHabito" },
            crearTarea = { llamadas += "crearTarea" },
            abrirRuta = { llamadas += "ruta:$it" },
        )

    private fun almacen(nombre: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher + Job())) { File(tmp.root, "$nombre.preferences_pb") }

    /** [dispatcherVM] se usa en los VM de Hoy y Camino: uno en pausa deja Hoy en carga hasta que se avance. */
    fun preparar(
        habitos: Int = 0,
        dispatcherVM: CoroutineDispatcher = dispatcher,
        marcaCero: Boolean = false,
    ) {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        habits = HabitsRepository(db)
        val journal = JournalRepository(db)
        val domainState = DomainStateRepository(db)
        val settings = SettingsRepository(almacen("inicio"))
        ajustes = settings
        // Con la marca a cero se celebra lo que entre en el libro desde ahora; sin marca, la primera lectura la fija.
        if (marcaCero) runBlocking { settings.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) } }
        val rewards = RewardsRepository(db)
        val tasks = TasksRepository(db)
        val reconciler = PointsReconciler(domainState, rewards)
        repeat(habitos) { sembrar("h$it", "Hábito $it", it) }
        hoy =
            TodayViewModel(
                domainState,
                habits,
                journal,
                settings,
                reconciler,
                rewards,
                tasks = tasks,
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcherVM,
            )
        camino = CaminoViewModel(domainState, settings, rewards, { fixedNow }, { utc }, dispatcherVM)
        celebraciones = CelebracionesViewModel(domainState, rewards, settings, CoroutineScope(dispatcher + Job()))
    }

    fun sembrar(
        id: String,
        nombre: String,
        orden: Int = 0,
    ) = runBlocking {
        habits.create(habitEntity(id = id, name = nombre, metric = Metric.CHECK, target = 1, createdOnDay = hoyLogico, sortOrder = orden))
    }

    fun cerrar() {
        Dispatchers.resetMain()
        if (::db.isInitialized) db.close()
    }

    @Composable
    fun Inicio(
        creado: String? = null,
        alCreadoVisto: () -> Unit = {},
        enPrimerPlano: Boolean = false,
    ) = CompositionLocalProvider(LocalSondaJardin provides sonda) {
        InicioScreen(hoy, camino, celebraciones, navegar, creado, alCreadoVisto, enPrimerPlano, registro)
    }
}
