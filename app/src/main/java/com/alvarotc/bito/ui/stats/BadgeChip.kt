package com.alvarotc.bito.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/**
 * One badge in the Logros section's [androidx.compose.foundation.layout.FlowRow] wall: unlocked
 * chips ride HojaTinte with the badge's own icon and name in Hoja/Tinta, locked ones stay a
 * plain Tarjeta+Borde pill in TintaSuave with a trailing [BitoIcons.Lock].
 */
@Composable
fun BadgeChip(
    badge: BadgeUi,
    modifier: Modifier = Modifier,
) {
    val unlocked = badge.unlockedAtMillis != null
    val contentColor = if (unlocked) Tinta else TintaSuave
    val iconTint = if (unlocked) Hoja else TintaSuave
    Surface(
        modifier = modifier.testTag("badge-${badge.def.id}"),
        shape = CircleShape,
        color = if (unlocked) HojaTinte else Tarjeta,
        border = if (unlocked) null else BorderStroke(1.dp, Borde),
    ) {
        Row(
            // [E]: Icon(null) + Text(name) [+ Icon(Lock, null)] — no onClick to auto-merge them.
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(BadgeStrings.badgeIcon(badge.def), contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            Text(
                stringResource(BadgeStrings.badgeNameRes(badge.def.id)),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
            if (!unlocked) {
                Icon(BitoIcons.Lock, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(12.dp))
            }
        }
    }
}
