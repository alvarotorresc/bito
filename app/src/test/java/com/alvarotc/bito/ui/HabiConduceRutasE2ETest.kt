package com.alvarotc.bito.ui

import android.app.Application
import android.app.NotificationManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.R
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Lo que solo se ve junto, por las rutas de la app (Tarea 32): la ficha que propone bajar el objetivo y abre Editar
 * con el número sugerido, el aviso de la noche que abre el cierre y la petición de lo gordo. Aquí manda el reloj del
 * sistema (las fábricas de la app no aceptan otro); lo que depende de la hora está en `HabiConduceE2ETest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp", application = Application::class)
class HabiConduceRutasE2ETest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val hoy = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())
    private lateinit var container: AppContainer
    private lateinit var nav: NavHostController

    @After
    fun tearDown() {
        // NavRequests es de todo el proceso: una petición pendiente no pasa al test siguiente.
        NavRequests.consume()
    }

    private fun montar(semilla: suspend AppContainer.() -> Unit = {}) {
        container = AppContainer(app)
        runBlocking {
            container.settings.update { it.copy(onboardingDone = true) }
            container.semilla()
        }
        compose.setContent {
            BitoTheme {
                val n = rememberNavController()
                nav = n
                BitoNavHost(container, n)
            }
        }
        compose.waitForIdle()
    }

    /** Espera dejando correr el bucle principal: Room y DataStore van por hilos reales (como `esperarRuta`). */
    private fun hasta(
        ms: Long = 10_000,
        cond: () -> Boolean,
    ) {
        val limite = System.currentTimeMillis() + ms
        while (!cond()) {
            check(System.currentTimeMillis() < limite) { "no llegó a tiempo" }
            compose.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun hay(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun pulsar(tag: String) {
        compose
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action
            ?.invoke()
    }

    @Test
    fun `a habit at 30 percent with 20 judged days offers a lower target and opens Edit with it`() {
        montar {
            // Ocho vasos al día; cumplido 6 de los 20 días juzgados (30 %) y hecho hoy, para que no salga «hoy falta».
            habits.create(
                habitEntity(id = "agua", name = "Beber agua", metric = Metric.COUNT, target = 8, step = 1, createdOnDay = hoy - 20),
            )
            (listOf(1, 4, 7, 10, 13, 16).map { hoy - it } + hoy).forEach { d ->
                database.entryDao().insert(entryEntity(id = "agua-$d", habitId = "agua", logicalDay = d, value = 8))
            }
        }
        hasta { hay("inicio") }
        compose.runOnUiThread { nav.navigate("detail/agua?lectura=false") }
        hasta { hay("habi-vale") }

        pulsar("habi-vale")

        hasta { compose.runOnUiThread { nav.currentDestination?.route } == RUTA_FORM_HABITO }
        val args = compose.runOnUiThread { nav.currentBackStackEntry?.arguments }
        assertEquals("agua", args?.getString("id"))
        assertEquals("la mitad de 8", 4, args?.getInt("objetivo"))
    }

    @Test
    fun `the night notice closes the day and the big thing request opens Today with the sheet`() {
        montar {
            habits.create(habitEntity(id = "agua", name = "Beber agua", metric = Metric.CHECK, target = 1, createdOnDay = hoy - 3))
            habits.create(habitEntity(id = "leer", name = "Leer", metric = Metric.CHECK, target = 1, createdOnDay = hoy - 3))
        }
        hasta { hay("inicio") }

        // «Cerrar el día» en el aviso de la noche: lo que emite es la ruta que MainActivity pasa a NavRequests.
        NotificationChannels.ensure(app)
        Notifier.showNoche(app, TextoHabi(R.string.habla_cerrar_dia), TextoHabi(R.string.habla_cerrar_dia), Personality.NEUTRA)
        val aviso = shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(Notifier.NOCHE_ID)
        val cerrar = aviso.actions.first { it.title == app.getString(R.string.habla_cerrar_dia) }
        val ruta = shadowOf(cerrar.actionIntent).savedIntent.getStringExtra(Notifier.EXTRA_OPEN_ROUTE)
        assertEquals("review", ruta)
        compose.runOnIdle { NavRequests.open(ruta!!) }
        hasta { hay("cierre") }

        // La petición de lo gordo (el aviso de la mañana): Hoy con la hoja abierta.
        pulsar("cierre-cerrar")
        hasta { !hay("cierre") }
        compose.runOnIdle { NavRequests.open("gordo") }
        hasta { hay("hoja-gordo") }
    }
}
