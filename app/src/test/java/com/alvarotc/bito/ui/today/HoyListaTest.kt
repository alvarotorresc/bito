package com.alvarotc.bito.ui.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.ui.premios.EventoGota
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h640dp")
class HoyListaTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var banco: BancoHoy

    @Before
    fun setUp() {
        banco = BancoHoy(rule, tmp)
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun estadoCon(
        habitos: Int,
        tareas: List<Tasks.TodaySlot>,
        pausados: Int,
    ) = TodayUiState(
        cards =
            (1..habitos).map {
                HabitCardUi("h$it", "Hábito $it", CardKind.CHECK, 0, 1, null, 1, Direction.AT_LEAST, Period.DAY, false, false, 0)
            },
        tasks = tareas.mapIndexed { i, s -> TaskRowUi("t$i", "Tarea $i", null, s, DueKind.NONE, null) },
        pausedHabits = (1..pausados).map { PausedHabitUi("p$it", "Pausado $it") },
        hasAnyTasks = tareas.isNotEmpty(),
        loading = false,
    )

    @Test
    fun `order is habits, then tasks by slot, then paused`() {
        val filas = filasDe(estadoCon(2, listOf(Tasks.TodaySlot.LOOSE, Tasks.TodaySlot.OVERDUE), 1))
        assertEquals(
            listOf("Habito", "Habito", "Tarea", "Tarea", "VerTareas", "EnPausa", "CerrarDia"),
            filas.map { it::class.simpleName },
        )
    }

    @Test
    fun `tapping the circle logs and tapping the name opens the habit sheet, for every kind`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed, RealHabits.water, RealHabits.reading, RealHabits.noSmoking)
        banco.montar()
        listOf("make-bed", "water", "reading").forEach { id ->
            rule.onNodeWithTag("circulo-$id").performClick()
            banco.esperar()
            assertTrue("$id registra al tocar el círculo", banco.entradasDeHoy(id) >= 1)
        }
        listOf("make-bed", "water", "reading", "no-smoking").forEach { id ->
            rule.onNodeWithTag("nombre-$id").performClick()
            assertEquals(id, banco.fichaAbierta)
            banco.fichaAbierta = null
        }
        assertEquals("el círculo de abstinencia no registra", 0, banco.entradasDeHoy("no-smoking"))
    }

    @Test
    fun `holding the step opens the exact total sheet and saving writes the total`() {
        banco.preparar()
        banco.sembrar(RealHabits.water)
        banco.montar()
        rule.onNodeWithTag("paso-water").performTouchInput { longClick(durationMillis = 500) }
        banco.esperar()
        rule.onNodeWithText("Ajustar el total de hoy").assertIsDisplayed()
        rule.onNodeWithText("Guardar").performClick()
        banco.esperar()
        // El total arranca en el progreso actual (0): guardar lo deja en 0 sin entradas nuevas.
        assertEquals(0, banco.entradasDeHoy("water"))
    }

    @Test
    fun `the exact total sheet changes the total with the stepper`() {
        banco.preparar()
        banco.sembrar(RealHabits.water)
        banco.montar()
        rule.onNodeWithTag("circulo-water").performTouchInput { longClick(durationMillis = 500) }
        banco.esperar()
        rule.onNodeWithText("Ajustar el total de hoy").assertIsDisplayed()
        rule.onNodeWithContentDescriptionMas().performClick()
        rule.onNodeWithContentDescriptionMas().performClick()
        rule.onNodeWithText("Guardar").performClick()
        banco.esperar()
        val total = runBlocking { banco.db.entryDao().all().filter { it.habitId == "water" }.sumOf { it.value } }
        assertEquals(2, total)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionMas() =
        onNodeWithContentDescription("Más")

    @Test
    fun `I relapsed opens the dialog and confirming logs it`() {
        banco.preparar()
        banco.sembrar(RealHabits.noSmoking)
        banco.montar()
        rule.onNodeWithText("He recaído").performClick()
        rule.onNodeWithText("¿Registrar una recaída?").assertIsDisplayed()
        rule.onNodeWithText("Registrar").performClick()
        banco.esperar()
        assertEquals(1, banco.entradasDeHoy("no-smoking"))
    }

    @Test
    fun `a task row opens with start, not today and done, and start opens the focus of that task`() {
        banco.preparar()
        runBlocking { TasksRepository(banco.db).create(taskEntity(id = "t1", createdOnDay = banco.hoy)) }
        runBlocking { TasksRepository(banco.db).create(taskEntity(id = "t2", title = "Otra", createdOnDay = banco.hoy)) }
        banco.montar()
        rule.onNodeWithText("Empezar").assertDoesNotExist()
        rule.onNodeWithTag("tarea-t1").performClick()
        banco.esperar()
        rule.onNodeWithTag("tarea-empezar-t1").assertIsDisplayed()
        rule.onNodeWithTag("tarea-hoy-no-t1").assertIsDisplayed()
        rule.onNodeWithTag("tarea-hecha-t1").assertIsDisplayed()
        rule.onNodeWithTag("tarea-empezar-t1").performClick()
        assertEquals(listOf("ruta:focus?taskId=t1"), banco.llamadas)
    }

    @Test
    fun `not today takes the task off the list`() {
        banco.preparar()
        runBlocking { TasksRepository(banco.db).create(taskEntity(id = "t1", createdOnDay = banco.hoy)) }
        banco.montar()
        rule.onNodeWithTag("tarea-t1").performClick()
        banco.esperar()
        rule.onNodeWithTag("tarea-hoy-no-t1").performClick()
        banco.esperar()
        rule.onAllNodesWithTag("tarea-t1").assertCountEquals(0)
    }

    @Test
    fun `see all tasks stays with every task done`() {
        banco.preparar()
        runBlocking {
            TasksRepository(banco.db).create(
                taskEntity(
                    id = "t1",
                    status = TaskStatus.DONE,
                    createdOnDay = banco.hoy,
                    doneOnDay = banco.hoy - 1,
                    doneAtMillis = banco.fixedNow - 86_400_000,
                ),
            )
        }
        banco.montar()
        rule.onNodeWithText("Ver todas las tareas ›").assertIsDisplayed().performClick()
        assertEquals(listOf("tareas"), banco.llamadas)
        rule.onNodeWithText("Nada pendiente para hoy").assertIsDisplayed()
    }

    @Test
    fun `empty Today offers to create a habit`() {
        banco.preparar()
        banco.montar()
        rule.onNodeWithText("Crear hábito").assertIsDisplayed().performClick()
        assertEquals(listOf("crearHabito"), banco.llamadas)
    }

    @Test
    fun `empty Today puts the seed in its niche`() {
        banco.preparar()
        banco.montar()
        rule.onNodeWithTag("hoy-semilla").assertIsDisplayed()
    }

    @Test
    fun `loading shows breathing placeholders and no card`() {
        banco.preparar(ticks = MutableSharedFlow())
        banco.sembrar(RealHabits.makeBed)
        banco.montar(reducido = true)
        rule.onNodeWithTag("fila-hueco-0").assertExists()
        rule.onAllNodesWithTag("fila-make-bed").assertCountEquals(0)
    }

    @Test
    fun `close the day opens the review`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed)
        banco.montar()
        rule.onNodeWithTag("cerrar-dia").performClick()
        assertEquals(listOf("repaso"), banco.llamadas)
    }

    @Test
    fun `your day counts only habits`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed, RealHabits.meditate)
        runBlocking { TasksRepository(banco.db).create(taskEntity(id = "t1", createdOnDay = banco.hoy)) }
        banco.montar()
        rule.onNodeWithTag("tu-dia", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("de 2", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a long habit name keeps the circle tappable`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed.copy(name = "x".repeat(60)))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) { banco.Contenido() }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("circulo-make-bed").assertIsDisplayed().assertHasClickAction()
        assertTrue(rule.onNodeWithTag("circulo-make-bed").getBoundsInRoot().width >= 40.dp)
    }

    @Test
    fun `going over the limit sinks the water without red and offers undo`() {
        banco.preparar()
        banco.sembrar(RealHabits.socialMedia.copy(target = 2))
        banco.montar()
        repeat(3) {
            rule.onNodeWithTag("circulo-social-media").performClick()
            banco.esperar(100)
        }
        rule.onNodeWithText("Límite pasado. Mañana cuenta otra vez.").assertIsDisplayed()
        rule.onNodeWithText("Deshacer").assertIsDisplayed()
        rule.onNodeWithText("Deshacer").performClick()
        banco.esperar()
        val total = runBlocking { banco.db.entryDao().all().filter { it.habitId == "social-media" }.sumOf { it.value } }
        assertEquals(2, total)
    }

    @Test
    fun `logging launches a drop from the row and unlogging puts it out`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed)
        val eventos = mutableListOf<EventoGota>()
        CoroutineScope(banco.dispatcher).launch { banco.registro.gotas.collect { eventos += it } }
        banco.montar()
        rule.onNodeWithTag("circulo-make-bed").performClick()
        banco.esperar()
        assertEquals(listOf<EventoGota>(EventoGota.Sale("fila:make-bed")), eventos)
        rule.onNodeWithTag("circulo-make-bed").performClick()
        banco.esperar()
        assertEquals(EventoGota.Apaga("fila:make-bed"), eventos.last())
        assertTrue("fila:make-bed" in banco.registro.posiciones)
    }

    @Test
    fun `finishing a task drops grains from its circle and sounds in the row`() {
        banco.preparar()
        runBlocking { TasksRepository(banco.db).create(taskEntity(id = "t1", createdOnDay = banco.hoy)) }
        val eventos = mutableListOf<EventoGota>()
        CoroutineScope(banco.dispatcher).launch { banco.registro.gotas.collect { eventos += it } }
        banco.montar()
        rule.onNodeWithTag("circulo-tarea-t1").performClick()
        banco.esperar(800)
        assertEquals(listOf<EventoGota>(EventoGota.Granos("tarea:t1")), eventos)
        assertTrue(Snd.ASENTAR in banco.sonido.snds())
        assertTrue(Haptica.FALL in banco.tacto.hapticas)
        rule.onNodeWithText("Deshacer").assertIsDisplayed()
        val hecha = runBlocking { banco.db.taskDao().all().first { it.id == "t1" }.status }
        assertEquals(TaskStatus.DONE, hecha)
    }

    @Test
    fun `the logged notice goes away by itself`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed)
        banco.montar()
        rule.onNodeWithTag("circulo-make-bed").performClick()
        banco.esperar(300)
        rule.onNodeWithText("Registrado").assertIsDisplayed()
        banco.esperar(Duraciones.AVISO + 500L)
        rule.onNodeWithText("Registrado").assertDoesNotExist()
    }

    @Test
    fun `a just created habit scrolls into view and calls back once`() {
        banco.preparar()
        banco.sembrar(*(1..15).map { RealHabits.makeBed.copy(id = "h$it", name = "Hábito $it") }.toTypedArray())
        var vistos = 0
        banco.montar(creado = "h15", alCreadoVisto = { vistos++ })
        banco.esperar(1500)
        rule.onNodeWithTag("fila-h15").assertIsDisplayed()
        assertEquals(1, vistos)
    }

    @Test
    fun `a created id that arrives while loading waits for the list`() {
        val ticks = MutableSharedFlow<Unit>()
        banco.preparar(ticks = ticks)
        banco.sembrar(*(1..15).map { RealHabits.makeBed.copy(id = "h$it", name = "Hábito $it") }.toTypedArray())
        var vistos = 0
        banco.montar(creado = "h15", alCreadoVisto = { vistos++ })
        banco.esperar(800)
        assertEquals("mientras carga no se avisa", 0, vistos)
        runBlocking { ticks.emit(Unit) }
        banco.esperar(1500)
        rule.onNodeWithTag("fila-h15").assertIsDisplayed()
        assertEquals(1, vistos)
    }

    @Test
    fun `a created task that does not touch today only calls back`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed)
        var vistos = 0
        banco.montar(creado = "no-esta", alCreadoVisto = { vistos++ })
        banco.esperar(ESPERA_CREADO_MS + 300)
        assertEquals(1, vistos)
    }

    @Test
    fun `a created habit missing from a stale loaded state still scrolls into view`() {
        // Así se ve volver tras más de 5 s en el formulario: Hoy conserva un estado cargado sin el hábito nuevo.
        val pausado = StandardTestDispatcher(TestScope().testScheduler)
        banco.preparar(dispatcherVM = pausado)
        banco.sembrar(*(1..15).map { RealHabits.makeBed.copy(id = "h$it", name = "Hábito $it") }.toTypedArray())
        var creado by mutableStateOf<String?>(null)
        var vistos = 0
        rule.setContent { banco.Contenido(creado = creado, alCreadoVisto = { vistos++ }) }
        rule.waitForIdle()
        pausado.scheduler.advanceUntilIdle()
        rule.waitForIdle()
        runBlocking {
            HabitsRepository(banco.db).create(habitEntity(id = "h16", name = "Hábito 16", createdOnDay = banco.hoy, sortOrder = 15))
        }
        creado = "h16"
        banco.esperar(300)
        pausado.scheduler.advanceUntilIdle()
        banco.esperar(1500)
        rule.onNodeWithTag("fila-h16").assertIsDisplayed()
        assertEquals(1, vistos)
    }

    @Test
    fun `a created task written after the signal still scrolls into view`() {
        banco.preparar()
        banco.sembrar(*(1..15).map { RealHabits.makeBed.copy(id = "h$it", name = "Hábito $it") }.toTypedArray())
        var creado by mutableStateOf<String?>(null)
        var vistos = 0
        rule.setContent { banco.Contenido(creado = creado, alCreadoVisto = { vistos++ }) }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        creado = "t1" // el id llega antes de que la tarea se escriba
        rule.mainClock.advanceTimeBy(200)
        runBlocking { TasksRepository(banco.db).create(taskEntity(id = "t1", createdOnDay = banco.hoy)) }
        rule.mainClock.autoAdvance = true
        banco.esperar(1500)
        rule.onNodeWithTag("tarea-t1").assertIsDisplayed()
        assertEquals(1, vistos)
    }

    @Test
    fun `a finished task is marked at once and its row leaves after the exit animation`() {
        banco.preparar()
        runBlocking { TasksRepository(banco.db).create(taskEntity(id = "t1", createdOnDay = banco.hoy)) }
        banco.montar()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("circulo-tarea-t1").performClick()
        rule.waitForIdle()
        val estado = runBlocking { banco.db.taskDao().all().first { it.id == "t1" }.status }
        assertEquals("se marca sin esperar a la animación", TaskStatus.DONE, estado)
        rule.onNodeWithTag("tarea-t1").assertExists()
        rule.mainClock.advanceTimeBy(800)
        rule.onAllNodesWithTag("tarea-t1").assertCountEquals(0)
    }

    @Test
    fun `holding the name and dragging reorders the habits`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed, RealHabits.meditate, RealHabits.floss)
        banco.montar()
        rule.onNodeWithTag("nombre-make-bed").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 60f), delayMillis = 50)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 120f), delayMillis = 50)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 60f), delayMillis = 50)
            up()
        }
        banco.esperar()
        val orden = runBlocking { banco.db.habitDao().all().sortedBy { it.sortOrder }.map { it.id } }
        assertTrue("make-bed ya no es el primero: $orden", orden.first() != "make-bed")
        assertEquals(null, banco.fichaAbierta)
    }

    @Test
    fun `habit rows keep their streaks for every kind and the name is a full touch target`() {
        fun card(
            id: String,
            kind: CardKind,
            period: Period = Period.DAY,
            direction: Direction = Direction.AT_LEAST,
            streak: Int,
        ) = HabitCardUi(id, "H $id", kind, 1, 3, null, 1, direction, period, false, false, streak)
        val cards =
            listOf(
                card("check", CardKind.CHECK, streak = 5),
                card("semana", CardKind.CHECK, period = Period.WEEK, streak = 3),
                card("cuenta", CardKind.COUNTER, streak = 4),
                card("dura", CardKind.DURATION, streak = 6),
                card("abst", CardKind.ABSTINENCE, direction = Direction.ZERO, streak = 12),
            )
        rule.setContent {
            com.alvarotc.bito.ui.sistema.ConSistema {
                androidx.compose.foundation.layout.Column {
                    cards.forEach { FilaHabito(it, {}, {}, {}, {}, {}) }
                }
            }
        }
        rule.onNodeWithText("día 5").assertIsDisplayed()
        rule.onNodeWithText("racha de 3", substring = true).assertIsDisplayed()
        rule.onNodeWithText("día 4").assertIsDisplayed()
        rule.onNodeWithText("día 6").assertIsDisplayed()
        rule.onNodeWithText("día 12", substring = true).assertIsDisplayed()
        listOf("check", "semana", "cuenta", "dura", "abst").forEach {
            assertTrue("nombre-$it pulsable", rule.onNodeWithTag("nombre-$it").getBoundsInRoot().height >= 44.dp)
        }
    }

    @Test
    fun `an exiting task row keeps its place among the tasks`() {
        banco.preparar()
        runBlocking {
            TasksRepository(
                banco.db,
            ).create(taskEntity(id = "t1", title = "Primera", dueKind = DueKind.DATE, dueDay = banco.hoy, createdOnDay = banco.hoy))
            TasksRepository(
                banco.db,
            ).create(taskEntity(id = "t2", title = "Segunda", dueKind = DueKind.DATE, dueDay = banco.hoy, createdOnDay = banco.hoy))
        }
        banco.montar()
        val antes = rule.onNodeWithTag("tarea-t1").getBoundsInRoot().top.value < rule.onNodeWithTag("tarea-t2").getBoundsInRoot().top.value
        val primera = if (antes) "t1" else "t2"
        val otra = if (antes) "t2" else "t1"
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("circulo-tarea-$primera").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tarea-$primera").assertExists()
        val arriba = rule.onNodeWithTag("tarea-$primera").getBoundsInRoot().top.value
        val abajo = rule.onNodeWithTag("tarea-$otra").getBoundsInRoot().top.value
        assertTrue(arriba < abajo)
    }

    private fun esperarNodo(tag: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the big thing tag reopens the sheet`() {
        val fuente = FuenteFalsa()
        banco.preparar(fuente)
        runBlocking {
            TasksRepository(banco.db).create(
                taskEntity(
                    id = "informe",
                    title = "Informe trimestral",
                    dueKind = DueKind.DATE,
                    dueDay = banco.hoy,
                    createdOnDay = banco.hoy,
                ),
            )
            com.alvarotc.bito.data.habla.LoGordoRepository(banco.db, now = { banco.fixedNow })
                .guardar(
                    banco.hoy,
                    listOf(com.alvarotc.bito.domain.habla.RefGordo(com.alvarotc.bito.domain.habla.TipoRefGordo.TAREA, "informe")),
                )
        }
        banco.montar()
        esperarNodo("gordo-etiqueta-informe")
        rule.onAllNodesWithTag("hoja-gordo").assertCountEquals(0)
        assertTrue(rule.onNodeWithTag("gordo-etiqueta-informe", useUnmergedTree = true).getBoundsInRoot().height >= 44.dp)
        rule.onNodeWithTag("gordo-etiqueta-informe", useUnmergedTree = true).performClick()
        banco.esperar()
        rule.onNodeWithTag("hoja-gordo").assertIsDisplayed()
    }

    @Test
    fun `the accepted hour shows in the row`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed, RealHabits.water)
        runBlocking {
            com.alvarotc.bito.data.habla.AvisosAceptadosRepository(banco.db).crear(
                com.alvarotc.bito.domain.habla.AvisoAceptado(
                    "a1",
                    banco.hoy,
                    "make-bed",
                    14 * 60 + 30,
                    0,
                    com.alvarotc.bito.domain.habla.EstadoAviso.PENDIENTE,
                    "HORA_HABITUAL:make-bed:${banco.hoy}",
                    banco.fixedNow,
                ),
            )
        }
        banco.montar()
        esperarNodo("hora-make-bed")
        rule.onNodeWithTag("hora-make-bed").assertIsDisplayed()
        rule.onNodeWithText("14:30").assertIsDisplayed()
        rule.onAllNodesWithTag("hora-water").assertCountEquals(0)
    }

    @Test
    fun `on a hard day the rest waits without hurry and can still be marked`() {
        val fuente =
            FuenteFalsa(
                com.alvarotc.bito.domain.propuestas.Propuesta(
                    id = "DIA_DURO:make-bed:${banco.hoy}",
                    texto = com.alvarotc.bito.domain.propuestas.TextoHabi(com.alvarotc.bito.R.string.habla_vale),
                    acciones = emptyList(),
                ),
            )
        banco.preparar(fuente)
        banco.sembrar(RealHabits.makeBed, RealHabits.water, RealHabits.reading)
        runBlocking {
            com.alvarotc.bito.data.repo.JournalRepository(banco.db).log(
                com.alvarotc.bito.data.db.EntryEntity("e1", "reading", banco.hoy, 60, banco.fixedNow),
            )
        }
        banco.montar()
        esperarNodo("esperan-sin-prisa")
        val seccion = androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("esperan-sin-prisa"))
        rule.onNodeWithText("Esperan sin prisa").assertIsDisplayed()
        // Lo que la tarjeta propone y lo que ya está hecho se quedan en su sitio.
        rule.onNode(androidx.compose.ui.test.hasTestTag("fila-water") and seccion).assertExists()
        rule.onAllNodes(androidx.compose.ui.test.hasTestTag("fila-make-bed") and seccion).assertCountEquals(0)
        rule.onAllNodes(androidx.compose.ui.test.hasTestTag("fila-reading") and seccion).assertCountEquals(0)
        rule.onNodeWithTag("fila-make-bed").assertExists()
        rule.onNodeWithTag("fila-reading").assertExists()
        // Tocables con el mismo gesto de marcar.
        rule.onNodeWithTag("circulo-water").performClick()
        banco.esperar()
        assertTrue(banco.entradasDeHoy("water") >= 1)
    }

    private fun sellarHoy() = runBlocking { com.alvarotc.bito.data.repo.JournalRepository(banco.db).sealDay(banco.hoy, banco.fixedNow) }

    @Test
    fun `at night sealed rows only tell the day is closed`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed, RealHabits.water)
        sellarHoy()
        banco.montar()
        esperarNodo("cerrar-dia")
        rule.onNodeWithText("Día sellado").assertIsDisplayed()
        rule.onNodeWithTag("circulo-make-bed").performClick()
        rule.onNodeWithTag("paso-water").performClick()
        banco.esperar()
        assertEquals(0, banco.entradasDeHoy("make-bed"))
        assertEquals(0, banco.entradasDeHoy("water"))
        assertTrue(Haptica.TICK in banco.tacto.hapticas)
        rule.onNodeWithText("El día está cerrado", substring = true).assertIsDisplayed()
        // El nombre sigue abriendo la ficha.
        rule.onNodeWithTag("nombre-make-bed").performClick()
        assertEquals("make-bed", banco.fichaAbierta)
    }

    @Test
    fun `tasks show tomorrow at night`() {
        banco.preparar()
        banco.sembrar(RealHabits.makeBed)
        runBlocking {
            TasksRepository(banco.db).create(
                taskEntity(id = "t1", title = "Primera", dueKind = DueKind.DATE, dueDay = banco.hoy, createdOnDay = banco.hoy),
            )
        }
        banco.montar()
        esperarNodo("tarea-t1")
        rule.onAllNodesWithTag("manana-t1", useUnmergedTree = true).assertCountEquals(0)
        sellarHoy()
        esperarNodo("manana-t1")
        rule.onNodeWithTag("manana-t1", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("mañana", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `day N goes bold and up during the streak vein`() {
        var veta by mutableStateOf(false)
        val card = HabitCardUi("check", "H check", CardKind.CHECK, 0, 1, null, 1, Direction.AT_LEAST, Period.DAY, false, false, 5)
        rule.setContent {
            com.alvarotc.bito.ui.sistema.ConSistema {
                FilaHabito(card, {}, {}, {}, {}, {}, rachaViva = veta)
            }
        }

        fun peso(): androidx.compose.ui.text.font.FontWeight? {
            val resultados = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithTag(
                "dia-n-check",
            ).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
                .action!!(resultados)
            return resultados.first().layoutInput.style.fontWeight
        }
        val pesoNormal = peso()
        val arriba = rule.onNodeWithTag("dia-n-check").getBoundsInRoot().top
        veta = true
        rule.waitForIdle()
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, peso())
        assertTrue("sin veta no es negrita: $pesoNormal", pesoNormal != androidx.compose.ui.text.font.FontWeight.Bold)
        val sube = arriba - rule.onNodeWithTag("dia-n-check").getBoundsInRoot().top
        assertEquals(4f, sube.value, 0.5f)
    }
}
