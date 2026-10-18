package com.alvarotc.bito.ui.camino

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.ui.premios.FilaPremio
import com.alvarotc.bito.ui.premios.premioDeLogro
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.HojaScope
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.stats.BadgeStrings
import java.time.LocalDate

enum class ModoLogro { NUEVO, CONSULTA }

private val EMBLEMA_HOJA = 124.dp

// (sin token) `.tc-big` y `.tc-emb` de tienda-congeladores.css: la hoja de un logro nuevo es más alta que la de consulta.
private val HORNACINA_NUEVA = 184.dp
private val EMBLEMA_NUEVA = 164.dp
private val FORMA_NUEVA = RoundedCornerShape(topStart = 80.dp, topEnd = 80.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
private val ABAJO_NUEVA = 14.dp
private const val ENTRADA_RETRASO_MS = 60
private const val ENTRADA_MS = 320

/**
 * La hoja de un logro (§7.4, G13). En [ModoLogro.CONSULTA] enseña el emblema grande, cómo se gana y la fecha en que
 * se consiguió o lo que falta; en [ModoLogro.NUEVO] el emblema se enciende entero con su secuencia. [hoy] y
 * [mesImposible] solo los usa Mes redondo para decir cuándo empieza otro mes entero.
 */
@Composable
fun HojaLogro(
    abierta: Boolean,
    logro: LogroUi?,
    modo: ModoLogro,
    alVerEnJardin: () -> Unit,
    alCerrar: () -> Unit,
    hoy: LocalDate? = null,
    mesImposible: Boolean = false,
) {
    Hoja(abierta, alCerrar) {
        if (logro != null) ContenidoLogro(logro, modo, alVerEnJardin, hoy, mesImposible)
    }
}

@Composable
private fun HojaScope.ContenidoLogro(
    logro: LogroUi,
    modo: ModoLogro,
    alVerEnJardin: () -> Unit,
    hoy: LocalDate?,
    mesImposible: Boolean,
) {
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    val nuevo = modo == ModoLogro.NUEVO
    val especial = logro.especial
    val conseguido = logro.conseguido != null
    Column(Modifier.testTag("hoja-logro")) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (nuevo) Modifier.height(HORNACINA_NUEVA) else Modifier)
                .clip(if (nuevo) FORMA_NUEVA else BitoFormas.hornacina)
                .background(colores.hornacina)
                .then(if (nuevo) Modifier else Modifier.padding(vertical = Medidas.e16))
                .testTag("logro-hornacina"),
            contentAlignment = Alignment.Center,
        ) {
            EmblemaGrande(logro, nuevo)
        }
        Spacer(Modifier.height(if (nuevo) ABAJO_NUEVA else Medidas.e12))
        if (nuevo) {
            Text(
                stringResource(R.string.store_reward_title),
                Modifier.testTag("logro-etiqueta"),
                style = BitoTema.tipos.t14.fuerte(),
                color = colores.tintaSuave,
            )
        }
        Text(stringResource(BadgeStrings.badgeNameRes(logro.def.id)), style = BitoTema.tipos.t24.fuerte(), color = colores.tinta)
        Text(
            stringResource(R.string.camino_como_se_gana, stringResource(BadgeStrings.badgeHowRes(logro.def.id))),
            Modifier.padding(top = Medidas.e4),
            style = BitoTema.tipos.t16,
            color = colores.tintaSuave,
        )
        if (!nuevo) {
            val estado =
                logro.conseguido?.let { dia ->
                    stringResource(R.string.camino_lo_conseguiste, fechaDelDia(LocalDate.ofEpochDay(dia.toLong())))
                } ?: faltaTexto(logro, hoy ?: LocalDate.ofEpochDay(0), mesImposible)
            if (estado != null) {
                Text(
                    estado,
                    Modifier.padding(top = 10.dp).testTag("logro-estado"),
                    style = BitoTema.tipos.t16.fuerte(),
                    color = colores.tinta,
                )
            }
        }
        val premio = if (nuevo) premioDeLogro(logro.def.id) else null
        when {
            premio != null -> FilaPremio(premio)
            especial != null -> FilaEspecial(especial, plantado = conseguido || nuevo)
        }
        Spacer(Modifier.height(Medidas.e16))
        val verlo = stringResource(R.string.camino_verlo)
        val irAlJardin = {
            gesto(null, Haptica.CLICK)
            cerrarY(alVerEnJardin)
        }
        when {
            nuevo ->
                BotonesHoja(
                    principal = stringResource(R.string.store_reward_done),
                    alPrincipal = {
                        gesto(null, Haptica.CONFIRM)
                        cerrar()
                    },
                )
            especial != null && conseguido ->
                BotonesHoja(
                    principal = verlo,
                    alPrincipal = irAlJardin,
                    suave = stringResource(R.string.sistema_cerrar),
                    alSuave = { cerrar() },
                )
            else ->
                BotonesHoja(principal = stringResource(R.string.sistema_cerrar), alPrincipal = { cerrar() })
        }
    }
}

/** El emblema de 124 dp (164 en la hoja de un logro nuevo). En consulta sube 10 dp desde .94; en nuevo se enciende con su propia secuencia. */
@Composable
private fun EmblemaGrande(
    logro: LogroUi,
    nuevo: Boolean,
) {
    val reducido = BitoTema.reducido
    val diezDp = with(LocalDensity.current) { 10.dp.toPx() }
    val entrada = remember { Animatable(if (nuevo) 1f else 0f) }
    LaunchedEffect(nuevo) {
        if (!nuevo) {
            val duracion = if (reducido) Duraciones.REDUCIDO else ENTRADA_MS
            val retraso = if (reducido) 0 else ENTRADA_RETRASO_MS
            entrada.animateTo(1f, tween(duracion, retraso, Curvas.out))
        }
    }
    EmblemaDeLogro(
        logro,
        if (nuevo) EMBLEMA_NUEVA else EMBLEMA_HOJA,
        Modifier.testTag("logro-emblema").graphicsLayer {
            val t = entrada.value
            alpha = 0.4f + 0.6f * t
            if (!reducido) {
                translationY = diezDp * (1f - t)
                scaleX = 0.94f + 0.06f * t
                scaleY = 0.94f + 0.06f * t
            }
        },
        celebrar = nuevo,
        encendido = nuevo || logro.conseguido != null,
    )
}

/** «Plantó el caminito en tu jardín.» (con el especial en color) o «Cuando llegue, planta…» (en silueta). */
@Composable
private fun FilaEspecial(
    especial: GardenSpecial,
    plantado: Boolean,
) {
    val colores = BitoTema.colores
    val nombre = stringResource(nombreDeEspecial(especial))
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colores.tinta.copy(alpha = 0.06f))
            .padding(horizontal = Medidas.e12, vertical = 10.dp)
            .testTag("logro-especial"),
        horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(arteDeEspecial(especial)),
            contentDescription = null,
            Modifier.size(52.dp).graphicsLayer { alpha = if (plantado) 1f else 0.2f },
            colorFilter = if (plantado) null else ColorFilter.tint(colores.roca),
        )
        Text(
            stringResource(if (plantado) R.string.camino_planto else R.string.camino_plantara, nombre),
            style = BitoTema.tipos.t16,
            color = colores.tinta,
        )
    }
}
