package com.alvarotc.bito.ui.theme

import androidx.compose.ui.graphics.Color
import com.alvarotc.bito.ui.sistema.BitoClaro
import com.alvarotc.bito.ui.sistema.Escena

// Alias de la paleta de la Bito 2 (spec pieza 2, §4.2.5, decisión 1): las pantallas que aún no se han
// rehecho compilan sin tocarlas y no conviven dos paletas. Se borran cuando la última deje de usarlos.
val Papel = BitoClaro.tAlta
val Tarjeta = BitoClaro.canto
val Borde = BitoClaro.hornacina
val Tinta = BitoClaro.tinta
val TintaSuave = BitoClaro.tintaSuave
val Hoja = BitoClaro.agua
val HojaTinte = BitoClaro.hielo
val Brasa = Escena.rachas.anillo
val BrasaTinte = Escena.rachas.disco
val Peligro = BitoClaro.peligro

// Piel de Habi y mofletes: son del arte, no de la interfaz; no cambian.
val HabiSalvia = Color(0xFFA9C9A1)
val Mofletes = Color(0xFFE8B4A8)
