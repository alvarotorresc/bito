package com.alvarotc.bito.ui.tienda.congeladores

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.ui.sistema.BitoColores
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import java.util.Locale
import kotlin.math.abs

/** Recursos de la app en [idioma] ("es" o "en"). */
internal fun recursos(idioma: String): Resources {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(idioma)) }
    return base.createConfigurationContext(cfg).resources
}

/** Cómo se pinta un componente de congeladores en un test: 412 dp de ancho, fondo blanco, en español. */
internal class Banco(
    private val rule: ComposeContentTestRule,
) {
    val sonido = SonidoGrabado()
    val tacto = TactoGrabado()
    lateinit var vista: View
    lateinit var colores: BitoColores

    fun colocar(
        idioma: String = "es",
        ancho: Int = 412,
        contenido: @Composable () -> Unit,
    ) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(idioma)) }
        val ctx = base.createConfigurationContext(cfg)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides cfg) {
                ConSistema(sonido = sonido, tacto = tacto) {
                    vista = LocalView.current
                    colores = BitoTema.colores
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        Box(Modifier.width(ancho.dp)) { contenido() }
                    }
                }
            }
        }
    }

    fun fotograma(): Bitmap {
        rule.waitForIdle()
        val foto = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(foto))
        return foto
    }

    /** El píxel a ([xDp], [yDp]) desde la esquina de arriba a la izquierda de la caja de [etiqueta]. */
    fun pixel(
        foto: Bitmap,
        etiqueta: String,
        xDp: Float,
        yDp: Float,
        indice: Int = 0,
    ): Int {
        val caja = rule.onAllNodesWithTag(etiqueta, useUnmergedTree = true)[indice].fetchSemanticsNode().boundsInRoot
        return foto.getPixel((caja.left + xDp * rule.density.density).toInt(), (caja.top + yDp * rule.density.density).toInt())
    }
}

internal fun cerca(
    a: Int,
    b: Int,
    margen: Int = 14,
) = abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) < margen &&
    abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) < margen &&
    abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) < margen
