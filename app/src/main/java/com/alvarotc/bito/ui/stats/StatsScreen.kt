package com.alvarotc.bito.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tinta

/** Skeleton for the Stats tab (M5 Task 4) — Task 6 fills in the real content. */
@Composable
fun StatsScreen() {
    Scaffold(containerColor = Papel) { padding ->
        Column(Modifier.padding(padding).padding(20.dp)) {
            Text(stringResource(R.string.nav_stats), style = MaterialTheme.typography.headlineLarge, color = Tinta)
        }
    }
}
