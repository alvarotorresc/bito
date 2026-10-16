package com.alvarotc.bito.data.habla

import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.PropuestaVigente
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class ConversionesTest {
    private val vigente =
        PropuestaVigente(
            ambito = Ambito.FICHA,
            habitId = "h1",
            dia = 20_000,
            situacion = Situacion.TAREA_VENCE,
            ref = "t1",
            trozos = listOf(ClaveTrozo(Bolsa.NU_SALUDO, Personality.NEUTRA, 3)),
            huecos =
                mapOf(
                    Hueco.HABITO to ValorHueco.Texto("Agua, \"fría\" y ;rara"),
                    Hueco.N to ValorHueco.Numero(7),
                    Hueco.HORA to ValorHueco.Hora(615),
                    Hueco.LISTA to ValorHueco.Lista(listOf("a", "b;c", "d")),
                    Hueco.DIA_SEMANA to ValorHueco.DiaSemana(DayOfWeek.FRIDAY),
                    Hueco.FECHA to ValorHueco.Fecha(20_001L),
                ),
            acciones = listOf(Respuesta.VALE, Respuesta.AHORA, Respuesta.HOY_NO),
            destino = Destino.Tarea("t1"),
            hora = 615,
            creadoMillis = 123L,
        )

    @Test
    fun `a current proposal survives the round trip through its row`() {
        assertEquals(vigente, vigente.aEntidad().aDominio())
    }

    @Test
    fun `the destination is rebuilt from the situation and the ref, one case per variant`() {
        fun destino(
            situacion: Situacion,
            ref: String?,
            esTarea: Boolean = false,
        ) = vigente.copy(situacion = situacion, ref = ref).aEntidad().aDominio(refEsTarea = esTarea)!!.destino

        assertEquals(Destino.Habito("h1"), destino(Situacion.RACHA_CERCA, "h1"))
        assertEquals(Destino.Habito("h1"), destino(Situacion.FI_HOY_FALTA, "h1"))
        assertEquals(Destino.Tarea("t1"), destino(Situacion.TAREA_VENCE, "t1"))
        assertEquals(Destino.Cierre, destino(Situacion.CERRAR_DIA, null))
        assertEquals(Destino.Cierre, destino(Situacion.TODO_HECHO, null))
        assertEquals(Destino.LoGordo, destino(Situacion.ELEGIR_GORDO, null))
        // Ningun caso de la spec lleva a Respirar: no se persiste.
        assertNull(destino(Situacion.SALUDO, null))
        assertNull(destino(Situacion.RACHA_CERCA, null))
    }

    @Test
    fun `a stored respirar destination reads back as null`() {
        assertNull(vigente.copy(situacion = Situacion.SALUDO, destino = Destino.Respirar).aEntidad().aDominio()!!.destino)
    }

    @Test
    fun `an ambiguous ref is a habit or a task depending on what the caller resolved`() {
        fun destino(esTarea: Boolean) =
            vigente.copy(situacion = Situacion.GORDO_SIN_EMPEZAR, ref = "x").aEntidad().aDominio(refEsTarea = esTarea)!!.destino
        assertEquals(Destino.Habito("x"), destino(false))
        assertEquals(Destino.Tarea("x"), destino(true))
    }

    @Test
    fun `a proposal without actions or hole values round trips`() {
        val sola = vigente.copy(acciones = emptyList(), hora = null, huecos = emptyMap())
        assertEquals(sola, sola.aEntidad().aDominio())
    }

    @Test
    fun `a key is written as bag, voice and index`() {
        assertEquals("NU_SALUDO:NEUTRA:3", ClaveTrozo(Bolsa.NU_SALUDO, Personality.NEUTRA, 3).aTexto())
    }

    @Test
    fun `a bag that no longer exists reads as a null current proposal`() {
        val fila = vigente.aEntidad().copy(trozos = "NU_BORRADA:NEUTRA:0")
        assertNull(fila.aDominio())
    }

    @Test
    fun `garbage keys read as null instead of throwing`() {
        assertNull(claveDeTexto("NU_SALUDO:NEUTRA"))
        assertNull(claveDeTexto("NU_SALUDO:NEUTRA:x"))
        assertNull(claveDeTexto("NU_SALUDO:ROBOT:1"))
    }
}
