package com.alvarotc.bito.ui.settings.hojas

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.backup.Argon2Params
import com.alvarotc.bito.data.backup.BackupCrypto
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.AutoBackupError
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.ui.settings.BackupViewModel
import com.alvarotc.bito.ui.settings.estratos.ContenidoAcerca
import com.alvarotc.bito.ui.settings.estratos.ContenidoCopias
import com.alvarotc.bito.ui.sistema.ConSistema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

private val ARGON2_BARATO = Argon2Params(memoryKib = 64, iterations = 1, parallelism = 1)
private const val CARPETA = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FBito"

/**
 * Las copias de seguridad de la Bito 2: el estrato (`ContenidoCopias`), sus hojas y Acerca. Ejercita el
 * `BackupViewModel` de verdad sobre Room en memoria y un DataStore temporal, como hacía la pantalla vieja.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h1200dp")
class HojasCopiasTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: BitoDatabase
    private lateinit var ajustes: SettingsRepository
    private lateinit var claves: BackupKeyStore
    private lateinit var copias: BackupRepository
    private val resolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private fun almacen(nombre: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher + Job())) { java.io.File(tmp.root, "$nombre.preferences_pb") }

    @Before
    fun preparar() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        ajustes = SettingsRepository(almacen("copias"))
        claves = BackupKeyStore(tmp.root)
        copias = BackupRepository(db, ajustes, claves, "test")
    }

    @After
    fun cerrar() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun crearVm(
        cripto: kotlinx.coroutines.CoroutineDispatcher = dispatcher,
        contar: suspend (String) -> Int? = { null },
    ) = BackupViewModel(
        copias,
        ajustes,
        claves,
        backupNow = {},
        ioDispatcher = dispatcher,
        cryptoDispatcher = cripto,
        deriveParams = ARGON2_BARATO,
        countBackups = contar,
    )

    private fun montarCopias(vm: BackupViewModel) {
        rule.setContent { ConSistema { ContenidoCopias(vm) } }
        rule.waitForIdle()
    }

    private fun carpeta() {
        runBlocking { ajustes.update { it.copy(backupFolderUri = CARPETA) } }
        rule.waitForIdle()
    }

    private fun registrar(
        nombre: String,
        bytes: ByteArray,
    ): Uri = Uri.parse("content://bito/$nombre.bito").also { shadowOf(resolver).registerInputStream(it, ByteArrayInputStream(bytes)) }

    private fun cifrada(): ByteArray =
        BackupCrypto.encrypt(
            runBlocking { copias.exportJson(0L) },
            BackupCrypto.deriveKey("right-pass".toCharArray(), BackupCrypto.newSalt(), ARGON2_BARATO),
        )

    // ---------- hoja de passphrase ----------

    @Test
    fun `the passphrase button stays switched off until both match and explains why`() {
        var cifrado: CharArray? = null
        rule.setContent { ConSistema { HojaPassphrase(true, { cifrado = it }, {}) } }
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("abcd")
        rule.onNodeWithText("Faltan 4 caracteres").assertIsDisplayed()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("efgh")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("abcdefgx")
        rule.onNodeWithText("No coinciden").assertIsDisplayed()
        rule.onNodeWithText("Cifrar las copias").performClick()
        assertNull(cifrado)
    }

    @Test
    fun `matching passphrases of eight or more characters hand over the passphrase`() {
        var cifrado: CharArray? = null
        rule.setContent { ConSistema { HojaPassphrase(true, { cifrado = it }, {}) } }
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("huerto-2026")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("huerto-2026")
        rule.onNodeWithText("Coinciden").assertIsDisplayed()
        rule.onNodeWithText("Cifrar las copias").performClick()
        assertEquals("huerto-2026", String(cifrado!!))
    }

    @Test
    fun `a short passphrase never enables the button even when both are equal`() {
        var cifrado: CharArray? = null
        rule.setContent { ConSistema { HojaPassphrase(true, { cifrado = it }, {}) } }
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("short1")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("short1")
        rule.onNodeWithText("Cifrar las copias").performClick()
        assertNull(cifrado)
    }

    // ---------- hoja de restaurar ----------

    @Test
    fun `an unencrypted restore has two steps`() {
        runBlocking { HabitsRepository(db).create(habitEntity(id = "h0", name = "Uno")) }
        val vm = crearVm()
        val uri = registrar("sin-cifrar", runBlocking { copias.exportJson(0L) }.toByteArray())
        rule.setContent { ConSistema { HojaRestaurar(vm) {} } }
        rule.onNodeWithText("Paso 1 de 2").assertIsDisplayed()
        rule.onNodeWithText("Elegir archivo").assertIsDisplayed()

        vm.loadImport(resolver, uri)
        rule.waitForIdle()

        rule.onNodeWithText("Paso 2 de 2").assertIsDisplayed()
        rule.onNodeWithText("Restaurar").assertIsDisplayed()
        rule.onNodeWithText("Ahora").assertIsDisplayed()
        rule.onNodeWithText("La copia").assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun `the restore preview lines up what there is now with the backup`() {
        runBlocking { HabitsRepository(db).create(habitEntity(id = "ip-h0", name = "Uno")) }
        val exportada = runBlocking { copias.exportJson(0L) }
        runBlocking {
            HabitsRepository(db).create(habitEntity(id = "ip-h1", name = "Dos"))
            HabitsRepository(db).create(habitEntity(id = "ip-h2", name = "Tres"))
        }
        val vm = crearVm()
        val uri = registrar("ahora-y-copia", exportada.toByteArray())
        rule.setContent { ConSistema { HojaRestaurar(vm) {} } }

        vm.loadImport(resolver, uri)
        rule.waitForIdle()

        // Ahora hay 3 hábitos; la copia trae 1. Ningún registro en ninguno de los dos.
        rule.onNodeWithText("Hábitos").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed()
        rule.onNodeWithText("1").assertIsDisplayed()
        assertEquals(2, rule.onAllNodesWithText("0").fetchSemanticsNodes().size)
    }

    @Test
    fun `an encrypted import goes through the passphrase step`() {
        val vm = crearVm()
        val uri = registrar("cifrada", cifrada())
        rule.setContent { ConSistema { HojaRestaurar(vm) {} } }

        vm.loadImport(resolver, uri)
        rule.waitForIdle()

        rule.onNodeWithText("Paso 2 de 3").assertIsDisplayed()
        rule.onNodeWithText("Esta copia está cifrada").assertIsDisplayed()

        rule.onAllNodes(hasSetTextAction())[0].performTextInput("right-pass")
        rule.onNodeWithText("Seguir").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Paso 3 de 3").assertIsDisplayed()
        rule.onNodeWithText("¿Restaurar esta copia?").assertIsDisplayed()
    }

    @Test
    fun `a wrong passphrase shows the error and keeps the step`() {
        val vm = crearVm()
        val uri = registrar("mala-clave", cifrada())
        rule.setContent { ConSistema { HojaRestaurar(vm) {} } }
        vm.loadImport(resolver, uri)
        rule.waitForIdle()

        rule.onAllNodes(hasSetTextAction())[0].performTextInput("nope-pass")
        rule.onNodeWithText("Seguir").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Passphrase incorrecta").assertIsDisplayed()
        rule.onNodeWithText("Esta copia está cifrada").assertIsDisplayed()
    }

    /**
     * Cancelar no espera: si el descifrado aún corre y resuelve mal después de cerrar, el siguiente
     * archivo cifrado no debe abrirse ya con «Passphrase incorrecta».
     */
    @Test
    fun `dismissing during a wrong-passphrase check does not poison the next one`() {
        val cripto = StandardTestDispatcher()
        val vm = crearVm(cripto)
        val bytes = cifrada()
        rule.setContent { ConSistema { HojaRestaurar(vm) {} } }
        vm.loadImport(resolver, registrar("veneno-1", bytes))
        rule.waitForIdle()

        rule.onAllNodes(hasSetTextAction())[0].performTextInput("nope-pass")
        rule.onNodeWithText("Seguir").performClick()
        vm.dismissImportPassphrase()
        rule.waitForIdle()
        cripto.scheduler.advanceUntilIdle()
        rule.waitForIdle()

        vm.loadImport(resolver, registrar("veneno-2", bytes))
        rule.waitForIdle()

        rule.onNodeWithText("Esta copia está cifrada").assertIsDisplayed()
        rule.onNodeWithText("Passphrase incorrecta").assertDoesNotExist()
    }

    @Test
    fun `a file that is not a backup is explained inside the sheet`() {
        val vm = crearVm()
        rule.setContent { ConSistema { HojaRestaurar(vm) {} } }
        vm.loadImport(resolver, registrar("basura", "esto no es una copia".toByteArray()))
        rule.waitForIdle()

        rule.onNodeWithText("Eso no es una copia válida de Bito").assertIsDisplayed()
        rule.onNodeWithText("Paso 1 de 2").assertIsDisplayed()
    }

    @Test
    fun `confirming the restore applies it and closes the sheet`() {
        runBlocking { HabitsRepository(db).create(habitEntity(id = "h1", name = "Uno")) }
        val exportada = runBlocking { copias.exportJson(0L) }
        runBlocking { db.habitDao().deleteAll() }
        val vm = crearVm()
        var cerrada = false
        rule.setContent { ConSistema { HojaRestaurar(vm) { cerrada = true } } }
        vm.loadImport(resolver, registrar("confirmar", exportada.toByteArray()))
        rule.waitForIdle()

        rule.onNodeWithText("Restaurar").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertEquals("h1", runBlocking { db.habitDao().all().single().id })
        assertEquals(true, cerrada)
    }

    // ---------- el estrato de copias ----------

    @Test
    fun `without a folder the strata offers choosing one and hides the schedule`() {
        montarCopias(crearVm())

        rule.onNodeWithText("Elegir carpeta").assertIsDisplayed()
        rule.onNodeWithText("Backup automático").assertDoesNotExist()
        rule.onNodeWithText("Conservar").assertDoesNotExist()
        rule.onNodeWithText("Hacer backup ahora").assertDoesNotExist()
        rule.onNodeWithText("Restaurar…").assertIsDisplayed()
        rule.onNodeWithText("Exportar copia…").assertIsDisplayed()
    }

    @Test
    fun `with a folder the schedule rows and the back up now button appear`() {
        montarCopias(crearVm(contar = { 4 }))
        carpeta()

        rule.onNodeWithText("Backup automático").assertIsDisplayed()
        rule.onNodeWithText("Diaria").assertIsDisplayed()
        rule.onNodeWithText("Conservar").assertIsDisplayed()
        rule.onNodeWithText("Hacer backup ahora").assertIsDisplayed()
        rule.onNodeWithText("4 copias en Documents/Bito").assertIsDisplayed()
    }

    @Test
    fun `a folder error shows its box with the action`() {
        runBlocking { ajustes.update { it.copy(backupFolderUri = CARPETA, lastAutoBackupError = AutoBackupError.FOLDER) } }
        montarCopias(crearVm())

        rule.onNodeWithText("La carpeta ya no está disponible").assertIsDisplayed()
        rule.onNodeWithText("Elegir carpeta").assertIsDisplayed()
    }

    @Test
    fun `a missing key box opens the passphrase sheet`() {
        runBlocking {
            ajustes.update {
                it.copy(
                    backupFolderUri = CARPETA,
                    backupEncryption = true,
                    lastAutoBackupError = AutoBackupError.MISSING_KEY,
                )
            }
        }
        montarCopias(crearVm())

        rule.onNodeWithText("Poner passphrase").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Cifrado de las copias").assertIsDisplayed()
    }

    @Test
    fun `the encryption switch opens the passphrase sheet and the key lands in the settings`() {
        val vm = crearVm()
        montarCopias(vm)

        rule.onNodeWithText("Cifrado").performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("huerto-2026")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("huerto-2026")
        rule.onNodeWithText("Cifrar las copias").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertEquals(true, runBlocking { ajustes.settings.first() }.backupEncryption)
        rule.onNodeWithText("Cifrado activado").assertIsDisplayed()
    }

    @Test
    fun `turning encryption off asks first and keeps the key until confirmed`() {
        runBlocking { ajustes.update { it.copy(backupEncryption = true) } }
        runBlocking {
            claves.save(BackupCrypto.deriveKey("huerto-2026".toCharArray(), BackupCrypto.newSalt(), ARGON2_BARATO))
        }
        montarCopias(crearVm())

        rule.onNodeWithText("Cifrado").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("¿Quitar el cifrado?").assertIsDisplayed()
        assertNotNull(claves.load())

        rule.onNodeWithText("Quitar el cifrado").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertEquals(false, runBlocking { ajustes.settings.first() }.backupEncryption)
    }

    @Test
    fun `needs-key state relabels the encryption row`() {
        runBlocking { ajustes.update { it.copy(backupEncryption = true) } }
        montarCopias(crearVm())

        rule.onNodeWithText("Necesita passphrase").assertIsDisplayed()
    }

    @Test
    fun `restore opens the restore sheet`() {
        montarCopias(crearVm())

        rule.onNodeWithText("Restaurar…").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Restaurar una copia").assertIsDisplayed()
        rule.onNodeWithText("Elegir archivo").assertIsDisplayed()
    }

    @Test
    fun `export opens the short sheet with the suggested name`() {
        val vm = crearVm()
        montarCopias(vm)

        rule.onNodeWithText("Exportar copia…").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Guardar archivo").assertIsDisplayed()
        rule.onNodeWithText(vm.suggestedFileName()).assertIsDisplayed()
        rule.onNodeWithText("Saldrá sin cifrar: cualquiera con el archivo podrá leerlo.").assertIsDisplayed()
    }

    @Test
    fun `a finished export is announced`() {
        val vm = crearVm()
        montarCopias(vm)
        val uri = Uri.parse("content://bito/exportada.bito")
        shadowOf(resolver).registerOutputStream(uri, ByteArrayOutputStream())

        vm.export(resolver, uri)
        rule.waitForIdle()

        rule.onNodeWithText("Copia guardada").assertIsDisplayed()
    }

    // ---------- Acerca ----------

    @Test
    fun `about shows the version and opens the support and license sheets`() {
        rule.setContent { ConSistema { ContenidoAcerca() } }

        rule.onNodeWithText("Versión").assertIsDisplayed()
        rule.onNodeWithText("Apoya el proyecto").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("sin anuncios", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the license sheet offers the full text`() {
        rule.setContent { ConSistema { ContenidoAcerca() } }

        rule.onNodeWithText("Licencia libre").performClick()
        rule.waitForIdle()

        // El subtítulo de la fila y el cuerpo de la hoja nombran la licencia.
        assertEquals(2, rule.onAllNodesWithText("GPL-3.0", substring = true).fetchSemanticsNodes().size)
        rule.onNodeWithText("Ver licencia").assertIsDisplayed()
    }

    @Test
    fun `the source code row warns and then fires a browser intent at the repo`() {
        rule.setContent { ConSistema { ContenidoAcerca() } }

        rule.onNodeWithText("Código fuente").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Se abre GitHub en el navegador").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()

        val intent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://github.com/alvarotorresc/bito", intent.data.toString())
    }
}
