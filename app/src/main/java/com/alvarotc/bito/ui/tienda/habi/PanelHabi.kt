package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.ui.habi.HabiAtlas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.DestinoMoneda
import com.alvarotc.bito.ui.tienda.EventoTienda
import com.alvarotc.bito.ui.tienda.MedidasArmario
import com.alvarotc.bito.ui.tienda.PanelTienda
import kotlinx.coroutines.launch

// La moneda cae en la sombra de los pies de Habi (`B.gastar(precio, habi.sh)` del mock): al 45 % de la sombra,
// que mide 1132 · 1,05 unidades del lienzo y empieza 0,1 de su ancho por encima de los pies.
private const val SOMBRA_LIENZO = 1132f * 1.05f
private const val SOMBRA_ARRIBA = 0.1f
private const val SOMBRA_ALTO = 0.17f
private const val SOMBRA_PUNTO = 0.45f
private val CENTRO_HABI_Y =
    run {
        val nicho = Medidas.hornacinaArmario.value
        val k = MedidasArmario.habiCuerpo(nicho) / HabiAtlas.BODY_HEIGHT
        val pies = MedidasArmario.nichoArriba.value + MedidasArmario.sitioHabi.yPct / 100f * nicho
        val sombra = SOMBRA_LIENZO * k
        pies - SOMBRA_ARRIBA * sombra + SOMBRA_PUNTO * SOMBRA_ALTO * sombra
    } / MedidasArmario.escenaAlto.value

/** La pestaña Habi: el armario con lo que lleva y lo que se prueba, y la tira de piezas por ranuras. */
object PanelHabi : PanelTienda {
    @Composable
    override fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        var caja by remember { mutableStateOf<Rect?>(null) }
        val cajaViva by rememberUpdatedState(caja)
        val estadoVivo by rememberUpdatedState(ctx.estado)
        val reducido by rememberUpdatedState(BitoTema.reducido)
        // El flujo es compartido y no se espera dentro de `collect`: cada compra celebra en su propia corrutina,
        // que muere con la escena (sin moneda a medias al cambiar de pestaña o salir).
        LaunchedEffect(ctx.eventos) {
            ctx.eventos.collect { evento ->
                if (evento is EventoTienda.Comprado && esDeHabi(evento.itemId)) {
                    launch {
                        celebrarCompra(ctx, { reducido }, { estadoVivo.look }, DestinoMoneda { centroDeHabi(cajaViva) })
                    }
                }
            }
        }
        ArmarioHabi(
            look = ctx.estado.look,
            prueba = ctx.estado.prueba,
            armario = ctx.armario,
            luz = ctx.luz,
            sonido = ctx.sonido,
            tacto = ctx.tacto,
            modifier = modifier.onGloballyPositioned { caja = it.boundsInRoot() },
        )
    }

    @Composable
    override fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        TiraHabi(
            estado = ctx.estado,
            alTocar = ctx.acciones::tocarPieza,
            modifier = modifier,
            ranuraVaciada = rememberRanuraVaciada(ctx.estado.puestos),
        )
    }
}

private fun esDeHabi(itemId: String): Boolean {
    val categoria = HabiCatalog.byId(itemId)?.category
    return categoria != null && categoria != CustomizationCategory.GARDEN_OBJECT
}

private fun centroDeHabi(caja: Rect?): Offset = caja?.let { Offset(it.center.x, it.top + it.height * CENTRO_HABI_Y) } ?: Offset.Zero

/**
 * Lo que pasa al comprar: suena y vibra al soltarse la moneda, vuela del saldo a Habi en 520 ms (el saldo rueda a la vez)
 * y, al llegar, Habi salta con cara alegre (el salto ya lleva su agachada del 12 %) y suena su voz.
 */
private suspend fun celebrarCompra(
    ctx: ContextoPanel,
    reducido: () -> Boolean,
    look: () -> EquippedSet,
    destino: DestinoMoneda,
) {
    ctx.sonido.play(Snd.GASTAR, 1f)
    ctx.tacto.haptica(Haptica.CONFIRM)
    ctx.vuelo.volar(destino)
    ctx.armario.saltar(SaltoCompra.enLienzo(SaltoCompra.amplitud(look(), reducido())))
    ctx.sonido.play(Snd.HABI, 1f)
}
