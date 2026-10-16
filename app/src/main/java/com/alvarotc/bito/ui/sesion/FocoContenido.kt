package com.alvarotc.bito.ui.sesion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.CatalogoHabla
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.sesion.FormatoSesion
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.PlanSesion
import com.alvarotc.bito.domain.sesion.TextoMinutos
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.sesion.AvisoSesion
import com.alvarotc.bito.sesion.MotorSesion
import com.alvarotc.bito.ui.habla.FraseSuelta
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.inicio.textoHabi
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.premios.anclaParticula
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.Globito
import com.alvarotc.bito.ui.sistema.componentes.pulsar
import com.alvarotc.bito.ui.sistema.fuerte
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

// `.ses-tit`: 10 dp debajo; el paso, 2 dp arriba. `.ses-reloj`: 12 dp entre la cifra y su lado; `.ses-de`: 6 dp abajo y entre piezas.
private val DEBAJO_TITULO = 10.dp
private val ARRIBA_PASO = 2.dp
private val HUECO_RELOJ = 12.dp
private val ABAJO_DE = 6.dp
private val HUECO_DE = 6.dp

// `.ses-et`: 10 dp arriba, 8 entre etiquetas, alto mínimo 22. `.etiqueta`: 12 sp / 700, 8 dp a los lados, espaciado .02 em.
private val ARRIBA_ETIQUETAS = 10.dp
private val HUECO_ETIQUETAS = 8.dp
private val ALTO_ETIQUETA = 22.dp
private val LADOS_ETIQUETA = 8.dp
private val LETRA_ETIQUETA = 12.sp
private val ETIQUETA_AGUA_FONDO = Color(0xFFE6F1F4)

// `.ses-ext`: 14 dp arriba y 8 entre mandos; `.pill`: 40 dp de alto, 14 dp a los lados, 14 sp. El +N salta 3 dp.
private val ARRIBA_MANDOS = 14.dp
private val HUECO_MANDOS = 8.dp
private val ALTO_PILDORA = 40.dp
private val LADOS_PILDORA = 14.dp
private val LETRA_PILDORA = 14.sp
private val SALTO_MAS = 3.dp
private const val SALTO_MAS_MS = 200
private val ICONO_PAUSA = 18.dp

// `.btnrow.llena`: 12 dp arriba. `.ses-dejar`: 6 dp arriba. Terminada: la pregunta, 14 dp arriba.
private val ARRIBA_HECHO = 12.dp
private val ARRIBA_DEJAR = 6.dp
private val ARRIBA_PREGUNTA = 14.dp

// La cifra: 48 sp / 900, espaciado -0,02 em (sin token), alto 1,08 em y letra limitada a 1,3 veces.
private val ESPACIADO_CIFRA = (-0.02).em
private const val ALTO_CIFRA = 1.08f
private const val ESCALA_MAX_CIFRA = 1.3f

// Pausa: la cifra y «de {n} min» bajan a 0,45 en 200 ms `ease` (sin token: `ease` de CSS).
private const val ALFA_PAUSA = 0.45f
private val EASE = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

// El saltito de Habi mientras corre: 0,11 cada 20 a 40 s, el primero a los 22 s.
private const val AMPLITUD_SALTITO = 0.11f
private const val PRIMER_SALTITO_MS = 22_000L
private const val SALTITO_MIN_MS = 20_000L
private const val SALTITO_MAX_MS = 40_000L

// El globito de entrada al primer bloque: a los 620 ms (100 en reducido), 2,6 s en pantalla.
private const val GLOBITO_RETRASO_MS = 620L
private const val GLOBITO_RETRASO_REDUCIDO_MS = 100L
private const val GLOBITO_VISIBLE_MS = 2_600L

// Tras «He terminado»: «Hecha, {nombre}.» y, a los 2200 ms, vuelta a Hoy.
private const val HECHA_MS = 2_200L

// Un bloque recién empezado: pasados unos segundos (al volver a abrir la app a mitad) no se saluda otra vez.
private const val SALUDO_HASTA_MS = 3_000L

private const val MILLIS_POR_MINUTO = 60_000L
private const val SEGUNDOS_POR_MINUTO = 60

/** Lo que se pinta del objetivo: su nombre (null = foco libre, «Solo foco») y su primer paso. */
data class DatosFoco(
    val nombre: String?,
    val primerPaso: String?,
)

/** Lo que pinta Foco en cada fotograma, solo para las pruebas. */
class SondaFoco {
    var alfaCifra: Float = 1f
    var alfaDe: Float = 1f
    var rodilloCifra: Float = 1f
    val saltoMas: MutableMap<Int, Float> = mutableMapOf()
}

/** Lo que Foco pide a su alrededor, para que la pantalla no dependa del Inicio. */
class EntornoFoco(
    val motor: MotorSesion,
    val salida: SalidaSesion,
    val retenido: BloqueRetenido,
    val saltarHabi: (Float) -> Unit,
    val alMasOtro: () -> Unit,
    /** Dónde está Habi en la raíz (para el globito); null si no se sabe. */
    val anclaHabi: () -> Rect?,
    /** El ancla de la que salen las monedas de «He terminado»; null sin objetivo. */
    val anclaMonedas: String?,
    /** El aviso G9 con su texto resuelto. */
    val textoAviso: (AvisoSesion) -> String,
    /** La cara de Habi: radiante con el «Hecha». */
    val cara: CaraHabi = CaraHabi(),
)

/** Los textos de Habi que Foco necesita (ya resueltos con su voz): el globito de entrada y el «Hecha». */
class FrasesFoco(
    val entrada: String?,
    val hecha: String?,
)

private class HechaFoco(
    val minutos: Int,
)

/**
 * Foco (`data-b="foco"`, spec §3.3.4 y §7.4). Emite sus piezas como hijos directos (la tierra las escalona): título,
 * reloj, etiquetas, mandos, «He terminado», «lo dejo» y el ancla del globito. Con «He terminado» el motor cierra la
 * sesión y el bloque se queda [HECHA_MS] ms («Hecha, {nombre}.», sin botones) antes de volver a Hoy con su aviso.
 */
@Composable
fun FocoContenido(
    instante: Instante,
    plan: PlanSesion,
    datos: DatosFoco,
    fondoSonando: SessionBackground?,
    noMolestar: Boolean,
    entorno: EntornoFoco,
    frases: FrasesFoco,
    sonda: SondaFoco? = null,
    reloj: () -> Long = SystemClock::elapsedRealtime,
) {
    val reducido = BitoTema.reducido
    val scope = rememberCoroutineScope()
    val foco = instante.actual as? Tramo.Foco
    val bloque = foco?.bloque ?: 1
    var hecha by remember { mutableStateOf<HechaFoco?>(null) }
    var cerrando by remember { mutableStateOf(false) }

    val minutosDe = (instante.duracionTramoMillis / MILLIS_POR_MINUTO).toInt()
    val pausado = instante.pausado
    val segundosTotales = ceil(instante.restaMillis.coerceAtLeast(0L) / 1_000.0).toInt()

    // El saltito de Habi: el reloj solo corre mientras no hay pausa ni «Hecha». Sin bucle por fotograma: un solo `delay`.
    val restanteSaltito = remember { longArrayOf(PRIMER_SALTITO_MS) }
    LaunchedEffect(pausado, hecha) {
        if (pausado || hecha != null) return@LaunchedEffect
        while (true) {
            val desde = reloj()
            try {
                delay(restanteSaltito[0])
            } finally {
                restanteSaltito[0] -= reloj() - desde
            }
            entorno.saltarHabi(AMPLITUD_SALTITO)
            restanteSaltito[0] = Random.nextLong(SALTITO_MIN_MS, SALTITO_MAX_MS)
        }
    }

    // El globito del primer bloque.
    var globito by remember { mutableStateOf(false) }
    val saluda = bloque == 1 && instante.focoMillis < SALUDO_HASTA_MS && frases.entrada != null
    LaunchedEffect(saluda, reducido) {
        if (!saluda) return@LaunchedEffect
        delay(if (reducido) GLOBITO_RETRASO_REDUCIDO_MS else GLOBITO_RETRASO_MS)
        globito = true
        delay(GLOBITO_VISIBLE_MS)
        globito = false
    }

    val hechaActual = hecha
    if (hechaActual != null) {
        PiezaHecha(hechaActual.minutos, frases.hecha)
        return
    }

    // 1. Título.
    Column(Modifier.fillMaxWidth().padding(bottom = DEBAJO_TITULO).testTag("f-titulo")) {
        Text(
            datos.nombre ?: stringResource(R.string.ses_solo_foco),
            Modifier.testTag("f-nom"),
            style = BitoTema.tipos.t19.fuerte(),
            color = BitoTema.colores.tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (datos.nombre != null) {
            Text(
                datos.primerPaso?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.ses_primer_paso, it.lowercase()) }
                    ?: stringResource(R.string.ses_sin_primer_paso_foco),
                Modifier.padding(top = ARRIBA_PASO).testTag("f-paso"),
                style = BitoTema.tipos.t14,
                color = BitoTema.colores.tintaSuave,
            )
        }
    }

    // 2. Reloj.
    RelojFoco(
        segundosTotales = segundosTotales,
        minutosDe = minutosDe,
        pausado = pausado,
        bloque = bloque,
        bloques = plan.bloques,
        sonda = sonda,
    )

    // 3. Etiquetas.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = ARRIBA_ETIQUETAS)
            .heightIn(min = ALTO_ETIQUETA)
            .testTag("f-et"),
        horizontalArrangement = Arrangement.spacedBy(HUECO_ETIQUETAS),
    ) {
        nombreDeFondo(fondoSonando)?.let { Etiqueta(it, agua = false, Modifier.testTag("f-et-fondo")) }
        if (noMolestar) Etiqueta(stringResource(R.string.ses_no_molestar), agua = true, Modifier.testTag("f-et-dnd"))
    }

    // 4. Mandos.
    MandosFoco(
        pausado = pausado,
        alPausa = { scope.launch { if (pausado) entorno.motor.seguir() else entorno.motor.pausar() } },
        alSumar = { m -> scope.launch { entorno.motor.sumar(m) } },
        alMasOtro = entorno.alMasOtro,
        sonda = sonda,
    )

    // 5. He terminado.
    Boton(
        stringResource(R.string.ses_he_terminado),
        alPulsar = {
            if (!cerrando) {
                cerrando = true
                scope.launch {
                    // Como `terminada(true)` del mock: la cámara pasa ya a Terminada y Habi se pone radiante.
                    entorno.retenido.retener(BloqueSesion.FOCO, Encuadre.TERMINADA)
                    entorno.cara.radiante = true
                    try {
                        val minutos = FormatoSesion.minutosDeFoco(instante.focoMillis)
                        val resultado = entorno.motor.heTerminado()
                        hecha = HechaFoco(minutos)
                        delay(HECHA_MS)
                        entorno.salida.volverAHoy()
                        resultado.aviso?.let { entorno.salida.avisar(entorno.textoAviso(it)) }
                    } finally {
                        entorno.retenido.soltar()
                    }
                }
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = ARRIBA_HECHO)
                .testTag("f-hecho")
                .then(entorno.anclaMonedas?.let { Modifier.anclaParticula(it) } ?: Modifier),
        estilo = EstiloBoton.PRINCIPAL,
        alto = Medidas.botonHoja,
        llena = true,
        pildoraMock = true,
    )

    // 6. Lo dejo.
    Box(Modifier.fillMaxWidth().padding(top = ARRIBA_DEJAR), contentAlignment = Alignment.Center) {
        BotonTexto(
            stringResource(R.string.ses_lo_dejo),
            alPulsar = {
                if (!cerrando) {
                    cerrando = true
                    scope.launch {
                        val resultado = entorno.motor.loDejo()
                        entorno.salida.volverAHoy()
                        resultado.aviso?.let { entorno.salida.avisar(entorno.textoAviso(it)) }
                    }
                }
            },
            modifier = Modifier.testTag("f-dejar"),
        )
    }

    // 7. El ancla del globito (sin tamaño) sobre Habi.
    AnclaGlobito(globito, frases.entrada, entorno.anclaHabi)
}

@Composable
private fun AnclaGlobito(
    visible: Boolean,
    texto: String?,
    anclaHabi: () -> Rect?,
) {
    var posicion by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .size(0.dp)
            .onGloballyPositioned { posicion = it.positionInRoot() }
            .offset {
                val habi = anclaHabi()
                if (habi == null) {
                    IntOffset.Zero
                } else {
                    IntOffset(
                        (habi.center.x - posicion.x).roundToInt(),
                        (habi.top - posicion.y).roundToInt(),
                    )
                }
            },
    ) {
        if (texto != null && anclaHabi() != null) {
            Globito(visible, texto, autoOcultar = false)
        }
    }
}

@Composable
private fun RelojFoco(
    segundosTotales: Int,
    minutosDe: Int,
    pausado: Boolean,
    bloque: Int,
    bloques: Int,
    sonda: SondaFoco?,
) {
    val reducido = BitoTema.reducido
    val colores = BitoTema.colores
    val alfa by animateFloatAsState(if (pausado) ALFA_PAUSA else 1f, tween(Duraciones.COLOR, easing = EASE), label = "foco-pausa")

    // La cifra rueda solo al sumar minutos (+N), nunca cada segundo.
    var minutosVistos by remember { mutableIntStateOf(minutosDe) }
    val rodillo = remember { Animatable(1f) }
    LaunchedEffect(minutosDe) {
        if (minutosDe == minutosVistos) return@LaunchedEffect
        minutosVistos = minutosDe
        rodillo.snapTo(0f)
        rodillo.animateTo(
            1f,
            tween(if (reducido) Duraciones.REDUCIDO else Duraciones.RODILLO, easing = if (reducido) Curvas.lin else Curvas.out),
        )
    }

    val minutos = segundosTotales / SEGUNDOS_POR_MINUTO
    val segundos = segundosTotales % SEGUNDOS_POR_MINUTO
    val lectura =
        stringResource(
            R.string.sesr_lectura_cifra,
            pluralStringResource(R.plurals.sesr_lectura_minutos, minutos, minutos),
            pluralStringResource(R.plurals.sesr_lectura_segundos, segundos, segundos),
            minutosDe,
        )
    val densidad = LocalDensity.current
    val limitada = Density(densidad.density, minOf(densidad.fontScale, ESCALA_MAX_CIFRA))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HUECO_RELOJ), verticalAlignment = Alignment.Bottom) {
        CompositionLocalProvider(LocalDensity provides limitada) {
            val estilo = BitoTema.tipos.t48.cifra().copy(letterSpacing = ESPACIADO_CIFRA, lineHeight = ALTO_CIFRA.em)
            val alto = with(limitada) { (estilo.fontSize.toPx() * ALTO_CIFRA).toDp() }
            Box(Modifier.height(alto), contentAlignment = Alignment.CenterStart) {
                Text(
                    "%d:%02d".format(minutos, segundos),
                    Modifier
                        .testTag("f-t")
                        .semantics { contentDescription = lectura }
                        .graphicsLayer {
                            val r = rodillo.value
                            this.alpha = alfa
                            if (!reducido) translationY = (1f - r) * size.height else this.alpha = alfa * r
                            sonda?.alfaCifra = this.alpha
                            sonda?.rodilloCifra = r
                        },
                    style = estilo,
                    color = colores.tinta,
                    maxLines = 1,
                )
            }
        }
        Column(Modifier.padding(bottom = ABAJO_DE), verticalArrangement = Arrangement.spacedBy(HUECO_DE)) {
            Text(
                if (pausado) stringResource(R.string.ses_en_pausa) else stringResource(R.string.ses_de_min, minutosDe),
                Modifier
                    .testTag("f-de")
                    .graphicsLayer {
                        this.alpha = alfa
                        sonda?.alfaDe = alfa
                    },
                style = BitoTema.tipos.t16.copy(fontFeatureSettings = "tnum"),
                color = colores.tintaSuave,
            )
            if (bloques > 1) {
                val descripcion = stringResource(R.string.ses_bloque_n_de_m, bloque, bloques)
                Puntitos(bloque, bloques, Modifier.testTag("f-blq").semantics { contentDescription = descripcion })
            }
        }
    }
}

@Composable
private fun MandosFoco(
    pausado: Boolean,
    alPausa: () -> Unit,
    alSumar: (Int) -> Unit,
    alMasOtro: () -> Unit,
    sonda: SondaFoco?,
) {
    val colores = BitoTema.colores
    val interaccion = remember { MutableInteractionSource() }
    val etiquetaPausa = stringResource(if (pausado) R.string.ses_seguir else R.string.ses_pausa)
    Row(
        Modifier.fillMaxWidth().padding(top = ARRIBA_MANDOS),
        horizontalArrangement = Arrangement.spacedBy(HUECO_MANDOS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .testTag("f-pausa")
                .semantics { contentDescription = etiquetaPausa }
                .clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = alPausa)
                .pulsar(interaccion, redondo = true)
                .size(Medidas.toqueMinimo)
                .background(colores.canto, CircleShape)
                .border(Medidas.linea, colores.contorno, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (pausado) IconosSistema.Play else IconosSistema.Pause,
                contentDescription = null,
                tint = colores.tinta,
                modifier = Modifier.size(ICONO_PAUSA),
            )
        }
        listOf(5 to R.string.ses_mas_5, 15 to R.string.ses_mas_15, 30 to R.string.ses_mas_30).forEach { (m, texto) ->
            PildoraMas(stringResource(texto), Modifier.testTag("f-mas-$m"), m, sonda) { alSumar(m) }
        }
        PildoraMas(stringResource(R.string.ses_mas_otro), Modifier.testTag("f-otro"), -1, sonda, alMasOtro)
    }
}

/** `.pill` de los mandos: 40 dp de alto con zona de toque de 44; el +N salta 3 dp al pulsarlo (no en reducido). */
@Composable
private fun PildoraMas(
    texto: String,
    modifier: Modifier,
    clave: Int,
    sonda: SondaFoco?,
    alPulsar: () -> Unit,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val interaccion = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val salto = remember { Animatable(0f) }
    val altura = with(LocalDensity.current) { SALTO_MAS.toPx() }
    Box(
        modifier
            .heightIn(min = Medidas.toqueMinimo)
            .defaultMinSize(minWidth = Medidas.toqueMinimo)
            .clickable(interactionSource = interaccion, indication = null, role = Role.Button) {
                if (clave > 0 && !reducido) {
                    scope.launch {
                        salto.animateTo(-altura, tween(SALTO_MAS_MS / 2, easing = Curvas.out))
                        salto.animateTo(0f, tween(SALTO_MAS_MS / 2, easing = Curvas.out))
                    }
                }
                alPulsar()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .pulsar(interaccion)
                .graphicsLayer {
                    translationY = salto.value
                    if (clave > 0) sonda?.saltoMas?.set(clave, salto.value)
                }.height(ALTO_PILDORA)
                .clip(BitoFormas.canto)
                .background(colores.canto)
                .border(Medidas.linea, colores.contorno, BitoFormas.canto)
                .padding(horizontal = LADOS_PILDORA),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                texto,
                style = BitoTema.tipos.t14.copy(fontWeight = FontWeight.Bold, fontSize = LETRA_PILDORA, fontFeatureSettings = "tnum"),
                color = colores.tinta,
                maxLines = 1,
            )
        }
    }
}

/** `.etiqueta` (`suave` o `agua`): 22 dp de alto, 12 sp / 700 con espaciado .02 em, contorno de 1,5 dp. */
@Composable
private fun Etiqueta(
    texto: String,
    agua: Boolean,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val tinta = if (agua) colores.copo else colores.tintaSuave
    val contorno = if (agua) colores.aguaHonda else colores.contorno.copy(alpha = 0.35f)
    Box(
        modifier
            .semantics(mergeDescendants = true) {}
            .height(ALTO_ETIQUETA)
            .clip(BitoFormas.canto)
            .background(if (agua) ETIQUETA_AGUA_FONDO else Color.Transparent)
            .border(Medidas.linea, contorno, BitoFormas.canto)
            .padding(horizontal = LADOS_ETIQUETA),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            style = BitoTema.tipos.t14.copy(fontWeight = FontWeight.Bold, fontSize = LETRA_ETIQUETA, letterSpacing = 0.02.em),
            color = tinta,
            maxLines = 1,
        )
    }
}

@Composable
private fun nombreDeFondo(fondo: SessionBackground?): String? =
    when (fondo) {
        SessionBackground.RAIN -> stringResource(R.string.ses_fondo_lluvia)
        SessionBackground.BROWN -> stringResource(R.string.ses_fondo_marron)
        SessionBackground.CAFE -> stringResource(R.string.ses_fondo_cafe)
        SessionBackground.NONE, null -> null
    }

/** Tras «He terminado»: los minutos de foco con «de foco» y «Hecha, {nombre}.», sin botones (como `terminada(true)` del mock). */
@Composable
private fun PiezaHecha(
    minutos: Int,
    frase: String?,
) {
    val colores = BitoTema.colores
    val t = FormatoSesion.minutos(minutos)
    val cifra =
        when (t.clave) {
            TextoMinutos.MIN -> stringResource(R.string.ses_min, t.args[0])
            TextoMinutos.H -> stringResource(R.string.ses_h, t.args[0])
            TextoMinutos.H_MIN -> stringResource(R.string.ses_h_min, t.args[0], t.args[1])
        }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HUECO_RELOJ), verticalAlignment = Alignment.Bottom) {
        Text(
            cifra,
            Modifier.testTag("t-min"),
            style = BitoTema.tipos.t48.cifra().copy(letterSpacing = ESPACIADO_CIFRA, lineHeight = ALTO_CIFRA.em),
            color = colores.tinta,
            maxLines = 1,
        )
        Text(
            stringResource(R.string.ses_de_foco),
            Modifier.padding(bottom = ABAJO_DE),
            style = BitoTema.tipos.t16,
            color = colores.tintaSuave,
        )
    }
    if (frase != null) {
        Text(
            frase,
            Modifier.fillMaxWidth().padding(top = ARRIBA_PREGUNTA).testTag("t-preg"),
            style = BitoTema.tipos.t19.fuerte(),
            color = colores.tinta,
            textAlign = TextAlign.Start,
        )
    }
}

/**
 * Foco con su hoja «¿Cuántos minutos más?» (`+ Otro`): Sumar los añade a lo que queda y Atrás la cierra antes que
 * nada, por el registro de hojas del Inicio.
 */
@Composable
internal fun FocoConHojaMas() {
    val motor = LocalMotorSesion.current
    val hojas = LocalRegistroHoja.current
    val alcance = rememberCoroutineScope()
    var abierta by remember { mutableStateOf(false) }
    DisposableEffect(abierta) {
        if (abierta) hojas.abrir { abierta = false }
        onDispose { if (abierta) hojas.soltar() }
    }
    FocoBloque(alMasOtro = { abierta = true })
    HojaMinutos(
        abierta = abierta,
        alGuardar = { minutos -> alcance.launch { motor?.sumar(minutos) } },
        alCerrar = { abierta = false },
        mas = true,
    )
}

/**
 * El bloque con los datos de verdad: lo que dice el motor (con el último foco a mano para cuando la sesión se cierra
 * y el bloque se queda), el objetivo, la voz y el fondo que suena. [alMasOtro] abre la hoja de minutos «más».
 */
@Composable
internal fun FocoBloque(alMasOtro: () -> Unit = {}) {
    val motor = LocalMotorSesion.current ?: return
    val sesion = LocalSesionInicio.current
    val viva by motor.instante.collectAsStateWithLifecycle()
    val registroVivo by motor.registro.collectAsStateWithLifecycle()
    // Mientras cae al bloque siguiente (o se cierra) sigue pintando su último foco.
    val ultimo = remember { arrayOfNulls<Instante>(1) }
    val ultimoRegistro = remember { arrayOfNulls<com.alvarotc.bito.domain.sesion.RegistroSesion>(1) }
    viva?.takeIf { it.actual is Tramo.Foco }?.let { ultimo[0] = it }
    registroVivo?.let { ultimoRegistro[0] = it }
    val instante = ultimo[0] ?: return
    val registro = ultimoRegistro[0] ?: return
    val objetivo = registro.plan.objetivo

    val datosObjetivo by produceState<DatosObjetivo?>(null, objetivo) { value = sesion?.objetivo(objetivo) }
    val datos =
        when (objetivo) {
            Objetivo.Libre -> DatosFoco(null, null)
            else -> DatosFoco(datosObjetivo?.nombre.orEmpty(), datosObjetivo?.primerPaso)
        }
    val ajustes by remember(sesion) { sesion?.ajustes?.settings ?: emptyFlow() }.collectAsStateWithLifecycle(null)
    val fondo by remember(sesion) { sesion?.fondo?.sonando ?: MutableStateFlow(null) }.collectAsStateWithLifecycle()
    val anclas = LocalAnclas.current
    val recursos = LocalContext.current.resources

    val nombre = ajustes?.userName?.trim()?.takeIf { it.isNotEmpty() }
    val voz = ajustes?.personality ?: Personality.NEUTRA
    val catalogo = sesion?.catalogo
    val minutos = (instante.duracionTramoMillis / MILLIS_POR_MINUTO).toInt()
    val entrada = frase(catalogo, SE_ENTRADA, voz, nombre, registro.inicioPared, objetivo, datosObjetivo, minutos)
    val hecha =
        frase(
            catalogo,
            if (objetivo is Objetivo.Habito) Bolsa.SE_HECHA_HABITO else Bolsa.SE_HECHA_TAREA,
            voz,
            nombre,
            registro.inicioPared,
            null,
            null,
            minutos,
        )

    val entorno =
        EntornoFoco(
            motor = motor,
            salida = LocalSalidaSesion.current,
            retenido = LocalBloqueRetenido.current,
            saltarHabi = LocalSaltoHabi.current,
            alMasOtro = alMasOtro,
            anclaHabi = { anclas?.posiciones?.get(ClavesAncla.HABI) },
            anclaMonedas =
                when (objetivo) {
                    is Objetivo.Tarea -> ClavesAncla.tarea(objetivo.id)
                    is Objetivo.Habito -> ClavesAncla.fila(objetivo.id)
                    Objetivo.Libre -> null
                },
            textoAviso = { it.texto(recursos) },
            cara = LocalCaraHabi.current,
        )
    FocoContenido(
        instante = instante,
        plan = registro.plan,
        datos = datos,
        fondoSonando = fondo,
        noMolestar = sesion?.noMolestarActivo(registro) == true,
        entorno = entorno,
        frases = FrasesFoco(entrada, if (objetivo == Objetivo.Libre) null else hecha),
    )
}

private val SE_ENTRADA = Bolsa.SE_ENTRAR

/** Una frase suelta de la bolsa con los huecos de la sesión, ya resuelta; null sin catálogo. */
@Composable
private fun frase(
    catalogo: CatalogoHabla?,
    bolsa: Bolsa,
    voz: Personality,
    nombre: String?,
    semilla: Long,
    objetivo: Objetivo?,
    datos: DatosObjetivo?,
    minutos: Int,
): String? {
    if (catalogo == null) return null
    val huecos =
        buildMap<Hueco, ValorHueco> {
            put(Hueco.MINUTOS, ValorHueco.Numero(minutos))
            val n = datos?.nombre
            if (n != null) {
                when (objetivo) {
                    is Objetivo.Tarea -> put(Hueco.TAREA, ValorHueco.Texto(n))
                    is Objetivo.Habito -> put(Hueco.HABITO, ValorHueco.Texto(n))
                    else -> Unit
                }
            }
        }
    val frase =
        remember(catalogo, bolsa, voz, nombre, semilla, huecos) { FraseSuelta.elegir(bolsa, voz, nombre, semilla, catalogo, huecos) }
    return textoHabi(frase)
}
