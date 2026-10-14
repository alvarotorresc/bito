package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Estrato
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * Si el padre pone [abierta] a false sin gesto, la hoja sale igual pero no llama a [alCerrar].
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
    var montada by remember { mutableStateOf(abierta) }
    var cerrando by remember { mutableStateOf(false) }
    var alto by remember { mutableFloatStateOf(0f) }
    var arrastre by remember { mutableFloatStateOf(0f) } // crudo, sin fricción
    val posicion = remember { Animatable(1f) } // 1 = cerrada, 0 = abierta (fracción del alto)
    val scope = rememberCoroutineScope()
    val gesto = rememberGesto()
    val reducido = BitoTema.reducido
    val alCerrarActual by androidx.compose.runtime.rememberUpdatedState(alCerrar)

    fun salir(
        llamar: Boolean,
        despues: (() -> Unit)? = null,
    ) {
        if (cerrando) return
        cerrando = true
        gesto(Snd.HUNDIR)
        scope.launch {
            despues?.let {
                launch {
                    delay(Duraciones.MENU_ACCION.toLong())
                    it()
                }
            }
            posicion.animateTo(1f, tween(if (reducido) Duraciones.REDUCIDO else Duraciones.CERRAR_HOJA, easing = Curvas.out))
            montada = false
            if (llamar) alCerrarActual()
        }
    }

    LaunchedEffect(abierta) {
        if (abierta) {
            montada = true
            cerrando = false
            arrastre = 0f
            gesto(Snd.BROTE, Haptica.TICK)
            posicion.animateTo(0f, if (reducido) tween(Duraciones.REDUCIDO) else Muelles.hoja())
        } else if (montada) {
            salir(llamar = false)
        }
    }
    if (!montada) return

    val hojaScope =
        remember {
            object : HojaScope {
                override fun cerrar() = salir(llamar = true)

                override fun cerrarY(accion: () -> Unit) = salir(llamar = true, despues = accion)
            }
        }
    val colores = BitoTema.colores
    val estado =
        rememberDraggableState { delta ->
            if (!cerrando) arrastre += delta
        }
    val arrastrar =
        Modifier.draggable(
            state = estado,
            orientation = Orientation.Vertical,
            enabled = !cerrando,
            onDragStopped = { velocidad ->
                if (cerrando) return@draggable
                if (HojaFisica.debeCerrar(arrastre, alto, velocidad / 1000f)) {
                    val actual = if (alto > 0f) (posicion.value * alto + HojaFisica.conFriccion(arrastre)) / alto else posicion.value
                    arrastre = 0f
                    posicion.snapTo(actual.coerceIn(0f, 1f))
                    salir(llamar = true)
                } else {
                    animate(arrastre, 0f, velocidad, Muelles.hoja<Float>(reducido)) { v, _ -> arrastre = v }
                }
            },
        )

    Dialog(
        onDismissRequest = { salir(llamar = true) },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
                dismissOnBackPress = true,
            ),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        val abiertaFraccion = (1f - posicion.value).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("hoja-velo")
                    .graphicsLayer { alpha = abiertaFraccion }
                    .background(colores.velo)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        salir(llamar = true)
                    },
            )
            Column(
                modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .onSizeChanged { alto = it.height.toFloat() }
                    .graphicsLayer {
                        if (alto == 0f) {
                            alpha = 0f
                        } else if (reducido) {
                            alpha = abiertaFraccion
                            translationY = HojaFisica.conFriccion(arrastre)
                        } else {
                            translationY = posicion.value * alto + HojaFisica.conFriccion(arrastre)
                        }
                    }.background(colores.canto, BitoFormas.tarjetaArriba)
                    .border(Medidas.linea, colores.contorno, BitoFormas.tarjetaArriba)
                    .navigationBarsPadding(),
            ) {
                Column(arrastrar.fillMaxWidth()) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = Medidas.e8, bottom = Medidas.e8)
                            .testTag("hoja-asa")
                            .size(width = 36.dp, height = 4.dp)
                            .clip(BitoFormas.canto)
                            .background(seccion?.fondo ?: colores.hornacina),
                    )
                    Column(Modifier.padding(horizontal = Medidas.margen)) {
                        etiqueta?.let {
                            Text(it, style = BitoTema.tipos.t14.fuerte(), color = seccion?.tinta2 ?: colores.tintaSuave)
                        }
                        titulo?.let { Text(it, style = BitoTema.tipos.t24.fuerte(), color = colores.tinta) }
                    }
                }
                Column(
                    Modifier
                        .testTag("hoja-cuerpo")
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(Medidas.margen),
                ) { hojaScope.contenido() }
                if (seccion != null) {
                    Spacer(Modifier.fillMaxWidth().height(Medidas.costra).background(seccion.fondo))
                }
            }
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
    val reducido = BitoTema.reducido
    val seis = with(LocalDensity.current) { 6.dp.roundToPx() }
    AnimatedContent(
        targetState = paso,
        modifier = modifier,
        transitionSpec = {
            val entra =
                if (reducido) {
                    fadeIn(tween(Duraciones.REDUCIDO))
                } else {
                    fadeIn(tween(Duraciones.PASO_HOJA_NUEVO, delayMillis = Duraciones.PASO_HOJA_RETRASO)) +
                        slideInVertically(tween(Duraciones.PASO_HOJA_NUEVO, delayMillis = Duraciones.PASO_HOJA_RETRASO)) { seis }
                }
            val sale = fadeOut(tween(if (reducido) Duraciones.REDUCIDO else Duraciones.PASO_HOJA_RETRASO))
            (entra togetherWith sale).using(
                SizeTransform(clip = false) { _, _ -> tween(Duraciones.PASO_HOJA, easing = Curvas.drawer) },
            )
        },
        label = "hoja-pasos",
    ) { p -> contenido(p) }
}
