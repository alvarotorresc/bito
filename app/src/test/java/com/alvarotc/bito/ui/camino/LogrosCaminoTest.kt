package com.alvarotc.bito.ui.camino

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.Progreso
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.stats.BadgeStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogrosCaminoTest {
    @get:Rule
    val rule = createComposeRule()

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private val hoy = LocalDate.of(2026, 10, 13)

    private fun cadena(
        id: Int,
        vararg args: Any,
    ) = contexto.getString(id, *args)

    /** Los catorce, con los de [conseguidos] encendidos y el resto a 13 de su objetivo. */
    private fun logros(conseguidos: Set<String> = setOf("streak-7")): List<LogroUi> =
        BadgeCatalog.all.map { def ->
            val hecho = def.id in conseguidos
            LogroUi(
                def = def,
                conseguido = if (hecho) hoy.toEpochDay().toInt() - 3 else null,
                progreso = def.threshold?.let { Progreso(if (hecho) it else 13.coerceAtMost(it), it) },
                especial = GardenSpecial.entries.firstOrNull { it.badgeId == def.id },
            )
        }

    private fun logroCon(progreso: Progreso?) =
        LogroUi(BadgeCatalog.byId("streak-100")!!, conseguido = null, progreso = progreso, especial = null)

    @Test
    fun `fourteen badges with N of 14 and three families`() {
        rule.setContent {
            ConSistema { LogrosCamino(CaminoUiState(logros = logros(), sinHabitos = false, loading = false, hoy = hoy), alTocar = {}) }
        }
        BadgeCatalog.all.forEach { rule.onNodeWithTag("logro-${it.id}").assertExists() }
        rule.onNodeWithText(cadena(R.string.camino_logros, 1, 14)).assertExists()
        listOf("streak", "constancy", "moment").forEach { clave ->
            val id = contexto.resources.getIdentifier("badge_family_$clave", "string", contexto.packageName)
            rule.onNodeWithText(cadena(id)).assertExists()
        }
    }

    @Test
    fun `what is left reads as a count`() {
        var texto: String? = null
        rule.setContent { ConSistema { texto = faltaTexto(logroCon(Progreso(13, 100)), hoy) } }
        assertEquals(cadena(R.string.camino_falta_dias, 13, 100), texto)
    }

    @Test
    fun `yes or no badges and earned ones have no count`() {
        var sinCuenta: String? = "x"
        var conseguido: String? = "x"
        rule.setContent {
            ConSistema {
                sinCuenta = faltaTexto(logroCon(Progreso(0, 1)), hoy)
                conseguido = faltaTexto(logroCon(Progreso(100, 100)).copy(conseguido = 5), hoy)
            }
        }
        assertEquals(null, sinCuenta)
        assertEquals(null, conseguido)
    }

    @Test
    fun `tapping one opens its sheet and see it in the garden goes up`() {
        var subido: GardenSpecial? = null
        rule.mainClock.autoAdvance = true
        rule.setContent {
            ConSistema {
                CaminoContenido(
                    CaminoUiState(logros = logros(), sinHabitos = false, loading = false, hoy = hoy),
                    rememberScrollState(),
                    alVerEnJardin = { subido = it },
                )
            }
        }
        rule.onNodeWithTag("logro-streak-7").performScrollTo().performClick()
        rule.waitForIdle()
        val nombre = cadena(BadgeStrings.badgeNameRes("streak-7"))
        rule.onNode(hasText(nombre) and hasAnyAncestor(hasTestTag("hoja-logro"))).assertExists()
        rule.onNodeWithText(cadena(R.string.camino_verlo)).performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertEquals(GardenSpecial.LANTERN, subido)
    }

    @Test
    fun `logro_r1 is not empty at 52 and 124 dp`() {
        // El arte ocupa 64/120 del emblema: se dibuja al tamaño que tendrá dentro de uno de 52 dp y de uno de 124 dp.
        val densidad = contexto.resources.displayMetrics.density
        val arte = checkNotNull(LogrosArte.de("streak-7"))
        listOf(52, 124).forEach { lado ->
            val px = (lado * 64f / 120f * densidad).toInt()
            val mapa = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val dibujo = checkNotNull(contexto.getDrawable(arte)) { "logro_r1 no se puede cargar" }
            dibujo.setBounds(0, 0, px, px)
            dibujo.draw(Canvas(mapa))
            val opacos = (0 until px).sumOf { y -> (0 until px).count { x -> (mapa.getPixel(x, y) ushr 24) > 200 } }
            assertTrue("el arte tiene que pintarse a $lado dp (opacos: $opacos de ${px * px})", opacos > px * px / 10)
        }
    }

    @Test
    fun `pending badges show their ring and earned ones do not need one`() {
        rule.setContent {
            ConSistema { LogrosCamino(CaminoUiState(logros = logros(), sinHabitos = false, loading = false, hoy = hoy), alTocar = {}) }
        }
        rule.onNodeWithText(cadena(R.string.camino_falta_dias, 13, 30)).assertExists()
        assertNotEquals(null, LogrosArte.de("streak-7"))
    }
}
