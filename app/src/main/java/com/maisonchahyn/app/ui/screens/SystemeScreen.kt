package com.maisonchahyn.app.ui.screens

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.maisonchahyn.app.AppState
import com.maisonchahyn.app.system.SystemTools
import com.maisonchahyn.app.toast
import com.maisonchahyn.app.ui.Accent
import com.maisonchahyn.app.ui.BodyText
import com.maisonchahyn.app.ui.Chips
import com.maisonchahyn.app.ui.GlassCard
import com.maisonchahyn.app.ui.IosOrange
import com.maisonchahyn.app.ui.IosYellow
import com.maisonchahyn.app.ui.PrimaryButton
import com.maisonchahyn.app.ui.SecondaryButton
import com.maisonchahyn.app.ui.ScreenColumn
import com.maisonchahyn.app.ui.SettingSlider
import com.maisonchahyn.app.ui.SettingSwitch
import com.maisonchahyn.app.ui.TextDim

@Composable
fun SystemeScreen() {
    val ctx = LocalContext.current
    val tick = AppState.resumeTick.intValue
    val canWrite = remember(tick) { SystemTools.canWriteSettings(ctx) }
    val hasSecure = remember(tick) { SystemTools.hasSecureSettings(ctx) }
    val clipboard = LocalClipboardManager.current

    var brightness by remember(tick) { mutableFloatStateOf(SystemTools.getSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS, 128).toFloat()) }
    var autoBright by remember(tick) {
        mutableStateOf(SystemTools.getSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
    }
    var timeout by remember(tick) { mutableIntStateOf(SystemTools.getSystemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, 30000)) }
    var rotation by remember(tick) { mutableStateOf(SystemTools.getSystemInt(ctx, Settings.System.ACCELEROMETER_ROTATION, 0) == 1) }
    var touchSounds by remember(tick) { mutableStateOf(SystemTools.getSystemInt(ctx, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1) }
    var haptics by remember(tick) { mutableStateOf(SystemTools.getSystemInt(ctx, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1) }
    var anim by remember(tick) { mutableFloatStateOf(SystemTools.getGlobalFloat(ctx, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)) }
    var batteryPct by remember(tick) { mutableStateOf(SystemTools.getSystemInt(ctx, "display_battery_percentage", 0) == 1) }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        if (uri != null) {
            try {
                RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, uri)
                ctx.toast("Sonnerie changée ✓")
            } catch (e: Throwable) {
                ctx.toast("Autorise d'abord les réglages directs")
            }
        }
    }

    ScreenColumn("Système", "Réglages directs, Samsung et Android.") {
        GlassCard(title = "Réglages directs", icon = Icons.Rounded.Brightness6, iconColor = IosYellow) {
            if (!canWrite) {
                BodyText("Autorise Maison à modifier les paramètres système pour tout régler d'ici, en un clic.")
                PrimaryButton("Autoriser") { SystemTools.requestWriteSettings(ctx) }
            }
            SettingSwitch("Luminosité automatique", null, autoBright, enabled = canWrite) {
                autoBright = it
                SystemTools.putSystemInt(
                    ctx, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (it) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
            }
            SettingSlider("Luminosité", brightness, 1f..255f, "${(brightness / 255f * 100).toInt()} %", enabled = canWrite && !autoBright) {
                brightness = it
                SystemTools.putSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                SystemTools.putSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS, it.toInt())
            }
            Text("Mise en veille de l'écran", color = Color.White, fontSize = 16.sp)
            Chips(
                listOf("15000" to "15 s", "30000" to "30 s", "60000" to "1 min", "120000" to "2 min", "300000" to "5 min", "600000" to "10 min"),
                timeout.toString()
            ) {
                if (!canWrite) SystemTools.requestWriteSettings(ctx)
                else {
                    timeout = it.toInt(); SystemTools.putSystemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, timeout)
                }
            }
            SettingSwitch("Rotation automatique", null, rotation, enabled = canWrite) {
                rotation = it; SystemTools.putSystemInt(ctx, Settings.System.ACCELEROMETER_ROTATION, if (it) 1 else 0)
            }
            SettingSwitch("Sons des touches", "Sons quand tu touches l'écran", touchSounds, enabled = canWrite) {
                touchSounds = it; SystemTools.putSystemInt(ctx, Settings.System.SOUND_EFFECTS_ENABLED, if (it) 1 else 0)
            }
            SettingSwitch("Retour haptique", "Petites vibrations au toucher", haptics, enabled = canWrite) {
                haptics = it; SystemTools.putSystemInt(ctx, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (it) 1 else 0)
            }
            SecondaryButton("🎵 Choisir la sonnerie", Modifier.fillMaxWidth(), enabled = canWrite) {
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonnerie")
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                try {
                    ringtonePicker.launch(i)
                } catch (e: Throwable) {
                    ctx.toast("Sélecteur indisponible")
                }
            }
        }

        GlassCard(title = "Personnalisation Samsung", icon = Icons.Rounded.PhoneAndroid, iconColor = Accent) {
            BodyText("Les modules officiels Samsung pour ce qu'aucune appli ne peut modifier seule : centre de contrôle, écran verrouillé, barre de navigation…")
            SystemTools.samsung.forEach { ShortcutRow(it) { s -> s.open(ctx) } }
        }

        GlassCard(title = "Paramètres Android", icon = Icons.Rounded.Settings, iconColor = Color(0xFF8E8E93)) {
            SystemTools.androidShortcuts.forEach { ShortcutRow(it) { s -> s.open(ctx) } }
        }

        GlassCard(title = "Mode avancé (ADB)", icon = Icons.Rounded.DeveloperMode, iconColor = IosOrange) {
            if (!hasSecure) {
                BodyText(
                    "Débloque encore plus de réglages (vitesse des animations, % de batterie…). " +
                        "Une seule fois, depuis un ordinateur avec le débogage USB activé, lance :"
                )
                val cmd = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(12.dp)
                ) {
                    Text(cmd, color = Color(0xFF64D2FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                SecondaryButton("📋 Copier la commande", Modifier.fillMaxWidth()) {
                    clipboard.setText(AnnotatedString(cmd)); ctx.toast("Commande copiée")
                }
            } else {
                BodyText("✅ Mode avancé débloqué.")
                Text("Vitesse des animations", color = Color.White, fontSize = 16.sp)
                Chips(
                    listOf("0.0" to "Aucune", "0.5" to "Rapides", "1.0" to "Normales", "1.5" to "Douces"),
                    anim.toString()
                ) {
                    val v = it.toFloat()
                    anim = v
                    val ok = SystemTools.putGlobalFloat(ctx, Settings.Global.WINDOW_ANIMATION_SCALE, v) &&
                        SystemTools.putGlobalFloat(ctx, Settings.Global.TRANSITION_ANIMATION_SCALE, v) &&
                        SystemTools.putGlobalFloat(ctx, Settings.Global.ANIMATOR_DURATION_SCALE, v)
                    if (!ok) ctx.toast("Réglage refusé par le système")
                }
                SettingSwitch("Pourcentage de batterie", "Dans la barre d'état (Samsung)", batteryPct) {
                    batteryPct = it
                    if (!SystemTools.putSystemInt(ctx, "display_battery_percentage", if (it) 1 else 0)) ctx.toast("Réglage refusé par le système")
                }
            }
        }

        GlassCard(title = "Permissions", icon = Icons.Rounded.AdminPanelSettings, iconColor = Color(0xFF30D158)) {
            Text(
                (if (canWrite) "✅" else "⬜") + "  Réglages système\n" + (if (hasSecure) "✅" else "⬜") + "  Mode avancé (ADB)",
                color = TextDim, fontSize = 14.sp, lineHeight = 22.sp
            )
            SecondaryButton("Infos de l'appli Maison", Modifier.fillMaxWidth()) { SystemTools.appDetails(ctx, ctx.packageName) }
        }
    }
}

@Composable
private fun ShortcutRow(s: SystemTools.Shortcut, onClick: (SystemTools.Shortcut) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick(s) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Text(s.emoji, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(s.desc, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text("›", color = Color.White.copy(alpha = 0.5f), fontSize = 22.sp)
    }
}
