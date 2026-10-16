package com.alvarotc.bito.ui.sesion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.data.habla.CatalogoRecursos
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.sesion.FormatoSesion
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.TextoMinutos
import com.alvarotc.bito.ui.habla.FraseSuelta
import com.alvarotc.bito.ui.inicio.textoHabi
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.anclaParticula
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.fuerte
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// `terminada()` y `marcarHecha()` del mock.
private const val SALTO_LLEGADA = 0.38f
private const val SALTO_LLEGADA_TRAS_MS = 420L
private const val FUNDIDO_BOTONES_MS = 160
private const val VUELTA_A_HOY_MS = 1_500L

// `.ses-reloj`: cifra y «de foco» al pie, 12 dp entre ellos; `.ses-de` sube 6 dp la etiqueta (sin token).
private val ENTRE_CIFRA_Y_DE = 12.dp
private val PIE_DE_LA_ETIQUETA = 6.dp

// `.ses-cifra`: 48 sp / 900, interletra -.02em y alto de línea 1 (sin token).
private val ALTO_LINEA_CIFRA = 48.sp
private val INTERLETRA_CIFRA = (-0.02).em

// `#t-preg` va 14 dp bajo la cifra; `.btnrow` lleva 12 dp arriba y 8 dp entre botones.
private val ARRIBA_PREGUNTA = 14.dp
private val ARRIBA_FILA = Medidas.e12
private val ENTRE_BOTONES = Medidas.e8

/** Lo que pintan los botones en cada fotograma; solo lo usan las pruebas. */
class SondaTerminada {
    var botonesAlfa: Float = 1f
}

/** Lo que Terminada sabe de la sesión aunque el motor ya la haya cerrado: la cifra y el objetivo no se mueven mientras se despide. */
private class RecuerdoTerminada {
    var focoMillis: Long = 0L
    var objetivo: Objetivo? = null
    var sesionId: String = ""
}

/** Lo que se lee del objetivo y de la voz al llegar: su nombre y «Hecha, {nombre}.» ya elegida. */
private class PreparadoTerminada(
    val nombre: String?,
    val hecha: com.alvarotc.bito.domain.propuestas.TextoHabi.Frase?,
)

/**
 * El bloque de Terminada (spec §3.3.6, §7.6): los minutos de foco, la pregunta de si has acabado y los botones. Cada
 * pieza es hijo directo del contenido del bloque para que entre escalonada. El sonido de la llegada y de «Sí, terminado»
 * es del motor; aquí solo se pintan los textos, el saltito de Habi y el origen de las monedas (las que apunta el libro).
 */
@Composable
fun TerminadaContenido(sonda: SondaTerminada? = null) {
    val motor = LocalMotorSesion.current ?: return
    val salida = LocalSalidaSesion.current
    val retenido = LocalBloqueRetenido.current
    val saltarHabi = LocalSaltoHabi.current
    val cara = LocalCaraHabi.current
    val sesion = LocalSesionInicio.current
    val reducido = BitoTema.reducido
    val scope = rememberCoroutineScope()
    val recursos = LocalContext.current.resources

    val instante by motor.instante.collectAsState()
    val registro by motor.registro.collectAsState()
    val recuerdo = remember { RecuerdoTerminada() }
    val vivo = instante
    if (vivo != null && vivo.actual == null) {
        recuerdo.focoMillis = vivo.focoMillis
        registro?.let {
            recuerdo.objetivo = it.plan.objetivo
            recuerdo.sesionId = it.id
        }
    }
    val objetivo = recuerdo.objetivo
    val libre = objetivo == Objetivo.Libre

    val preparado by preparadoDe(objetivo, recuerdo.sesionId, sesion)

    var respondida by remember { mutableStateOf(false) }
    var dichoQueSi by remember { mutableStateOf(false) }
    val alfaBotones = remember { Animatable(1f) }

    // Habi llega radiante (`expresion('radiante')`) y lo deja de estar al irse el bloque.
    DisposableEffect(Unit) {
        cara.radiante = true
        onDispose { cara.radiante = false }
    }

    // Llega radiante y salta; en reducido, de inmediato (el mock: 420 ms, 0 en reducido).
    LaunchedEffect(Unit) {
        if (!reducido) delay(SALTO_LLEGADA_TRAS_MS)
        saltarHabi(SALTO_LLEGADA)
    }

    fun siTerminado() {
        if (respondida) return
        respondida = true
        dichoQueSi = true
        saltarHabi(SALTO_LLEGADA)
        scope.launch { alfaBotones.animateTo(0f, tween(FUNDIDO_BOTONES_MS, easing = Curvas.out)) }
        scope.launch {
            retenido.retener(BloqueSesion.TERMINADA)
            try {
                val resultado = motor.siTerminado()
                delay(VUELTA_A_HOY_MS)
                salida.volverAHoy()
                resultado.aviso?.texto(recursos)?.let(salida.avisar)
            } finally {
                retenido.soltar()
            }
        }
    }

    fun sigoLuego() {
        if (respondida) return
        respondida = true
        scope.launch {
            val resultado = motor.sigoLuego()
            salida.volverAHoy()
            resultado.aviso?.texto(recursos)?.let(salida.avisar)
        }
    }

    fun otraDeCinco() {
        if (respondida) return
        respondida = true
        scope.launch { motor.otraDeCinco() }
    }

    // Pieza 1: la cifra con «de foco» al lado.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ENTRE_CIFRA_Y_DE),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            cifraDeFoco(FormatoSesion.minutosDeFoco(recuerdo.focoMillis)),
            Modifier.testTag("t-min"),
            style = BitoTema.tipos.t48.cifra().copy(lineHeight = ALTO_LINEA_CIFRA, letterSpacing = INTERLETRA_CIFRA),
            color = BitoTema.colores.tinta,
        )
        Text(
            stringResource(R.string.ses_de_foco),
            Modifier.padding(bottom = PIE_DE_LA_ETIQUETA),
            style = BitoTema.tipos.t16.copy(fontWeight = FontWeight.Medium),
            color = BitoTema.colores.tintaSuave,
        )
    }

    // Pieza 2: la pregunta (una sesión libre no pregunta nada).
    if (!libre) {
        val hecha = preparado?.hecha
        val nombre = preparado?.nombre
        val pregunta =
            when {
                dichoQueSi && hecha != null -> textoHabi(hecha)
                nombre != null -> stringResource(R.string.ses_has_acabado, nombre)
                else -> ""
            }
        Text(
            pregunta,
            Modifier.fillMaxWidth().padding(top = ARRIBA_PREGUNTA).testTag("t-preg"),
            style = BitoTema.tipos.t19.fuerte(),
            color = BitoTema.colores.tinta,
        )
    }

    // Pieza 3: `.btnrow.llena`. Los botones se funden al responder y dejan de existir para quien los lee.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = ARRIBA_FILA)
            .graphicsLayer {
                alpha = alfaBotones.value
                sonda?.botonesAlfa = alpha
            }.then(if (respondida) Modifier.clearAndSetSemantics {} else Modifier),
        horizontalArrangement = Arrangement.spacedBy(ENTRE_BOTONES),
    ) {
        if (!libre) {
            Boton(
                stringResource(R.string.ses_sigo_luego),
                ::sigoLuego,
                Modifier.weight(1f).testTag("t-luego"),
                alto = Medidas.botonHoja,
                llena = true,
            )
        }
        Boton(
            stringResource(if (libre) R.string.ses_listo else R.string.ses_si_terminado),
            ::siTerminado,
            Modifier.weight(1f).testTag("t-si").then(anclaDeLasMonedas(objetivo)),
            estilo = EstiloBoton.PRINCIPAL,
            alto = Medidas.botonHoja,
            llena = true,
        )
    }

    // Pieza 4: «Otra de 5 min», fantasma y centrado.
    Row(
        Modifier.fillMaxWidth().padding(top = ARRIBA_FILA),
        horizontalArrangement = Arrangement.Center,
    ) {
        Boton(stringResource(R.string.ses_otra_5), ::otraDeCinco, Modifier.testTag("t-otra"), estilo = EstiloBoton.FANTASMA)
    }
}

/** De dónde salen las monedas del libro: el botón de «Sí, terminado» hace de círculo de la tarea o del hábito. */
private fun anclaDeLasMonedas(objetivo: Objetivo?): Modifier =
    when (objetivo) {
        is Objetivo.Tarea -> Modifier.anclaParticula(ClavesAncla.tarea(objetivo.id))
        is Objetivo.Habito -> Modifier.anclaParticula(ClavesAncla.fila(objetivo.id))
        Objetivo.Libre, null -> Modifier
    }

/** El nombre del objetivo y «Hecha, {nombre}.» en la voz de quien usa la app (sin nombre, la frase hermana). */
@Composable
private fun preparadoDe(
    objetivo: Objetivo?,
    sesionId: String,
    sesion: SesionInicio?,
): State<PreparadoTerminada?> {
    val recursos = LocalContext.current.resources
    val propio = remember(recursos) { CatalogoRecursos { recursos } }
    return produceState<PreparadoTerminada?>(null, objetivo, sesionId, sesion) {
        if (objetivo == null || sesion == null) return@produceState
        val datos = sesion.objetivo(objetivo)
        val ajustes = sesion.ajustes.settings.first()
        val bolsa =
            when (objetivo) {
                is Objetivo.Tarea -> Bolsa.SE_HECHA_TAREA
                is Objetivo.Habito -> Bolsa.SE_HECHA_HABITO
                Objetivo.Libre -> null
            }
        val frase =
            bolsa?.let {
                FraseSuelta.elegir(
                    bolsa = it,
                    voz = ajustes.personality,
                    nombre = ajustes.userName.trim().ifBlank { null },
                    semilla = sesionId.hashCode().toLong(),
                    catalogo = sesion.catalogo ?: propio,
                    contexto = sesion.contexto(),
                )
            }
        value = PreparadoTerminada(datos?.nombre, frase)
    }
}

/** «{m} min», «{h} h» o «{h} h {m} min». */
@Composable
private fun cifraDeFoco(minutos: Int): String {
    val texto = FormatoSesion.minutos(minutos)
    val id =
        when (texto.clave) {
            TextoMinutos.MIN -> R.string.ses_min
            TextoMinutos.H -> R.string.ses_h
            TextoMinutos.H_MIN -> R.string.ses_h_min
        }
    return stringResource(id, *texto.args.toTypedArray())
}
