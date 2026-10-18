package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.LuzTienda
import com.alvarotc.bito.ui.tienda.PanelTienda
import com.alvarotc.bito.ui.tienda.PestanaTienda
import com.alvarotc.bito.ui.tienda.TiendaAcciones

/** Acciones que apuntan lo que la pestaña Jardín les pide. */
internal class AccionesJardin : TiendaAcciones {
    val piezas = mutableListOf<String>()
    val especiales = mutableListOf<String>()
    val compras = mutableListOf<String>()
    var quitadas = 0
    var alComprar: (String) -> Unit = {}
    var alQuitar: () -> Unit = {}

    override fun cambiarPestana(p: PestanaTienda) = Unit

    override fun cambiarLuz(l: LuzTienda) = Unit

    override fun tocarPieza(itemId: String) {
        piezas += itemId
    }

    override fun tocarEspecial(logroId: String) {
        especiales += logroId
    }

    override fun quitarPrueba() {
        quitadas++
        alQuitar()
    }

    override fun comprar(itemId: String) {
        compras += itemId
        alComprar(itemId)
    }

    override fun comprarCongelador() = Unit

    override fun deshacerCongelador() = Unit
}

/** El panel del Jardín de verdad, y el contexto que le llega a cada una de sus mitades en cada recomposición. */
internal class PanelEspia : PanelTienda {
    val escenas = mutableListOf<ContextoPanel>()
    val catalogos = mutableListOf<ContextoPanel>()

    @Composable
    override fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        escenas += ctx
        PanelJardin.Escena(ctx, modifier)
    }

    @Composable
    override fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        catalogos += ctx
        PanelJardin.Catalogo(ctx, modifier)
    }
}
