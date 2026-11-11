package com.chahyn.boost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableIntStateOf

object AppState {
    val resumeTick = mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { BoostScreen() } }
    }

    override fun onResume() {
        super.onResume()
        AppState.resumeTick.intValue++
    }
}
