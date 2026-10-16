package com.alvarotc.bito.ui.sesion

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.sesion.MotorSesion
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import kotlinx.coroutines.launch

// `.ses-sub`: 6 dp arriba. `.btnrow`: 12 dp arriba; el fantasma de `.ses-centro` mide 40 dp (toque 44).
private val ARRIBA_CUENTA = 6.dp
private val ARRIBA_SALTAR = 12.dp
private val ALTO_SALTAR = 40.dp

/** Los ciclos de Calmarme con los que se entra (60 s). */
private const val CICLOS_ENTRAR = 6

/**
 * Respirar para entrar (`data-b="entrar"`, spec §3.3.3 y §7.3): la fase con sus puntitos, «{ciclo} de 6» y «Saltar».
 * El llenado de Habi y la fase salen del reloj ([elapsed]) y del ancla del registro, por fotograma: no hay un contador
 * propio. [respiracion] recibe la lectura del llenado (la escena la lee al dibujar) y se suelta al irse el bloque, que
 * es cuando Habi abre los ojos. La pantalla queda encendida mientras dura. La háptica por fase la hace el aire.
 */
@Composable
fun RespirarEntrarContenido(
    instante: Instante,
    anclaElapsed: Long,
    alSaltar: () -> Unit,
    respiracion: MutableState<(() -> Float)?>?,
    modifier: Modifier = Modifier,
    elapsed: () -> Long = SystemClock::elapsedRealtime,
) {
    val ciclos = (instante.actual as? Tramo.Respirar)?.ciclos ?: CICLOS_ENTRAR

    fun puntoAhora() = BreathingRhythm.at(BreathingMode.CALM, (elapsed() - anclaElapsed).coerceAtLeast(0L), ciclos)

    val llenado = remember { mutableFloatStateOf(instante.respiracion?.fill ?: puntoAhora().fill) }
    var punto by remember { mutableStateOf(instante.respiracion ?: puntoAhora()) }
    LaunchedEffect(anclaElapsed, ciclos) {
        while (true) {
            withFrameNanos { }
            val p = puntoAhora()
            llenado.floatValue = p.fill
            // Solo se recompone al cambiar de fase o de ciclo; el llenado se lee al dibujar.
            if (p.stepIndex != punto.stepIndex || p.finished != punto.finished) punto = p
            if (p.finished) break
        }
    }
    DisposableEffect(respiracion) {
        respiracion?.value = { llenado.floatValue }
        onDispose { respiracion?.value = null }
    }
    PantallaEncendida()

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        FaseRespiracion(punto, punto.cycle, ciclos, mostrarPuntitos = true)
        Spacer(Modifier.height(ARRIBA_CUENTA))
        Text(
            stringResource(R.string.ses_x_de_y, punto.cycle, ciclos),
            Modifier.fillMaxWidth().testTag("e-cuenta"),
            style = BitoTema.tipos.t16,
            color = BitoTema.colores.tintaSuave,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ARRIBA_SALTAR))
        Boton(
            stringResource(R.string.ses_saltar),
            alSaltar,
            Modifier.testTag("e-saltar"),
            estilo = EstiloBoton.FANTASMA,
            alto = ALTO_SALTAR,
            pildoraMock = true,
        )
    }
}

/**
 * El bloque con los datos de verdad: el motor, el ancla del registro y el llenado que lee la escena. Nada más: la
 * música y Atrás los pone la cabecera del Inicio.
 */
@Composable
internal fun RespirarEntrarBloque() {
    val motor = LocalMotorSesion.current ?: return
    val instante by motor.instante.collectAsStateWithLifecycle()
    val registro by motor.registro.collectAsStateWithLifecycle()
    val i = instante ?: return
    val r = registro ?: return
    RespirarEntrarAcciones(motor, i, r.anclaElapsed)
}

@Composable
private fun RespirarEntrarAcciones(
    motor: MotorSesion,
    instante: Instante,
    anclaElapsed: Long,
) {
    val scope = rememberCoroutineScope()
    RespirarEntrarContenido(
        instante,
        anclaElapsed,
        alSaltar = { scope.launch { motor.saltar() } },
        respiracion = LocalRespiracionJardin.current,
    )
}

/** `FLAG_KEEP_SCREEN_ON` mientras está en la composición, solo en Respirar: el foco no deja la pantalla encendida (§21.7). */
@Composable
private fun PantallaEncendida() {
    val actividad = LocalContext.current.actividadDe()
    DisposableEffect(actividad) {
        actividad?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { actividad?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

internal fun Context.actividadDe(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
