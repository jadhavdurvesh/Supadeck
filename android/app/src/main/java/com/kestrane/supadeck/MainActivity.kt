package com.kestrane.supadeck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kestrane.supadeck.ui.SupaDeckApp
import com.kestrane.supadeck.ui.SupaDeckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SupaDeckTheme {
                Surface(Modifier.fillMaxSize()) { SupaDeckApp() }
            }
        }
    }
}
