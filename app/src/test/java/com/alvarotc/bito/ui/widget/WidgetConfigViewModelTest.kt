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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetConfigViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: BitoDatabase
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var vm: WidgetConfigViewModel

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
        vm = WidgetConfigViewModel(habitsRepo)
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

    @Test
    fun `toggling flips membership`() =
        runTest {
            habitsRepo.create(habitEntity(id = "agua"))
            offeredHabits()

            vm.toggle("agua")
            assertTrue(vm.selected.value.contains("agua"))

            vm.toggle("agua")
            assertFalse(vm.selected.value.contains("agua"))
        }

    @Test
    fun `initial selection comes from the widget state`() =
        runTest {
            vm.setInitial(setOf("agua"))

            assertTrue(vm.selected.value.contains("agua"))
        }
}
