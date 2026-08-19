@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("purchase-sheet"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                PillButton(
                    stringResource(R.string.store_buy_button),
                    onClick = {
                        onBuy()
                        onDismiss()
                    },
                    enabled = canAfford,
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("freezer-sheet"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.freezer_sheet_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Text(stringResource(R.string.freezer_sheet_body), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
            Text(stringResource(R.string.freezers_owned_label, owned), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            PillButton(
                text =
                    if (canSpend) {
                        stringResource(R.string.buy_freezer_action, price)
                    } else {
                        stringResource(R.string.buy_freezer_missing, price - balance)
                    },
                onClick = {
                    onBuy()
                    onDismiss()
                },
                enabled = canSpend,
                modifier = Modifier.fillMaxWidth().testTag("buy-freezer"),
            )
        }
    }
}
