package com.alvarotc.bito.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
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
import java.text.NumberFormat

/** One tile of the Numbers grid: an icon naming the datum, a big number and the small caption below. */
private data class NumberTile(val value: String, val labelRes: Int, val icon: ImageVector)

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
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = NumberFormat.getIntegerInstance(locale)

    fun format(value: Int) = numberFormat.format(value)

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        NumbersSectionGroup(
            titleRes = R.string.numbers_section_log,
            tiles =
                listOf(
                    NumberTile(format(totals.entriesCount), R.string.numbers_entries, BitoIcons.Check),
                    NumberTile(format(totals.perfectDays), R.string.stats_perfect_days_label, BitoIcons.Flame),
                    NumberTile(
                        totals.daysSinceFirstHabit?.let { format(it) } ?: placeholder,
                        R.string.numbers_days_since_first,
                        BitoIcons.Home,
                    ),
                ),
        )
        NumbersSectionGroup(
            titleRes = R.string.numbers_section_rewards,
            tiles =
                listOf(
                    NumberTile(format(totals.pointsEarned), R.string.numbers_points_earned, BitoIcons.Trophy),
                    NumberTile(format(totals.balance), R.string.numbers_balance, BitoIcons.ChartColumn),
                    NumberTile(format(totals.freezersUsed), R.string.numbers_freezers_used, BitoIcons.Snowflake),
                    NumberTile(format(totals.freezersOwned), R.string.numbers_freezers_owned, BitoIcons.Snowflake),
                ),
        )
        NumbersSectionGroup(
            titleRes = R.string.numbers_section_habits,
            tiles =
                listOf(
                    NumberTile(format(totals.activeHabits), R.string.numbers_active_habits, BitoIcons.Home),
                    NumberTile(format(totals.pausedHabits), R.string.numbers_paused_habits, BitoIcons.Pause),
                    NumberTile(format(totals.archivedHabits), R.string.numbers_archived_habits, BitoIcons.Archive),
                ),
        )
    }
}

/** A `labelMedium` section header above its own 2-col tile grid; an odd tile out stays half-width. */
@Composable
private fun NumbersSectionGroup(
    titleRes: Int,
    tiles: List<NumberTile>,
) {
    Column {
        Text(stringResource(titleRes), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { tile -> NumberTileCard(tile, modifier = Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NumberTileCard(
    tile: NumberTile,
    modifier: Modifier = Modifier,
) {
    BitoCard(
        // [E]: Icon(null) + Text(value) + Text(label), no explicit inner Column of its own to
        // attach mergeDescendants to (unlike BestRecordHero/BadgesHero) — BitoCard's own Surface
        // modifier is the merge boundary here.
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("number-tile-${tile.labelRes}"),
    ) {
        Icon(tile.icon, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            tile.value,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
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
