package com.chahyn.boost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppState {
    val resumeTick = mutableIntStateOf(0)
    /** Onglet demandé par l'agent ou un bouton. */
    val tab = mutableStateOf("accueil")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Profile.load(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
                if (!Profile.done.value) OnboardingScreen() else BoostApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppState.resumeTick.intValue++
    }
}

private val tabs = listOf(
    Triple("accueil", "⚡", "Accueil"),
    Triple("agent", "🤖", "Agent"),
    Triple("modes", "🎛️", "Modes"),
    Triple("stockage", "💾", "Stockage"),
)

@Composable
fun BoostApp() {
    val tab = AppState.tab.value
    Box(Modifier.fillMaxSize().background(Bg)) {
        Crossfade(tab, label = "tab") { t ->
            when (t) {
                "agent" -> AgentScreen()
                "modes" -> ModesScreen()
                "stockage" -> StorageScreen()
                else -> HomeScreen()
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF0181826))
                .padding(5.dp)
        ) {
            tabs.forEach { (id, emoji, label) ->
                val sel = id == tab
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (sel) Accent.copy(alpha = 0.28f) else Color.Transparent)
                        .clickable { AppState.tab.value = id }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(emoji, fontSize = 20.sp)
                    Text(label, fontSize = 11.sp, color = if (sel) Color.White else TextDim, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
