package com.alvarotc.bito

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alvarotc.bito.ui.BitoNavHost
import com.alvarotc.bito.ui.theme.BitoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitoTheme {
                BitoNavHost((application as BitoApp).container)
            }
        }
    }
}
