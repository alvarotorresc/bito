package com.alvarotc.bito.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Una vibracion corta y suave del Vibrator de verdad (QA 2026-08-23/24): performHapticFeedback
 * obedecia al ajuste del sistema de respuesta tactil, que casi todo el mundo tiene apagado, y
 * parecia que «no vibra». Quien llama decide si toca vibrar (el ajuste logHapticEnabled); esto
 * solo vibra. EFFECT_CLICK en API 29+, un pulso de 20 ms antes.
 */
@Composable
fun rememberSoftBuzz(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        val vibrator = vibratorOf(context)
        val buzz: () -> Unit = { vibrator.vibrate(softClick()) }
        buzz
    }
}

private fun vibratorOf(context: Context): Vibrator =
    if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

private fun softClick(): VibrationEffect =
    if (Build.VERSION.SDK_INT >= 29) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    } else {
        VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
    }
