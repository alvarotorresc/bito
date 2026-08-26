package com.alvarotc.bito.ui.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.domain.model.HabitStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SingleHabitConfigViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: BitoDatabase
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var vm: SingleHabitConfigViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        habitsRepo = HabitsRepository(db)
        vm = SingleHabitConfigViewModel(habitsRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun TestScope.offeredHabits(): List<HabitEntity> {
        backgroundScope.launch { vm.habits.collect() }
        advanceUntilIdle()
        return vm.habits.value
    }

    @Test
    fun `only active habits are offered`() =
        runTest {
            habitsRepo.create(habitEntity(id = "agua", status = HabitStatus.ACTIVE, sortOrder = 0))
            habitsRepo.create(habitEntity(id = "pausado", status = HabitStatus.PAUSED, sortOrder = 1))
            habitsRepo.create(habitEntity(id = "archivado", status = HabitStatus.ARCHIVED, sortOrder = 2))

            val offered = offeredHabits()

            assertEquals(listOf("agua"), offered.map { it.id })
        }

    // Saving that null is legal (SingleHabitConfigContentTest covers it) — it stores no habit id
    // and the widget renders its tappable "choose a habit" fallback.
    @Test
    fun `nothing is chosen until the user picks`() =
        runTest {
            assertNull(vm.selected.value)
        }

    @Test
    fun `picking a habit replaces the previous choice`() =
        runTest {
            vm.select("agua")
            assertEquals("agua", vm.selected.value)

            vm.select("pasos")
            assertEquals("pasos", vm.selected.value)
        }

    @Test
    fun `the stored choice preloads on reconfigure`() =
        runTest {
            vm.setInitial("agua")

            assertEquals("agua", vm.selected.value)
        }
}
