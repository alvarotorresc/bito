package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.EscenaArmario
import com.alvarotc.bito.ui.tienda.PanelTienda

/** La pestaña Habi: el armario con lo que lleva y lo que se prueba. El catálogo lo trae el carril de Habi. */
object PanelHabi : PanelTienda {
    @Composable
    override fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        EscenaArmario(look = ctx.estado.look, luz = ctx.luz, estado = ctx.armario, modifier = modifier, conCostra = false)
    }

    @Composable
    override fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) = Unit
}
