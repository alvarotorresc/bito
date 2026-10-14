package com.alvarotc.bito.ui.settings.estratos

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alvarotc.bito.BuildConfig

@Composable
fun ContenidoAcerca(modifier: Modifier = Modifier) = Box(modifier)

@Composable
fun resumenAcerca(): String = BuildConfig.VERSION_NAME
