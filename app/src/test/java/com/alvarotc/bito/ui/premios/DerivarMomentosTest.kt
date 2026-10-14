package com.alvarotc.bito.ui.premios

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.settings.MarcaLibro
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.model.PointsLedgerEntry
import com.alvarotc.bito.domain.model.PointsReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DerivarMomentosTest {
    private fun e(
        id: String,
        t: Long,
        reason: PointsReason,
        ref: String,
        delta: Int = 1,
    ) = PointsLedgerEntry(id = id, delta = delta, reason = reason, refId = ref, logicalDay = 100, createdAtMillis = t)

    private val prefs = Settings()

    @Test
    fun `without a mark nothing is celebrated and the mark is set to the newest`() {
        val libro = listOf(e("a", 10, PointsReason.PERFECT_DAY, "day:99", 3), e("b", 20, PointsReason.HABIT_DONE, "h1:100"))
        val r = DerivarMomentos.delLibro(libro, marca = null, vistas = null, prefs = prefs, enHoy = true, lote = 1)
        assertTrue(r.momentos.isEmpty())
        assertEquals(MarcaLibro(20, "b"), r.marcaLote)
    }

    @Test
    fun `day, week and month give different veins and a streak milestone gives a streak vein`() {
        val libro =
            listOf(
                e("1", 10, PointsReason.PERFECT_DAY, "day:100", 3),
                e("2", 10, PointsReason.PERFECT_DAY, "week:14", 10),
                e("3", 10, PointsReason.PERFECT_DAY, "month:24319", 50),
                e("4", 10, PointsReason.STREAK_MILESTONE, "h1:7", 5),
            )
        val r = DerivarMomentos.delLibro(libro, MarcaLibro(0, ""), vistas = null, prefs = prefs, enHoy = true, lote = 1)
        val tipos = r.momentos.filterIsInstance<Momento.Veta>().map { it.tipo }.toSet()
        assertEquals(setOf(TipoVeta.DIA, TipoVeta.SEMANA, TipoVeta.MES, TipoVeta.RACHA), tipos)
        assertEquals(7, r.momentos.filterIsInstance<Momento.Veta>().single { it.tipo == TipoVeta.RACHA }.racha)
    }

    @Test
    fun `same time entries are split by id and nothing is celebrated twice`() {
        val libro = listOf(e("a", 50, PointsReason.PERFECT_DAY, "week:1", 10), e("b", 50, PointsReason.PERFECT_DAY, "week:2", 10))
        val r = DerivarMomentos.delLibro(libro, MarcaLibro(50, "a"), vistas = null, prefs = prefs, enHoy = true, lote = 1)
        assertEquals(1, r.momentos.size)
        val otra = DerivarMomentos.delLibro(libro, MarcaLibro(50, "b"), vistas = null, prefs = prefs, enHoy = true, lote = 2)
        assertTrue(otra.momentos.isEmpty())
    }

    @Test
    fun `an entry that appears during the session celebrates even if its time is below the mark`() {
        val antes = listOf(e("a", 1_000, PointsReason.HABIT_DONE, "h1:100"))
        val ahora = antes + e("z", 10, PointsReason.PERFECT_DAY, "day:100", 3) // reloj atrasado
        val r = DerivarMomentos.delLibro(ahora, MarcaLibro(1_000, "a"), vistas = setOf("a"), prefs = prefs, enHoy = true, lote = 2)
        assertEquals(TipoVeta.DIA, (r.momentos.single() as Momento.Veta).tipo)
    }

    @Test
    fun `a retroactive grant with a new time is celebrated`() {
        val libro = listOf(e("viejo", 10, PointsReason.HABIT_DONE, "h1:90"), e("nuevo", 99, PointsReason.PERFECT_DAY, "day:90", 3))
        val r = DerivarMomentos.delLibro(libro, MarcaLibro(10, "viejo"), vistas = null, prefs = prefs, enHoy = true, lote = 1)
        assertEquals(90, (r.momentos.single() as Momento.Veta).dia)
    }

    @Test
    fun `coins only while Today is on screen and never at start`() {
        val libro = listOf(e("a", 1, PointsReason.HABIT_DONE, "h1:100"), e("t", 2, PointsReason.TASK_DONE, "task:t1", 5))
        assertTrue(
            DerivarMomentos.delLibro(
                libro,
                MarcaLibro(0, ""),
                vistas = emptySet(),
                prefs = prefs,
                enHoy = false,
                lote = 1,
            ).momentos.isEmpty(),
        )
        assertTrue(
            DerivarMomentos.delLibro(libro, MarcaLibro(0, ""), vistas = null, prefs = prefs, enHoy = true, lote = 1).momentos.isEmpty(),
        )
        val enHoy = DerivarMomentos.delLibro(libro, MarcaLibro(0, ""), vistas = emptySet(), prefs = prefs, enHoy = true, lote = 1).momentos
        assertEquals(listOf("fila:h1" to 1, "tarea:t1" to 3), enHoy.map { (it as Momento.Monedas).origen to it.monedas })
    }

    @Test
    fun `a perfect day already celebrated in the review is not a vein in Today`() {
        val libro = listOf(e("a", 5, PointsReason.PERFECT_DAY, "day:100", 3))
        val r =
            DerivarMomentos.delLibro(
                libro,
                MarcaLibro(0, ""),
                vistas = null,
                prefs = Settings(perfectDayCelebratedDay = 100),
                enHoy = true,
                lote = 1,
            )
        assertTrue(r.momentos.isEmpty())
        assertEquals(MarcaLibro(5, "a"), r.marcaLote)
    }

    @Test
    fun `a streak vein carries the habit name and lights the badge only the first time`() {
        val libro = listOf(e("r", 10, PointsReason.STREAK_MILESTONE, "h1:7", 5))
        val habitos = mapOf("h1" to "Leer")
        val nueva =
            DerivarMomentos.delLibro(
                libro,
                MarcaLibro(0, ""),
                vistas = null,
                prefs = prefs,
                enHoy = true,
                lote = 1,
                habitos = habitos,
                badges = emptyList(),
            ).momentos.single() as Momento.Veta
        assertEquals("Leer", nueva.nombre)
        assertTrue(nueva.logroNuevo)
        val yaVisto = prefs.copy(badgesSeenUntilMillis = 500)
        val repetida =
            DerivarMomentos.delLibro(
                libro,
                MarcaLibro(0, ""),
                vistas = null,
                prefs = yaVisto,
                enHoy = true,
                lote = 1,
                habitos = habitos,
                badges = listOf(BadgeEntity("streak-7", 400)),
            ).momentos.single() as Momento.Veta
        assertEquals(false, repetida.logroNuevo)
        val borrado =
            DerivarMomentos.delLibro(
                libro,
                MarcaLibro(0, ""),
                vistas = null,
                prefs = prefs,
                enHoy = true,
                lote = 1,
            ).momentos.single() as Momento.Veta
        assertEquals(null, borrado.nombre)
    }

    @Test
    fun `unmarking adds nothing to the ledger, so nothing launches`() {
        val libro = listOf(e("a", 1, PointsReason.HABIT_DONE, "h1:100"))
        val r = DerivarMomentos.delLibro(libro, MarcaLibro(1, "a"), vistas = setOf("a"), prefs = prefs, enHoy = true, lote = 2)
        assertTrue(r.momentos.isEmpty())
    }
}
