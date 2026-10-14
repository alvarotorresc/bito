package com.alvarotc.bito.ui.sistema

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Galería de depuración (spec §4.6): cada componente, sonido y háptica, para afinar en el Pixel. La llena la Tarea 22. */
@Composable
fun GaleriaSistema(alCerrar: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BitoTema.colores.tAlta).padding(Medidas.margen)) {
        TextButton(onClick = alCerrar) { Text("Cerrar") }
    }
}
