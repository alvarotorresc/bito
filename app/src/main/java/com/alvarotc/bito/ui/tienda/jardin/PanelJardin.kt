package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.PanelTienda

/** La pestaña Jardín: de momento una escena vacía. La escena y el catálogo los trae el carril del jardín. */
object PanelJardin : PanelTienda {
    @Composable
    override fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        Box(modifier.fillMaxSize())
    }

    @Composable
    override fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) = Unit
}
