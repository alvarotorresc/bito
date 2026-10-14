package com.alvarotc.bito.ui.sistema

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Los 15 muelles del catálogo §1.5 (`dampingRatio / stiffness`). Con [reducido], el mismo muelle con
 * amortiguamiento 1 (sin rebote). [umbral] es el `visibilityThreshold` (p. ej. `IntOffset.VisibilityThreshold`).
 */
object Muelles {
    val tabla: Map<String, Pair<Float, Float>> =
        mapOf(
            "marcar" to (0.70f to 700f),
            "hoja" to (0.85f to 350f),
            "hundir" to (1.0f to 600f),
            "interruptor" to (0.90f to 600f),
            "indicador" to (0.85f to 500f),
            "planta" to (0.22f to 170f),
            "nodulo" to (0.60f to 520f),
            "camara" to (1.0f to 260f),
            "capa" to (0.90f to 420f),
            "caida" to (0.78f to 300f),
            "luz" to (1.0f to 300f),
            "camino" to (0.92f to 240f),
            "lado" to (1.0f to 380f),
            "fader" to (1.0f to 1200f),
            "reloj" to (1.0f to 200f),
        )

    private fun <T> muelle(
        nombre: String,
        reducido: Boolean,
        umbral: T?,
    ): SpringSpec<T> {
        val (d, k) = tabla.getValue(nombre)
        return spring(dampingRatio = if (reducido) 1f else d, stiffness = k, visibilityThreshold = umbral)
    }

    fun <T> marcar(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("marcar", reducido, umbral)

    fun <T> hoja(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("hoja", reducido, umbral)

    fun <T> hundir(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("hundir", reducido, umbral)

    fun <T> interruptor(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("interruptor", reducido, umbral)

    fun <T> indicador(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("indicador", reducido, umbral)

    fun <T> planta(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("planta", reducido, umbral)

    fun <T> nodulo(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("nodulo", reducido, umbral)

    fun <T> camara(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("camara", reducido, umbral)

    fun <T> capa(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("capa", reducido, umbral)

    fun <T> caida(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("caida", reducido, umbral)

    fun <T> luz(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("luz", reducido, umbral)

    fun <T> camino(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("camino", reducido, umbral)

    fun <T> lado(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("lado", reducido, umbral)

    fun <T> fader(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("fader", reducido, umbral)

    fun <T> reloj(
        reducido: Boolean = false,
        umbral: T? = null,
    ) = muelle("reloj", reducido, umbral)
}

/** Curvas del catálogo §1.6. */
object Curvas {
    val out = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val drawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val io = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    val gota = CubicBezierEasing(0.5f, 0f, 0.2f, 1f)
    val cae = Easing { it * it }
    val lin = LinearEasing
}

/** Duraciones del catálogo §1.6, en milisegundos. Todas declaradas, para que las piezas siguientes no inventen valores. */
object Duraciones {
    const val PULSAR = 140
    const val ESCALA_PULSAR = 0.97f
    const val ESCALA_PULSAR_REDONDO = 0.94f
    const val COLOR = 200
    const val SALIR = 180
    const val CERRAR_HOJA = 180
    const val ENTRAR = 280
    const val REDUCIDO = 150
    const val RODILLO = 240
    const val GOTA = 520
    const val GOTA_RETRASO = 40
    const val SALPICADURA = 300
    const val MONEDA = 620
    const val MONEDA_ESCALON = 80
    const val TIC_SALDO = 160
    const val ESCALA_TIC_SALDO = 0.94f
    const val MAS_N = 900
    const val GASTAR = 520
    const val CAER = 210
    const val GRANOS = 460
    const val GRANOS_ESCALON = 12
    const val POLVO = 420
    const val PASO_HOJA = 240
    const val PASO_HOJA_NUEVO = 200
    const val PASO_HOJA_RETRASO = 60
    const val MES_SALE = 180
    const val MES_ENTRA = 280
    const val RELEVO_SALE = 140
    const val RELEVO_ENTRA = 180
    const val BROTAR_FILA = 340
    const val BORDE_AGUA = 1100
    const val HUNDIR_FILA = 220
    const val PLEGAR_ABRE = 280
    const val PLEGAR_CIERRA = 200
    const val TEMBLOR = 260
    const val TEMBLOR_FUERTE = 320
    const val FRANJA = 1100
    const val RESPIRO = 360
    const val GLOBITO = 2400
    const val AVISO = 4000
    const val FLECHA_LINEA = 280
    const val FLECHA_PUNTA = 110
    const val FLECHA_ESCALON = 90
    const val EMBLEMA = 1400
    const val ANOCHECER = 2400
    const val FILTRACION = 2800
    const val MANTENER = 450
    const val MENU_ACCION = 160
    const val VISTA_PREVIA_MESA = 90
    const val VELO = 0.38f
}

/**
 * True si hay que reducir animaciones (spec §4.3): el ajuste propio o la escala de animaciones del
 * sistema a 0. Sonido y háptica no cambian nunca por esto.
 */
val LocalReducido = compositionLocalOf { false }

fun animacionesDelSistemaApagadas(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** El ajuste o el sistema; el del sistema se vuelve a leer cada vez que la app vuelve a primer plano. */
@Composable
fun rememberReducido(ajuste: Boolean): Boolean {
    val context = LocalContext.current
    var sistema by remember(context) { mutableStateOf(animacionesDelSistemaApagadas(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { sistema = animacionesDelSistemaApagadas(context) }
    return ajuste || sistema
}
