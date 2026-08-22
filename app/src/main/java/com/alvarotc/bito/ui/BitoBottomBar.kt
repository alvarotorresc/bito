package com.alvarotc.bito.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/**
 * Lives above the NavHost (in the shared Scaffold) so it survives route changes.
 * Visual order: Hoy · Stats · «+» · Habi · Ajustes.
 */
@Composable
fun BitoBottomBar(
    currentRoute: String?,
    onToday: () -> Unit,
    onStats: () -> Unit,
    onCreate: () -> Unit,
    onHabi: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().padding(20.dp).testTag("bottom-bar"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = Tarjeta, border = BorderStroke(1.dp, Borde)) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NavSlot(BitoIcons.Home, stringResource(R.string.nav_today), currentRoute == "today", onToday)
                NavSlot(BitoIcons.ChartColumn, stringResource(R.string.nav_stats), currentRoute == "stats", onStats)
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Hoja)
                        .clickable(onClick = onCreate),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BitoIcons.Plus, contentDescription = stringResource(R.string.nav_new_habit), tint = Tarjeta)
                }
                NavSlot(BitoIcons.Habi, stringResource(R.string.nav_habi), currentRoute == "habi", onHabi)
                NavSlot(BitoIcons.Settings, stringResource(R.string.nav_settings), currentRoute == "settings", onSettings)
            }
        }
    }
}

/**
 * One bottom-bar destination: icon 20dp + label 12sp stacked, active = a HojaTinte pill wrapping
 * both, inactive = plain TintaSuave icon+label with no container. [Modifier.selectable] (not plain
 * `clickable`) is attached unconditionally, including on the already-selected slot — every
 * [BitoBottomBar] callback (`onToday`/`onStats`/`onHabi`/`onSettings`) is idempotent when the
 * target route is already current (`onToday` is a no-op `popBackStack`; the other three pass
 * `launchSingleTop = true`), so re-firing `onClick` on the active tab pushes nothing new. This is
 * what lets TalkBack announce the real "Tab, selected" state instead of the label alone.
 * [contentDescription] and the click action both land on this single container node (not on the
 * inner [Icon], whose own description is left null) so they keep resolving as one node, same as
 * the plain-glyph bar tests already depend on.
 */
@Composable
private fun NavSlot(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) Tinta else TintaSuave
    val base =
        Modifier
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .semantics { contentDescription = label }
            .then(if (selected) Modifier.clip(CircleShape).background(HojaTinte) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    Column(base, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            label,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            color = tint,
            maxLines = 1,
        )
    }
}
