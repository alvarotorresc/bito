package com.alvarotc.bito.ui.detail

import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.daySealEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.freezerUseEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.sonido.MotorSonidoMudo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
import java.io.File
import java.time.ZoneId

/**
 * La ficha real sobre una base Room en memoria, con el arnés de [com.alvarotc.bito.ui.today.TodayScreenTest]: los
 * ejecutores de Room y el dispatcher principal comparten un [UnconfinedTestDispatcher] para que `uiState` se asiente
 * en `waitForIdle`. Con `reducido`, Habi no respira (un bucle infinito no deja nunca en reposo) y el resto de
 * movimientos se funde en 150 ms; el movimiento en sí lo prueban los tests de [CalendarioGotas] y [Lamina].
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class DetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private var storeIndex = 0
    private var habiOpened = false
    private var edited: String? = null
    private var backed = false

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "detail-screen-${storeIndex++}.preferences_pb") }

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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun setContent(
        habitId: String,
        lectura: Boolean = false,
        propuestas: FuentePropuestas? = null,
    ) {
        val domainState = DomainStateRepository(db)
        val vm =
            DetailViewModel(
                habitId,
                domainState,
                HabitsRepository(db),
                JournalRepository(db),
                RewardsRepository(db),
                SettingsRepository(settingsStore()),
                PointsReconciler(domainState, RewardsRepository(db)),
                HabiSounds(MotorSonidoMudo),
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcher,
                lectura = lectura,
                propuestas = propuestas,
            )
        compose.setContent {
            ConSistema(reducido = true) {
                DetailScreen(
                    viewModel = vm,
                    onBack = { backed = true },
                    onEdit = { edited = it },
                    onOpenHabi = { habiOpened = true },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun create(
        id: String = "h1",
        name: String = "Meditar",
        metric: Metric = Metric.CHECK,
        target: Int = 1,
        createdOnDay: Int = today - 5,
        period: Period = Period.DAY,
        direction: Direction = Direction.AT_LEAST,
        status: HabitStatus = HabitStatus.ACTIVE,
    ) = runBlocking {
        HabitsRepository(db).create(
            habitEntity(
                id = id,
                name = name,
                metric = metric,
                target = target,
                createdOnDay = createdOnDay,
                period = period,
                direction = direction,
                status = status,
            ),
        )
    }

    private fun buyFreezer() =
        runBlocking {
            db.pointsLedgerDao().insert(
                pointsLedgerEntity(id = "buy1", delta = 0, reason = PointsReason.BUY_FREEZER, refId = null, logicalDay = today - 5),
            )
        }

    private fun tapDay(day: Int) {
        compose.onNodeWithTag("dia-$day").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the plate shows three data with the unit of the period`() {
        create(period = Period.WEEK, target = 1, createdOnDay = today - 20)
        runBlocking {
            listOf(0, 7, 14).forEachIndexed { i, d ->
                db.entryDao().insert(entryEntity(id = "e$i", habitId = "h1", logicalDay = today - d, value = 1))
            }
        }
        setContent("h1")

        compose.onNodeWithText("semanas seguidas").assertExists()
        compose.onNodeWithTag("lamina").assertExists()
        compose.onNodeWithText("de los últimos 30 días").assertExists()
        compose.onNodeWithText("Alimenta", substring = true).assertExists()
    }

    @Test
    fun `the calendar changes month with the arrows and stops at the limits`() {
        create(createdOnDay = today - 5) // creado este mes
        setContent("h1")
        compose.onNodeWithTag("mes-anterior").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("mes-siguiente").assertIsNotEnabled()
    }

    @Test
    fun `two months back and no more when created two months ago`() {
        create(createdOnDay = today - 62) // 14 de junio
        setContent("h1")
        compose.onNodeWithText("Agosto 2025").assertExists()
        tap("mes-anterior")
        tap("mes-anterior")
        compose.onNodeWithText("Junio 2025").assertExists()
        compose.onNodeWithTag("mes-anterior").assertIsNotEnabled()
        compose.onNodeWithTag("mes-siguiente").assertIsEnabled()
    }

    @Test
    fun `tapping a past check day opens the day sheet and Hecho writes it`() {
        create()
        setContent("h1")
        tapDay(today - 1)

        compose.onNodeWithTag("day-sheet").assertExists()
        compose.onNodeWithText("No lo hice").assertExists()
        compose.onNodeWithText("Hecho").performClick()
        compose.waitForIdle()
        runBlocking { assertEquals(listOf(today - 1), db.entryDao().all().map { it.logicalDay }) }
    }

    @Test
    fun `a counter day opens the total with the stepper at the day value`() {
        create(metric = Metric.COUNT, target = 8)
        runBlocking { db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today - 2, value = 5)) }
        setContent("h1")
        tapDay(today - 2)

        compose.onNodeWithContentDescription("Total del día").assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(5f, 0f..99_999f)))
        compose.onNodeWithText("Guardar").assertExists()
    }

    @Test
    fun `an abstinence day offers relapse or clean and says it seals the day`() {
        create(direction = Direction.ZERO, target = 0)
        // Una recaída: el día queda fallado y se puede tocar (un día sin sellar es solo el número).
        runBlocking { db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today - 2, value = 1)) }
        setContent("h1")
        tapDay(today - 2)

        compose.onNodeWithText("Recaí ese día").assertExists()
        compose.onNodeWithText("Estuve limpio").assertExists()
        compose.onNodeWithText("Esto sella el día entero.").assertExists()
    }

    @Test
    fun `using a freezer asks for confirmation first`() {
        create(metric = Metric.COUNT, target = 8)
        buyFreezer()
        setContent("h1")
        tapDay(today - 2) // un día fallado de un hábito diario

        compose.onNodeWithText("Usar un congelador (tienes 1)").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("¿Usar un congelador?").assertExists()
        compose.onNodeWithText("Te quedará 0").assertExists()
        runBlocking { assertTrue(db.freezerUseDao().all().isEmpty()) }

        compose.onNodeWithText("Usar congelador").performClick()
        compose.waitForIdle()
        runBlocking { assertEquals("h1", db.freezerUseDao().all().single().habitId) }
    }

    @Test
    fun `a frozen day does not change`() {
        create(metric = Metric.COUNT, target = 8)
        runBlocking { db.freezerUseDao().insert(freezerUseEntity(id = "f1", habitId = "h1", protectedDay = today - 2)) }
        setContent("h1")
        tapDay(today - 2)

        compose.onNodeWithTag("day-sheet").assertDoesNotExist()
        runBlocking { assertEquals(1, db.freezerUseDao().all().size) }
    }

    @Test
    fun `the footer has 7 and 365 compliance, freezers, pause and archive`() {
        create()
        buyFreezer()
        setContent("h1")

        compose.onNodeWithText("7 días", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("365 días", substring = true).assertExists()
        compose.onNodeWithText("Tienes 1").assertExists()
        compose.onNodeWithTag("pause").assertExists()
        compose.onNodeWithTag("archive").assertExists()
        compose.onNodeWithTag("reactivate").assertDoesNotExist()
    }

    @Test
    fun `a paused habit shows since when and offers resume`() {
        create()
        runBlocking { HabitsRepository(db).pause("h1", today - 2, null) }
        setContent("h1")

        compose.onNodeWithText("En pausa desde", substring = true).performScrollTo().assertExists()
        compose.onNodeWithTag("resume").assertExists()
        compose.onNodeWithTag("pause").assertDoesNotExist()
    }

    @Test
    fun `an archived habit only offers reactivation`() {
        create(direction = Direction.ZERO, target = 0, status = HabitStatus.ARCHIVED)
        setContent("h1")

        compose.onNodeWithTag("reactivate").performScrollTo().assertExists()
        compose.onNodeWithTag("pause").assertDoesNotExist()
        compose.onNodeWithTag("archive").assertDoesNotExist()
        compose.onNodeWithTag("freezer-chip").assertDoesNotExist()
        compose.onNodeWithTag("relapse-pill").assertDoesNotExist()
        tapDay(today - 3)
        compose.onNodeWithTag("day-sheet").assertDoesNotExist()
    }

    @Test
    fun `read only mode has no circle, no tappable days and only reactivate`() {
        create(status = HabitStatus.ARCHIVED)
        setContent("h1", lectura = true)

        compose.onNodeWithTag("marcar-hoy").assertDoesNotExist()
        tapDay(today - 3)
        compose.onNodeWithTag("day-sheet").assertDoesNotExist()
        compose.onNodeWithTag("reactivate").performScrollTo().assertExists()
        compose.onNodeWithTag("pause").assertDoesNotExist()
        compose.onNodeWithTag("freezer-chip").assertDoesNotExist()
    }

    @Test
    fun `reactivating from read only mode goes back to the list`() {
        create(status = HabitStatus.ARCHIVED)
        setContent("h1", lectura = true)

        tap("reactivate")
        assertTrue(backed)
    }

    @Test
    fun `the circle marks today and so does tapping today in the calendar`() {
        create()
        setContent("h1")

        tap("marcar-hoy")
        runBlocking { assertEquals(listOf(today), db.entryDao().all().map { it.logicalDay }) }
        // Tocar hoy en el calendario es lo mismo: marcar (aquí, desmarcar).
        tapDay(today)
        runBlocking { assertTrue(db.entryDao().all().isEmpty()) }
    }

    @Test
    fun `edit navigates`() {
        create()
        setContent("h1")

        tap("editar")
        assertEquals("h1", edited)
    }

    @Test
    fun `back goes back`() {
        create()
        setContent("h1")

        tap("atras")
        assertTrue(backed)
    }

    @Test
    fun `the freezer pill without inventory opens the habi store`() {
        create()
        setContent("h1")

        compose.onNodeWithText("Sin congeladores").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(habiOpened)
    }

    @Test
    fun `the freezer info opens from the info icon`() {
        create()
        buyFreezer()
        setContent("h1")

        tap("freezer-info")
        compose.onNodeWithTag("freezer-info-sheet").assertExists()
        compose.onNodeWithText("Entendido").assertExists()
        assertEquals(false, habiOpened)
    }

    @Test
    fun `the freezer info sheet keeps its signature for the store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            ConSistema(reducido = true) {
                FreezerInfoSheet(Personality.NEUTRA, "Ana", EquippedSet(), onDismiss = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("freezer-info-sheet").assertExists()
        compose.onNodeWithText(context.getString(HabiVoice.freezerInfoRes(Personality.NEUTRA), "Ana")).assertExists()
    }

    @Test
    fun `I relapsed asks and the streak rolls to zero`() {
        create(direction = Direction.ZERO, target = 0, createdOnDay = today - 5)
        // Cinco días limpios sellados: la racha es 5 y la cifra no dice «hoy empieza otra».
        runBlocking { (1..5).forEach { db.daySealDao().insert(daySealEntity(logicalDay = today - it)) } }
        setContent("h1")
        compose.onNodeWithText("días limpios").assertExists()
        compose.onNodeWithText("hoy empieza otra", substring = true).assertDoesNotExist()

        tap("relapse-pill")
        compose.onNodeWithText("¿Registrar una recaída?").assertExists()
        compose.onNodeWithText("Registrar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("días seguidos; hoy empieza otra", substring = true).assertExists()
    }

    @Test
    fun `a habit with no entries shows the empty hint`() {
        create()
        setContent("h1")

        compose.onNodeWithText("Registra tu primer día y esto cobra vida").assertExists()
    }

    @Test
    fun `the hint disappears after the first entry`() {
        create()
        runBlocking { db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = today - 3, value = 1)) }
        setContent("h1")

        compose.onNodeWithText("Registra tu primer día y esto cobra vida").assertDoesNotExist()
    }

    @Test
    fun `Habi's note shows the proposal and its answers only when it has actions`() {
        create()
        val respuestas = mutableListOf<Respuesta>()
        val fuente =
            object : FuentePropuestas {
                override fun propuesta(
                    ambito: Ambito,
                    habitId: String?,
                ): Flow<Propuesta?> = flowOf(Propuesta("p1", TextoHabi(R.string.got_it), setOf(Respuesta.VALE, Respuesta.HOY_NO)))

                override suspend fun responder(
                    id: String,
                    respuesta: Respuesta,
                ) {
                    respuestas += respuesta
                }
            }
        setContent("h1", propuestas = fuente)

        compose.onNodeWithText("Entendido").performScrollTo().assertExists()
        compose.onNodeWithText("No hace falta").assertExists()
        compose.onNodeWithText("Vale").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(Respuesta.VALE), respuestas)
    }

    @Test
    fun `a note without actions has no buttons`() {
        create()
        val fuente =
            object : FuentePropuestas {
                override fun propuesta(
                    ambito: Ambito,
                    habitId: String?,
                ): Flow<Propuesta?> = flowOf(Propuesta("p1", TextoHabi(R.string.got_it), emptySet()))

                override suspend fun responder(
                    id: String,
                    respuesta: Respuesta,
                ) = Unit
            }
        setContent("h1", propuestas = fuente)

        compose.onNodeWithText("Entendido").performScrollTo().assertExists()
        compose.onNodeWithText("Vale").assertDoesNotExist()
        compose.onNodeWithText("No hace falta").assertDoesNotExist()
    }
}
