package com.maisonchahyn.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maisonchahyn.app.ui.BackdropHost
import com.maisonchahyn.app.ui.LiquidGlass
import com.maisonchahyn.app.ui.MaisonTheme
import com.maisonchahyn.app.ui.WallpaperStore
import com.maisonchahyn.app.ui.hubBackdrop
import com.maisonchahyn.app.ui.screens.AccueilScreen
import com.maisonchahyn.app.ui.screens.ClavierScreen
import com.maisonchahyn.app.ui.screens.EcranScreen
import com.maisonchahyn.app.ui.screens.ModelesScreen
import com.maisonchahyn.app.ui.screens.StyleScreen
import com.maisonchahyn.app.ui.screens.SystemeScreen

class MainActivity : ComponentActivity() {
    private val requestedTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        enableEdgeToEdge()
        requestedTab.value = intent?.getStringExtra("tab")
        setContent { MaisonTheme { HubApp(requestedTab.value) { requestedTab.value = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("tab")?.let { requestedTab.value = it }
    }

    override fun onResume() {
        super.onResume()
        AppState.resumeTick.intValue++
    }
}

data class TabDef(val id: String, val label: String, val icon: ImageVector)

val tabs = listOf(
    TabDef("accueil", "Accueil", Icons.Rounded.Home),
    TabDef("modeles", "Modèles", Icons.Rounded.AutoAwesome),
    TabDef("ecran", "Écran", Icons.Rounded.PhoneIphone),
    TabDef("clavier", "Clavier", Icons.Rounded.Keyboard),
    TabDef("style", "Fonds", Icons.Rounded.Palette),
    TabDef("systeme", "Système", Icons.Rounded.Tune),
)

@Composable
fun HubApp(requested: String?, consumed: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Prefs.wallpaperPath.value, Prefs.wallpaperVersion.value) { WallpaperStore.ensure(ctx) }
    val img = WallpaperStore.image.value
    val backdrop = remember(img) { hubBackdrop(img) }
    var tab by rememberSaveable { mutableStateOf("accueil") }
    LaunchedEffect(requested) {
        if (requested != null && tabs.any { it.id == requested }) {
            tab = requested; consumed()
        }
    }

    BackdropHost(backdrop, Modifier.fillMaxSize()) {
        Crossfade(targetState = tab, label = "onglets") { t ->
            when (t) {
                "modeles" -> ModelesScreen()
                "ecran" -> EcranScreen()
                "clavier" -> ClavierScreen()
                "style" -> StyleScreen()
                "systeme" -> SystemeScreen()
                else -> AccueilScreen(onTab = { tab = it })
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            LiquidGlass(Modifier.fillMaxWidth(), shape = CircleShape, tintAlpha = 0.14f) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    tabs.forEach { t ->
                        val sel = t.id == tab
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (sel) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                                .clickable { tab = t.id }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                t.icon, t.label,
                                tint = if (sel) com.maisonchahyn.app.ui.Accent else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                t.label, fontSize = 10.sp, maxLines = 1,
                                color = if (sel) com.maisonchahyn.app.ui.Accent else Color.White,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
