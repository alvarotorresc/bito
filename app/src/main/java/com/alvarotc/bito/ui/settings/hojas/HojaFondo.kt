package com.alvarotc.bito.ui.settings.hojas

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.ElegirConAgua
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.OpcionAgua
import com.alvarotc.bito.ui.sistema.sonido.Bucle
import com.alvarotc.bito.ui.sistema.sonido.LocalSonido
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VISTA_PREVIA_MS = 1600L

private fun SessionBackground.sonido(): Snd? =
    when (this) {
        SessionBackground.RAIN -> Snd.FONDO_LLUVIA
        SessionBackground.BROWN -> Snd.FONDO_MARRON
        SessionBackground.CAFE -> Snd.FONDO_CAFE
        SessionBackground.NONE -> null
    }

/** Sonido de fondo de la sesión. Al elegir uno suena 1,6 s como vista previa; al cerrar, se calla. */
@Composable
fun HojaFondo(
    abierta: Boolean,
    elegido: SessionBackground,
    alElegir: (SessionBackground) -> Unit,
    alCerrar: () -> Unit,
) {
    val motor = LocalSonido.current
    val scope = rememberCoroutineScope()
    val vista = remember { VistaPrevia() }
    DisposableEffect(Unit) { onDispose { vista.parar() } }
    LaunchedEffect(abierta) { if (!abierta) vista.parar() }

    val opciones =
        listOf(
            opcion(SessionBackground.RAIN, R.string.hojas_fondo_lluvia, R.string.hojas_fondo_lluvia_nota),
            opcion(SessionBackground.BROWN, R.string.hojas_fondo_marron, R.string.hojas_fondo_marron_nota),
            opcion(SessionBackground.CAFE, R.string.hojas_fondo_cafe, R.string.hojas_fondo_cafe_nota),
            opcion(SessionBackground.NONE, R.string.hojas_fondo_ninguno, R.string.hojas_fondo_ninguno_nota),
        )
    Hoja(
        abierta = abierta,
        alCerrar = alCerrar,
        seccion = BitoTema.colores.estratos.sonido,
        etiqueta = stringResource(R.string.hojas_estrato_sonido),
        titulo = stringResource(R.string.hojas_fondo_titulo),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            Text(stringResource(R.string.hojas_fondo_desc), style = BitoTema.tipos.t14, color = BitoTema.colores.tintaSuave)
            ElegirConAgua(
                opciones = opciones,
                elegida = elegido.name,
                alElegir = { clave ->
                    val nuevo = SessionBackground.valueOf(clave)
                    alElegir(nuevo)
                    vista.parar()
                    nuevo.sonido()?.let { snd ->
                        val bucle = motor.bucle(snd)
                        bucle.empezar()
                        vista.sonando = bucle
                        vista.trabajo =
                            scope.launch {
                                delay(VISTA_PREVIA_MS)
                                bucle.parar()
                                if (vista.sonando === bucle) vista.sonando = null
                            }
                    }
                },
            )
            BotonesHoja(stringResource(R.string.hojas_rec_hecho), { cerrar() })
        }
    }
}

/** Lo que suena ahora mismo como vista previa, para pararlo al elegir otro o al cerrar. */
private class VistaPrevia {
    var sonando: Bucle? = null
    var trabajo: Job? = null

    fun parar() {
        trabajo?.cancel()
        trabajo = null
        sonando?.parar()
        sonando = null
    }
}

@Composable
private fun opcion(
    fondo: SessionBackground,
    @StringRes texto: Int,
    @StringRes nota: Int,
) = OpcionAgua(fondo.name, stringResource(texto), stringResource(nota))
