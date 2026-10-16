package com.alvarotc.bito.ui.sesion

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.sesion.AvisoSesion
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Un solo mapeo de cada aviso G9 a su texto, el que usan Preparar, Foco, Terminada y Atrás. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
class AvisoSesionTextoTest {
    private val recursos = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun `the five notices have their text`() {
        assertEquals("Sigue en marcha: 18:42 en la notificación.", AvisoSesion.SigueEnMarcha("18:42").texto(recursos))
        assertEquals("Llamar al banco, hecho.", AvisoSesion.Hecho("Llamar al banco").texto(recursos))
        assertEquals("Lo dejamos aquí. Tus 12 min cuentan.", AvisoSesion.LoDejamos(12).texto(recursos))
        assertEquals("Lo dejamos aquí. Otra vez será.", AvisoSesion.LoDejamos(0).texto(recursos))
        assertEquals("Queda en tu día para luego.", AvisoSesion.QuedaParaLuego.texto(recursos))
        assertEquals("Llamar al banco: queda como intento.", AvisoSesion.ComoIntento("Llamar al banco").texto(recursos))
    }
}
