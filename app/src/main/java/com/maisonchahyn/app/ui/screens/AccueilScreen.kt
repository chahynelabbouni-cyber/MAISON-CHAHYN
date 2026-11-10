package com.maisonchahyn.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardAlt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maisonchahyn.app.AppState
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.launcher.LauncherActivity
import com.maisonchahyn.app.system.SystemTools
import com.maisonchahyn.app.toast
import com.maisonchahyn.app.ui.ActionRow
import com.maisonchahyn.app.ui.Accent
import com.maisonchahyn.app.ui.AccentPurple
import com.maisonchahyn.app.ui.BodyText
import com.maisonchahyn.app.ui.GlassCard
import com.maisonchahyn.app.ui.IosGreen
import com.maisonchahyn.app.ui.IosIndigo
import com.maisonchahyn.app.ui.IosOrange
import com.maisonchahyn.app.ui.IosPink
import com.maisonchahyn.app.ui.IosTeal
import com.maisonchahyn.app.ui.PrimaryButton
import com.maisonchahyn.app.ui.ScreenColumn
import com.maisonchahyn.app.ui.SecondaryButton
import com.maisonchahyn.app.wallpaper.WallpaperGen
import kotlinx.coroutines.launch

/** Préréglage « iPhone » : tout ce qui se règle dans Maison d'un seul coup. */
fun applyIphonePreset() {
    Prefs.lColumns.value = 4
    Prefs.lIconSize.value = 60
    Prefs.lShowLabels.value = true
    Prefs.lLabelSize.value = 12
    Prefs.lIconShape.value = "squircle"
    Prefs.lIconStyle.value = "couleur"
    Prefs.lDock.value = true
    Prefs.lClock.value = true
    Prefs.lSearchBar.value = true
    Prefs.lLibrary.value = true
    Prefs.lUseSystemWallpaper.value = false
    Prefs.gBlur.value = 24
    Prefs.gTint.value = 0.16f
    Prefs.gDark.value = false
    Prefs.gBorder.value = 0.45f
    Prefs.gHighlight.value = true
    Prefs.gCorner.value = 26
    Prefs.kTheme.value = "verre"
    Prefs.kAutoCap.value = true
    Prefs.kDoubleSpace.value = true
    Prefs.kPreview.value = true
}

@Composable
fun AccueilScreen(onTab: (String) -> Unit) {
    val ctx = LocalContext.current
    val tick = AppState.resumeTick.intValue
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val homeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        AppState.resumeTick.intValue++
        if (r.resultCode != Activity.RESULT_OK && !SystemTools.isDefaultLauncher(ctx)) {
            ctx.toast("Choisis « Maison — Accueil iPhone » dans la liste")
            SystemTools.openHomeSettings(ctx)
        }
    }
    fun askHome() {
        try {
            homeLauncher.launch(SystemTools.homeRoleIntent(ctx))
        } catch (e: Throwable) {
            SystemTools.openHomeSettings(ctx)
        }
    }

    val wallOk = Prefs.wallpaperPath.value.isNotEmpty()
    val launcherOk = remember(tick) { SystemTools.isDefaultLauncher(ctx) }
    val imeOn = remember(tick) { SystemTools.imeEnabled(ctx) }
    val imeSel = remember(tick) { SystemTools.imeSelected(ctx) }
    val writeOk = remember(tick) { SystemTools.canWriteSettings(ctx) }
    val goodLock = remember(tick) { SystemTools.isInstalled(ctx, SystemTools.GOOD_LOCK) }
    val steps = listOf(wallOk, launcherOk, imeOn, imeSel, writeOk, goodLock)
    val done = steps.count { it }

    ScreenColumn("Maison Chahyn", "Ton Galaxy S26 Ultra, façon iPhone — de A à Z.") {
        GlassCard(title = "Style iPhone en 1 clic", icon = Icons.Rounded.AutoAwesome, iconColor = AccentPurple) {
            BodyText(
                "Applique d'un coup : fond d'écran « Verre liquide », icônes arrondies iPhone, dock et widgets " +
                    "en verre liquide, barre de recherche, clavier iPhone en verre. Ensuite, Android te demandera " +
                    "de choisir Maison comme écran d'accueil."
            )
            PrimaryButton(if (busy) "Application en cours…" else "✨ Tout appliquer", enabled = !busy) {
                busy = true
                scope.launch {
                    applyIphonePreset()
                    val spec = WallpaperGen.presets.first { it.id == "liquide_bleu" }
                    val bmp = WallpaperGen.renderFull(ctx, spec)
                    val ok = WallpaperGen.apply(ctx, bmp, WallpaperGen.BOTH, spec.name)
                    busy = false
                    ctx.toast(if (ok) "Fond d'écran appliqué ✓" else "Réglages appliqués ✓")
                    if (!SystemTools.isDefaultLauncher(ctx)) askHome()
                    else ctx.toast("Tout est prêt ✓ — active maintenant le clavier")
                }
            }
        }

        GlassCard(title = "Ta progression  $done / ${steps.size}", icon = Icons.Rounded.Checklist, iconColor = IosGreen) {
            ActionRow(Icons.Rounded.Wallpaper, IosPink, "1. Fond d'écran Maison",
                if (wallOk) "Appliqué : ${Prefs.wallpaperName.value}" else "Choisis un fond pour activer le verre liquide", wallOk) { onTab("style") }
            ActionRow(Icons.Rounded.Home, Accent, "2. Écran d'accueil iPhone",
                if (launcherOk) "Maison est ton écran d'accueil" else "Définir Maison comme écran d'accueil", launcherOk) { askHome() }
            ActionRow(Icons.Rounded.Keyboard, IosIndigo, "3. Activer le clavier iPhone",
                "Active « Clavier Maison (iPhone) » dans la liste", imeOn) { SystemTools.openImeSettings(ctx) }
            ActionRow(Icons.Rounded.KeyboardAlt, IosTeal, "4. Choisir le clavier iPhone",
                "Le sélectionner comme clavier actuel", imeSel) {
                if (!imeOn) SystemTools.openImeSettings(ctx) else SystemTools.showImePicker(ctx)
            }
            ActionRow(Icons.Rounded.AdminPanelSettings, IosOrange, "5. Autoriser les réglages directs",
                "Luminosité, mise en veille, sonnerie… depuis Maison", writeOk) { SystemTools.requestWriteSettings(ctx) }
            ActionRow(Icons.Rounded.Lock, Color(0xFF8E8E93), "6. Good Lock (Samsung)",
                "Centre de contrôle et écran verrouillé façon iPhone", goodLock) {
                SystemTools.openAppOrStore(ctx, SystemTools.GOOD_LOCK)
            }
        }

        GlassCard(title = "Raccourcis", icon = Icons.Rounded.Tune, iconColor = Accent) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("🏠 Voir l'accueil", Modifier.weight(1f)) {
                    ctx.startActivity(Intent(ctx, LauncherActivity::class.java))
                }
                SecondaryButton("🎨 Fonds", Modifier.weight(1f)) { onTab("style") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("⌨️ Clavier", Modifier.weight(1f)) { onTab("clavier") }
                SecondaryButton("🎛️ Centre de contrôle", Modifier.weight(1f)) {
                    SystemTools.openGoodLockModule(ctx, "com.samsung.android.qstuner", "QuickStar")
                }
            }
        }

        GlassCard(title = "Bon à savoir", icon = Icons.Rounded.Info, iconColor = Color(0xFF8E8E93)) {
            BodyText(
                "Pour des raisons de sécurité, Android interdit à toute appli de modifier elle-même la barre d'état, " +
                    "le volet de notifications et l'écran verrouillé. Maison s'occupe de tout ce qui est possible " +
                    "directement (écran d'accueil, clavier, fonds d'écran, verre liquide, réglages) et t'emmène en " +
                    "1 clic dans les modules officiels Samsung Good Lock pour le reste."
            )
            Text("Astuce : appui long sur l'écran d'accueil → « Personnaliser ».", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

