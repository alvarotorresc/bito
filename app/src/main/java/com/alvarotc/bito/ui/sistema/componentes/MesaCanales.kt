package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.LocalTacto
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Canal
import com.alvarotc.bito.ui.sistema.sonido.LocalSonido
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class CanalMesa { HABI, EFECTOS, AMBIENTE, VIBRACION }

private const val NIVEL_MAX = 10
private val ALTO_RIEL = 160.dp
private val ANCHO_RIEL = 44.dp

/**
 * Vista previa con plazo: la primera suena ya y abre un plazo; lo que llegue durante el plazo sustituye
 * al pendiente, que suena al cerrarse. Como mucho una cada [Duraciones.VISTA_PREVIA_MESA] ms.
 */
private class VistaPreviaContenida(
    private val scope: CoroutineScope,
    private val sonar: (CanalMesa, Int) -> Unit,
) {
    private var plazo: Job? = null
    private var pendiente: Pair<CanalMesa, Int>? = null

    fun pedir(
        canal: CanalMesa,
        nivel: Int,
    ) {
        if (plazo?.isActive == true) {
            pendiente = canal to nivel
            return
        }
        sonar(canal, nivel)
        plazo =
            scope.launch {
                delay(Duraciones.VISTA_PREVIA_MESA.toLong())
                while (true) {
                    val p = pendiente ?: break
                    pendiente = null
                    sonar(p.first, p.second)
                    delay(Duraciones.VISTA_PREVIA_MESA.toLong())
                }
            }
    }
}

/** 3.5: cuatro rieles verticales; el nivel (0–10) es agua; vista previa cada 90 ms como mucho. [todoApagado] = «Sonido» apagado (35 %, sin respuesta). */
@Composable
fun MesaCanales(
    niveles: Map<CanalMesa, Int>,
    alCambiar: (CanalMesa, Int) -> Unit,
    modifier: Modifier = Modifier,
    apagados: Set<CanalMesa> = emptySet(),
    todoApagado: Boolean = false,
) {
    val gesto = rememberGesto()
    val sonido = LocalSonido.current
    val tacto = LocalTacto.current
    val scope = rememberCoroutineScope()
    val vista =
        remember(scope, sonido, tacto) {
            VistaPreviaContenida(scope) { canal, nivel ->
                when (canal) {
                    CanalMesa.HABI -> sonido.probar(Canal.HABI, nivel)
                    CanalMesa.EFECTOS -> sonido.probar(Canal.EFECTOS, nivel)
                    CanalMesa.AMBIENTE -> sonido.probar(Canal.AMBIENTE, nivel)
                    CanalMesa.VIBRACION -> tacto.probar(nivel)
                }
            }
        }
    val cambiar by rememberUpdatedState(alCambiar)

    Row(
        modifier.alpha(if (todoApagado) 0.35f else 1f),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        CanalMesa.entries.forEach { canal ->
            val nivel = (niveles[canal] ?: 0).coerceIn(0, NIVEL_MAX)
            val deshabilitado = todoApagado || canal in apagados
            Riel(
                canal = canal,
                nivel = nivel,
                habilitado = !deshabilitado,
                alPoner = { nuevo ->
                    val n = nuevo.coerceIn(0, NIVEL_MAX)
                    if (n != nivel) {
                        cambiar(canal, n)
                        gesto(h = Haptica.SEG)
                        vista.pedir(canal, n)
                    }
                },
                modifier = Modifier.alpha(if (canal in apagados && !todoApagado) 0.35f else 1f),
            )
        }
    }
}

@Composable
private fun Riel(
    canal: CanalMesa,
    nivel: Int,
    habilitado: Boolean,
    alPoner: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val nombre =
        stringResource(
            when (canal) {
                CanalMesa.HABI -> R.string.sistema_b5b_canal_habi
                CanalMesa.EFECTOS -> R.string.sistema_b5b_canal_efectos
                CanalMesa.AMBIENTE -> R.string.sistema_b5b_canal_ambiente
                CanalMesa.VIBRACION -> R.string.sistema_b5b_canal_vibracion
            },
        )
    val fraccion by animateFloatAsState(nivel / NIVEL_MAX.toFloat(), Muelles.fader(reducido), label = "riel")
    val poner by rememberUpdatedState(alPoner)
    val nivelActual by rememberUpdatedState(nivel)

    fun nivelDe(
        y: Float,
        alto: Int,
    ) = ((1f - (y / alto).coerceIn(0f, 1f)) * NIVEL_MAX).roundToInt()

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CifraRodillo(nivel, estilo = BitoTema.tipos.t24.fuerte(), color = colores.tinta)
        var riel =
            Modifier
                .width(ANCHO_RIEL)
                .height(ALTO_RIEL)
                .testTag("riel-${canal.name}")
                .clip(BitoFormas.canto)
                .border(Medidas.linea, colores.contorno, BitoFormas.canto)
                .drawBehind {
                    drawRect(colores.campo)
                    val alto = size.height * fraccion
                    drawRect(colores.agua, Offset(0f, size.height - alto), Size(size.width, alto))
                    val tirador = 10.dp.toPx()
                    drawRoundRect(
                        colores.aguaHonda,
                        Offset(4.dp.toPx(), (size.height - alto - tirador / 2f).coerceIn(0f, size.height - tirador)),
                        Size(size.width - 8.dp.toPx(), tirador),
                        CornerRadius(tirador / 2f),
                    )
                }.semantics {
                    contentDescription = nombre
                    progressBarRangeInfo = ProgressBarRangeInfo(nivel.toFloat(), 0f..NIVEL_MAX.toFloat(), steps = NIVEL_MAX - 1)
                    if (habilitado) {
                        setProgress { valor ->
                            poner(valor.roundToInt())
                            true
                        }
                    } else {
                        disabled()
                    }
                }
        if (habilitado) {
            riel =
                riel
                    .pointerInput(Unit) { detectTapGestures { poner(nivelDe(it.y, size.height)) } }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { poner(nivelDe(it.y, size.height)) },
                            onVerticalDrag = { cambio, _ -> poner(nivelDe(cambio.position.y, size.height)) },
                        )
                    }.onKeyEvent { evento ->
                        if (evento.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (evento.key) {
                                Key.DirectionUp -> poner(nivelActual + 1).let { true }
                                Key.DirectionDown -> poner(nivelActual - 1).let { true }
                                else -> false
                            }
                        }
                    }.focusable()
        }
        Spacer(riel)
        Text(
            nombre,
            Modifier.width(ANCHO_RIEL + 20.dp),
            style = BitoTema.tipos.t14.fuerte(),
            color = colores.tinta,
            textAlign = TextAlign.Center,
        )
    }
}
