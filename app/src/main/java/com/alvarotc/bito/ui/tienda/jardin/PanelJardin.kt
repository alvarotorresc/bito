package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.PanelTienda

/** La pestaña Jardín: el jardín de Hoy con su cámara y lo que se prueba. El catálogo llega con la tarea siguiente del carril. */
object PanelJardin : PanelTienda {
    @Composable
    override fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        val estado = rememberEscenaJardinEstado()
        EscenaJardin(
            jardin = ctx.estado.jardin,
            puestos = ctx.estado.puestos,
            voz = ctx.estado.voz,
            prueba = ctx.estado.prueba,
            luz = ctx.luz,
            estado = estado,
            modifier = modifier,
        )
    }

    @Composable
    override fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) = Unit
}
