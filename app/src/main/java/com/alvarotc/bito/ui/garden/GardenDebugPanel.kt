package com.alvarotc.bito.ui.garden

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.GardenLight
import com.alvarotc.bito.domain.model.GardenPlot
import com.alvarotc.bito.domain.model.GardenSeason
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.LightPhase
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Tinta

/** [count] parcelas abiertas: todas en árbol menos la última, en [lastStage]. */
internal fun plotsWith(
    count: Int,
    lastStage: GrowthStage,
): List<GardenPlot> =
    GardenSpecies.entries.take(count.coerceIn(1, GardenSpecies.entries.size)).let { species ->
        species.mapIndexed { i, s -> GardenPlot(s, if (i < species.size - 1) GrowthStage.TREE else lastStage) }
    }

/**
 * Solo en la build de depuración (§6.3, comprobación de §9.2): fuerza etapas, mustio, luz,
 * estación y especiales. Los textos van en línea a propósito: nunca los ve un usuario.
 */
@Composable
internal fun GardenDebugPanel(
    current: GardenState,
    onForce: (GardenState?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    BitoCard(modifier = modifier.fillMaxWidth().testTag("garden-debug"), onClick = { open = !open }) {
        Text("Depuración del jardín", style = MaterialTheme.typography.titleSmall, color = Tinta)
        if (!open) return@BitoCard
        val last = current.plots.last().stage
        DebugChoices("Parcelas", (1..6).map { "$it" }, current.plots.size - 1) { onForce(current.copy(plots = plotsWith(it + 1, last))) }
        DebugChoices("Etapa", GrowthStage.entries.map { it.name }, last.ordinal) {
            onForce(current.copy(plots = plotsWith(current.plots.size, GrowthStage.entries[it])))
        }
        DebugChoices("Mustio", (0..3).map { "$it" }, current.wilt) { onForce(current.copy(wilt = it)) }
        DebugChoices("Luz", LightPhase.entries.map { it.name }, current.light.phase.ordinal) {
            val phase = LightPhase.entries[it]
            onForce(current.copy(light = GardenLight(phase, phase, phase, 0f)))
        }
        DebugChoices("Transición", listOf("no", "a la siguiente"), if (current.light.from != current.light.to) 1 else 0) {
            val phase = current.light.phase
            val next = LightPhase.entries[(phase.ordinal + 1) % LightPhase.entries.size]
            onForce(current.copy(light = if (it == 0) GardenLight(phase, phase, phase, 0f) else GardenLight(phase, phase, next, 0.5f)))
        }
        DebugChoices("Estación", GardenSeason.entries.map { it.name }, current.season.ordinal) {
            onForce(current.copy(season = GardenSeason.entries[it]))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GardenSpecial.entries.forEach { special ->
                val on = special in current.specials
                DebugChip(special.key, on) {
                    onForce(current.copy(specials = if (on) current.specials - special else current.specials + special))
                }
            }
        }
        GhostPillButton("Volver al jardín de verdad", onClick = { onForce(null) }, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun DebugChoices(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = Tinta, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, option -> DebugChip(option, index == selected) { onSelect(index) } }
    }
}

@Composable
private fun DebugChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) Hoja else Tinta,
        modifier =
            Modifier
                .border(1.dp, if (selected) Hoja else Borde, CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
