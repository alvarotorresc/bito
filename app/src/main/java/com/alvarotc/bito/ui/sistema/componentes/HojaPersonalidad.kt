package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas

/** 3.4, D14: Habi grande y sin accesorios en su hornacina con la cara de la voz; tres filas de texto con su frase. */
@Composable
fun HojaPersonalidad(
    abierta: Boolean,
    actual: Personality,
    alElegir: (Personality) -> Unit,
    alCerrar: () -> Unit,
) {
    var saltos by remember { mutableIntStateOf(0) }
    val colores = BitoTema.colores
    val voces =
        listOf(
            Triple(Personality.NEUTRA, R.string.personality_neutra, R.string.sistema_b4c_voz_neutra_ej),
            Triple(Personality.CHEERLEADER, R.string.personality_cheerleader, R.string.sistema_b4c_voz_cheerleader_ej),
            Triple(Personality.SARGENTO, R.string.personality_sargento, R.string.sistema_b4c_voz_sargento_ej),
        )
    val opciones =
        voces.map { (p, nombre, ejemplo) -> OpcionAgua(p.name, stringResource(nombre), stringResource(ejemplo)) }
    Hoja(
        abierta = abierta,
        alCerrar = alCerrar,
        etiqueta = stringResource(R.string.general_section_title),
        titulo = stringResource(R.string.sistema_b4c_personalidad_titulo),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = Medidas.e12), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(Medidas.hornacinaPersonalidad)
                    .clip(BitoFormas.hornacina)
                    .background(colores.hornacina)
                    .border(Medidas.linea, colores.contorno, BitoFormas.hornacina),
            ) {
                HabiAvatar(
                    spec = HabiSpec(Mood.NORMAL, actual, EquippedSet()),
                    modifier = Modifier.fillMaxSize(),
                    nudge = saltos,
                )
            }
        }
        ElegirConAgua(
            opciones = opciones,
            elegida = actual.name,
            alElegir = { clave ->
                saltos++
                alElegir(Personality.valueOf(clave))
            },
        )
    }
}
