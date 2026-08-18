package com.alvarotc.bito.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.Totals
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostIconButton
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/** One tile of the Numbers grid: a big number and the small caption naming it. */
private data class NumberTile(val value: String, val labelRes: Int)

/** The Numbers secondary screen: a 2-column grid, one mini-card per [Totals] field. */
@Composable
fun NumbersScreen(
    viewModel: NumbersViewModel,
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
            NumbersHeader(onBack)
            NumbersGrid(state.totals)
        }
    }
}

@Composable
private fun NumbersHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostIconButton(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), onClick = onBack)
        Text(
            stringResource(R.string.stats_teaser_numbers),
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun NumbersGrid(totals: Totals) {
    val placeholder = stringResource(R.string.no_data_placeholder)
    val tiles =
        listOf(
            NumberTile("${totals.entriesCount}", R.string.numbers_entries),
            NumberTile("${totals.perfectDays}", R.string.stats_perfect_days_label),
            NumberTile("${totals.pointsEarned}", R.string.numbers_points_earned),
            NumberTile("${totals.balance}", R.string.numbers_balance),
            NumberTile("${totals.freezersUsed}", R.string.numbers_freezers_used),
            NumberTile("${totals.freezersOwned}", R.string.numbers_freezers_owned),
            NumberTile("${totals.activeHabits}", R.string.numbers_active_habits),
            NumberTile("${totals.pausedHabits}", R.string.numbers_paused_habits),
            NumberTile("${totals.archivedHabits}", R.string.numbers_archived_habits),
            NumberTile(totals.daysSinceFirstHabit?.toString() ?: placeholder, R.string.numbers_days_since_first),
        )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { tile -> NumberTileCard(tile, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NumberTileCard(
    tile: NumberTile,
    modifier: Modifier = Modifier,
) {
    BitoCard(modifier = modifier.fillMaxWidth()) {
        Text(
            tile.value,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            color = Tinta,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(tile.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = TintaSuave,
        )
    }
}
