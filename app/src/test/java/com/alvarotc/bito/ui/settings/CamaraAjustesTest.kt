package com.alvarotc.bito.ui.settings

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.habi.HabiViewModel
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.sonido.MotorSonidoMudo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La cámara Hoy → Ajustes (spec §4.6): la maqueta encoge desde el tamaño de Hoy mientras Hoy se funde. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h600dp")
class CamaraAjustesTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: BitoDatabase
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        repository =
            SettingsRepository(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher + Job())) {
                    java.io.File(tmp.root, "ajustes.preferences_pb")
                },
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private class Vms(
        val backup: BackupViewModel,
        val settings: SettingsViewModel,
        val habi: HabiViewModel,
    )

    private fun vms(): Vms {
        val keyStore = BackupKeyStore(tmp.root)
        val domainState = DomainStateRepository(db)
        val rewards = RewardsRepository(db)
        return Vms(
            BackupViewModel(
                BackupRepository(db, repository, keyStore, "test"),
                repository,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            ),
            SettingsViewModel(repository, HabitsRepository(db)),
            HabiViewModel(
                domainState,
                rewards,
                repository,
                PointsReconciler(domainState, rewards),
                HabiSounds(MotorSonidoMudo),
                defaultDispatcher = dispatcher,
            ),
        )
    }

    private fun altoMaqueta() = rule.onNodeWithTag("maqueta-jardin").getBoundsInRoot().height.value

    @Test
    fun `camera scale goes from the Today size to one`() {
        assertEquals(1f / 0.74f, escalaCamara(false), 0.001f)
        assertEquals(1f, escalaCamara(true), 0f)
    }

    @Test
    fun `without an entrance scope the model stays at scale one`() {
        val v = vms()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema {
                AjustesScreen(v.backup, v.settings, v.habi, onBack = {}, onOpenArchived = {})
            }
        }
        rule.mainClock.advanceTimeBy(16)
        val alInicio = altoMaqueta()
        rule.mainClock.advanceTimeBy(2000)
        assertEquals(alInicio, altoMaqueta(), 0.01f)
    }

    @Test
    fun `entering settings from Today starts bigger and settles`() {
        val v = vms()
        var nav: androidx.navigation.NavHostController? = null
        rule.setContent {
            ConSistema {
                val controlador = rememberNavController()
                nav = controlador
                NavHost(controlador, startDestination = "today") {
                    composable("today") { Text("hoy") }
                    composable("settings") {
                        AjustesScreen(v.backup, v.settings, v.habi, onBack = {}, onOpenArchived = {}, entrada = this)
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { nav!!.navigate("settings") }
        // La ruta nueva se compone en el primer fotograma: se avanza hasta que la maqueta existe.
        repeat(10) {
            if (rule.onAllNodesWithTag("maqueta-jardin").fetchSemanticsNodes().isEmpty()) rule.mainClock.advanceTimeByFrame()
        }
        val alEntrar = altoMaqueta()
        rule.mainClock.advanceTimeBy(2000)
        val alFinal = altoMaqueta()
        assertTrue("al entrar $alEntrar debe medir más que al asentarse $alFinal", alEntrar > alFinal * 1.1f)
    }
}
