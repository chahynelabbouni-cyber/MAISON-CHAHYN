package com.maisonchahyn.app.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.toast
import com.maisonchahyn.app.ui.Accent
import com.maisonchahyn.app.ui.Backdrop
import com.maisonchahyn.app.ui.BackdropHost
import com.maisonchahyn.app.ui.BodyText
import com.maisonchahyn.app.ui.GlassCard
import com.maisonchahyn.app.ui.IosPink
import com.maisonchahyn.app.ui.IosTeal
import com.maisonchahyn.app.ui.LiquidGlass
import com.maisonchahyn.app.ui.PrimaryButton
import com.maisonchahyn.app.ui.ScreenColumn
import com.maisonchahyn.app.ui.SecondaryButton
import com.maisonchahyn.app.ui.SettingSlider
import com.maisonchahyn.app.ui.SettingSwitch
import com.maisonchahyn.app.ui.WallpaperStore
import com.maisonchahyn.app.ui.drawAurora
import com.maisonchahyn.app.ui.drawImageCover
import com.maisonchahyn.app.wallpaper.WallSpec
import com.maisonchahyn.app.wallpaper.WallpaperGen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val palette = listOf(
    0xFF0A84FF, 0xFF64D2FF, 0xFF30D158, 0xFF7CE3C3, 0xFFFFD60A, 0xFFFF9F0A,
    0xFFFF375F, 0xFFFF4FA3, 0xFFBF5AF2, 0xFF5E5CE6, 0xFF1C1C1E, 0xFFF2F2F7
).map { it.toInt() }

/** Ce qu'on s'apprête à appliquer (image déjà prête, ou modèle à générer en pleine résolution). */
private class PendingWall(val name: String, val spec: WallSpec?, val bitmap: Bitmap?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingWall?>(null) }
    var busy by remember { mutableStateOf(false) }

    val previews by produceState<List<ImageBitmap>>(emptyList()) {
        value = withContext(Dispatchers.Default) {
            WallpaperGen.presets.map { WallpaperGen.render(it, 180, 390).asImageBitmap() }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val bmp = WallpaperGen.loadFromUri(ctx, uri)
            busy = false
            if (bmp == null) ctx.toast("Image illisible") else pending = PendingWall("Ma photo", null, bmp)
        }
    }

    var c1 by remember { mutableIntStateOf(palette[0]) }
    var c2 by remember { mutableIntStateOf(palette[8]) }
    var c3 by remember { mutableIntStateOf(palette[7]) }
    var ribbons by remember { mutableStateOf(true) }
    val customSpec = remember(c1, c2, c3, ribbons) { WallpaperGen.custom(c1, c2, c3, ribbons) }
    val customPreview by produceState<ImageBitmap?>(null, customSpec) {
        value = withContext(Dispatchers.Default) { WallpaperGen.render(customSpec, 180, 390).asImageBitmap() }
    }

    ScreenColumn("Style", "Fonds d'écran et verre liquide.") {
        GlassCard(title = "Fonds d'écran Maison", icon = Icons.Rounded.Wallpaper, iconColor = IosPink) {
            BodyText("Touche un fond pour l'appliquer à l'écran d'accueil, à l'écran verrouillé, ou aux deux.")
            if (previews.isEmpty()) BodyText("Création des fonds…")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WallpaperGen.presets.forEachIndexed { i, spec ->
                    val img = previews.getOrNull(i)
                    if (img != null) {
                        val sel = Prefs.wallpaperName.value == spec.name
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                            Image(
                                img, spec.name,
                                Modifier
                                    .width(90.dp)
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(if (sel) 3.dp else 1.dp, if (sel) Accent else Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .clickable { pending = PendingWall(spec.name, spec, null) },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(spec.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
            SecondaryButton(if (busy) "Chargement…" else "🖼️ Choisir dans ma galerie", Modifier.fillMaxWidth(), enabled = !busy) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        GlassCard(title = "Créer mon dégradé", icon = Icons.Rounded.Brush, iconColor = Accent) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .width(90.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    customPreview?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorLine("Couleur 1", c1) { c1 = it }
                    ColorLine("Couleur 2", c2) { c2 = it }
                    ColorLine("Couleur 3", c3) { c3 = it }
                }
            }
            SettingSwitch("Rubans de verre liquide", null, ribbons) { ribbons = it }
            PrimaryButton("Appliquer mon dégradé") { pending = PendingWall("Mon dégradé", customSpec, null) }
        }

        GlassCard(title = "Verre liquide", icon = Icons.Rounded.BlurOn, iconColor = IosTeal) {
            BodyText("S'applique à l'écran d'accueil Maison (dock, widgets, recherche), aux menus et à cette appli.")
            GlassPreview()
            SettingSlider("Flou", Prefs.gBlur.value.toFloat(), 0f..48f, "${Prefs.gBlur.value}") { Prefs.gBlur.value = it.toInt() }
            SettingSlider("Opacité du verre", Prefs.gTint.value, 0f..0.6f, "${(Prefs.gTint.value * 100).toInt()} %") { Prefs.gTint.value = it }
            SettingSlider("Bordure lumineuse", Prefs.gBorder.value, 0f..1f, "${(Prefs.gBorder.value * 100).toInt()} %") { Prefs.gBorder.value = it }
            SettingSlider("Arrondi des coins", Prefs.gCorner.value.toFloat(), 8f..40f, "${Prefs.gCorner.value} dp") { Prefs.gCorner.value = it.toInt() }
            SettingSwitch("Reflets", "Lumière en haut du verre, comme iOS 26", Prefs.gHighlight.value) { Prefs.gHighlight.value = it }
            SettingSwitch("Verre teinté sombre", "Plus lisible sur les fonds clairs", Prefs.gDark.value) { Prefs.gDark.value = it }
            SecondaryButton("Réglages par défaut", Modifier.fillMaxWidth()) {
                Prefs.gBlur.reset(); Prefs.gTint.reset(); Prefs.gBorder.reset()
                Prefs.gCorner.reset(); Prefs.gHighlight.reset(); Prefs.gDark.reset()
            }
        }
    }

    val p = pending
    if (p != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Color(0xFF1C1C2E),
            title = { Text("Appliquer « ${p.name} »", color = Color.White) },
            text = {
                Column {
                    listOf(
                        "Écran d'accueil" to WallpaperGen.HOME,
                        "Écran verrouillé" to WallpaperGen.LOCK,
                        "Les deux" to WallpaperGen.BOTH
                    ).forEach { (label, which) ->
                        Text(
                            label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    pending = null
                                    busy = true
                                    scope.launch {
                                        val bmp = p.bitmap ?: WallpaperGen.renderFull(ctx, p.spec!!)
                                        val ok = WallpaperGen.apply(ctx, bmp, which, p.name)
                                        busy = false
                                        ctx.toast(if (ok) "« ${p.name} » appliqué ✓" else "Échec de l'application du fond")
                                    }
                                }
                                .padding(vertical = 14.dp, horizontal = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorLine(label: String, value: Int, onPick: (Int) -> Unit) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            palette.forEach { c ->
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (c == value) 2.5.dp else 0.5.dp, if (c == value) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable { onPick(c) }
                )
            }
        }
    }
}

/** Aperçu en direct : du verre liquide posé sur ton fond d'écran. */
@Composable
private fun GlassPreview() {
    val img = WallpaperStore.image.value
    val backdrop = remember(img) {
        Backdrop { s -> if (img != null) drawImageCover(img, s) else drawAurora(s) }
    }
    BackdropHost(
        backdrop,
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            LiquidGlass(Modifier.fillMaxWidth().height(78.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Mardi 10 novembre", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("09:41", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                }
            }
            LiquidGlass(Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(30.dp)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    listOf(Color(0xFF30D158), Color(0xFF0A84FF), Color(0xFFFF9F0A), Color(0xFFFF375F)).forEach {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(it))
                    }
                }
            }
        }
    }
}
