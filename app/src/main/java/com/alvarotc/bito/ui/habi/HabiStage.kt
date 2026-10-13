package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Habi de pie en un hueco de [stageSize]: el círculo verde se fue con la pieza 1 (§3.6) y la sombra
 * ya es una capa del propio Habi. Lo usan el día sellado, el foco y la respiración; la pantalla de
 * Habi usa el jardín. [onTap], [delighted], [nudge], [animated] y [breath] pasan tal cual a [HabiAvatar].
 */
@Composable
fun HabiStage(
    spec: HabiSpec,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    stageSize: Dp = 220.dp,
    avatarSize: Dp = 150.dp,
    delighted: Boolean = false,
    nudge: Int = 0,
    animated: Boolean = true,
    breath: HabiBreath? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        // Conserva el hueco que ocupaba el escenario para que las pantallas no se muevan.
        Spacer(Modifier.size(stageSize).testTag("habi-stage"))
        HabiAvatar(
            spec = spec,
            modifier = Modifier.size(avatarSize),
            animated = animated,
            onTap = onTap,
            delighted = delighted,
            nudge = nudge,
            breath = breath,
        )
    }
}
