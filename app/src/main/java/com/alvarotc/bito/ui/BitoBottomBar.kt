package com.alvarotc.bito.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/** Lives above the NavHost (in the shared Scaffold) so it survives route changes. */
@Composable
fun BitoBottomBar(
    currentRoute: String?,
    onToday: () -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().padding(20.dp).testTag("bottom-bar"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = Tarjeta, border = BorderStroke(1.dp, Borde)) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (currentRoute == "today") {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(HojaTinte),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(BitoIcons.Home, contentDescription = stringResource(R.string.nav_today), tint = Tinta)
                    }
                } else {
                    Icon(
                        BitoIcons.Home,
                        contentDescription = stringResource(R.string.nav_today),
                        tint = TintaSuave,
                        modifier = Modifier.size(24.dp).clickable(onClick = onToday),
                    )
                }
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
                if (currentRoute == "settings") {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(HojaTinte),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(BitoIcons.Settings, contentDescription = stringResource(R.string.nav_settings), tint = Tinta)
                    }
                } else {
                    Icon(
                        BitoIcons.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        tint = TintaSuave,
                        modifier = Modifier.size(24.dp).clickable(onClick = onSettings),
                    )
                }
            }
        }
    }
}
