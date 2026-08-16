package com.alvarotc.bito.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostIconButton
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.BrasaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/** The Records secondary screen: the global best streak, then every habit's current vs. best. */
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = Papel) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RecordsHeader(onBack)
            val best = state.records.firstOrNull()
            if (best == null) {
                // In a data screen empty is poverty, not reward — the structure (the card) still
                // renders, only its body swaps for the empty line. Mirrors StatsScreen's streak wall.
                BitoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.records_empty), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
                }
            } else {
                BestRecordHero(best)
                RecordsList(state.records)
            }
        }
    }
}

@Composable
private fun RecordsHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostIconButton(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), onClick = onBack)
        Text(
            stringResource(R.string.stats_teaser_records),
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
    }
}

/** No accent card here — like [com.alvarotc.bito.ui.detail.DetailScreen]'s monument, the number is the protagonist. */
@Composable
private fun BestRecordHero(best: RecordRow) {
    Column {
        Text(stringResource(R.string.records_best_label), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Icon(BitoIcons.Flame, contentDescription = null, tint = Brasa, modifier = Modifier.height(40.dp))
            Spacer(Modifier.width(8.dp))
            Text("${best.best}", style = MaterialTheme.typography.displayLarge, color = Tinta)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(periodUnitRes(best.period)), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
        Spacer(Modifier.height(4.dp))
        Text(best.name, style = MaterialTheme.typography.titleMedium, color = Tinta)
    }
}

@Composable
private fun RecordsList(records: List<RecordRow>) {
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            records.forEach { row -> RecordLine(row) }
        }
    }
}

@Composable
private fun RecordLine(row: RecordRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, color = Tinta)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val currentLabel = "${row.current} ${stringResource(periodUnitRes(row.period))}"
                Text(currentLabel, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
                if (row.archived) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.records_archived_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = TintaSuave,
                    )
                }
            }
        }
        Row(
            Modifier.clip(CircleShape).background(BrasaTinte).padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                stringResource(R.string.record_chip, row.best),
                style = MaterialTheme.typography.labelMedium,
                color = Brasa,
            )
        }
    }
}

private fun periodUnitRes(period: Period): Int =
    when (period) {
        Period.DAY -> R.string.unit_days
        Period.WEEK -> R.string.unit_weeks
        Period.MONTH -> R.string.unit_months
    }
