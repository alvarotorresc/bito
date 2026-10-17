package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.GardenEngine
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.garden.GardenArt
import com.alvarotc.bito.ui.garden.GardenScene
import com.alvarotc.bito.ui.garden.ObjetoJardin
import com.alvarotc.bito.ui.garden.SceneItem
import com.alvarotc.bito.ui.garden.SceneRect
import com.alvarotc.bito.ui.garden.sceneItems
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.tienda.LuzArmario
import com.alvarotc.bito.ui.tienda.PruebaTienda
import com.alvarotc.bito.ui.tienda.VueloMoneda
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Las medidas de la escena del jardín de la tienda (`.tj-cielo` de tienda-jardin.css y `tocarItem` de tienda-jardin.js). Sin token. */
internal object MedidasEscenaJardin {
    // El cielo que se funde bajo la cabecera: 104 dp, de día rgba(207,226,216,.94) a 0 y de noche rgba(38,48,84,.92) a 0.
    val CIELO_ALTO = 104.dp
    val CIELO_DIA = Color(red = 207, green = 226, blue = 216, alpha = 240)
    val CIELO_NOCHE = Color(red = 38, green = 48, blue = 84, alpha = 235)

    // La noche del cielo y del farolillo: `smooth(1.2, 1.9, v)` del mock, con la luz de 0 a 2 (aquí de 0 a 1).
    const val NOCHE_DESDE = 0.6f
    const val NOCHE_HASTA = 0.95f

    // El tinte de noche del jardín entra de la Tarde (0,5) a la Noche (1), como el tramo final de `LUZ_A`/`LUZ_B`.
    const val TINTE_DESDE = 0.5f

    // Lo ya tuyo que se toca se mece a los 260 ms, cuando la cámara ya va de camino.
    const val MECER_TUYO_MS = 260L
}

/** Cuánto de noche tiene el cielo con la luz [luz] (0 a 1). */
internal fun nocheDelCielo(luz: Float): Float {
    val q = ((luz - MedidasEscenaJardin.NOCHE_DESDE) / (MedidasEscenaJardin.NOCHE_HASTA - MedidasEscenaJardin.NOCHE_DESDE)).coerceIn(0f, 1f)
    return q * q * (3f - 2f * q)
}

/** El tinte de noche que se multiplica sobre el jardín con la luz [luz]: 0 hasta la Tarde y 1 de Noche. */
internal fun tinteDeNoche(luz: Float): Float =
    ((luz - MedidasEscenaJardin.TINTE_DESDE) / (1f - MedidasEscenaJardin.TINTE_DESDE)).coerceIn(0f, 1f)

/** Lo que las pruebas miran de la escena sin cambiar su firma. */
internal class SondaEscenaJardin {
    var recomposiciones = 0
    var cieloNoche: Float = 0f
    var pintados: List<String> = emptyList()
}

/**
 * Un toque en algo que ya es tuyo (o una compra): [vez] cuenta los pedidos para que el mismo objeto se mezca otra vez,
 * [retrasoMs] es lo que tarda en mecerse y [enfoque] lo que estaba a prueba al pedirlo, para distinguir la prueba que se
 * va de una prueba nueva.
 */
internal class PedidoTuyo(val clave: String, val vez: Int, val retrasoMs: Long, val enfoque: String?)

/**
 * Lo que la escena del jardín guarda entre fotogramas: la cámara, la caída y el meceo. El catálogo de la pestaña lo
 * comparte con la escena para pedir [tocarTuyo].
 */
@Stable
class EscenaJardinEstado {
    val camara = CamaraJardin()
    val caida = CaidaObjeto()
    internal var sonda: SondaEscenaJardin? = null
    internal var pedido by mutableStateOf<PedidoTuyo?>(null)
    private var veces = 0
    internal var mecidas = 0

    // Lo que la escena tiene a prueba ahora (su clave de arte) y adónde llega una moneda a [clave], en píxeles de la raíz.
    internal var enfoqueActual: String? = null
    internal var destinoDe: (String) -> Offset? = { null }

    /** Algo ya tuyo (por su clave de arte, «banco» o «farolillo»): la cámara va a su sitio y se mece a los 260 ms. No abre barra. */
    fun tocarTuyo(clave: String) {
        pedido = PedidoTuyo(clave, ++veces, MedidasEscenaJardin.MECER_TUYO_MS, enfoqueActual)
    }

    /** Se acaba de comprar [clave]: la cámara se queda en ella y se mece al cabo de [retrasoMs] (lo que tarda la moneda). */
    fun comprado(
        clave: String,
        retrasoMs: Long,
    ) {
        pedido = PedidoTuyo(clave, ++veces, retrasoMs, enfoqueActual)
    }

    /** Adónde llega la moneda que paga [clave] (centro del objeto, con la cámara ya en su sitio), o null si la escena no lo pinta. */
    fun destinoMoneda(clave: String): Offset? = destinoDe(clave)
}

@Composable
fun rememberEscenaJardinEstado(): EscenaJardinEstado = remember { EscenaJardinEstado() }

/**
 * La escena de la pestaña Jardín: el jardín de Hoy (la misma `GardenArt`) en 412 x 300 dp, con su Habi con lo que
 * lleva puesto (la prueba solo la lleva el Habi del armario), el cielo que se funde bajo la cabecera y la cámara. Lo
 * que se prueba ([prueba]) cae en su sitio y la cámara va a él; al quitarla desaparece sin animación y la cámara vuelve
 * al jardín entero. La luz es la del selector ([luz], leída al dibujar), nunca la del reloj. Sin costra: la pinta la pantalla.
 */
@Composable
fun EscenaJardin(
    jardin: GardenState,
    puestos: EquippedSet,
    voz: Personality,
    prueba: PruebaTienda?,
    luz: () -> Float,
    estado: EscenaJardinEstado,
    modifier: Modifier = Modifier,
    // Habi con sus gestos propios (parpadeo, mirada); las pruebas de píxeles lo paran.
    animated: Boolean = true,
    // Un toque en un objeto o un especial de la escena (por su clave de arte): lo mismo que tocarlo en el catálogo.
    alTocar: ((String) -> Unit)? = null,
) {
    val reducido = BitoTema.reducido
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    estado.caida.densidad = LocalDensity.current.density
    val margen = with(LocalDensity.current) { MedidasCamara.MARGEN.toPx() }
    val libreArriba = with(LocalDensity.current) { MedidasCamara.LIBRE_ARRIBA.toPx() }

    val especialPrueba = (prueba as? PruebaTienda.Especial)?.let { p -> GardenSpecial.entries.firstOrNull { it.badgeId == p.logroId } }
    val objetoPrueba = (prueba as? PruebaTienda.Objeto)?.let { GardenEngine.objectKeysOf(listOf(it.itemId)).firstOrNull() }
    // Lo que ya está en el jardín no se repite ni cae: solo se enfoca.
    val especialNuevo = especialPrueba?.takeIf { it !in jardin.specials }
    val objetoNuevo = objetoPrueba?.takeIf { it !in jardin.objects }
    val claveCae = objetoNuevo ?: especialNuevo?.key
    val claveEnfoque = objetoPrueba ?: especialPrueba?.key
    // La luz del jardín de la tienda es la del selector: la del reloj se queda en el día.
    val pintado =
        remember(jardin, especialNuevo) {
            jardin.copy(
                light = GardenState.INITIAL.light,
                specials = if (especialNuevo != null) jardin.specials + especialNuevo else jardin.specials,
            )
        }
    val objetos = remember(objetoNuevo) { listOfNotNull(objetoNuevo?.let { ObjetoJardin(it, efimero = true) }) }
    val spec = remember(voz, puestos) { HabiSpec(Mood.NORMAL, voz, puestos) }
    val pose = remember(estado) { { clave: String -> estado.caida.pose(clave) } }
    val luzActual = luz
    val noche = remember(luzActual) { { tinteDeNoche(luzActual()) } }
    // El tinte multiplicado de Día, Tarde y Noche (`LUZ_A`/`LUZ_B`): una sola vez; la noche solo enciende el farolillo.
    val tinte = remember(luzActual) { { LuzArmario.arriba(luzActual()) to LuzArmario.abajo(luzActual()) } }
    var origenRaiz by remember { mutableStateOf(Offset.Zero) }

    SideEffect {
        estado.enfoqueActual = claveEnfoque
        if (claveCae == null) estado.caida.quitar()
        estado.sonda?.let { s ->
            s.recomposiciones++
            s.pintados = pintado.specials.map { it.key } + pintado.objects + objetos.map { it.clave }
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().clipToBounds().onGloballyPositioned { origenRaiz = it.positionInRoot() }.testTag("tj-escena"),
    ) {
        val vistaW = constraints.maxWidth.toFloat()
        val vistaH = constraints.maxHeight.toFloat()
        val escenaW = vistaW
        val escenaH = vistaW / GardenArt.SCENE_ASPECT
        val items = remember(pintado, objetos, escenaW, escenaH) { sceneItems(pintado, escenaW, escenaH, objetos) }

        fun rectDe(clave: String): SceneRect? =
            items.firstOrNull {
                (it is SceneItem.Objeto && it.clave == clave) || (it is SceneItem.Special && it.special.key == clave)
            }?.rect

        fun encuadrePara(clave: String?): Encuadre {
            val r = clave?.let(::rectDe) ?: return encuadreEntero(escenaW, escenaH, vistaW, vistaH)
            return encuadreDe(cajaConSombra(r), escenaW, escenaH, vistaW, vistaH, margen, libreArriba)
        }

        // Adónde llega una moneda: el centro del objeto con la cámara en su sitio, en la raíz.
        SideEffect {
            estado.destinoDe = { clave ->
                rectDe(clave)?.let { r ->
                    val e = encuadrePara(clave)
                    origenRaiz + Offset((r.left + r.width / 2f) * e.escala + e.tx, (r.top + r.height / 2f) * e.escala + e.ty)
                }
            }
        }

        // La primera vez la cámara se pone donde toca sin moverse; después va con el muelle `camara`.
        val colocada = remember { booleanArrayOf(false) }
        val pedido = estado.pedido
        LaunchedEffect(claveEnfoque, pedido, vistaW, vistaH) {
            // Una prueba nueva, posterior al toque, lo deja atrás. La que ya estaba al pedirlo (o la del propio objeto) se
            // está yendo: el pedido sigue en pie y el estado la quita cuando llegue.
            if (pedido != null && claveEnfoque != null && claveEnfoque != pedido.enfoque && claveEnfoque != pedido.clave) {
                estado.pedido = null
                return@LaunchedEffect
            }
            val foco = pedido?.clave ?: claveEnfoque
            val destino = encuadrePara(foco)
            if (!colocada[0]) {
                colocada[0] = true
                estado.camara.fijar(destino)
            } else {
                launch { estado.camara.ir(destino, reducido) }
            }
            if (pedido != null && pedido.vez > estado.mecidas) {
                if (!reducido) delay(pedido.retrasoMs)
                estado.mecidas = pedido.vez
                estado.caida.mecer(pedido.clave, reducido, gesto)
            }
        }
        // La caída: solo lo que no está ya en el jardín. Cambiar de objeto a mitad la empieza de nuevo.
        LaunchedEffect(claveCae, escenaW, escenaH) {
            val clave = claveCae ?: return@LaunchedEffect
            val r = rectDe(clave) ?: return@LaunchedEffect
            estado.caida.caer(clave, Offset(r.left + r.width / 2f, r.top + r.height), reducido, gesto)
        }

        Box(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .camaraJardin(estado.camara),
        ) {
            GardenScene(
                garden = pintado,
                spec = spec,
                modifier = Modifier.fillMaxWidth(),
                animated = animated,
                forma = RectangleShape,
                noche = noche,
                objetos = objetos,
                poseObjeto = pose,
                tinteLuz = tinte,
                alTocarElemento = alTocar,
            )
            Canvas(Modifier.matchParentSize()) { with(estado.caida) { pintarGranos(colores.tMedia2) } }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(MedidasEscenaJardin.CIELO_ALTO)
                .testTag("tj-cielo")
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(MedidasEscenaJardin.CIELO_DIA, MedidasEscenaJardin.CIELO_DIA.copy(alpha = 0f))))
                    val n = nocheDelCielo(luzActual())
                    estado.sonda?.cieloNoche = n
                    if (n > 0f) {
                        val c = MedidasEscenaJardin.CIELO_NOCHE
                        drawRect(Brush.verticalGradient(listOf(c, c.copy(alpha = 0f))), alpha = n)
                    }
                },
        )
    }
}

private val escenasPorPantalla = java.util.WeakHashMap<VueloMoneda, EscenaJardinEstado>()

/**
 * La escena de jardín de una pantalla de tienda. La pantalla recuerda su [vuelo] pero rehace el contexto de los paneles
 * en cada recomposición: atar la escena al vuelo la hace sobrevivir, y compartir la misma entre la escena y el catálogo.
 */
fun escenaJardinDe(vuelo: VueloMoneda): EscenaJardinEstado =
    synchronized(escenasPorPantalla) { escenasPorPantalla.getOrPut(vuelo) { EscenaJardinEstado() } }
