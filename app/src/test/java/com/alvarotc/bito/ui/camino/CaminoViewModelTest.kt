package com.alvarotc.bito.ui.camino

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.ContextoDescanso
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaminoViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "camino-vm.preferences_pb") }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BitoDatabase::class.java)
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

    @Test
    fun `the state arrives loaded with the badges, the habit and today as a date`() =
        runTest {
            HabitsRepository(db).create(habitEntity(id = "h1", metric = Metric.CHECK, target = 1, createdOnDay = today - 5))
            val rewards = RewardsRepository(db)
            db.badgeDao().insert(BadgeEntity("first-habit", fixedNow - 2 * 86_400_000L))
            val vm =
                CaminoViewModel(
                    DomainStateRepository(db),
                    SettingsRepository(settingsStore()),
                    rewards,
                    now = { fixedNow },
                    zone = { utc },
                    defaultDispatcher = dispatcher,
                )
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val ui = vm.uiState.value
            assertFalse(ui.loading)
            assertFalse(ui.stats.loading)
            assertFalse(ui.sinHabitos)
            assertEquals(14, ui.logros.size)
            assertEquals(1, ui.conseguidos)
            assertEquals(today - 2, ui.logros.single { it.def.id == "first-habit" }.conseguido)
            assertEquals(LocalDate.ofEpochDay(today.toLong()), ui.hoy)
        }

    private class FuenteCamino(
        inicial: Propuesta?,
    ) : FuentePropuestas {
        val actual = MutableStateFlow(inicial)
        val pedidas = mutableListOf<Ambito>()

        override fun propuesta(
            ambito: Ambito,
            habitId: String?,
            descanso: ContextoDescanso?,
        ): Flow<Propuesta?> {
            pedidas += ambito
            return actual
        }

        override suspend fun responder(
            id: String,
            respuesta: Respuesta,
            hora: Int?,
        ): Efecto = Efecto.Ninguno

        override suspend fun guardarGordo(refs: List<RefGordo>) = Unit
    }

    private fun vmCon(fuente: FuentePropuestas?) =
        CaminoViewModel(
            DomainStateRepository(db),
            SettingsRepository(settingsStore()),
            RewardsRepository(db),
            now = { fixedNow },
            zone = { utc },
            defaultDispatcher = dispatcher,
            propuestas = fuente,
        )

    @Test
    fun `the comment comes from the source for the Camino scope`() =
        runTest {
            HabitsRepository(db).create(habitEntity(id = "h1", metric = Metric.CHECK, target = 1, createdOnDay = today - 5))
            val comentario = Propuesta("CA_NORMAL::20315", TextoHabi(R.string.got_it), emptyList())
            val fuente = FuenteCamino(comentario)
            val vm = vmCon(fuente)
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(comentario, vm.uiState.value.comentario)
            assertEquals(listOf(Ambito.CAMINO), fuente.pedidas)

            val otro = Propuesta("CA_MUSTIO::20315", TextoHabi(R.string.got_it), emptyList())
            fuente.actual.value = otro
            advanceUntilIdle()
            assertEquals(otro, vm.uiState.value.comentario)
        }

    @Test
    fun `without a source Camino has no comment`() =
        runTest {
            HabitsRepository(db).create(habitEntity(id = "h1", metric = Metric.CHECK, target = 1, createdOnDay = today - 5))
            val vm = vmCon(null)
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertNull(vm.uiState.value.comentario)
            assertFalse(vm.uiState.value.loading)
        }

    @Test
    fun `Camino paints without waiting for a comment that never arrives`() =
        runTest {
            HabitsRepository(db).create(habitEntity(id = "h1", metric = Metric.CHECK, target = 1, createdOnDay = today - 5))
            val silenciosa =
                object : FuentePropuestas {
                    override fun propuesta(
                        ambito: Ambito,
                        habitId: String?,
                        descanso: ContextoDescanso?,
                    ): Flow<Propuesta?> = flow { awaitCancellation() }

                    override suspend fun responder(
                        id: String,
                        respuesta: Respuesta,
                        hora: Int?,
                    ): Efecto = Efecto.Ninguno

                    override suspend fun guardarGordo(refs: List<RefGordo>) = Unit
                }
            val vm = vmCon(silenciosa)
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertFalse(vm.uiState.value.loading)
            assertFalse(vm.uiState.value.sinHabitos)
            assertNull(vm.uiState.value.comentario)
        }
}
