package com.alvarotc.bito.ui.sesion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.propuestas.ContextoDescanso
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.sesion.FormatoSesion
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.domain.sesion.Tramos
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.FilaHabito
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.TarjetaHabi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Lo que el descanso necesita de Hoy y que el Inicio le pone: la propuesta de Habi para este descanso, las filas de
 * hábitos de Hoy (las mismas, con su estado) y cómo se marca lo propuesto. Que no haya fila nunca se decide por un
 * estado de carga: sin la fila de ese hábito en Hoy aún, simplemente no se pinta (C1 de la pieza 3).
 */
class ApoyoDescanso(
    val propuesta: (ContextoDescanso) -> Flow<Propuesta?>,
    val tarjetas: () -> List<HabitCardUi>,
    val marcar: (propuestaId: String) -> Unit,
)

val LocalApoyoDescanso = compositionLocalOf<ApoyoDescanso?> { null }

// `.ses-b` del descanso (sin token): 14 dp bajo el reloj, 12 bajo la tarjeta, 6 bajo la fila y 12 sobre los botones.
private val ARRIBA_TARJETA = 14
private val ARRIBA_FILA = 12
private val ARRIBA_DESPUES = 6
private val ARRIBA_BOTONES = 12
private val HUECO_RELOJ = 12
private val ABAJO_DE = 6

/** Lo que el descanso pinta, sacado del motor; se recuerda el último para que el bloque saliente no se quede en blanco. */
private class DatosDescanso(
    val registro: RegistroSesion,
    val instante: Instante,
    val tramo: Tramo.Descanso,
) {
    val siguienteFoco: Tramo.Foco? = Tramos.de(registro.plan).getOrNull(instante.tramo + 1) as? Tramo.Foco
    val hayMasBloques: Boolean get() = tramo.trasBloque < registro.plan.bloques
}

/**
 * El descanso (`.ses-b[data-b=descanso]`, spec §3.3.5, §7.5, §12): el reloj, la tarjeta de Habi con lo que propone, la
 * fila real de Hoy con ese hábito, «Después: …» y «Saltar el descanso». Con el descanso esperando (`esperandoDescanso`),
 * la cifra queda quieta y «Empezar el descanso» es la acción principal. Cada pieza es un hijo directo del bloque.
 */
@Composable
internal fun DescansoContenido() {
    val motor = LocalMotorSesion.current ?: return
    val instante by motor.instante.collectAsState()
    val registro by motor.registro.collectAsState()
    // No es estado: se escribe al componer y un estado así volvería a componer sin fin.
    val ultimo = remember { arrayOfNulls<DatosDescanso>(1) }
    val tramo = instante?.actual as? Tramo.Descanso
    val r = registro
    val i = instante
    if (tramo != null && r != null && i != null) ultimo[0] = DatosDescanso(r, i, tramo)
    val datos = ultimo[0] ?: return
    Piezas(datos)
}

@Composable
private fun Piezas(d: DatosDescanso) {
    val motor = LocalMotorSesion.current ?: return
    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    val gesto = rememberGesto()
    val alcance = rememberCoroutineScope()
    val apoyo = LocalApoyoDescanso.current
    val anclas = LocalAnclas.current
    val saltoHabi = LocalSaltoHabi.current
    val reducido = BitoTema.reducido
    val espera = d.instante.esperandoDescanso

    // Llegada con la sesión en vivo: Habi salta 0,24 (la campanilla y el click son del motor). Al abrir la app en mitad
    // del descanso, o al recuperarla, no: solo si el descanso acaba de empezar.
    LaunchedEffect(Unit) {
        if (d.instante.duracionTramoMillis - d.instante.restaMillis < LLEGADA_VIVA_MS) saltoHabi(SALTO_LLEGADA)
    }
    val resta = if (espera) d.tramo.minutos * MILLIS_MINUTO else d.instante.restaMillis

    // Reloj: la cifra como en Foco y, al lado, «de descanso» o «de descanso largo».
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(HUECO_RELOJ.dp),
    ) {
        Text(
            FormatoSesion.cuenta(resta),
            style = tipos.t48.cifra().copy(letterSpacing = (-0.02).em, lineHeight = 1.08.em),
            color = colores.tinta,
            maxLines = 1,
            modifier = Modifier.testTag("d-t"),
        )
        Text(
            stringResource(if (d.tramo.largo) R.string.ses_de_descanso_largo else R.string.ses_de_descanso),
            style = tipos.t16,
            color = colores.tintaSuave,
            modifier = Modifier.padding(bottom = ABAJO_DE.dp).testTag("d-de"),
        )
    }

    // Habi habla: la propuesta de este descanso, sin botones.
    val contexto =
        remember(d.registro.id, d.tramo.trasBloque, d.tramo.largo, d.tramo.minutos) {
            ContextoDescanso(
                sesionId = d.registro.id,
                bloque = d.tramo.trasBloque,
                largo = d.tramo.largo,
                minutos = d.tramo.minutos,
                excluir = (d.registro.plan.objetivo as? Objetivo.Habito)?.id,
            )
        }
    val flujo = remember(apoyo, contexto) { apoyo?.propuesta?.invoke(contexto) ?: flowOf(null) }
    val propuesta by flujo.collectAsState(initial = null)
    TarjetaHabi(
        propuesta,
        alResponder = {},
        modifier = Modifier.padding(top = ARRIBA_TARJETA.dp).testTag("d-txt"),
        sonidoAlLlegar = false,
    )

    // La fila real de Hoy con el hábito propuesto; sin hábito (sin pendientes), no hay fila.
    val habitoId = (propuesta?.destino as? Destino.Habito)?.id
    val tarjetas = apoyo?.tarjetas?.invoke().orEmpty()
    val card = habitoId?.let { id -> tarjetas.firstOrNull { it.id == id } }
    if (card != null && propuesta != null) {
        val id = propuesta!!.id
        val marcar = {
            if (!card.doneToday) {
                anclas?.gota(ClavesAncla.fila(card.id))
                gesto(Snd.GORGOTEO, Haptica.TICKS)
                alcance.launch {
                    apoyo?.marcar?.invoke(id)
                }
                // Habi salta 0,11 cuando la gota llega a la planta.
                alcance.launch {
                    delay(if (reducido) Duraciones.REDUCIDO.toLong() else (Duraciones.GOTA_RETRASO + Duraciones.GOTA).toLong())
                    saltoHabi(SALTO_MARCA)
                }
            }
        }
        FilaHabito(
            card,
            alCirculo = marcar,
            alPaso = marcar,
            alExacto = {},
            alAbrir = {},
            alRecaer = {},
            modifier = Modifier.padding(top = ARRIBA_FILA.dp).testTag("d-fila"),
        )
    }

    // «Después: …» o, tras el último bloque, que la ronda está hecha.
    val despues =
        if (d.hayMasBloques) {
            stringResource(
                R.string.ses_despues,
                d.siguienteFoco?.minutos ?: d.registro.plan.minutosFoco,
                d.tramo.trasBloque + 1,
                d.registro.plan.bloques,
            )
        } else {
            stringResource(R.string.ses_ronda_hecha, d.tramo.trasBloque)
        }
    Text(
        despues,
        style = tipos.t16.copy(fontFeatureSettings = "tnum", lineHeight = 1.4.em),
        color = colores.tintaSuave,
        modifier = Modifier.padding(top = ARRIBA_DESPUES.dp).testTag("d-luego"),
    )

    // Los mandos: con el descanso esperando, «Saltar» encima como texto y «Empezar el descanso» principal debajo.
    val saltar = {
        gesto(h = Haptica.TICK)
        alcance.launch { motor.saltar() }
        Unit
    }
    BotonTexto(
        stringResource(R.string.ses_saltar_descanso),
        saltar,
        Modifier.fillMaxWidth().padding(top = ARRIBA_BOTONES.dp).testTag("d-saltar"),
    )
    if (espera) {
        Boton(
            stringResource(R.string.ses_empezar_descanso),
            { alcance.launch { motor.empezarDescanso() } },
            Modifier.fillMaxWidth().padding(top = Medidas.e8).testTag("d-empezar"),
            estilo = EstiloBoton.PRINCIPAL,
            alto = Medidas.botonHoja,
            llena = true,
            pildoraMock = true,
        )
    }
}

private const val MILLIS_MINUTO = 60_000L

// Sin token: lo que lleva el descanso en marcha para contarlo como recién llegado, y los saltos de Habi del mock.
private const val LLEGADA_VIVA_MS = 2_000L
private const val SALTO_LLEGADA = 0.24f
private const val SALTO_MARCA = 0.11f
