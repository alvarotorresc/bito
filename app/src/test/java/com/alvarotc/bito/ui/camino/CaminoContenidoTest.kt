package com.alvarotc.bito.ui.camino

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.ActiveStreak
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.HabitRecord
import com.alvarotc.bito.domain.PerfectDaysSummary
import com.alvarotc.bito.domain.Totals
import com.alvarotc.bito.domain.WeekRow
import com.alvarotc.bito.domain.WeekSummary
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.Progreso
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.stats.NumbersUiState
import com.alvarotc.bito.ui.stats.RecordRow
import com.alvarotc.bito.ui.stats.RecordsUiState
import com.alvarotc.bito.ui.stats.StatsUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class CaminoContenidoTest {
    @get:Rule
    val rule = createComposeRule()

    private val contexto: Context = ApplicationProvider.getApplicationContext()

    private fun cadena(
        id: Int,
        vararg args: Any,
    ) = contexto.getString(id, *args)

    private val puntos =
        listOf(DayDot.FULFILLED, DayDot.FAILED, DayDot.FROZEN, DayDot.PAUSED, DayDot.PENDING, DayDot.OFF, DayDot.ACTIVITY)

    private fun estado(
        delta: Int? = 12,
        sinHabitos: Boolean = false,
    ): CaminoUiState {
        val semana =
            WeekSummary(
                rows = listOf(WeekRow("leer", "Leer", puntos, 1, 7), WeekRow("agua", "Agua", puntos, 3, 7)),
                thisWeekPercent = 80,
                deltaVsLastWeek = delta,
            )
        return CaminoUiState(
            stats =
                StatsUiState(
                    mood = Mood.NORMAL,
                    personality = Personality.NEUTRA,
                    perfectDays = PerfectDaysSummary(total = 20, thisMonth = 4, thisYear = 14),
                    week = semana,
                    activeStreaks = listOf(ActiveStreak("leer", "Leer", 3, Period.DAY), ActiveStreak("agua", "Agua", 13, Period.DAY)),
                    bestRecord = HabitRecord("leer", "Leer", 3, 31, Period.DAY),
                    totalEntries = 1234,
                    loading = false,
                ),
            records =
                RecordsUiState(
                    listOf(
                        RecordRow("leer", "Leer", 3, 31, Period.DAY, archived = false),
                        RecordRow("fumar", "Sin fumar", 0, 12, Period.DAY, archived = true),
                    ),
                    loading = false,
                ),
            numbers = NumbersUiState(Totals(1234, 20, 300, 80, 2, 1, 4, 1, 2, 90), loading = false),
            sinHabitos = sinHabitos,
            loading = false,
        )
    }

    private fun montar(estado: CaminoUiState) {
        rule.setContent {
            ConSistema { CaminoContenido(estado, rememberScrollState(), alVerEnJardin = {}) }
        }
    }

    @Test
    fun `Camino composes inside its own scroll without nesting a second vertical scroll`() {
        rule.setContent {
            ConSistema { CaminoContenido(CaminoUiState(), rememberScrollState(), alVerEnJardin = {}) }
        }
        rule.onNodeWithTag("inicio-camino").assertIsDisplayed()
    }

    @Test
    fun `the six stat pieces show with test data`() {
        montar(estado())
        rule.onNodeWithText(cadena(HabiVoice.bubbleRes(Mood.NORMAL, Personality.NEUTRA))).assertExists()
        rule.onNodeWithText(cadena(R.string.stats_perfect_days_label)).assertExists()
        rule.onNodeWithText(cadena(R.string.camino_este_mes)).assertExists()
        rule.onNodeWithText(cadena(R.string.camino_este_ano)).assertExists()
        rule.onNodeWithText(cadena(R.string.camino_tu_semana)).assertExists()
        rule.onNodeWithTag("streak-agua").assertExists()
        rule.onNodeWithText(cadena(R.string.camino_mejor_racha, 31)).assertExists()
        rule.onNodeWithText(cadena(R.string.camino_registros_totales)).assertExists()
    }

    @Test
    fun `the week delta shows only when positive`() {
        montar(estado(delta = -5))
        rule.onNodeWithText(cadena(R.string.camino_frente_semana, -5)).assertDoesNotExist()
        rule.onNodeWithText(cadena(R.string.camino_frente_semana, 5)).assertDoesNotExist()
    }

    @Test
    fun `a positive week delta reads as plus N percent`() {
        montar(estado(delta = 12))
        rule.onNodeWithText(cadena(R.string.camino_frente_semana, 12)).assertExists()
        assertTrue(cadena(R.string.camino_frente_semana, 12).startsWith("+12"))
    }

    @Test
    fun `a zero delta shows no chip either`() {
        montar(estado(delta = 0))
        rule.onNodeWithText(cadena(R.string.camino_frente_semana, 0)).assertDoesNotExist()
    }

    @Test
    fun `records and numbers open their sheets`() {
        montar(estado())
        rule.onNodeWithText(cadena(R.string.camino_mejor_racha, 31)).performScrollTo().performClick()
        rule.waitForIdle()
        val dentro = hasAnyAncestor(hasTestTag("hoja-records"))
        rule.onNode(hasText("Sin fumar") and dentro).assertExists()
        rule.onNode(hasText(cadena(R.string.records_archived_label), substring = true) and dentro).assertExists()
    }

    @Test
    fun `the numbers sheet groups the counters`() {
        montar(estado())
        rule.onNodeWithText(cadena(R.string.camino_registros_totales)).performScrollTo().performClick()
        rule.waitForIdle()
        val dentro = hasAnyAncestor(hasTestTag("hoja-numeros"))
        rule.onNode(hasText(cadena(R.string.numbers_section_log)) and dentro).assertExists()
        rule.onNode(hasText(cadena(R.string.numbers_section_rewards)) and dentro).assertExists()
        rule.onNode(hasText(cadena(R.string.numbers_section_habits)) and dentro).assertExists()
    }

    @Test
    fun `without habits it says so`() {
        montar(estado(sinHabitos = true))
        rule.onNodeWithText(cadena(R.string.detail_empty_hint)).assertExists()
        rule.onNodeWithText(cadena(R.string.camino_tu_semana)).assertDoesNotExist()
    }

    @Test
    fun `the week strip announces which days were done, not just the tally`() {
        // Lunes primero; OFF (sábado) no se dice, como en el resto de la app.
        rule.setContent { ConSistema { Text(weekRowDaysDescription(puntos)) } }
        rule.onNodeWithText("Mon Done, Tue Not done, Wed Frozen, Thu Paused, Fri Pending, Sun Done").assertExists()
    }

    @Test
    fun `each week row carries its aggregated description on the dot cluster`() {
        montar(estado())
        val descripcion =
            rule.onNodeWithTag("week-dots-leer", useUnmergedTree = true).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription)
        assertTrue(!descripcion.isNullOrEmpty() && descripcion.first().startsWith("Mon"))
    }

    @Test
    fun `a streak pill speaks as one stop`() {
        montar(estado())
        rule.onNodeWithTag("streak-agua").assertContentDescriptionEquals("Agua, streak of 13 days")
        rule.onNodeWithTag("streak-agua").onChildren().assertCountEquals(0)
    }

    private fun conLogros(
        estado: CaminoUiState,
        conseguidos: Set<String> = emptySet(),
    ) = estado.copy(
        logros =
            BadgeCatalog.all.map { def ->
                LogroUi(
                    def,
                    if (def.id in conseguidos) 1 else null,
                    def.threshold?.let { Progreso(0, it) },
                    GardenSpecial.entries.firstOrNull { it.badgeId == def.id },
                )
            },
        hoy = LocalDate.of(2026, 10, 13),
    )

    @Test
    fun `order is stats then badges`() {
        montar(conLogros(estado()))

        fun arriba(etiqueta: String) = rule.onNodeWithText(etiqueta).fetchSemanticsNode().positionInRoot.y
        val perfectos = arriba(cadena(R.string.stats_perfect_days_label))
        val logros = arriba(cadena(R.string.camino_logros, 0, 14))
        assertTrue(perfectos < logros)
    }

    @Test
    fun `without habits it still lists badges`() {
        montar(conLogros(estado(sinHabitos = true)))
        rule.onNodeWithText(cadena(R.string.detail_empty_hint)).assertExists()
        BadgeCatalog.all.forEach { rule.onNodeWithTag("logro-${it.id}").assertExists() }
    }

    @Test
    fun `next month text comes from today`() {
        // Este mes lleva 2 días perfectos y hoy es 13: algún día anterior falló, el mes redondo ya no puede ser.
        val base = conLogros(estado())
        montar(base.copy(stats = base.stats.copy(perfectDays = PerfectDaysSummary(2, 2, 2))))
        val otroMes = cadena(R.string.camino_otro_mes, "November 1")
        // La rejilla ya lo dice en su celda, sin abrir la hoja.
        rule.onNodeWithTag("logro-perfect-month").performScrollTo()
        rule.onAllNodesWithText(otroMes).assertCountEquals(1)
        rule.onNodeWithTag("logro-perfect-month").performClick()
        rule.waitForIdle()
        // Y la hoja lo repite: la celda y la hoja.
        rule.onAllNodesWithText(otroMes).assertCountEquals(2)
    }
}
