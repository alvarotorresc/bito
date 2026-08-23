package com.alvarotc.bito.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostIconButton
import com.alvarotc.bito.ui.components.formatDayMedium
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/** The Badges secondary screen: every catalog badge, grouped by family, unlocked or not. */
@Composable
fun BadgesScreen(
    viewModel: BadgesViewModel,
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
            BadgesHeader(onBack)
            BadgesHero(state.unlocked, state.total)
            state.groups.forEach { group -> BadgeFamilySection(group) }
        }
    }
}

@Composable
private fun BadgesHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostIconButton(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), onClick = onBack)
        Text(
            stringResource(R.string.badges_title),
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
    }
}

/**
 * Trophy, then the dato grande, then the Logros caption — same shape as [RecordsScreen]'s
 * `BestRecordHero`: the unlocked count on its own at `displayLarge` 56dp, `badge_of_total`
 * riding its baseline as the unit (mirrors [periodUnitRes] there), then [stats_badges_title] as
 * the closing caption below both.
 */
@Composable
private fun BadgesHero(
    unlocked: Int,
    total: Int,
) {
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            // [E]: Icon(Trophy, null) + 3 plain Text stops (count, "of N", "Badges" caption), no
            // onClick to auto-merge them — same shape as RecordsScreen's BestRecordHero.
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(BitoIcons.Trophy, contentDescription = null, tint = Brasa, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$unlocked",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
                    color = Brasa,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.badge_of_total, total),
                    style = MaterialTheme.typography.labelMedium,
                    color = TintaSuave,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.stats_badges_title), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
    }
}

/** One family's slice: its label outside the card (rule: bare titles sit above their card, per [StatsScreen]'s StreaksSection), then a card of rows. */
@Composable
private fun BadgeFamilySection(group: BadgeGroupUi) {
    Column {
        Text(
            stringResource(BadgeStrings.badgeFamilyRes(group.family)),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp),
            color = TintaSuave,
        )
        Spacer(Modifier.height(12.dp))
        BitoCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                group.badges.forEach { badge -> BadgeListRow(badge) }
            }
        }
    }
}

/**
 * Icon + name + caption (unlock date, or how to earn it); locked rows dim to 0.6 alpha with a
 * trailing lock. [BadgeRowUi.unlockedDay] is already a [com.alvarotc.bito.domain.model.LogicalDay]
 * resolved through the day-cutoff model by [buildBadgesUiState] — this only formats it.
 */
@Composable
private fun BadgeListRow(badge: BadgeRowUi) {
    val unlockedDay = badge.unlockedDay
    val unlocked = unlockedDay != null
    Row(
        // [E]: Icon(null) + name + caption (unlock date OR how-to-earn) [+ Icon(Lock, null)] —
        // no onClick to auto-merge them into "<name>, unlocked on <date>" / "<name>, locked —
        // <how to earn>" (the caption Text already carries that exact wording).
        Modifier.fillMaxWidth().alpha(if (unlocked) 1f else 0.6f).testTag("badge-${badge.def.id}").semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            BadgeStrings.badgeIcon(badge.def),
            contentDescription = null,
            tint = if (unlocked) Hoja else TintaSuave,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(BadgeStrings.badgeNameRes(badge.def.id)),
                style = MaterialTheme.typography.bodyLarge,
                color = Tinta,
            )
            val caption =
                if (unlockedDay != null) {
                    stringResource(R.string.badge_unlocked_on, formatDayMedium(unlockedDay))
                } else {
                    stringResource(R.string.badge_how_prefix, stringResource(BadgeStrings.badgeHowRes(badge.def.id)))
                }
            Text(caption, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
        if (!unlocked) {
            Icon(BitoIcons.Lock, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(16.dp))
        }
    }
}
