package com.alvarotc.bito.ui.tienda.congeladores

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.EscenaArmario
import com.alvarotc.bito.ui.tienda.EventoTienda
import com.alvarotc.bito.ui.tienda.PanelTienda
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// (sin token) `.tc-sec` y `.tc-veta` de tienda-congeladores.css.
private val ARRIBA_TITULO = 20.dp
private val LATERAL = 20.dp
private val ARRIBA_PISTA = 4.dp
private val ARRIBA_VETA = 24.dp
private val ABAJO_LISTA = 24.dp
private val HUECO_TIENES = 6.dp

/** La pista bajo el título según la voz de Ajustes. */
@StringRes
internal fun pistaDe(voz: Personality): Int =
    when (voz) {
        Personality.NEUTRA -> R.string.store_freezer_hint_neutra
        Personality.SARGENTO -> R.string.store_freezer_hint_sargento
        Personality.CHEERLEADER -> R.string.store_freezer_hint_cheerleader
    }

/** La pestaña Congeladores: el armario sin prueba y, debajo, el título, la pista, la veta fría y el botón directo. */
object PanelCongeladores : PanelTienda {
    @Composable
    override fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        EscenaArmario(look = ctx.estado.puestos, luz = ctx.luz, estado = ctx.armario, modifier = modifier, conCostra = false)
    }

    @Composable
    override fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        val precio = EconomyConfig().freezerPrice
        val compra = remember { CompraCongelador() }
        val fila = remember { FilaCopitos() }
        val ambito = rememberCoroutineScope()
        val gesto = rememberGesto()
        val densidad = LocalDensity.current.density
        val aviso = remember(ctx.avisos, ambito) { AvisoDeshacer(ctx.avisos, ambito) }
        val estadoVivo by rememberUpdatedState(ctx.estado)
        val accionesVivas by rememberUpdatedState(ctx.acciones)
        val textoComprado = stringResource(R.string.store_freezer_bought)
        val textoDeshacer = stringResource(R.string.store_freezer_undo)
        val cuenta = compra.cuentaFija ?: ctx.estado.congeladores
        val cuentaViva by rememberUpdatedState(cuenta)

        fun deshacer() {
            if (compra.ocupado) return
            compra.empezarDeshacer(cuentaViva)
            accionesVivas.deshacerCongelador()
        }

        fun comprar() {
            if (compra.ocupado) return
            compra.empezarCompra(estadoVivo.congeladores, estadoVivo.saldo)
            aviso.retirar() // solo se deshace la última: el aviso viejo se va al empezar la nueva
            accionesVivas.comprarCongelador()
        }

        // Los eventos se recogen en un solo sitio; cada vuelo y cada aviso corren aparte para no frenar al siguiente.
        LaunchedEffect(Unit) {
            ctx.eventos.collect { evento ->
                when (evento) {
                    is EventoTienda.CongeladorComprado ->
                        if (compra.aceptarCompra()) {
                            ambito.launch {
                                gesto(Snd.GASTAR, Haptica.CONFIRM)
                                ctx.vuelo.volar({ fila.centro(huecoDe(evento.cuantos), densidad) })
                                compra.llegar(evento.cuantos, precio)
                                gesto(Snd.CAMPANILLA)
                                aviso.mostrar(textoComprado, textoDeshacer, ::deshacer)
                                delay(POSADO_MS)
                                compra.posado()
                            }
                        }
                    is EventoTienda.CongeladorDeshecho ->
                        if (compra.aceptarDeshacer()) {
                            val antes = cuentaViva
                            ambito.launch {
                                gesto(Snd.HUNDIR, Haptica.LOW)
                                // Con 9 o más la pastilla solo cambia de cifra; con menos se va el último con un fundido.
                                if (antes <= COPITOS_CABEN + 1) {
                                    compra.irse()
                                    delay(Duraciones.REDUCIDO.toLong())
                                }
                                compra.yaSeFue(antes - 1)
                            }
                        }
                    else -> Unit
                }
            }
        }
        LaunchedEffect(compra.generacion) {
            if (compra.generacion == 0) return@LaunchedEffect
            delay(TOPE_ESPERA_MS)
            compra.soltarSiSigueEsperando()
        }
        LaunchedEffect(ctx.estado.congeladores, compra.cuentaFija) { compra.soltarSiAlcanzada(ctx.estado.congeladores) }

        CatalogoCongeladores(
            voz = vozDeAjustes(),
            congeladores = cuenta,
            saldo = compra.saldoFijo ?: ctx.estado.saldo,
            precio = precio,
            alComprar = ::comprar,
            modifier = modifier,
            comprando = compra.comprando,
            entrando = compra.entrando,
            saliendo = compra.saliendo,
            fila = fila,
        )
    }
}

// Lo que tarda el copito en posarse (320 ms) más un fotograma, y cuánto se espera al evento antes de soltar.
private const val POSADO_MS = 352L
private const val TOPE_ESPERA_MS = 2000L

/** El cuerpo de la pestaña, sin estado propio: todo entra por parámetros. */
@Composable
internal fun CatalogoCongeladores(
    voz: Personality,
    congeladores: Int,
    saldo: Int,
    precio: Int,
    alComprar: () -> Unit,
    modifier: Modifier = Modifier,
    comprando: Boolean = false,
    entrando: Boolean = false,
    saliendo: Boolean = false,
    fila: FilaCopitos? = null,
) {
    val colores = BitoTema.colores
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.store_freezer_title),
            Modifier.padding(start = LATERAL, end = LATERAL, top = ARRIBA_TITULO).semantics { heading() },
            style = BitoTema.tipos.t19.copy(fontWeight = FontWeight.Bold),
            color = colores.tinta,
        )
        Text(
            stringResource(pistaDe(voz)),
            Modifier.padding(start = LATERAL, end = LATERAL, top = ARRIBA_PISTA).testTag("tc-pista"),
            style = BitoTema.tipos.t14.copy(lineHeight = 19.6.sp),
            color = colores.tintaSuave,
        )
        Spacer(Modifier.height(ARRIBA_VETA))
        VetaFria { tintaSuave ->
            Tienes(congeladores)
            Copitos(congeladores, tintaSuave = tintaSuave, entrando = entrando, saliendo = saliendo, fila = fila)
            BotonCongelador(precio, saldo, alComprar, tintaSuave = tintaSuave, comprando = comprando)
        }
        Spacer(Modifier.height(ABAJO_LISTA))
    }
}

/** «Tienes N»: el número rueda. La frase la pone el recurso, que también decide dónde cae la cifra. */
@Composable
private fun Tienes(cantidad: Int) {
    val colores = BitoTema.colores
    val total = stringResource(R.string.freezers_owned_label, cantidad)
    val molde = stringResource(R.string.freezers_owned_label, MARCA)
    val (antes, despues) = molde.split(MARCA.toString()).let { it.first() to it.getOrElse(1) { "" } }
    val estilo = BitoTema.tipos.t16.copy(fontWeight = FontWeight.Bold)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = total },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(antes.trimEnd(), style = estilo, color = colores.tinta)
        Spacer(Modifier.width(HUECO_TIENES))
        CifraRodillo(cantidad, estilo = estilo.cifra().copy(fontWeight = FontWeight.Bold), color = colores.tinta)
        if (despues.isNotBlank()) {
            Spacer(Modifier.width(HUECO_TIENES))
            Text(despues.trimStart(), style = estilo, color = colores.tinta)
        }
    }
}

private const val MARCA = -1

/** La voz que eligió el usuario en Ajustes; Neutra mientras no llega. */
@Composable
private fun vozDeAjustes(): Personality {
    val contexto = LocalContext.current
    val flujo =
        remember(contexto) {
            runCatching { (contexto.applicationContext as BitoApp).container.settings.settings.map { it.personality } }
                .getOrNull() ?: flowOf(Personality.NEUTRA)
        }
    return flujo.collectAsState(Personality.NEUTRA).value
}
