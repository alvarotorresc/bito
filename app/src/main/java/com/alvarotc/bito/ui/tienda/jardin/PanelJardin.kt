package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.alvarotc.bito.domain.GardenEngine
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.DestinoMoneda
import com.alvarotc.bito.ui.tienda.EventoTienda
import com.alvarotc.bito.ui.tienda.PanelTienda
import kotlinx.coroutines.launch

/** La pestaña Jardín: el jardín de Hoy con su cámara y lo que se prueba, y el catálogo de objetos y especiales. */
object PanelJardin : PanelTienda {
    @Composable
    override fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        val estado = ctx.jardin
        val gesto = rememberGesto()
        val reducido by rememberUpdatedState(BitoTema.reducido)
        val gestoVivo by rememberUpdatedState(gesto)
        val ctxVivo by rememberUpdatedState(ctx)
        val alcance = rememberCoroutineScope()
        // Una compra de un objeto de jardín: GASTAR y CONFIRM ya, la moneda del saldo al objeto y el meceo al llegar. Cada compra
        // va en su propia corrutina del alcance de la escena, nunca dentro del collect; salir de la pestaña la cancela.
        LaunchedEffect(ctx.eventos) {
            ctx.eventos.collect { evento ->
                val clave = (evento as? EventoTienda.Comprado)?.let { GardenEngine.objectKeysOf(listOf(it.itemId)).firstOrNull() }
                if (clave != null) {
                    alcance.launch {
                        val espera = (if (reducido) Duraciones.REDUCIDO else Duraciones.GASTAR).toLong()
                        gestoVivo(Snd.GASTAR, Haptica.CONFIRM)
                        estado.comprado(clave, espera)
                        estado.destinoMoneda(clave)?.let { destino -> ctxVivo.vuelo.volar(DestinoMoneda { destino }) }
                    }
                }
            }
        }
        EscenaJardin(
            jardin = ctx.estado.jardin,
            puestos = ctx.estado.puestos,
            voz = ctx.estado.voz,
            prueba = ctx.estado.prueba,
            luz = ctx.luz,
            estado = estado,
            modifier = modifier,
            alTocar = { clave -> tocarElementoJardin(ctxVivo, gesto, clave) },
        )
    }

    @Composable
    override fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) = CatalogoJardin(ctx, modifier)
}
