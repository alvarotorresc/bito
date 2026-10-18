package com.alvarotc.bito.ui.tienda

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.tienda.congeladores.PanelCongeladores
import com.alvarotc.bito.ui.tienda.habi.PanelHabi
import com.alvarotc.bito.ui.tienda.jardin.EscenaJardinEstado
import com.alvarotc.bito.ui.tienda.jardin.PanelJardin
import com.alvarotc.bito.ui.tienda.jardin.escenaJardinDe
import kotlinx.coroutines.flow.Flow

/**
 * Lo que una pestaña ve de la tienda: el estado, las acciones y los eventos, y nada más. No conoce el repositorio.
 * [luz] va de 0 a 1 y se lee en la fase de dibujo.
 */
class ContextoPanel(
    val estado: TiendaUiState,
    val acciones: TiendaAcciones,
    val eventos: Flow<EventoTienda>,
    val luz: () -> Float,
    val vuelo: VueloMoneda,
    val armario: EscenaArmarioEstado,
    val avisos: AvisoEstado,
    val sonido: MotorSonido,
    val tacto: Tacto,
    // La escena del jardín que comparten su escena y su catálogo (el toque en lo tuyo). Sale del vuelo, que la pantalla recuerda.
    val jardin: EscenaJardinEstado = escenaJardinDe(vuelo),
)

/**
 * Lo que da cada pestaña a la pantalla: lo que va en la escena (412 x 300 dp, sin la costra: la pinta la pantalla una vez) y lo
 * que va en el catálogo (desde 404 dp hasta abajo, única zona con scroll).
 */
interface PanelTienda {
    @Composable fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    )

    @Composable fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    )
}

/** El panel de cada pestaña. Cada carril reescribe el suyo. */
fun panelDe(pestana: PestanaTienda): PanelTienda =
    when (pestana) {
        PestanaTienda.HABI -> PanelHabi
        PestanaTienda.JARDIN -> PanelJardin
        PestanaTienda.CONGELADORES -> PanelCongeladores
    }
