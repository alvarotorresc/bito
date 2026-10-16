package com.alvarotc.bito.ui.sesion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.sesion.NoMolestar
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.SondaSalto
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.HojaPasos
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

/** En qué punto del permiso de No molestar está la persona. */
enum class PasoPermiso { NINGUNO, PERMISO, DENEGADO, LISTO }

/** Quien necesita el permiso lo abre con [pedir]; las hojas de [HojasNoMolestar] hacen el resto. */
@Stable
class FlujoNoMolestar {
    var paso by mutableStateOf(PasoPermiso.NINGUNO)
        internal set

    fun pedir() {
        paso = PasoPermiso.PERMISO
    }
}

@Composable
fun rememberFlujoNoMolestar(): FlujoNoMolestar = remember { FlujoNoMolestar() }

// `.hoja .big`: el hueco de Habi de las hojas del mock (sin token): 140 dp de alto, arco de 70 arriba y 18 abajo.
private val ALTO_HUECO = 140.dp
private val FORMA_HUECO = RoundedCornerShape(topStart = 70.dp, topEnd = 70.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

// `hb.jump(.24)` al entrar en «Listo».
private const val SALTO_LISTO = 0.24f

// `habiEn` pone a Habi al 86 % del alto del hueco con los pies al 98 %.
private const val HABI_ALTO = 0.86f
private const val HABI_PIES = 0.98f

// `.hoja .desc` y `.nota-dnd`: interlineado 1,4.
private val DESC_LINEA = 22.4.sp
private val NOTA_LINEA = 19.6.sp
private val TITULO_LINEA = 27.6.sp

/**
 * El permiso de No molestar (spec §10): la hoja que lo pide, la pantalla de Android, y al volver «Listo» o la hoja de
 * «sigue apagado». [alActivar] enciende el interruptor de quien pidió; [alAhoraNo] avisa de «Ahora no»; [aviso] enseña
 * los avisos breves (si quien pide no tiene dónde, null).
 */
@Composable
fun HojasNoMolestar(
    flujo: FlujoNoMolestar,
    noMolestar: NoMolestar,
    spec: HabiSpec,
    alActivar: () -> Unit,
    alAhoraNo: () -> Unit = {},
    aviso: ((String) -> Unit)? = null,
    sondaSalto: SondaSalto? = null,
) {
    val gesto = rememberGesto()
    val avisoListo = stringResource(R.string.ses_aviso_dnd_listo)
    val avisoSin = stringResource(R.string.ses_aviso_dnd_sin)
    // El sistema devuelve CANCELED aunque se conceda: solo vale lo que dice el permiso al volver.
    val lanzador =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val concedido = noMolestar.permitido()
            when (flujo.paso) {
                PasoPermiso.PERMISO ->
                    if (concedido) {
                        flujo.paso = PasoPermiso.NINGUNO
                        alActivar()
                        gesto(Snd.INTERRUPTOR_ON, Haptica.CONFIRM)
                        aviso?.invoke(avisoListo)
                    } else {
                        flujo.paso = PasoPermiso.DENEGADO
                    }
                PasoPermiso.DENEGADO ->
                    if (concedido) {
                        flujo.paso = PasoPermiso.LISTO
                        alActivar()
                        gesto(Snd.LLEGADA, Haptica.CONFIRM)
                    }
                else -> Unit
            }
        }
    val pedirAjustes = {
        runCatching { lanzador.launch(noMolestar.intentAjustes()) }
        Unit
    }

    Hoja(
        abierta = flujo.paso == PasoPermiso.PERMISO,
        alCerrar = { flujo.paso = PasoPermiso.NINGUNO },
        titulo = stringResource(R.string.ses_permiso_titulo),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.ses_permiso_desc),
                style = BitoTema.tipos.t16.copy(lineHeight = DESC_LINEA),
                color = BitoTema.colores.tintaSuave,
                modifier = Modifier.padding(top = 6.dp),
            )
            BotonesHoja(
                principal = stringResource(R.string.ses_dar_permiso),
                alPrincipal = pedirAjustes,
                modifier = Modifier.padding(top = Medidas.e18),
                suave = stringResource(R.string.ses_ahora_no),
                alSuave = {
                    flujo.paso = PasoPermiso.NINGUNO
                    alAhoraNo()
                },
            )
        }
    }

    // Mientras la hoja sale, sigue enseñando el paso que tenía.
    var visible by remember { mutableIntStateOf(0) }
    if (flujo.paso == PasoPermiso.LISTO) {
        visible = 1
    } else if (flujo.paso == PasoPermiso.DENEGADO) {
        visible = 0
    }
    Hoja(
        abierta = flujo.paso == PasoPermiso.DENEGADO || flujo.paso == PasoPermiso.LISTO,
        alCerrar = { flujo.paso = PasoPermiso.NINGUNO },
    ) {
        HojaPasos(paso = visible) { paso ->
            if (paso == 0) {
                Column(Modifier.fillMaxWidth()) {
                    HuecoHabi(spec.copy(mood = Mood.NORMAL), salta = false)
                    TituloDeHoja(stringResource(R.string.ses_dnd_no_titulo))
                    Descripcion(stringResource(frase(spec.personality)))
                    Text(
                        stringResource(R.string.ses_dnd_no_nota),
                        style = BitoTema.tipos.t14.copy(lineHeight = NOTA_LINEA),
                        color = BitoTema.colores.tintaSuave,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    BotonesHoja(
                        principal = stringResource(R.string.ses_dar_permiso),
                        alPrincipal = pedirAjustes,
                        modifier = Modifier.padding(top = Medidas.e18),
                        suave = stringResource(R.string.ses_dnd_seguir_sin),
                        alSuave = {
                            gesto(h = Haptica.LOW)
                            flujo.paso = PasoPermiso.NINGUNO
                            aviso?.invoke(avisoSin)
                        },
                    )
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    HuecoHabi(spec.copy(mood = Mood.RADIANT), salta = true, sondaSalto = sondaSalto)
                    TituloDeHoja(stringResource(R.string.ses_dnd_listo_titulo))
                    Descripcion(stringResource(R.string.ses_dnd_listo_frase))
                    BotonesHoja(
                        principal = stringResource(R.string.ses_vale),
                        alPrincipal = { flujo.paso = PasoPermiso.NINGUNO },
                        modifier = Modifier.padding(top = Medidas.e18),
                    )
                }
            }
        }
    }
}

private fun frase(voz: Personality): Int =
    when (voz) {
        Personality.NEUTRA -> R.string.ses_dnd_no_neutra
        Personality.CHEERLEADER -> R.string.ses_dnd_no_cheerleader
        Personality.SARGENTO -> R.string.ses_dnd_no_sargento
    }

@Composable
private fun TituloDeHoja(texto: String) {
    Text(texto, style = BitoTema.tipos.t24.fuerte().copy(lineHeight = TITULO_LINEA), color = BitoTema.colores.tinta)
}

@Composable
private fun Descripcion(texto: String) {
    Text(
        texto,
        style = BitoTema.tipos.t16.copy(lineHeight = DESC_LINEA),
        color = BitoTema.colores.tintaSuave,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** El hueco de Habi de `.hoja .big`; con [salta], Habi hace su reacción al entrar. */
@Composable
private fun HuecoHabi(
    spec: HabiSpec,
    salta: Boolean,
    sondaSalto: SondaSalto? = null,
) {
    var reaccion by remember { mutableIntStateOf(0) }
    LaunchedEffect(salta) { if (salta) reaccion = 1 }
    Box(
        Modifier
            .padding(bottom = Medidas.e12)
            .fillMaxWidth()
            .height(ALTO_HUECO)
            .testTag("dnd-hueco")
            .clip(FORMA_HUECO)
            .background(BitoTema.colores.hornacina),
        contentAlignment = Alignment.BottomCenter,
    ) {
        HabiAvatar(
            spec,
            Modifier
                .padding(bottom = ALTO_HUECO * (1f - HABI_PIES))
                .size(ALTO_HUECO * HABI_ALTO)
                .testTag("dnd-habi"),
            animated = !BitoTema.reducido,
            nudge = reaccion,
            // `hb.jump(.24)` del mock: el salto de Listo, no la reacción al toque.
            nudgeSalto = if (salta) SALTO_LISTO else null,
            sondaSalto = sondaSalto,
        )
    }
}
