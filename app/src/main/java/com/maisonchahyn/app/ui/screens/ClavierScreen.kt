package com.maisonchahyn.app.ui.screens

import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maisonchahyn.app.AppState
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.system.SystemTools
import com.maisonchahyn.app.ui.Accent
import com.maisonchahyn.app.ui.BodyText
import com.maisonchahyn.app.ui.GlassCard
import com.maisonchahyn.app.ui.IosIndigo
import com.maisonchahyn.app.ui.IosPink
import com.maisonchahyn.app.ui.LiquidGlass
import com.maisonchahyn.app.ui.PrimaryButton
import com.maisonchahyn.app.ui.ScreenColumn
import com.maisonchahyn.app.ui.SecondaryButton
import com.maisonchahyn.app.ui.SettingSlider
import com.maisonchahyn.app.ui.SettingSwitch

private data class KbTheme(val id: String, val name: String, val bg: List<Color>, val key: Color, val text: Color)

private val kbThemes = listOf(
    KbTheme("verre", "Verre clair", listOf(Color(0xFFF2F3F7), Color(0xFFE3E6EE)), Color.White, Color.Black),
    KbTheme("verre_sombre", "Verre sombre", listOf(Color(0xFF2A2A30), Color(0xFF141416)), Color.White.copy(alpha = 0.24f), Color.White),
    KbTheme("clair", "iPhone clair", listOf(Color(0xFFD3D5DB), Color(0xFFCDD0D6)), Color.White, Color.Black),
    KbTheme("sombre", "iPhone sombre", listOf(Color(0xFF2C2C2E), Color(0xFF262628)), Color(0xFF6B6B6B), Color.White),
    KbTheme("rose", "Verre rose", listOf(Color(0xFFFFE3EE), Color(0xFFF7D0E2)), Color.White, Color(0xFF3A1F2B)),
    KbTheme("nuit_bleue", "Nuit bleue", listOf(Color(0xFF0B1A3A), Color(0xFF061026)), Color(0x550A84FF), Color.White),
    KbTheme("auto", "Auto (jour/nuit)", listOf(Color(0xFFF2F3F7), Color(0xFF141416)), Color.White.copy(alpha = 0.6f), Color.Black),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClavierScreen() {
    val ctx = LocalContext.current
    val tick = AppState.resumeTick.intValue
    val enabled = remember(tick) { SystemTools.imeEnabled(ctx) }
    val selected = remember(tick) { SystemTools.imeSelected(ctx) }
    var test by remember { mutableStateOf("") }

    ScreenColumn("Clavier", "AZERTY façon iPhone, en verre liquide.") {
        GlassCard(title = "Activer le clavier", icon = Icons.Rounded.Keyboard, iconColor = IosIndigo) {
            BodyText(
                when {
                    selected -> "✅ Le clavier Maison est actif."
                    enabled -> "Étape 2 : choisis « Clavier Maison (iPhone) » comme clavier."
                    else -> "Étape 1 : active « Clavier Maison (iPhone) » dans la liste des claviers. " +
                        "Android affiche un avertissement standard pour tous les claviers : Maison ne collecte rien."
                }
            )
            if (!enabled) PrimaryButton("1. Activer le clavier") { SystemTools.openImeSettings(ctx) }
            else if (!selected) PrimaryButton("2. Choisir le clavier") { SystemTools.showImePicker(ctx) }
            else SecondaryButton("Changer de clavier", Modifier.fillMaxWidth()) { SystemTools.showImePicker(ctx) }

            Text("Essaie-le ici :", color = Color.White, fontSize = 15.sp)
            LiquidGlass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Box(Modifier.padding(14.dp)) {
                    if (test.isEmpty()) Text("Tape quelque chose…", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                    BasicTextField(
                        value = test, onValueChange = { test = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        GlassCard(title = "Thème", icon = Icons.Rounded.Palette, iconColor = IosPink) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                kbThemes.forEach { t ->
                    val sel = Prefs.kTheme.value == t.id
                    Column(
                        Modifier
                            .width(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { Prefs.kTheme.value = t.id }
                            .padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.verticalGradient(t.bg))
                                .border(if (sel) 3.dp else 1.dp, if (sel) Accent else Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(3) { r ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        repeat(if (r == 2) 3 else 5) {
                                            Box(
                                                Modifier
                                                    .size(width = if (r == 2) 20.dp else 10.dp, height = 10.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(t.key)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(t.name, color = Color.White, fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }

        GlassCard(title = "Réglages", icon = Icons.Rounded.Tune, iconColor = Accent) {
            SettingSlider("Hauteur des touches", Prefs.kHeight.value.toFloat(), 80f..130f, "${Prefs.kHeight.value} %") {
                Prefs.kHeight.value = it.toInt()
            }
            SettingSwitch("Rangée de chiffres", "1 2 3 … au-dessus des lettres", Prefs.kNumberRow.value) { Prefs.kNumberRow.value = it }
            SettingSwitch("Barre d'outils", "Réglages, Coller, changer de clavier, masquer", Prefs.kToolbar.value) { Prefs.kToolbar.value = it }
            SettingSwitch("Aperçu des touches", "La bulle au-dessus de la touche, comme sur iPhone", Prefs.kPreview.value) { Prefs.kPreview.value = it }
            SettingSwitch("Majuscule automatique", "En début de phrase", Prefs.kAutoCap.value) { Prefs.kAutoCap.value = it }
            SettingSwitch("Raccourci « . »", "Deux espaces = point + espace", Prefs.kDoubleSpace.value) { Prefs.kDoubleSpace.value = it }
            SettingSwitch("Vibration", "Retour haptique léger à chaque touche", Prefs.kVibrate.value) { Prefs.kVibrate.value = it }
            SettingSwitch("Son des touches", null, Prefs.kSound.value) { Prefs.kSound.value = it }
            SettingSwitch(
                "Flou réel derrière le clavier (expérimental)",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Vrai verre liquide sur les thèmes en verre" else "Nécessite Android 12+",
                Prefs.kRealBlur.value,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) { Prefs.kRealBlur.value = it }
        }

        GlassCard(title = "Astuces façon iPhone") {
            BodyText(
                "• Appui long sur une lettre → accents (é è ê ë, à â, ç, œ…)\n" +
                    "• Glisse le doigt sur la barre d'espace → déplace le curseur\n" +
                    "• Double appui sur ⇧ → verrouillage majuscules\n" +
                    "• Appui long sur 😊 → changer de clavier\n" +
                    "• Après « 123 », l'espace ou l'apostrophe ramène aux lettres"
            )
        }
    }
}
