package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Estrato
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.fuerte

/** Lo que el contenido de una hoja puede pedirle a su hoja. */
@Stable
interface HojaScope {
    /** Cierra con su salida (180 ms `out`) y después llama a `alCerrar`. */
    fun cerrar()

    /** Cierra y lanza [accion] 160 ms después de empezar a cerrarse (menú de fila). */
    fun cerrarY(accion: () -> Unit)
}

/**
 * Hoja arrastrable (G7, G8). Ventana `Dialog` propia a pantalla completa: velo al 38 %, asa y pie con el
 * color de [seccion], [etiqueta] encima del [titulo]. Se cierra con el velo, atrás o arrastrando.
 */
@Composable
fun Hoja(
    abierta: Boolean,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    seccion: Estrato? = null,
    etiqueta: String? = null,
    titulo: String? = null,
    contenido: @Composable HojaScope.() -> Unit,
) {
    if (!abierta) return
    val scope =
        remember(alCerrar) {
            object : HojaScope {
                override fun cerrar() = alCerrar()

                override fun cerrarY(accion: () -> Unit) {
                    alCerrar()
                    accion()
                }
            }
        }
    Dialog(onDismissRequest = alCerrar) {
        Column(modifier.background(BitoTema.colores.canto, BitoFormas.tarjeta).padding(Medidas.margen)) {
            etiqueta?.let { Text(it, style = BitoTema.tipos.t14) }
            titulo?.let { Text(it, style = BitoTema.tipos.t24.fuerte()) }
            scope.contenido()
        }
    }
}

/** G8: pasos dentro de una hoja; la altura se ajusta y lo nuevo entra desde 6 dp. */
@Composable
fun HojaPasos(
    paso: Int,
    modifier: Modifier = Modifier,
    contenido: @Composable (Int) -> Unit,
) {
    Box(modifier) { contenido(paso) }
}
