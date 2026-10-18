package com.alvarotc.bito.ui.settings

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.habi.HabiViewModel
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.sonido.MotorSonidoMudo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.provider.Settings as AndroidSettings

/**
 * Ajustes de la Bito 2 sobre dependencias aisladas (Room en memoria, DataStore temporal), no sobre
 * `AppContainer`, cuyo `ReminderSync` dejaría corrutinas vivas entre tests de Robolectric.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h600dp")
class AjustesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: BitoDatabase
    private lateinit var repository: SettingsRepository
    private var archivadosAbiertos = 0

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

    private fun montar() {
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, repository, keyStore, "test"),
                repository,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(repository, HabitsRepository(db))
        val domainState = DomainStateRepository(db)
        val rewards = RewardsRepository(db)
        val habiVm =
            HabiViewModel(
                domainState,
                rewards,
                repository,
                HabiSounds(MotorSonidoMudo),
                defaultDispatcher = dispatcher,
            )
        rule.setContent {
            ConSistema {
                AjustesScreen(
                    backupViewModel = backupVm,
                    settingsViewModel = settingsVm,
                    habiViewModel = habiVm,
                    onBack = {},
                    onOpenArchived = { archivadosAbiertos++ },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun abrir(titulo: String) {
        rule.onNodeWithText(titulo).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun avanzar() = rule.waitForIdle()

    private fun ajustes() = runBlocking { repository.settings.first() }

    private fun pulsar(texto: String) {
        rule.onNodeWithText(texto).performScrollTo().performClick()
        avanzar()
    }

    @Test
    fun `the five strata are there and only one is open at a time`() {
        montar()
        listOf("General", "Avisos y respiración", "Sonido y tacto", "Copias de seguridad", "Acerca de").forEach {
            rule.onNodeWithText(it).assertExists()
        }
        rule.onNodeWithText("General").performClick()
        rule.onNodeWithText("Tu nombre").assertIsDisplayed()
        rule.onNodeWithText("Sonido y tacto").performScrollTo().performClick()
        rule.onNodeWithText("Reducir animaciones").assertExists()
        rule.onNodeWithText("Tu nombre").assertDoesNotExist()
    }

    @Test
    fun `closed strata summarise their contents`() {
        runBlocking { repository.update { it.copy(globalReminderMinutes = SettingsRepository.DEFAULT_GLOBAL_REMINDER_MINUTES) } }
        montar()
        rule.onNodeWithText("Neutra · Español").assertDoesNotExist()
        rule.onNodeWithText("Neutra · Del sistema").assertExists()
        rule.onNodeWithText("12:00 y 19:00 · Habi 8:30 y 21:30").assertExists()
        rule.onNodeWithText("Sonido encendido · vibración 7").assertExists()
    }

    @Test
    fun `every row of the sound stratum writes its key`() {
        montar()
        abrir("Sonido y tacto")
        pulsar("Reducir animaciones")
        pulsar("Sonidos de Habi")
        pulsar("Sonido al registrar")
        rule.onNode(hasText("Vibración") and hasClickAction()).performScrollTo().performClick()
        avanzar()
        pulsar("Sonido")
        val s = ajustes()
        assertTrue(s.reduceMotion)
        assertFalse(s.habiSoundsEnabled)
        assertFalse(s.logSoundEnabled)
        assertFalse(s.logHapticEnabled)
        assertFalse(s.soundEnabled)
        rule.onNodeWithText("Sonido y tacto").assertExists()
    }

    @Test
    fun `Habi sounds off switches its rail off`() {
        runBlocking { repository.update { it.copy(habiSoundsEnabled = false) } }
        montar()
        abrir("Sonido y tacto")
        rule.onNodeWithTag("riel-HABI").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("riel-EFECTOS").assertIsEnabled()
    }

    @Test
    fun `sound off writes the summary and mutes every rail`() {
        runBlocking { repository.update { it.copy(soundEnabled = false) } }
        montar()
        rule.onNodeWithText("Sonido apagado").assertExists()
        abrir("Sonido y tacto")
        rule.onNodeWithTag("riel-EFECTOS").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `the notices stratum switches write their keys`() {
        montar()
        abrir("Avisos y respiración")
        pulsar("Celebración de día perfecto")
        pulsar("Avisos de tareas")
        pulsar("Recordatorio diario")
        val s = ajustes()
        assertFalse(s.perfectDayCelebration)
        assertFalse(s.taskNoticesEnabled)
        assertTrue(s.breathingReminderEnabled)
    }

    @Test
    fun `the breathing hour row only exists with the reminder on`() {
        montar()
        abrir("Avisos y respiración")
        rule.onNodeWithTag("breathing-time-row").assertDoesNotExist()
        pulsar("Recordatorio diario")
        rule.onNodeWithTag("breathing-time-row").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("breathing-time-row").assertExists()
        rule.onNodeWithText("22:00").assertExists()
    }

    @Test
    fun `the archived row only shows with archived habits`() {
        montar()
        abrir("General")
        rule.onNodeWithText("Hábitos archivados", substring = true).assertDoesNotExist()
    }

    @Test
    fun `tapping the archived row opens the archived list`() {
        runBlocking { HabitsRepository(db).create(habitEntity(id = "gone", name = "Fumar", status = HabitStatus.ARCHIVED)) }
        montar()
        abrir("General")
        rule.onNodeWithText("Hábitos archivados (1)").assertExists()
        pulsar("Hábitos archivados (1)")
        assertEquals(1, archivadosAbiertos)
    }

    @Test
    fun `Habi notices row opens its sheet`() {
        montar()
        abrir("Avisos y respiración")
        rule.onNode(isDialog()).assertDoesNotExist()
        pulsar("Avisos de Habi")
        rule.onNode(isDialog()).assertExists()
        rule.onNodeWithText("Mañana").assertExists()
        rule.onNodeWithText("Noche").assertExists()
    }

    @Test
    fun `the language row cycles system to es to en to system, persisting each step`() {
        montar()
        abrir("General")
        rule.onNodeWithText("Del sistema").assertExists()
        pulsar("Idioma")
        assertEquals("es", ajustes().languageTag)
        pulsar("Idioma")
        assertEquals("en", ajustes().languageTag)
        pulsar("Idioma")
        assertNull(ajustes().languageTag)
    }

    @Test
    fun `the name field saves on done`() {
        montar()
        abrir("General")
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("Alvaro")
        rule.onNode(hasSetTextAction()).performImeAction()
        avanzar()
        assertEquals("Alvaro", ajustes().userName)
    }

    @Test
    fun `the notifications notice shows when notifications are switched off and stays hidden otherwise`() {
        montar()
        abrir("Avisos y respiración")
        rule.onAllNodesWithText("Permite las notificaciones para oír los avisos").assertCountEquals(0)
    }

    @Test
    fun `with notifications off the notice shows and opens this app's notification settings`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        montar()
        abrir("Avisos y respiración")
        rule.onNodeWithText("Permite las notificaciones para oír los avisos").performScrollTo().assertIsDisplayed()
        pulsar("Permite las notificaciones para oír los avisos")
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(AndroidSettings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun `with notifications off the breathing reminder repeats the notice while its switch is on`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        runBlocking { repository.update { it.copy(breathingReminderEnabled = true) } }
        montar()
        abrir("Avisos y respiración")
        rule.onAllNodesWithText("Permite las notificaciones para oír los avisos").assertCountEquals(2)
    }

    @Test
    fun `the celebration row is one switch stop`() {
        montar()
        abrir("Avisos y respiración")
        rule.onNodeWithTag("celebration-row").performScrollTo().assertIsOn()
        rule.onNodeWithTag("celebration-row").performClick()
        avanzar()
        rule.onNodeWithTag("celebration-row").assertIsOff()
    }
}
