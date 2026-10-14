package com.alvarotc.bito.ui.sistema

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.PlantaFicha
import com.alvarotc.bito.domain.WindowStats
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.detail.Lamina
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonAgua
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.CalendarioElegir
import com.alvarotc.bito.ui.sistema.componentes.Campo
import com.alvarotc.bito.ui.sistema.componentes.CampoGrande
import com.alvarotc.bito.ui.sistema.componentes.CanalMesa
import com.alvarotc.bito.ui.sistema.componentes.Dialogo
import com.alvarotc.bito.ui.sistema.componentes.ElegirConAgua
import com.alvarotc.bito.ui.sistema.componentes.Emblema
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.EstratoPlegable
import com.alvarotc.bito.ui.sistema.componentes.FamiliaLogro
import com.alvarotc.bito.ui.sistema.componentes.Fila
import com.alvarotc.bito.ui.sistema.componentes.FilaEnlace
import com.alvarotc.bito.ui.sistema.componentes.FilaInterruptor
import com.alvarotc.bito.ui.sistema.componentes.FilaValor
import com.alvarotc.bito.ui.sistema.componentes.FormaAgua
import com.alvarotc.bito.ui.sistema.componentes.Globito
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.HojaPasos
import com.alvarotc.bito.ui.sistema.componentes.HojaPersonalidad
import com.alvarotc.bito.ui.sistema.componentes.Interruptor
import com.alvarotc.bito.ui.sistema.componentes.MenuFila
import com.alvarotc.bito.ui.sistema.componentes.MesaCanales
import com.alvarotc.bito.ui.sistema.componentes.Monedita
import com.alvarotc.bito.ui.sistema.componentes.OpcionAgua
import com.alvarotc.bito.ui.sistema.componentes.OpcionMenu
import com.alvarotc.bito.ui.sistema.componentes.Plegable
import com.alvarotc.bito.ui.sistema.componentes.RelojCielo
import com.alvarotc.bito.ui.sistema.componentes.Saldo
import com.alvarotc.bito.ui.sistema.componentes.Segmentado
import com.alvarotc.bito.ui.sistema.componentes.Stepper
import com.alvarotc.bito.ui.sistema.componentes.TonoOpcion
import com.alvarotc.bito.ui.sistema.componentes.rememberAvisoEstado
import com.alvarotc.bito.ui.sistema.sonido.LocalSonido
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.CardKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private val MAULLIDOS_VIEJOS =
    listOf(
        "habi_meeh" to R.raw.habi_meeh,
        "habi_cheer" to R.raw.habi_cheer,
        "habi_sad" to R.raw.habi_sad,
        "habi_pop" to R.raw.habi_pop,
        "habi_happy" to R.raw.habi_happy,
        "log_tick" to R.raw.log_tick,
    )

/** Una sección de la galería: su título y su contenido en una sola fila de la lista. */
private fun LazyListScope.seccion(
    titulo: String,
    contenido: @Composable () -> Unit,
) {
    item(key = titulo) {
        Column(Modifier.fillMaxWidth().padding(vertical = Medidas.e12), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            Text(titulo, style = BitoTema.tipos.t19, color = BitoTema.colores.tinta)
            contenido()
        }
    }
}

/**
 * Galería de depuración (spec §4.6): cada componente, sonido y háptica, para afinar en el Pixel.
 * Solo existe con `BuildConfig.DEBUG` (la abre el panel del jardín), así que sus textos van en línea.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun GaleriaSistema(alCerrar: () -> Unit) {
    var reducido by remember { mutableStateOf(false) }
    val sonido = LocalSonido.current
    val tacto = LocalTacto.current
    val aviso = rememberAvisoEstado()
    val scope = rememberCoroutineScope()

    // Los maullidos de antes (decisión 4): un SoundPool propio, liberado al salir.
    val contexto = LocalContext.current
    val viejos =
        remember {
            val pool =
                SoundPool
                    .Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
                    .build()
            pool to MAULLIDOS_VIEJOS.associate { (nombre, res) -> nombre to pool.load(contexto, res, 1) }
        }
    DisposableEffect(Unit) { onDispose { viejos.first.release() } }

    Box(Modifier.fillMaxSize().background(BitoTema.colores.tAlta)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("galeria").padding(horizontal = Medidas.margen),
        ) {
            item(key = "cabecera") {
                Column(Modifier.padding(top = Medidas.e16)) {
                    Boton("Cerrar", alCerrar, estilo = EstiloBoton.FANTASMA)
                }
            }
            seccion("Reducido") {
                FilaInterruptor("Movimiento reducido", reducido, { reducido = it })
            }
            item(key = "componentes") {
                CompositionLocalProvider(LocalReducido provides (reducido || BitoTema.reducido)) {
                    Componentes(aviso)
                }
            }
            seccion("Sonidos") {
                var intensidad by remember { mutableIntStateOf(10) }
                Stepper(intensidad, { intensidad = it }, 2, 10, "Intensidad", formato = { "%.1f".format(it / 10f) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.e8), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
                    Snd.entries.filterNot { it.bucle }.forEach { snd ->
                        Boton(
                            snd.name,
                            { sonido.play(snd, intensidad / 10f) },
                            modifier = Modifier.testTag("snd-${snd.name}"),
                            estilo = EstiloBoton.FANTASMA,
                        )
                    }
                }
                Snd.entries.filter { it.bucle }.forEach { snd ->
                    val bucle = remember(snd, sonido) { sonido.bucle(snd) }
                    var sonando by remember { mutableStateOf(bucle.sonando) }
                    FilaInterruptor(
                        snd.name,
                        sonando,
                        {
                            sonando = it
                            if (it) bucle.empezar() else bucle.parar()
                        },
                    )
                }
            }
            seccion("Maullidos de antes") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.e8), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
                    MAULLIDOS_VIEJOS.forEach { (nombre, _) ->
                        Boton(
                            nombre,
                            { viejos.second[nombre]?.let { viejos.first.play(it, 1f, 1f, 1, 0, 1f) } },
                            estilo = EstiloBoton.FANTASMA,
                        )
                    }
                    Boton("Snd.HABI (nuevo)", { sonido.play(Snd.HABI) }, estilo = EstiloBoton.NORMAL)
                }
            }
            seccion("Hápticas") {
                var fuerza by remember { mutableIntStateOf(7) }
                Stepper(
                    fuerza,
                    {
                        fuerza = it
                        tacto.probar(it)
                    },
                    0,
                    10,
                    "Fuerza",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.e8), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
                    Haptica.entries.forEach { h ->
                        Boton(h.name, { tacto.haptica(h) }, modifier = Modifier.testTag("hap-${h.name}"), estilo = EstiloBoton.FANTASMA)
                    }
                }
            }
            seccion("Muelles") {
                Muelles.tabla.forEach { (nombre, par) -> Muelle(nombre, par.first, par.second) }
            }
            seccion("Láminas") { Laminas() }
            item(key = "pie") { Box(Modifier.size(48.dp)) }
        }
        AvisoHost(aviso, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
    }
}

/**
 * Las treinta láminas de la ficha (6 especies × 5 etapas) a escala 0,5, de dos en dos y con su clave encima
 * («manzano-5»), para revisar las flechas de cada planta en el Pixel (spec §10.2).
 */
@Composable
private fun Laminas() {
    Column(Modifier.testTag("galeria-laminas"), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
        val todas = GardenSpecies.entries.flatMap { especie -> GrowthStage.entries.map { etapa -> especie to etapa } }
        todas.chunked(2).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
                fila.forEach { (especie, etapa) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Medidas.e4)) {
                        Text("${especie.key}-${etapa.ordinal + 1}", style = BitoTema.tipos.t14, color = BitoTema.colores.tinta)
                        Lamina(
                            planta = PlantaFicha(especie, etapa, 0),
                            mejor = 21,
                            pct30 = WindowStats(26, 30),
                            actual = 9,
                            periodo = Period.DAY,
                            kind = CardKind.CHECK,
                            modifier = Modifier.aMediaEscala(),
                        )
                    }
                }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Mide el contenido al doble del ancho disponible (el de una pantalla) y lo pinta a la mitad: la lámina a escala 0,5. */
private fun Modifier.aMediaEscala(): Modifier =
    layout { medible, restricciones ->
        val colocable = medible.measure(Constraints.fixedWidth(restricciones.maxWidth * 2))
        layout(colocable.width / 2, colocable.height / 2) {
            colocable.placeWithLayer(0, 0) {
                scaleX = 0.5f
                scaleY = 0.5f
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }

/** Un cuadradito que salta con el muelle [nombre] al tocarlo. */
@Composable
private fun Muelle(
    nombre: String,
    amortiguamiento: Float,
    rigidez: Float,
) {
    val desplazamiento = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reducido = BitoTema.reducido
    Column(
        Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    desplazamiento.snapTo(if (desplazamiento.value > 100f) 0f else 160f)
                    desplazamiento.animateTo(
                        if (desplazamiento.value > 100f) 0f else 160f,
                        androidx.compose.animation.core.spring(if (reducido) 1f else amortiguamiento, rigidez),
                    )
                }
            }.padding(vertical = Medidas.e4),
    ) {
        Text("$nombre  ($amortiguamiento / $rigidez)", style = BitoTema.tipos.t14, color = BitoTema.colores.tintaSuave)
        Box(
            Modifier
                .offset { IntOffset(desplazamiento.value.dp.roundToPx(), 0) }
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(BitoTema.colores.agua),
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Componentes(aviso: com.alvarotc.bito.ui.sistema.componentes.AvisoEstado) {
    val colores = BitoTema.colores
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(Medidas.e12), modifier = Modifier.padding(vertical = Medidas.e12)) {
        Text("Componentes", style = BitoTema.tipos.t19, color = colores.tinta)

        Text("Boton", style = BitoTema.tipos.t14, color = colores.tintaSuave)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.e8), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            EstiloBoton.entries.forEach { Boton(it.name, {}, estilo = it) }
            Boton("Apagado", {}, apagado = true)
        }
        BotonesHoja(principal = "Guardar", alPrincipal = {}, suave = "Cancelar")
        var trabajando by remember { mutableStateOf(false) }
        BotonAgua("Botón de agua", trabajando, {
            scope.launch {
                trabajando = true
                delay(2000)
                trabajando = false
            }
        })

        var interruptor by remember { mutableStateOf(true) }
        Interruptor(interruptor, { interruptor = it })
        var seg by remember { mutableIntStateOf(0) }
        Segmentado(listOf("Uno", "Dos", "Tres"), seg, { seg = it })

        Fila("Fila", subtitulo = "con subtítulo")
        FilaInterruptor("Fila interruptor", interruptor, { interruptor = it })
        FilaValor("Fila valor", "Valor", {})
        FilaEnlace("Fila enlace", {})

        var paso by remember { mutableIntStateOf(3) }
        Stepper(paso, { paso = it }, 1, 5, "Stepper 1–5")

        var texto by remember { mutableStateOf("") }
        Campo(texto, { texto = it }, etiqueta = "Campo", pista = "Escribe algo")
        Campo(texto, { texto = it }, etiqueta = "Campo con error", error = "Esto no vale")
        CampoGrande(texto, { texto = it }, unidad = "min")

        var plegable by remember { mutableStateOf(false) }
        Plegable("Plegable", plegable, { plegable = it }) { Text("Contenido", color = colores.tinta) }
        var estratoAbierto by remember { mutableIntStateOf(-1) }
        colores.estratos.todos.forEachIndexed { i, e ->
            EstratoPlegable(
                e,
                "Estrato ${i + 1}",
                "resumen",
                estratoAbierto == i,
                { estratoAbierto = if (estratoAbierto == i) -1 else i },
            ) {
                Text("Contenido del estrato", color = e.tinta)
            }
        }

        val opciones = listOf(OpcionAgua("a", "Uno"), OpcionAgua("b", "Dos", "subtítulo"), OpcionAgua("c", "Tres"))
        var elegida by remember { mutableStateOf<String?>("a") }
        ElegirConAgua(opciones, elegida, { elegida = it })
        ElegirConAgua(opciones, elegida, { elegida = it }, forma = FormaAgua.CANTO)

        Text("Monedita y saldo", style = BitoTema.tipos.t14, color = colores.tintaSuave)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            listOf(18.dp, 36.dp, 48.dp).forEach { Monedita(it) }
        }
        var bitos by remember { mutableIntStateOf(12) }
        Saldo(bitos)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            Boton("+1", { bitos++ }, estilo = EstiloBoton.FANTASMA)
            Boton("−1", { bitos-- }, estilo = EstiloBoton.FANTASMA)
        }

        var celebrar by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            FamiliaLogro.entries.forEach { Emblema(it, 1f, true, tamano = 96.dp, celebrar = celebrar) }
        }
        Boton("Celebrar", {
            scope.launch {
                celebrar = true
                delay(100)
                celebrar = false
            }
        })

        var m5 by remember { mutableIntStateOf(8 * 60) }
        RelojCielo(m5, { m5 = it }, paso = 5)
        var m30 by remember { mutableIntStateOf(21 * 60) }
        RelojCielo(m30, { m30 = it }, paso = 30, desde = 19 * 60, hasta = 23 * 60 + 30)
        var fecha by remember { mutableStateOf<LocalDate?>(null) }
        CalendarioElegir(LocalDate.now(), fecha, { fecha = it })
        var niveles by remember { mutableStateOf(CanalMesa.entries.associateWith { 7 }) }
        MesaCanales(niveles, { c, n -> niveles = niveles + (c to n) })

        var hoja by remember { mutableStateOf(false) }
        var hojaPasos by remember { mutableStateOf(false) }
        var dialogo by remember { mutableStateOf(false) }
        var dialogoPeligro by remember { mutableStateOf(false) }
        var menu by remember { mutableStateOf(false) }
        var personalidad by remember { mutableStateOf(false) }
        var actual by remember { mutableStateOf(Personality.NEUTRA) }
        var globito by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Medidas.e8), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            Boton("Hoja", { hoja = true }, estilo = EstiloBoton.FANTASMA)
            Boton("Hoja con pasos", { hojaPasos = true }, estilo = EstiloBoton.FANTASMA)
            Boton("Diálogo", { dialogo = true }, estilo = EstiloBoton.FANTASMA)
            Boton("Diálogo de peligro", { dialogoPeligro = true }, estilo = EstiloBoton.FANTASMA)
            Boton("Menú de fila", { menu = true }, estilo = EstiloBoton.FANTASMA)
            Boton("Personalidad", { personalidad = true }, estilo = EstiloBoton.FANTASMA)
            Boton("Aviso con Deshacer", {
                scope.launch { aviso.mostrar("Hábito archivado", "Deshacer") {} }
            }, estilo = EstiloBoton.FANTASMA)
            Boton("Globito", { globito = true }, estilo = EstiloBoton.FANTASMA)
        }
        Globito(globito, "Un globito de Habi", alIrse = { globito = false })

        Hoja(hoja, { hoja = false }, titulo = "Una hoja") {
            Column(Modifier.padding(Medidas.margen)) {
                Text("Contenido de la hoja", color = colores.tinta)
                BotonesHoja("Cerrar", ::cerrar)
            }
        }
        var pasoHoja by remember { mutableIntStateOf(0) }
        Hoja(hojaPasos, {
            hojaPasos = false
            pasoHoja = 0
        }, titulo = "Dos pasos") {
            Column(Modifier.padding(Medidas.margen)) {
                HojaPasos(pasoHoja) { p -> Text(if (p == 0) "Paso uno" else "Paso dos", color = colores.tinta) }
                BotonesHoja(if (pasoHoja == 0) "Siguiente" else "Hecho", { if (pasoHoja == 0) pasoHoja = 1 else cerrar() })
            }
        }
        Dialogo(dialogo, "¿Seguro?", "Vale", { dialogo = false }, { dialogo = false }, cuerpo = "Un diálogo normal.")
        Dialogo(dialogoPeligro, "¿Borrar?", "Borrar", {
            dialogoPeligro = false
        }, { dialogoPeligro = false }, cuerpo = "No se puede deshacer.", peligro = true)
        MenuFila(
            menu,
            { menu = false },
            listOf(
                OpcionMenu("Editar", IconosSistema.Plus, TonoOpcion.AGUA) {},
                OpcionMenu("Archivar", IconosSistema.Folder, TonoOpcion.PIEDRA) {},
                OpcionMenu("Borrar", IconosSistema.Trash, TonoOpcion.PELIGRO) {},
            ),
            titulo = "Menú de fila",
        )
        HojaPersonalidad(personalidad, actual, { actual = it }, { personalidad = false })
    }
}
