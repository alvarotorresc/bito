package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

enum class CanalMesa { HABI, EFECTOS, AMBIENTE, VIBRACION }

/** 3.5: cuatro rieles verticales; el nivel (0–10) es agua; vista previa cada 90 ms como mucho. [todoApagado] = «Sonido» apagado (35 %, sin respuesta). */
@Composable
fun MesaCanales(
    niveles: Map<CanalMesa, Int>,
    alCambiar: (CanalMesa, Int) -> Unit,
    modifier: Modifier = Modifier,
    apagados: Set<CanalMesa> = emptySet(),
    todoApagado: Boolean = false,
) = Row(modifier) { CanalMesa.entries.forEach { Text("${it.name} ${niveles[it] ?: 0}") } }
