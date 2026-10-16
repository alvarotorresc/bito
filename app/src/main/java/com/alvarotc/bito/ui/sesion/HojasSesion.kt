package com.alvarotc.bito.ui.sesion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.sesion.Fondo
import com.alvarotc.bito.ui.settings.hojas.HojaFondo
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.OpcionAgua
import com.alvarotc.bito.ui.sistema.componentes.Stepper
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

// `.hoja .opt`: 12 dp arriba y abajo y una línea de 1,5 dp al 15 % de tinta entre opciones.
private val OPCION_VERTICAL = 12.dp
private val OPCION_LINEA = Color(0x264A2C22)

// `.hoja .desc`: 16 sp con interlineado 1,4, 6 dp arriba.
private val DESC_LINEA = 22.4.sp

// `.step.ses-grande`: botones de 48 dp, 18 dp entre piezas, 18 dp arriba y 4 abajo; cifra de 48 sp con ancho mínimo 2,2 cifras.
private val GRANDE_BOTON = 48.dp
private val GRANDE_ENTRE = 18.dp
private val GRANDE_ARRIBA = 18.dp
private val GRANDE_ABAJO = 4.dp
private val GRANDE_CIFRA = 58.dp

private const val MINUTOS_INICIO = 30
private const val MINUTOS_INICIO_MAS = 10
private const val MINUTOS_MIN = 5
private const val MINUTOS_MAX = 180
private const val MINUTOS_PASO = 5

@Composable
private fun Descripcion(texto: String) {
    Text(
        texto,
        style = BitoTema.tipos.t16.copy(lineHeight = DESC_LINEA),
        color = BitoTema.colores.tintaSuave,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Una opción de lista de las hojas: nombre en negrita de 19 sp y su nota debajo. [elegida] null = no es de elegir. */
@Composable
private fun OpcionHoja(
    nombre: String,
    nota: String?,
    elegida: Boolean?,
    ultima: Boolean,
    alPulsar: () -> Unit,
) {
    val colores = BitoTema.colores
    val mando =
        if (elegida != null) {
            Modifier.selectable(selected = elegida, role = Role.RadioButton, onClick = alPulsar)
        } else {
            Modifier.clickable(role = Role.Button, onClick = alPulsar)
        }
    Column(
        Modifier
            .fillMaxWidth()
            .then(mando)
            .drawBehind {
                if (!ultima) {
                    val grosor = Medidas.linea.toPx()
                    drawLine(OPCION_LINEA, Offset(0f, size.height - grosor / 2), Offset(size.width, size.height - grosor / 2), grosor)
                }
            }.padding(vertical = OPCION_VERTICAL),
    ) {
        Text(nombre, style = BitoTema.tipos.t19.fuerte(), color = if (elegida == true) colores.aguaHonda else colores.tinta)
        if (nota != null) Text(nota, style = BitoTema.tipos.t14, color = colores.tintaSuave)
    }
}

/** «¿En qué te concentras?»: las tareas de hoy y, al final, «Solo foco». */
@Composable
fun HojaTarea(
    abierta: Boolean,
    tareas: List<OpcionTarea>,
    elegida: Objetivo,
    alElegir: (Objetivo) -> Unit,
    alCerrar: () -> Unit,
) {
    val gesto = rememberGesto()
    val sinPaso = stringResource(R.string.ses_hoja_sin_paso)
    Hoja(abierta = abierta, alCerrar = alCerrar, titulo = stringResource(R.string.ses_preparar_titulo)) {
        Column(Modifier.fillMaxWidth()) {
            fun elegir(objetivo: Objetivo) {
                gesto(Snd.TIC, Haptica.SEG)
                alElegir(objetivo)
                cerrar()
            }
            tareas.forEach { t ->
                OpcionHoja(
                    nombre = t.nombre,
                    nota = t.primerPaso?.let { stringResource(R.string.ses_primer_paso, it.lowercase()) } ?: sinPaso,
                    elegida = elegida == Objetivo.Tarea(t.id),
                    ultima = false,
                ) { elegir(Objetivo.Tarea(t.id)) }
            }
            OpcionHoja(
                nombre = stringResource(R.string.ses_solo_foco),
                nota = null,
                elegida = elegida == Objetivo.Libre,
                ultima = true,
            ) { elegir(Objetivo.Libre) }
        }
    }
}

/** «¿Cuántos minutos?» (o «más», que se suman a lo que queda): de 5 a 180 en pasos de 5, empezando en 30 (10 en «más»). */
@Composable
fun HojaMinutos(
    abierta: Boolean,
    alGuardar: (Int) -> Unit,
    alCerrar: () -> Unit,
    mas: Boolean = false,
) {
    val gesto = rememberGesto()
    var minutos by remember(abierta, mas) { mutableIntStateOf(if (mas) MINUTOS_INICIO_MAS else MINUTOS_INICIO) }
    val titulo = stringResource(if (mas) R.string.ses_minutos_mas_titulo else R.string.ses_minutos_titulo)
    Hoja(abierta = abierta, alCerrar = alCerrar, titulo = titulo) {
        Column(Modifier.fillMaxWidth()) {
            Descripcion(stringResource(if (mas) R.string.ses_minutos_mas_desc else R.string.ses_minutos_desc))
            Column(
                Modifier.fillMaxWidth().padding(top = GRANDE_ARRIBA, bottom = GRANDE_ABAJO),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Stepper(
                    valor = minutos,
                    alCambiar = { minutos = it },
                    min = MINUTOS_MIN,
                    max = MINUTOS_MAX,
                    descripcion = titulo,
                    paso = MINUTOS_PASO,
                    tamano = GRANDE_BOTON,
                    etiquetaMenos = stringResource(R.string.ses_menos),
                    etiquetaMas = stringResource(R.string.ses_mas),
                    estiloCifra = BitoTema.tipos.t48.cifra(),
                    anchoCifra = GRANDE_CIFRA,
                    separacion = GRANDE_ENTRE,
                )
            }
            BotonesHoja(
                principal = stringResource(if (mas) R.string.ses_sumar else R.string.ses_listo),
                alPrincipal = {
                    gesto(h = Haptica.CONFIRM)
                    alGuardar(minutos)
                    cerrar()
                },
                modifier = Modifier.padding(top = Medidas.e18),
                suave = stringResource(R.string.ses_cancelar),
                alSuave = { cerrar() },
            )
        }
    }
}

/** «Ya tienes algo en marcha»: seguir con la otra o dejarla y empezar esta. */
@Composable
fun HojaOcupado(
    abierta: Boolean,
    nombre: String,
    quedan: Int,
    deMin: Int,
    alSeguir: () -> Unit,
    alDejar: () -> Unit,
    alCerrar: () -> Unit,
) {
    Hoja(abierta = abierta, alCerrar = alCerrar, titulo = stringResource(R.string.ses_ocupado_titulo)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Descripcion(stringResource(R.string.ses_ocupado_resta, nombre, quedan, deMin))
            Column(Modifier.padding(top = Medidas.e8)) {
                OpcionHoja(
                    nombre = stringResource(R.string.ses_ocupado_seguir, nombre),
                    nota = stringResource(R.string.ses_ocupado_seguir_desc),
                    elegida = null,
                    ultima = false,
                ) {
                    alSeguir()
                    cerrar()
                }
                OpcionHoja(
                    nombre = stringResource(R.string.ses_ocupado_dejar),
                    nota = stringResource(R.string.ses_ocupado_dejar_desc),
                    elegida = null,
                    ultima = true,
                ) {
                    alDejar()
                    cerrar()
                }
            }
        }
    }
}

/** El sonido de fondo de Preparar: la hoja de Ajustes con los textos del mock; la muestra la da el [fondo] de la sesión. */
@Composable
fun HojaFondoSesion(
    abierta: Boolean,
    elegido: SessionBackground,
    alElegir: (SessionBackground) -> Unit,
    fondo: Fondo,
    alCerrar: () -> Unit,
) {
    val opciones =
        listOf(
            OpcionAgua(
                SessionBackground.NONE.name,
                stringResource(R.string.ses_fondo_ninguno),
                stringResource(R.string.ses_fondo_ninguno_desc),
            ),
            OpcionAgua(
                SessionBackground.RAIN.name,
                stringResource(R.string.ses_fondo_lluvia),
                stringResource(R.string.ses_fondo_lluvia_desc),
            ),
            OpcionAgua(
                SessionBackground.BROWN.name,
                stringResource(R.string.ses_fondo_marron),
                stringResource(R.string.ses_fondo_marron_desc),
            ),
            OpcionAgua(SessionBackground.CAFE.name, stringResource(R.string.ses_fondo_cafe), stringResource(R.string.ses_fondo_cafe_desc)),
        )
    HojaFondo(
        abierta = abierta,
        elegido = elegido,
        alElegir = alElegir,
        alCerrar = alCerrar,
        titulo = stringResource(R.string.ses_fondo),
        descripcion = stringResource(R.string.ses_fondo_hoja_desc),
        opcionesPropias = opciones,
        conEstrato = false,
        conHecho = false,
        muestra = fondo::muestra,
    )
}
