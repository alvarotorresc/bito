@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/**
 * The store's shared bottom-sheet shell: [PurchaseSheet] and [FreezerSheet] both open a
 * `ModalBottomSheet(containerColor = Tarjeta)` around a padded, test-tagged `Column` — this is
 * that wrapper, extracted so neither sheet repeats it. [testTag] is what distinguishes them in
 * compose tests ("purchase-sheet" / "freezer-sheet") and is unchanged from before this refactor.
 */
@Composable
private fun BitoSheet(
    onDismiss: () -> Unit,
    testTag: String,
    verticalArrangement: Arrangement.Vertical,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(
            Modifier.padding(20.dp).testTag(testTag),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * The buy pill both sheets end on: tapping it always fires [onBuy] then [onDismiss] — the sheet
 * closes on tap regardless of whether the purchase itself is later confirmed or refused, which is
 * why [enabled] is what actually gates a real refusal (see [HabiViewModel.purchase]'s KDoc).
 */
@Composable
private fun BuyButton(
    text: String,
    enabled: Boolean,
    onBuy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PillButton(
        text,
        onClick = {
            onBuy()
            onDismiss()
        },
        enabled = enabled,
        modifier = modifier,
    )
}

/**
 * The store's purchase confirmation (mockups 4b affordable / 4c insufficient balance). The grid
 * never hides the price behind a refusal — this sheet is the ONLY place "te faltan X pts" shows
 * (GUIA Fidelidad M6 checkpoint ruling): Comprar disables itself on [balance] < [item].price and
 * the shortfall prints in `Brasa` under the buttons; affordable shows "saldo después" instead.
 */
@Composable
fun PurchaseSheet(
    item: CatalogItem,
    balance: Int,
    onBuy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val price = item.price ?: return // exclusives never reach a live preview — nothing to sell.
    val canAfford = balance >= price
    BitoSheet(onDismiss, testTag = "purchase-sheet", verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.store_buy_title, stringResource(itemNameRes(item.id))),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = Tinta,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(BitoIcons.Sparkle, contentDescription = null, tint = Hoja, modifier = Modifier.size(24.dp))
            Text(
                "$price",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                color = Tinta,
            )
            Text(stringResource(R.string.store_pts_label), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (canAfford) {
                stringResource(R.string.store_balance_after, balance - price)
            } else {
                stringResource(R.string.store_your_balance, balance)
            },
            style = MaterialTheme.typography.labelMedium,
            color = TintaSuave,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BuyButton(
                stringResource(R.string.store_buy_button),
                enabled = canAfford,
                onBuy = onBuy,
                onDismiss = onDismiss,
                modifier = Modifier.weight(1f).testTag("buy-item"),
            )
            GhostPillButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
        }
        if (!canAfford) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.store_missing_pts, price - balance),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = Brasa,
            )
        }
    }
}

/**
 * Buying a freezer: shows the running inventory and a price disabled when the balance is short.
 * Moved here from `ui.detail.DetailSheets` (docs/05 §1: freezer purchase moves to the Habi
 * store) — body, strings and testTags unchanged, only the file it lives in.
 */
@Composable
fun FreezerSheet(
    owned: Int,
    price: Int,
    balance: Int,
    onBuy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canSpend = balance >= price
    BitoSheet(onDismiss, testTag = "freezer-sheet", verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.freezer_sheet_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
        Text(stringResource(R.string.freezer_sheet_body), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
        Text(stringResource(R.string.freezers_owned_label, owned), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        BuyButton(
            text =
                if (canSpend) {
                    stringResource(R.string.buy_freezer_action, price)
                } else {
                    stringResource(R.string.buy_freezer_missing, price - balance)
                },
            enabled = canSpend,
            onBuy = onBuy,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxWidth().testTag("buy-freezer"),
        )
    }
}

/**
 * "How points are earned" — the balance chip's sheet (QA 2026-08-23). Every number renders from
 * [EconomyConfig]; nothing is baked into strings, the same rule the freezer price follows
 * (strings_habi.xml's own header note).
 */
@Composable
fun PointsInfoSheet(
    economy: EconomyConfig,
    onDismiss: () -> Unit,
) {
    BitoSheet(onDismiss, testTag = "points-info-sheet", verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.points_info_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = Tinta,
        )
        Spacer(Modifier.height(4.dp))
        PointsRow(stringResource(R.string.points_info_habit_done), economy.habitDonePoints)
        PointsRow(stringResource(R.string.points_info_perfect_day), economy.perfectDayPoints)
        PointsRow(stringResource(R.string.points_info_perfect_week), economy.perfectWeekPoints)
        PointsRow(stringResource(R.string.points_info_perfect_month), economy.perfectMonthPoints)
        economy.streakMilestonePoints.toSortedMap().forEach { (days, points) ->
            PointsRow(stringResource(R.string.points_info_streak_row, days), points)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.points_info_freezer_note, economy.freezerPrice),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = TintaSuave,
        )
    }
}

/** One earn rule: caption-style label left, bold "+N" in hoja right — merged into one TalkBack stop. */
@Composable
private fun PointsRow(
    label: String,
    points: Int,
) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Tinta,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.points_info_plus, points),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            color = Hoja,
        )
    }
}
