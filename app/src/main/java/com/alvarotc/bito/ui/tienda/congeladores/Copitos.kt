package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo

/** En una fila caben 7 copitos de 44 dp con 8 dp de hueco (356 de los 372 de la veta). */
internal const val COPITOS_CABEN = 7

private val DIAMETRO = Medidas.toqueMinimo
private val HUECO = Medidas.e8
private val COPO = 26.dp // (sin token) tamaño del copo dentro del círculo
private val AIRE_ARRIBA = 10.dp
private val AIRE_ABAJO = 16.dp

/** Qué se pinta para [total] congeladores: [copos] círculos y, si no caben todos, una pastilla con [mas]. */
internal data class PlanCopitos(
    val copos: Int,
    val mas: Int?,
)

internal fun planCopitos(total: Int): PlanCopitos =
    when {
        total <= COPITOS_CABEN -> PlanCopitos(total.coerceAtLeast(0), null)
        else -> PlanCopitos(COPITOS_CABEN - 1, total - (COPITOS_CABEN - 1))
    }

/**
 * La fila de copitos: una sola línea, sin scroll. Hasta 7 caben todos; con 8 o más, 6 y una pastilla «+N» del
 * mismo tamaño e igual hielo. Sin ninguno, la frase «Aún no tienes ninguno.». [tintaSuave] es la de la banda
 * donde se coloca (la veta fría baja la suya).
 */
@Composable
fun Copitos(
    total: Int,
    modifier: Modifier = Modifier,
    tintaSuave: Color = BitoTema.colores.tintaSuave,
) {
    val plan = planCopitos(total)
    Row(
        modifier
            .testTag("tc-copos")
            .fillMaxWidth()
            .padding(top = AIRE_ARRIBA, bottom = AIRE_ABAJO)
            .heightIn(min = DIAMETRO),
        horizontalArrangement = Arrangement.spacedBy(HUECO),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (total <= 0) {
            Text(
                stringResource(R.string.store_freezer_none),
                style = BitoTema.tipos.t16.copy(lineHeight = 44.sp),
                color = tintaSuave,
            )
        } else {
            repeat(plan.copos) {
                Copito(Modifier.testTag("tc-copo")) { Icon(BitoIcons.Snowflake, null, Modifier.size(COPO), tint = BitoTema.colores.copo) }
            }
            plan.mas?.let { PastillaMas(it) }
        }
    }
}

@Composable
private fun Copito(
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    val colores = BitoTema.colores
    Box(
        modifier
            .size(DIAMETRO)
            .background(colores.hielo, CircleShape)
            .border(BorderStroke(Medidas.linea, colores.contorno), CircleShape),
        contentAlignment = Alignment.Center,
    ) { contenido() }
}

@Composable
private fun PastillaMas(mas: Int) {
    val descripcion = stringResource(R.string.store_freezer_more_desc, mas)
    Copito(
        Modifier.testTag("tc-mas").clearAndSetSemantics {
            contentDescription = descripcion
            role = Role.Image
        },
    ) {
        CifraRodillo(
            mas,
            estilo = BitoTema.tipos.t16.cifra().copy(fontWeight = FontWeight.Bold),
            color = BitoTema.colores.copo,
            formato = { "+$it" },
        )
    }
}
