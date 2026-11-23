package com.aiza.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aiza.app.ui.AizaNavHost
import com.aiza.app.ui.theme.AizaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AizaTheme {
                AizaNavHost()
            }
        }
    }
}
