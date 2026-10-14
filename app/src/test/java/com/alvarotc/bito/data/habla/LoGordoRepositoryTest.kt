package com.alvarotc.bito.data.habla

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoGordoRepositoryTest {
    private lateinit var db: BitoDatabase
    private lateinit var repo: LoGordoRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        repo = LoGordoRepository(db) { 42L }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ref(id: String) = RefGordo(TipoRefGordo.HABITO, id)

    @Test
    fun `saving three refs and then two leaves two`() =
        runTest {
            repo.guardar(20_000, listOf(ref("a"), ref("b"), ref("c")))
            repo.guardar(20_000, listOf(ref("b"), RefGordo(TipoRefGordo.TAREA, "t1")))

            val hoy = repo.observe(listOf(20_000)).first().getValue(20_000)

            assertEquals(listOf(ref("b"), RefGordo(TipoRefGordo.TAREA, "t1")), hoy)
            assertEquals(42L, db.hablaDao().todoGordo().first().creadoMillis)
        }

    @Test
    fun `observe brings both days`() =
        runTest {
            repo.guardar(20_000, listOf(ref("a")))
            repo.guardar(19_999, listOf(ref("z"), ref("y")))
            repo.guardar(19_990, listOf(ref("old")))

            val mapa = repo.observe(listOf(20_000, 19_999)).first()

            assertEquals(setOf(20_000, 19_999), mapa.keys)
            assertEquals(listOf(ref("z"), ref("y")), mapa.getValue(19_999))
        }

    @Test
    fun `at most three refs are kept`() =
        runTest {
            repo.guardar(20_000, listOf(ref("a"), ref("b"), ref("c"), ref("d")))
            assertEquals(3, repo.observe(listOf(20_000)).first().getValue(20_000).size)
        }
}
