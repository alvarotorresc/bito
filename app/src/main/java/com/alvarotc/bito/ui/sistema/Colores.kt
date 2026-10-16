package com.alvarotc.bito.ui.sistema

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Un estrato de Ajustes (D6, D7): tierra, dos tintas, línea y si es oscuro. Valores de `ajustes-hojas.html` (`CAPAS`). */
@Immutable
data class Estrato(
    val fondo: Color,
    val tinta: Color,
    val tinta2: Color,
    val linea: Color,
    val oscuro: Boolean,
)

@Immutable
data class Estratos(
    val general: Estrato,
    val avisos: Estrato,
    val sonido: Estrato,
    val copias: Estrato,
    val acerca: Estrato,
) {
    val todos: List<Estrato> get() = listOf(general, avisos, sonido, copias, acerca)
}

/** La paleta con nombre del catálogo §1.1, sin cambiar ningún nombre. Solo modo claro (D2): el oscuro será otra instancia. */
@Immutable
data class BitoColores(
    val cielo: Color,
    val costra: Color,
    val tAlta: Color,
    val tAlta2: Color,
    val hornacina: Color,
    val tMedia: Color,
    val tMedia2: Color,
    val tMediaCamino: Color,
    val tHonda: Color,
    val tHonda2: Color,
    val roca: Color,
    val raiz: Color,
    val raizClara: Color,
    val agua: Color,
    val aguaHonda: Color,
    val piedra: Color,
    val piedraClara: Color,
    val canto: Color,
    val campo: Color,
    val tinta: Color,
    val tintaSuave: Color,
    val tintaSobreTAlta2: Color,
    val tintaEstratoClaro: Color,
    val tintaClara: Color,
    val tintaClaraSuave: Color,
    val contorno: Color,
    val oro: Color,
    val oroHondo: Color,
    val oroTexto: Color,
    val hielo: Color,
    val copo: Color,
    val vetaFria: Color,
    val peligro: Color,
    val peligroFondo: Color,
    val peligroTexto: Color,
    val okTexto: Color,
    val velo: Color,
    val sombraFlota: Color,
    val estratos: Estratos,
)

private val LINEA_CLARA = Color(0x292E241C) // rgba(46,36,28,.16)
private val LINEA_OSCURA = Color(0x29F4EAD8) // rgba(244,234,216,.16)

val BitoClaro =
    BitoColores(
        cielo = Color(0xFFCFE2D8),
        costra = Color(0xFFB8956F),
        tAlta = Color(0xFFE4CFB4),
        tAlta2 = Color(0xFFDBC2A1),
        hornacina = Color(0xFFCDAE8B),
        tMedia = Color(0xFF8A6447),
        tMedia2 = Color(0xFF6E4E39),
        tMediaCamino = Color(0xFF7A5740),
        tHonda = Color(0xFF4B3628),
        tHonda2 = Color(0xFF3A2B21),
        roca = Color(0xFF2F241C),
        raiz = Color(0xFF8E6748),
        raizClara = Color(0xFFE9D3A8),
        agua = Color(0xFF7DB6C9),
        aguaHonda = Color(0xFF4F8FA6),
        piedra = Color(0xFFA89C8E),
        piedraClara = Color(0xFFDDD5CA),
        canto = Color(0xFFF3E7D3),
        campo = Color(0xFFFFFBF4),
        tinta = Color(0xFF2E241C),
        tintaSuave = Color(0xFF6B5644),
        tintaSobreTAlta2 = Color(0xFF5E4A3A),
        tintaEstratoClaro = Color(0xFF4E3D30),
        tintaClara = Color(0xFFF4EAD8),
        tintaClaraSuave = Color(0xFFCDB89C),
        contorno = Color(0xFF4A2C22),
        oro = Color(0xFFE2B04A),
        oroHondo = Color(0xFFA9792A),
        oroTexto = Color(0xFF7A5418),
        hielo = Color(0xFFD6ECF2),
        copo = Color(0xFF2F6E86),
        vetaFria = Color(0xFFCFC5B8),
        peligro = Color(0xFF8E2F22),
        peligroFondo = Color(0xFFF6DCD5),
        peligroTexto = Color(0xFF7A2418),
        okTexto = Color(0xFF2F6B45),
        // #1E1610 al 38 %
        velo = Color(0x611E1610),
        // #2E241C al 18 %
        sombraFlota = Color(0x2E2E241C),
        estratos =
            Estratos(
                general = Estrato(Color(0xFFE4CFB4), Color(0xFF2E241C), Color(0xFF5E4A3A), LINEA_CLARA, oscuro = false),
                avisos = Estrato(Color(0xFFDBC2A1), Color(0xFF2E241C), Color(0xFF4E3D30), LINEA_CLARA, oscuro = false),
                sonido = Estrato(Color(0xFFCDAE8B), Color(0xFF2E241C), Color(0xFF4E3D30), LINEA_CLARA, oscuro = false),
                copias = Estrato(Color(0xFF6E4E39), Color(0xFFF4EAD8), Color(0xFFE9D3A8), LINEA_OSCURA, oscuro = true),
                acerca = Estrato(Color(0xFF4B3628), Color(0xFFF4EAD8), Color(0xFFCDB89C), LINEA_OSCURA, oscuro = true),
            ),
    )

/** Colores de escena del catálogo §1.1. Los de Camino, la tienda y el cierre aún no se usan: están para que las piezas siguientes no inventen valores. */
object Escena {
    /** Paradas del reloj de cielo (minuto del día → color), `CIELO_RELOJ` de sistema.js. */
    val relojCielo: List<Pair<Int, Color>> =
        listOf(
            0 to Color(0xFF26305A),
            300 to Color(0xFF34416F),
            390 to Color(0xFFE9B48A),
            480 to Color(0xFFBFDCE0),
            960 to Color(0xFFBFDCE0),
            1110 to Color(0xFFE8A070),
            1230 to Color(0xFF3E4A80),
            1440 to Color(0xFF26305A),
        )
    val sol = Color(0xFFF2B544)
    val luna = Color(0xFFE4E0F2)

    val monedaClaro = Color(0xFFFBE3A0)
    val monedaHondo = Color(0xFFB07A26)
    val monedaCanto = Color(0xFF8C5F1C)

    val caminoManana = listOf(Color(0xFFF1D2B2), Color(0xFFDCE6D6))
    val caminoMananaLuz = Color(0x38FFCDA0)
    val caminoDia = listOf(Color(0xFFA6CDE0), Color(0xFFCFE2D8))
    val caminoTarde = listOf(Color(0xFFDC9670), Color(0xFFEFCFAE))
    val caminoTardeLuz = Color(0x4DE28454)
    val caminoNoche = listOf(Color(0xFF1F2742), Color(0xFF3A4670))

    val tiendaDia = Color(0xFFFFFFFF)
    val tiendaTarde = listOf(Color(0xFFFFCF9A), Color(0xFFFFDDB8))
    val tiendaNoche = listOf(Color(0xFF31406E), Color(0xFF4B5A8A))

    val cierreTarde = listOf(Color(0x9EDC9670), Color(0x4DE28454))
    val cierreNoche = listOf(Color(0xFF31406E), Color(0xFF4B5A8A))
    val cierreTierraNoche = Color(0xFF3A4670)
    val cierreFiltracion = Color(0x6B7DB6C9)

    @Immutable
    data class Familia(val anillo: Color, val disco: Color)

    val rachas = Familia(Color(0xFFDA6F49), Color(0xFFFBE6DA))
    val constancia = Familia(Color(0xFF669B6A), Color(0xFFE4EFDF))
    val momentos = Familia(Color(0xFF515D6A), Color(0xFFE2E8E6))
    val moldeBloqueado = Color(0xFFECE3D5)
}

val LocalBitoColores = staticCompositionLocalOf { BitoClaro }

/** El estrato en el que se pinta algo (Ajustes): las filas toman de aquí su tinta y su línea. Null fuera de Ajustes. */
val LocalEstrato = staticCompositionLocalOf<Estrato?> { null }
