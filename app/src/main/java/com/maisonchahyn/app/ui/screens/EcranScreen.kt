package com.maisonchahyn.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maisonchahyn.app.AppState
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.launcher.AppRepository
import com.maisonchahyn.app.launcher.Dock
import com.maisonchahyn.app.launcher.IconShapes
import com.maisonchahyn.app.launcher.LauncherActivity
import com.maisonchahyn.app.system.SystemTools
import com.maisonchahyn.app.toast
import com.maisonchahyn.app.ui.Accent
import com.maisonchahyn.app.ui.BodyText
import com.maisonchahyn.app.ui.Chips
import com.maisonchahyn.app.ui.GlassCard
import com.maisonchahyn.app.ui.IosGreen
import com.maisonchahyn.app.ui.IosOrange
import com.maisonchahyn.app.ui.IosPink
import com.maisonchahyn.app.ui.IosTeal
import com.maisonchahyn.app.ui.LiquidGlass
import com.maisonchahyn.app.ui.PrimaryButton
import com.maisonchahyn.app.ui.ScreenColumn
import com.maisonchahyn.app.ui.SecondaryButton
import com.maisonchahyn.app.ui.SettingSlider
import com.maisonchahyn.app.ui.SettingSwitch
import com.maisonchahyn.app.widget.ClockWidget

val tintColors = listOf(
    0xFF0A84FF, 0xFF64D2FF, 0xFF30D158, 0xFFFFD60A, 0xFFFF9F0A, 0xFFFF375F, 0xFFBF5AF2, 0xFFFFFFFF
).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EcranScreen() {
    val ctx = LocalContext.current
    val tick = AppState.resumeTick.intValue
    LaunchedEffect(Unit) { if (AppRepository.apps.value.isEmpty()) AppRepository.load(ctx.applicationContext) }
    val apps = AppRepository.apps.value
    val isDefault = remember(tick) { SystemTools.isDefaultLauncher(ctx) }
    val homeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        AppState.resumeTick.intValue++
        if (!SystemTools.isDefaultLauncher(ctx)) SystemTools.openHomeSettings(ctx)
    }

    ScreenColumn("Écran d'accueil", "Icônes, dock, widgets et grille — façon iPhone.") {
        // Aperçu
        LiquidGlass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aperçu", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                val shape = IconShapes.of(Prefs.lIconShape.value)
                val size = (Prefs.lIconSize.value * 0.85f).dp
                val tinted = Prefs.lIconStyle.value == "teinte"
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    apps.take(Prefs.lColumns.value.coerceIn(3, 6)).forEach { app ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 8.dp)) {
                            Box(Modifier.size(size).clip(shape).background(Color(0xFF1C1C1E))) {
                                if (tinted && app.mono != null) {
                                    Image(app.mono, null, Modifier.size(size), colorFilter = ColorFilter.tint(Color(Prefs.lTintColor.value)))
                                } else {
                                    Image(app.icon, null, Modifier.size(size), contentScale = ContentScale.Crop)
                                }
                            }
                            if (Prefs.lShowLabels.value) {
                                Text(app.label, color = Color.White, fontSize = (Prefs.lLabelSize.value * 0.85f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        GlassCard(title = "Maison comme écran d'accueil", icon = Icons.Rounded.Home, iconColor = Accent) {
            BodyText(
                if (isDefault) "✅ Maison est ton écran d'accueil. Appuie sur le bouton Accueil pour le voir."
                else "Remplace One UI Home par l'écran d'accueil iPhone de Maison (tu peux revenir en arrière à tout moment)."
            )
            if (!isDefault) PrimaryButton("Définir comme écran d'accueil") {
                try {
                    homeLauncher.launch(SystemTools.homeRoleIntent(ctx))
                } catch (e: Throwable) {
                    SystemTools.openHomeSettings(ctx)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Voir l'aperçu", Modifier.weight(1f)) {
                    ctx.startActivity(Intent(ctx, LauncherActivity::class.java))
                }
                SecondaryButton("Revenir à One UI", Modifier.weight(1f)) { SystemTools.openHomeSettings(ctx) }
            }
        }

        GlassCard(title = "Icônes", icon = Icons.Rounded.Apps, iconColor = IosPink) {
            Text("Forme", color = Color.White, fontSize = 16.sp)
            Chips(IconShapes.options, Prefs.lIconShape.value) { Prefs.lIconShape.value = it }
            Text("Style", color = Color.White, fontSize = 16.sp)
            Chips(
                listOf("couleur" to "En couleur", "teinte" to "Teintées (iOS 18)"),
                Prefs.lIconStyle.value
            ) { Prefs.lIconStyle.value = it }
            if (Prefs.lIconStyle.value == "teinte") {
                Text("Couleur de teinte", color = Color.White, fontSize = 16.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tintColors.forEach { c ->
                        val sel = Prefs.lTintColor.value == c
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (sel) 3.dp else 0.dp, Color.White, CircleShape)
                                .clickable { Prefs.lTintColor.value = c }
                        )
                    }
                }
            }
            SettingSlider("Taille des icônes", Prefs.lIconSize.value.toFloat(), 44f..76f, "${Prefs.lIconSize.value} dp") {
                Prefs.lIconSize.value = it.toInt()
            }
            SettingSlider("Colonnes", Prefs.lColumns.value.toFloat(), 3f..6f, "${Prefs.lColumns.value}", steps = 2) {
                Prefs.lColumns.value = it.toInt()
            }
            SettingSwitch("Afficher les noms", checked = Prefs.lShowLabels.value) { Prefs.lShowLabels.value = it }
            if (Prefs.lShowLabels.value) {
                SettingSlider("Taille des noms", Prefs.lLabelSize.value.toFloat(), 9f..15f, "${Prefs.lLabelSize.value} sp", steps = 5) {
                    Prefs.lLabelSize.value = it.toInt()
                }
            }
        }

        GlassCard(title = "Dock, recherche et pages", icon = Icons.Rounded.Dock, iconColor = IosTeal) {
            SettingSwitch("Dock en verre liquide", "Tes 4–5 applis favorites en bas", Prefs.lDock.value) { Prefs.lDock.value = it }
            SettingSwitch("Widget horloge en verre", "Heure, date et batterie en haut de la 1re page", Prefs.lClock.value) { Prefs.lClock.value = it }
            SettingSwitch("Bouton « Rechercher »", "Comme sur iOS, au-dessus du dock", Prefs.lSearchBar.value) { Prefs.lSearchBar.value = it }
            SettingSwitch("Bibliothèque d'apps", "Dernière page : applis classées par catégorie", Prefs.lLibrary.value) { Prefs.lLibrary.value = it }
            Text("Glisser vers le bas", color = Color.White, fontSize = 16.sp)
            Chips(listOf("notifications" to "Notifications", "recherche" to "Recherche"), Prefs.lSwipeDown.value) {
                Prefs.lSwipeDown.value = it
            }
            SettingSwitch("Utiliser le fond d'écran du système", "Sinon : le fond choisi dans Maison (meilleur flou)", Prefs.lUseSystemWallpaper.value) {
                Prefs.lUseSystemWallpaper.value = it
            }
            SettingSlider("Assombrir le fond", Prefs.lDimWallpaper.value, 0f..0.6f, "${(Prefs.lDimWallpaper.value * 100).toInt()} %") {
                Prefs.lDimWallpaper.value = it
            }
            SecondaryButton("Réinitialiser le dock", Modifier.fillMaxWidth()) {
                Dock.reset(); ctx.toast("Dock réinitialisé")
            }
        }

        val hidden = Prefs.lHidden.value
        GlassCard(title = "Applis masquées (${hidden.size})", icon = Icons.Rounded.VisibilityOff, iconColor = IosOrange) {
            if (hidden.isEmpty()) BodyText("Aucune. Appui long sur une icône de l'écran d'accueil → « Masquer l'appli ».")
            hidden.forEach { key ->
                val app = apps.firstOrNull { it.key == key }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (app != null) {
                        Image(app.icon, null, Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(app?.label ?: key, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SecondaryButton("Afficher") { Prefs.lHidden.value = Prefs.lHidden.value - key }
                }
            }
        }

        GlassCard(title = "Widget pour One UI Home", icon = Icons.Rounded.Widgets, iconColor = IosGreen) {
            BodyText("Tu préfères garder l'écran d'accueil Samsung ? Ajoute-y quand même l'horloge en verre de Maison.")
            SecondaryButton("Ajouter le widget horloge", Modifier.fillMaxWidth()) {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ok = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr.isRequestPinAppWidgetSupported &&
                    mgr.requestPinAppWidget(ComponentName(ctx, ClockWidget::class.java), null, null)
                if (!ok) ctx.toast("Appui long sur l'écran d'accueil Samsung → Widgets → Maison Chahyn")
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.2f), Color.Transparent)))
        )
    }
}
