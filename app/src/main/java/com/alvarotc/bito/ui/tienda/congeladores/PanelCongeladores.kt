package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.EscenaArmario
import com.alvarotc.bito.ui.tienda.PanelTienda

/** La pestaña Congeladores: el armario sin prueba. El catálogo lo trae el carril de congeladores. */
object PanelCongeladores : PanelTienda {
    @Composable
    override fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        EscenaArmario(look = ctx.estado.puestos, luz = ctx.luz, estado = ctx.armario, modifier = modifier)
    }

    @Composable
    override fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) = Unit
}
