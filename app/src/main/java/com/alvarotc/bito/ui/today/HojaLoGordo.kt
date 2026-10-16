package com.alvarotc.bito.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.CandidatoGordo
import com.alvarotc.bito.domain.habla.LoGordo
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.SubtituloGordo
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Aro
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

private const val TEMBLOR_MS = 260
private val TEMBLOR_IZQUIERDA = (-5).dp
private val TEMBLOR_DERECHA = 4.dp
private const val FILA_APAGADA = 0.45f

/**
 * «¿Qué es lo gordo de hoy?» (spec §7.2, D13). Una fila por candidato con el aro de agua; al abrir queda marcado
 * lo que ya se eligió, o si no la [sugerencia]. Hasta tres: la cuarta no entra, la nota tiembla y vibra sin sonido.
 * «Guardar» entrega las referencias en el orden en que se marcaron y avisa con [alAvisar] de la lista elegida.
 */
@Composable
fun HojaLoGordo(
    abierta: Boolean,
    candidatos: List<CandidatoGordo>,
    sugerencia: RefGordo?,
    elegidos: List<RefGordo>,
    alGuardar: (List<RefGordo>) -> Unit,
    alCerrar: () -> Unit,
    alAvisar: (String) -> Unit = {},
) {
    val gesto = rememberGesto()
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido

    val posibles = candidatos.map { it.ref }.toSet()
    val semilla = (elegidos.filter { it in posibles }.ifEmpty { listOfNotNull(sugerencia).filter { it in posibles } }).take(LoGordo.MAXIMO)

    // Al abrir se siembra lo elegido o la sugerencia, y se vuelve a sembrar si llegan datos más frescos mientras el
    // usuario no haya tocado nada (Hoy aún cargando o viejo al volver). Mientras se cierra conserva lo que había.
    var seleccion by remember { mutableStateOf(semilla) }
    var tocada by remember { mutableStateOf(false) }
    LaunchedEffect(abierta, semilla) {
        if (!abierta) {
            tocada = false
        } else if (!tocada) {
            seleccion = semilla
        }
    }
    var rechazos by remember { mutableIntStateOf(0) }
    var rechazada by remember { mutableStateOf(false) }
    val temblor = remember { Animatable(0f) }
    LaunchedEffect(rechazos) {
        if (rechazos > 0 && !reducido) {
            temblor.animateTo(
                0f,
                keyframes {
                    durationMillis = TEMBLOR_MS
                    TEMBLOR_IZQUIERDA.value at TEMBLOR_MS / 3 using Curvas.out
                    TEMBLOR_DERECHA.value at 2 * TEMBLOR_MS / 3 using Curvas.out
                    0f at TEMBLOR_MS using Curvas.out
                },
            )
        }
    }

    val nombreDe = { ref: RefGordo -> candidatos.firstOrNull { it.ref == ref }?.nombre.orEmpty() }
    val aviso = stringResource(R.string.habla_gordo_aviso, seleccion.joinToString(", ", transform = nombreDe))
    val notaTexto =
        when {
            rechazada -> stringResource(R.string.habla_gordo_cuarta)
            seleccion.isEmpty() -> stringResource(R.string.habla_gordo_nota_0)
            seleccion.size >= LoGordo.MAXIMO -> stringResource(R.string.habla_gordo_nota_3)
            else -> stringResource(R.string.habla_gordo_nota_n, seleccion.size)
        }

    Hoja(abierta, alCerrar, Modifier.testTag("hoja-gordo"), titulo = stringResource(R.string.habla_gordo_titulo)) {
        Text(stringResource(R.string.habla_gordo_desc), style = BitoTema.tipos.t14, color = colores.tintaSuave)
        Column(Modifier.padding(top = Medidas.e8)) {
            candidatos.forEach { c ->
                val marcada = c.ref in seleccion
                Row(
                    Modifier
                        .testTag("gordo-${c.ref.id}")
                        .fillMaxWidth()
                        .heightIn(min = Medidas.toqueMinimo)
                        .alpha(if (!marcada && seleccion.size >= LoGordo.MAXIMO) FILA_APAGADA else 1f)
                        .toggleable(value = marcada, role = Role.Checkbox) {
                            tocada = true
                            rechazada = false
                            when {
                                marcada -> {
                                    gesto(Snd.VACIAR, Haptica.LOW)
                                    seleccion = seleccion - c.ref
                                }
                                seleccion.size >= LoGordo.MAXIMO -> {
                                    gesto(h = Haptica.REJECT)
                                    rechazada = true
                                    rechazos++
                                }
                                else -> {
                                    gesto(Snd.TIC, Haptica.SEG)
                                    seleccion = seleccion + c.ref
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
                ) {
                    Aro(marcada)
                    Column(Modifier.padding(vertical = Medidas.e8)) {
                        Text(c.nombre, style = BitoTema.tipos.t16.fuerte(), color = colores.tinta)
                        Text(subtituloTexto(c.subtitulo), style = BitoTema.tipos.t14, color = colores.tintaSuave)
                    }
                }
            }
        }
        Text(
            notaTexto,
            Modifier
                .testTag("hoja-gordo-nota")
                .padding(top = Medidas.e12)
                .offset(x = temblor.value.dp),
            style = if (rechazada) BitoTema.tipos.t14.fuerte() else BitoTema.tipos.t14,
            color = if (rechazada) colores.tinta else colores.tintaSuave,
        )
        BotonTexto(
            stringResource(R.string.habla_ahora_no),
            { cerrar() },
            Modifier.fillMaxWidth().testTag("hoja-gordo-ahora-no"),
        )
        Boton(
            stringResource(R.string.habla_gordo_guardar),
            {
                val refs = seleccion
                gesto(h = Haptica.CONFIRM)
                alGuardar(refs)
                alAvisar(aviso)
                cerrar()
            },
            Modifier.fillMaxWidth().testTag("hoja-gordo-guardar"),
            estilo = EstiloBoton.PRINCIPAL,
            apagado = seleccion.isEmpty(),
        )
    }
}

@Composable
private fun subtituloTexto(s: SubtituloGordo): String =
    when (s) {
        SubtituloGordo.TareaHoy -> stringResource(R.string.habla_sub_tarea_hoy)
        is SubtituloGordo.TareaVencida -> pluralStringResource(R.plurals.habla_sub_tarea_vencida, s.dias, s.dias)
        SubtituloGordo.TareaSemana -> stringResource(R.string.habla_sub_tarea_semana)
        SubtituloGordo.Tarea -> stringResource(R.string.habla_sub_tarea)
        SubtituloGordo.Habito -> stringResource(R.string.habla_sub_habito)
    }
