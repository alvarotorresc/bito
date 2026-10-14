package com.alvarotc.bito.ui.premios

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.MarcaLibro
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.GardenPlot
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
class PremiosEnInicioTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private val hoy = 20_300
    private val planificador = TestCoroutineScheduler()
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val registro = RegistroAnclas()
    private val llamadasJardin = mutableListOf<Pair<GardenSpecial?, Int?>>()
    private val almacen = Job()

    private lateinit var db: BitoDatabase
    private lateinit var ajustes: SettingsRepository
    private lateinit var vm: CelebracionesViewModel

    @After
    fun cerrar() {
        vm.viewModelScope.cancel()
        db.close()
        almacen.cancel()
    }

    /** El libro, vacío, con la marca a cero; la cola del modelo corre en un reloj que este test mueve a la vez que el de Compose. */
    private fun montar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(contexto, BitoDatabase::class.java).allowMainThreadQueries().build()
        ajustes =
            SettingsRepository(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + almacen)) {
                    File(tmp.root, "premios.preferences_pb")
                },
            )
        runBlocking { ajustes.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) } }
        vm =
            CelebracionesViewModel(
                DomainStateRepository(db),
                RewardsRepository(db),
                ajustes,
                CoroutineScope(StandardTestDispatcher(planificador)),
            )
        vm.enHoy(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema(sonido, tacto) {
                CompositionLocalProvider(LocalAnclas provides registro) {
                    PremiosEnInicio(
                        vm,
                        registro,
                        costuraY = { 0f },
                        alVerEnJardin = {},
                        alJardin = { llegando, creciendo -> llamadasJardin += llegando to creciendo },
                        alAsomarCabecera = {},
                    )
                }
            }
        }
        avanzar(32)
    }

    private fun avanzar(ms: Long) {
        var resto = ms
        while (resto > 0) {
            val paso = minOf(16L, resto)
            planificador.advanceTimeBy(paso)
            planificador.runCurrent()
            rule.mainClock.advanceTimeBy(paso)
            ShadowLooper.idleMainLooper()
            resto -= paso
        }
    }

    /** Espera, en tiempo real, a que la base y los ajustes entreguen lo suyo al modelo. */
    private fun esperar(condicion: () -> Boolean) {
        repeat(300) {
            avanzar(16)
            if (condicion()) return
            Thread.sleep(10)
        }
        throw AssertionError("la condición no llegó")
    }

    /** Da tiempo real a que la base y los ajustes lleguen al modelo antes del siguiente paso. */
    private fun dejarPasar() =
        repeat(30) {
            avanzar(16)
            Thread.sleep(10)
        }

    private fun hay(tag: String) = rule.onAllNodesWithTagCount(tag) > 0

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    private fun insertar(vararg filas: com.alvarotc.bito.data.db.PointsLedgerEntity) =
        runBlocking { db.pointsLedgerDao().insertAll(filas.toList()) }

    private fun fila(
        id: String,
        t: Long,
        motivo: PointsReason,
        ref: String,
        delta: Int,
    ) = pointsLedgerEntity(id = id, delta = delta, reason = motivo, refId = ref, logicalDay = hoy, createdAtMillis = t)

    @Test
    fun `the last habit of the day launches the day vein and then the streak vein, never together`() {
        montar()
        insertar(fila("d", 10, PointsReason.PERFECT_DAY, "day:$hoy", 3), fila("r", 11, PointsReason.STREAK_MILESTONE, "make-bed:7", 5))
        esperar { hay("veta-DIA") }
        avanzar(500)
        assertTrue(hay("veta-DIA"))
        assertTrue(!hay("veta-RACHA"))
        avanzar(1_100L + 600L + 360L + 500L)
        assertTrue(!hay("veta-DIA"))
        assertTrue(hay("veta-RACHA"))
    }

    @Test
    fun `a new badge opens its sheet and the chain waits`() {
        montar()
        runBlocking { db.badgeDao().insert(BadgeEntity("first-habit", 500)) }
        esperar { hay("hoja-logro") }
        insertar(fila("d", 10, PointsReason.PERFECT_DAY, "day:$hoy", 3))
        avanzar(5_000)
        assertTrue(hay("hoja-logro"))
        assertTrue("la cadena espera a que se lea el logro", !hay("veta-DIA"))
    }

    @Test
    fun `a special asks the garden to plant it`() {
        montar()
        val g0 = GardenState.INITIAL
        vm.jardin(g0)
        avanzar(32)
        vm.jardin(g0.copy(specials = setOf(GardenSpecial.LANTERN)))
        avanzar(100)
        assertEquals(GardenSpecial.LANTERN to null, llamadasJardin.last())
        avanzar(1_000)
        assertEquals(null to null, llamadasJardin.last())
    }

    @Test
    fun `a new plot asks the garden to grow it`() {
        montar()
        val g0 = GardenState.INITIAL
        vm.jardin(g0)
        avanzar(32)
        vm.jardin(g0.copy(plots = g0.plots + GardenPlot(GardenSpecies.SUNFLOWERS, GrowthStage.SEED)))
        avanzar(100)
        assertEquals(null to g0.plots.size, llamadasJardin.last())
    }

    @Test
    fun `a drop flies with no moment going on`() {
        montar()
        registro.poner("fila:h1", androidx.compose.ui.geometry.Rect(0f, 400f, 40f, 440f))
        registro.poner(ClavesAncla.PARCELA, androidx.compose.ui.geometry.Rect(100f, 100f, 200f, 220f))
        registro.gota("fila:h1")
        avanzar(100)
        assertEquals(1, sonido.snds().count { it == Snd.GORGOTEO })
    }

    @Test
    fun `the streak vein shows the habit name and its count`() {
        montar()
        runBlocking { db.habitDao().upsert(habitEntity(id = "make-bed", name = "Hacer la cama")) }
        esperar { true }
        avanzar(200)
        insertar(fila("r", 11, PointsReason.STREAK_MILESTONE, "make-bed:7", 5))
        esperar { hay("veta-RACHA") }
        rule.onNodeWithTag("veta-RACHA").assertExists()
        rule.onNodeWithText("7 días seguidos", substring = true, ignoreCase = true).assertExists()
        rule.onNodeWithText("Hacer la cama").assertExists()
    }

    @Test
    fun `the streak badge lights up only the first time`() {
        montar()
        insertar(fila("r", 11, PointsReason.STREAK_MILESTONE, "make-bed:7", 5))
        esperar { hay("veta-RACHA") }
        avanzar(200)
        assertTrue("un logro nuevo se enciende", Snd.LOGRO in sonido.snds())
    }

    @Test
    fun `a badge already seen enters lit without celebrating`() {
        montar()
        runBlocking {
            ajustes.update { it.copy(badgesSeenUntilMillis = 1_000) }
        }
        dejarPasar()
        runBlocking { db.badgeDao().insert(BadgeEntity("streak-7", 500)) }
        dejarPasar()
        insertar(fila("r", 11, PointsReason.STREAK_MILESTONE, "make-bed:7", 5))
        esperar { hay("veta-RACHA") }
        avanzar(200)
        assertTrue("el logro ya visto no suena otra vez", Snd.LOGRO !in sonido.snds())
    }
}
