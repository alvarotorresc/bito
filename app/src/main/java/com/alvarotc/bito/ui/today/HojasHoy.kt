package com.alvarotc.bito.ui.today

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.CampoGrande
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.Stepper
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay

private const val TOPE_TOTAL = 99_999

/** «Ajustar el total de hoy»: stepper y número grande; en duración, +5, +15 y +objetivo. */
@Composable
fun HojaTotalExacto(
    abierta: Boolean,
    card: HabitCardUi?,
    alGuardar: (Int) -> Unit,
    alCerrar: () -> Unit,
) {
    val retenida = remember { arrayOfNulls<HabitCardUi>(1) }
    if (card != null) retenida[0] = card
    val actual = retenida[0]
    var valor by remember(actual?.id, abierta) { mutableIntStateOf(actual?.progress ?: 0) }
    Hoja(abierta, alCerrar, titulo = stringResource(R.string.exact_value_title)) {
        if (actual != null) {
            val unidad = if (actual.kind == CardKind.DURATION) stringResource(R.string.unit_min) else actual.unit
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Stepper(
                    valor,
                    { valor = it },
                    min = 0,
                    max = TOPE_TOTAL,
                    descripcion = stringResource(R.string.exact_value_hint),
                    paso = actual.step.coerceAtLeast(1),
                )
            }
            CampoGrande(
                valor.toString(),
                { texto -> valor = texto.filter(Char::isDigit).take(5).toIntOrNull() ?: 0 },
                Modifier.padding(top = Medidas.e12).testTag("total-campo"),
                unidad = unidad,
            )
            if (actual.kind == CardKind.DURATION) {
                Row(Modifier.padding(top = Medidas.e12), horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
                    Boton(stringResource(R.string.add_amount, 5), { valor = (valor + 5).coerceAtMost(TOPE_TOTAL) })
                    Boton(stringResource(R.string.add_amount, 15), { valor = (valor + 15).coerceAtMost(TOPE_TOTAL) })
                    Boton(stringResource(R.string.add_target), { valor = maxOf(valor, actual.target) })
                }
            }
            BotonesHoja(
                principal = stringResource(R.string.sistema_guardar),
                alPrincipal = { alGuardar(valor) },
                modifier = Modifier.padding(top = Medidas.e16),
            )
        }
    }
}

private const val MAX_CIRCULOS = 14

/** «Estuviste fuera»: «Sellar N días» llena el círculo de cada día uno tras otro y luego sella. */
@Composable
fun HojaEstuvisteFuera(
    abierta: Boolean,
    dias: List<LogicalDay>,
    alSellar: () -> Unit,
    alCerrar: () -> Unit,
) {
    val retenidos = remember { arrayOfNulls<List<LogicalDay>>(1) }
    if (dias.isNotEmpty()) retenidos[0] = dias
    val lista = retenidos[0] ?: dias
    val n = lista.size
    var sellando by remember { mutableStateOf(false) }
    var llenos by remember { mutableIntStateOf(0) }
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val colores = BitoTema.colores
    val alSellarActual by androidx.compose.runtime.rememberUpdatedState(alSellar)
    LaunchedEffect(abierta) {
        if (abierta) {
            sellando = false
            llenos = 0
        }
    }
    LaunchedEffect(sellando) {
        if (!sellando) return@LaunchedEffect
        val visibles = minOf(n, MAX_CIRCULOS)
        if (reducido) {
            llenos = visibles
        } else {
            repeat(visibles) {
                llenos = it + 1
                gesto(Snd.TIC, Haptica.SEG)
                delay(PASO_SELLO_MS)
            }
        }
        alSellarActual()
    }
    Hoja(abierta, alCerrar, titulo = stringResource(R.string.seal_sheet_title)) {
        Text(
            if (n == 1) stringResource(R.string.seal_sheet_body_one) else stringResource(R.string.seal_sheet_body_many, n),
            style = BitoTema.tipos.t16,
            color = colores.tintaSuave,
        )
        Row(
            Modifier.padding(vertical = Medidas.e16).testTag("sellos"),
            horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
        ) {
            repeat(minOf(n, MAX_CIRCULOS)) { i ->
                val lleno = i < llenos
                Box(
                    Modifier
                        .size(24.dp)
                        .testTag("sello-$i")
                        .clip(CircleShape)
                        .drawBehind { if (lleno) drawRect(colores.agua) }
                        .border(Medidas.linea, colores.contorno, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (lleno) Icon(IconosSistema.Check, null, tint = colores.tinta, modifier = Modifier.size(14.dp))
                }
            }
        }
        BotonesHoja(
            principal =
                if (n == 1) {
                    stringResource(
                        R.string.seal_sheet_confirm_one,
                    )
                } else {
                    stringResource(R.string.seal_sheet_confirm_many, n)
                },
            alPrincipal = { sellando = true },
            suave = stringResource(R.string.seal_sheet_later),
            alSuave = { cerrar() },
            apagado = sellando,
        )
    }
}

internal const val PASO_SELLO_MS = 140L
