package com.alvarotc.bito.ui.tienda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.alvarotc.bito.domain.model.EquippedSet
import java.time.LocalDate

/** Un estado de tienda de mentira, completo, para los tests de pantalla. */
internal fun estadoTienda(
    pestana: PestanaTienda = PestanaTienda.HABI,
    luz: LuzTienda = LuzTienda.DIA,
    prueba: PruebaTienda? = null,
    barra: BarraPrueba? = null,
    saldo: Int = 140,
    cargado: Boolean = true,
) = TiendaUiState(
    saldo = saldo,
    pestana = pestana,
    luz = luz,
    prueba = prueba,
    barra = barra,
    puestos = EquippedSet(),
    look = EquippedSet(),
    poseidos = emptySet(),
    objetosJardin = emptySet(),
    congeladores = 0,
    mejorRacha = 0,
    hoy = LocalDate.of(2025, 10, 17),
    cargado = cargado,
)

/** Acciones que solo apuntan lo que se les pide. */
internal class AccionesGrabadas : TiendaAcciones {
    val pestanas = mutableListOf<PestanaTienda>()
    val luces = mutableListOf<LuzTienda>()
    val compras = mutableListOf<String>()
    var quitadas = 0

    override fun cambiarPestana(p: PestanaTienda) {
        pestanas += p
    }

    override fun cambiarLuz(l: LuzTienda) {
        luces += l
    }

    override fun tocarPieza(itemId: String) = Unit

    override fun tocarEspecial(logroId: String) = Unit

    override fun quitarPrueba() {
        quitadas++
    }

    override fun comprar(itemId: String) {
        compras += itemId
    }

    override fun comprarCongelador() = Unit

    override fun deshacerCongelador() = Unit
}

/** Un panel que solo pinta su escena y su catálogo con un tag propio, y apunta cuántas veces se le pidió cada uno. */
internal class PanelFalso(
    val nombre: String,
    private val alMostrar: suspend (ContextoPanel) -> Unit = {},
) : PanelTienda {
    val escenas = mutableListOf<ContextoPanel>()
    val catalogos = mutableListOf<ContextoPanel>()

    @Composable
    override fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        escenas += ctx
        Box(modifier.fillMaxSize().testTag("escena-$nombre"))
    }

    @Composable
    override fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        catalogos += ctx
        LaunchedEffect(Unit) { alMostrar(ctx) }
        Box(modifier.fillMaxSize().testTag("catalogo-$nombre"))
    }
}
