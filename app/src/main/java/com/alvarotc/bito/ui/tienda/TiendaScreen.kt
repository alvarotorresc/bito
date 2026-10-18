package com.alvarotc.bito.ui.tienda

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.GardenEngine
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.premios.HojaSaldo
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.LocalTacto
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.sistema.componentes.rememberAvisoEstado
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.LocalSonido
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.flow.Flow

// Las medidas verticales del mock (`tienda-habi.css`, un px es un dp): escena 300 + costra 6, luz 52, pestañas 46.
private val ESCENA = 300.dp
private val ESCENA_CORTA = 260.dp
private val COSTRA = 6.dp
private val ALTO_LUZ = 52.dp
private val ALTO_PESTANAS = 46.dp

// H1: con la barra visible el catálogo no baja de 120 dp; la barra mide como mucho 158 dp (la de más texto).
private val CATALOGO_MIN = 120.dp
private val BARRA_MAX = 158.dp

private val ALTO_CABECERA = 64.dp
private val ICONO_ATRAS = 22.dp

// El contenido de pestaña entra desde 24 dp; el armario baja 10 dp al irse el jardín y sube al volver.
private val ENTRADA_PESTANA = 24.dp
private val BAJADA_ARMARIO = 10.dp
private const val FUNDIDO_PESTANA = 220

/** El alto de la escena más su costra: 306 dp, o 266 dp cuando la pantalla es corta y la barra está a la vista. */
internal fun altoEscenaTienda(
    alto: Dp,
    barraVisible: Boolean,
): Dp {
    val completa = ESCENA + COSTRA + ALTO_LUZ + ALTO_PESTANAS
    val corta = barraVisible && alto - completa - BARRA_MAX < CATALOGO_MIN
    return (if (corta) ESCENA_CORTA else ESCENA) + COSTRA
}

/**
 * La tienda de bitos: escena fija arriba (armario o jardín), el selector de luz, las tres pestañas, el catálogo
 * (única zona con scroll) y la barra de prueba flotando abajo. Escena, selector, pestañas y barra se escriben una
 * vez; cada pestaña da su escena y su catálogo por [PanelTienda].
 */
@Composable
fun TiendaScreen(
    viewModel: TiendaViewModel,
    alSalir: () -> Unit,
    modifier: Modifier = Modifier,
    alComprarObjeto: (String) -> Unit = {},
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    // J31: cada objeto de jardín comprado se avisa fuera (Hoy lo mece al volver); no depende del estado de Hoy.
    val aviso by rememberUpdatedState(alComprarObjeto)
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            val clave = (evento as? EventoTienda.Comprado)?.let { GardenEngine.objectKeysOf(listOf(it.itemId)).firstOrNull() }
            if (clave != null) aviso(clave)
        }
    }
    TiendaContenido(estado, viewModel, viewModel.eventos, alSalir, modifier)
}

@Composable
internal fun TiendaContenido(
    estado: TiendaUiState,
    acciones: TiendaAcciones,
    eventos: Flow<EventoTienda>,
    alSalir: () -> Unit,
    modifier: Modifier = Modifier,
    paneles: (PestanaTienda) -> PanelTienda = ::panelDe,
) {
    val gesto = rememberGesto()
    val estadoVivo by rememberUpdatedState(estado)
    val accionesVivas by rememberUpdatedState(acciones)
    val alSalirVivo by rememberUpdatedState(alSalir)
    val avisos = rememberAvisoEstado()

    BloquearVertical()
    LaunchedEffect(Unit) { gesto(Snd.BROTE, Haptica.TICK) }

    fun quitarPrueba() {
        gesto(Snd.HUNDIR, Haptica.LOW)
        accionesVivas.quitarPrueba()
    }

    fun salir() {
        gesto(Snd.HUNDIR, Haptica.LOW)
        alSalirVivo()
    }

    // D8: con una prueba, atrás la quita y el siguiente sale. Con el aviso de Deshacer a la vista, atrás sale.
    fun atras() {
        if (estadoVivo.prueba != null && avisos.actual == null) quitarPrueba() else salir()
    }

    BackHandler { atras() }
    Box(
        modifier
            .fillMaxSize()
            .background(BitoTema.colores.tAlta)
            .onPreviewKeyEvent {
                val escape = it.type == KeyEventType.KeyDown && it.key == Key.Escape
                if (escape) atras()
                escape
            }.testTag("tienda"),
    ) {
        if (!estado.cargado) {
            // Nada se decide con el relleno de antes del primer estado real: papel y espera.
            Box(Modifier.fillMaxSize().testTag("tienda-cargando"))
        } else {
            Cuerpo(estado, acciones, eventos, avisos, paneles, ::atras, ::quitarPrueba)
        }
    }
}

@Composable
private fun Cuerpo(
    estado: TiendaUiState,
    acciones: TiendaAcciones,
    eventos: Flow<EventoTienda>,
    avisos: AvisoEstado,
    paneles: (PestanaTienda) -> PanelTienda,
    atras: () -> Unit,
    quitarPrueba: () -> Unit,
) {
    val sonido = LocalSonido.current
    val tacto = LocalTacto.current
    val luzEstado = remember { LuzEstado(estado.luz) }
    val vuelo = remember { VueloMoneda() }
    val armario = remember { EscenaArmarioEstado() }
    var saldoAbierto by rememberSaveable { mutableStateOf(false) }
    val ctx =
        ContextoPanel(
            estado = estado,
            acciones = acciones,
            eventos = eventos,
            luz = { luzEstado.valor },
            vuelo = vuelo,
            armario = armario,
            avisos = avisos,
            sonido = sonido,
            tacto = tacto,
        )
    val reducido = BitoTema.reducido

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val altoEscena = altoEscenaTienda(maxHeight, estado.barra != null)
        Column(Modifier.fillMaxSize()) {
            EscenaConCabecera(estado, ctx, luzEstado, altoEscena, paneles, reducido, atras) { saldoAbierto = true }
            SelectorLuz(estado.luz, luzEstado, acciones::cambiarLuz, Modifier.height(ALTO_LUZ))
            Box(Modifier.height(ALTO_PESTANAS)) {
                PestanasTienda(estado.pestana, acciones::cambiarPestana, Modifier.wrapContentHeight(Alignment.Top, unbounded = true))
            }
            Catalogo(estado.pestana, ctx, paneles, reducido, Modifier.weight(1f).fillMaxWidth())
        }
        val txtComprar = (estado.barra as? BarraPrueba.Comprar)?.let { stringResource(R.string.store_buy, it.precio) }.orEmpty()
        BarraPruebaView(
            barra = estado.barra,
            txtComprar = txtComprar,
            txtAhora = stringResource(R.string.store_not_now),
            txtQuitar = stringResource(R.string.store_take_off),
            alComprar = { comprable(estado.prueba)?.let(acciones::comprar) },
            alAhoraNo = quitarPrueba,
            alQuitar = quitarPrueba,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        AvisoHost(avisos, Modifier.align(Alignment.BottomCenter).testTag("aviso-deshacer"))
        CapaMoneda(vuelo)
    }
    HojaSaldo(
        abierta = saldoAbierto,
        saldo = estado.saldo,
        movimientos = estado.movimientos,
        economia = estado.economia,
        alIrTienda = null,
        alCerrar = { saldoAbierto = false },
    )
}

/** El id de lo que «Comprar» paga: una pieza o un objeto. Un especial por logro no se compra. */
private fun comprable(prueba: PruebaTienda?): String? =
    when (prueba) {
        is PruebaTienda.Pieza -> prueba.itemId
        is PruebaTienda.Objeto -> prueba.itemId
        else -> null
    }

@Composable
private fun EscenaConCabecera(
    estado: TiendaUiState,
    ctx: ContextoPanel,
    luzEstado: LuzEstado,
    alto: Dp,
    paneles: (PestanaTienda) -> PanelTienda,
    reducido: Boolean,
    atras: () -> Unit,
    abrirSaldo: () -> Unit,
) {
    val enJardin = estado.pestana == PestanaTienda.JARDIN
    val bajada = with(LocalDensity.current) { BAJADA_ARMARIO.roundToPx() }
    val tapa = if (reducido) fadeOut(tween(Duraciones.REDUCIDO)) else fadeOut(tween(FUNDIDO_PESTANA, easing = Curvas.out))
    Box(Modifier.fillMaxWidth().height(alto).clipToBounds().testTag("tienda-escena")) {
        AnimatedContent(
            targetState = estado.pestana,
            modifier = Modifier.fillMaxWidth().height(alto - COSTRA),
            transitionSpec = {
                val haciaJardin = targetState == PestanaTienda.JARDIN
                val desdeJardin = initialState == PestanaTienda.JARDIN
                val entra = if (reducido) fadeIn(tween(Duraciones.REDUCIDO)) else fadeIn(tween(FUNDIDO_PESTANA, easing = Curvas.out))
                when {
                    reducido -> entra togetherWith tapa
                    haciaJardin == desdeJardin -> EnterTransition.None togetherWith ExitTransition.None
                    haciaJardin -> entra togetherWith (tapa + slideOutVertically(tween(FUNDIDO_PESTANA, easing = Curvas.out)) { bajada })
                    else -> (entra + slideInVertically(tween(FUNDIDO_PESTANA, easing = Curvas.out)) { bajada }) togetherWith tapa
                }
            },
            label = "tienda-escena",
        ) { pestana ->
            EscenaEscalada(alto - COSTRA, Modifier.fillMaxSize()) { paneles(pestana).Escena(ctx, Modifier.fillMaxSize()) }
        }
        // La costra es de la pantalla: una sola, quieta bajo la escena mientras el armario y el jardín se cambian.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .testTag("tcostra")
                .fillMaxWidth()
                .height(COSTRA)
                .background(BitoTema.colores.costra),
        )
        val noche = enJardin && luzEstado.destino == LuzTienda.NOCHE
        val tinta by animateColorAsState(
            if (noche) BitoTema.colores.tintaClara else BitoTema.colores.tinta,
            tween(CAMBIO_TINTA),
            label = "tienda-tinta",
        )
        Cabecera(estado.saldo, tinta, ctx.vuelo, atras, abrirSaldo)
    }
}

private const val CAMBIO_TINTA = 300

/** La escena a su alto de 300 dp; si hay menos sitio se encoge proporcional desde arriba (H1). */
@Composable
private fun EscenaEscalada(
    alto: Dp,
    modifier: Modifier,
    contenido: @Composable () -> Unit,
) {
    val completa = ESCENA
    if (alto >= completa) {
        contenido()
        return
    }
    BoxWithConstraints(modifier) {
        val factor = alto / completa
        Box(
            Modifier
                .requiredWidth(maxWidth)
                .requiredHeight(completa)
                .graphicsLayer {
                    scaleX = factor
                    scaleY = factor
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) { contenido() }
    }
}

@Composable
private fun Cabecera(
    saldo: Int,
    tinta: Color,
    vuelo: VueloMoneda,
    atras: () -> Unit,
    abrirSaldo: () -> Unit,
) {
    val titulo = stringResource(R.string.store_title)
    val volver = stringResource(R.string.back)
    Row(
        Modifier.fillMaxWidth().height(ALTO_CABECERA).padding(start = Medidas.e4, end = Medidas.e8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Medidas.toqueMinimo)
                .testTag("tienda-atras")
                .semantics {
                    contentDescription = volver
                    role = Role.Button
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = atras),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconosSistema.ArrowLeft, contentDescription = null, tint = tinta, modifier = Modifier.size(ICONO_ATRAS))
        }
        Text(
            titulo,
            Modifier.weight(1f).padding(start = Medidas.e4),
            style = BitoTema.tipos.t24.copy(fontWeight = FontWeight.Bold),
            color = tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .height(Medidas.toqueMinimo)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = abrirSaldo)
                .padding(start = Medidas.e8),
            contentAlignment = Alignment.Center,
        ) {
            SaldoConRodillo(saldo, Modifier.origenMoneda(vuelo))
        }
    }
}

@Composable
private fun Catalogo(
    pestana: PestanaTienda,
    ctx: ContextoPanel,
    paneles: (PestanaTienda) -> PanelTienda,
    reducido: Boolean,
    modifier: Modifier,
) {
    val densidad = LocalDensity.current
    val desplazamiento = with(densidad) { ENTRADA_PESTANA.roundToPx() }
    AnimatedContent(
        targetState = pestana,
        modifier = modifier.clipToBounds().testTag("tienda-catalogo"),
        transitionSpec = {
            val haciaDelante = targetState.ordinal > initialState.ordinal
            if (reducido) {
                fadeIn(tween(Duraciones.REDUCIDO)) togetherWith fadeOut(tween(Duraciones.REDUCIDO))
            } else {
                val sentido = if (haciaDelante) 1 else -1
                (
                    fadeIn(tween(FUNDIDO_PESTANA, easing = Curvas.out)) +
                        slideInHorizontally(tween(FUNDIDO_PESTANA, easing = Curvas.out)) { sentido * desplazamiento }
                ) togetherWith fadeOut(tween(FUNDIDO_PESTANA, easing = Curvas.out))
            }
        },
        label = "tienda-catalogo",
    ) { p ->
        Box(Modifier.fillMaxSize().testTag("tienda-contenido-${p.name}")) { paneles(p).Catalogo(ctx, Modifier.fillMaxSize()) }
    }
}

/** D1: vertical mientras la tienda esté abierta; al salir, la orientación vuelve a quien la decida. */
@Composable
private fun BloquearVertical() {
    val contexto = LocalContext.current
    DisposableEffect(contexto) {
        val actividad = contexto.actividad()
        actividad?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { actividad?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

private tailrec fun Context.actividad(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.actividad()
        else -> null
    }
