package com.alvarotc.bito.ui.widget

import android.os.Bundle
import androidx.activity.ComponentActivity

class TodayWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        finish()
    }
}
