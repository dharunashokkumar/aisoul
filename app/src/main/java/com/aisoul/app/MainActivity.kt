package com.aisoul.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aisoul.app.ui.AiSoulNav
import com.aisoul.app.ui.theme.AiSoulTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiSoulTheme {
                AiSoulNav((application as AiSoulApp).container)
            }
        }
    }
}
