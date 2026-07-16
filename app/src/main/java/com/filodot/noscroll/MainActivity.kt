package com.filodot.noscroll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.filodot.noscroll.ui.NoScrollApp
import com.filodot.noscroll.ui.theme.NoScrollTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoScrollTheme {
                NoScrollApp(graph = (application as NoScrollApplication).runtime.appGraph)
            }
        }
    }
}
