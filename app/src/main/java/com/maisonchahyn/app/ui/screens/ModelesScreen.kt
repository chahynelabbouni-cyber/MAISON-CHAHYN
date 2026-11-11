package com.maisonchahyn.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.launcher.AppEntry
import com.maisonchahyn.app.launcher.AppRepository
import com.maisonchahyn.app.launcher.IconShapes
import com.maisonchahyn.app.launcher.StyledIcon
import com.maisonchahyn.app.system.SystemTools
import com.maisonchahyn.app.theme.Modele
import com.maisonchahyn.app.theme.Modeles
import com.maisonchahyn.app.toast
import com.maisonchahyn.app.ui.Backdrop
import com.maisonchahyn.app.ui.BackdropHost
import com.maisonchahyn.app.ui.BodyText
import com.maisonchahyn.app.ui.GlassCard
import com.maisonchahyn.app.ui.IosYellow
import com.maisonchahyn.app.ui.LiquidGlass
import com.maisonchahyn.app.ui.PrimaryButton
import com.maisonchahyn.app.ui.ScreenColumn
import com.maisonchahyn.app.ui.SecondaryButton
import com.maisonchahyn.app.ui.TextDim
import com.maisonchahyn.app.ui.drawAurora
import com.maisonchahyn.app.ui.drawImageCover
import com.maisonchahyn.app.wallpaper.WallpaperGen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModelesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { AppRepository.load(ctx.applicationContext) }
    val apps = AppRepository.apps.value
    val sample = remember(apps) {
        val prefer = listOf("com.samsung.android.dialer", "com.samsung.android.messaging", "com.sec.android.app.camera",
            "com.sec.android.gallery3d", "com.google.android.youtube", "com.whatsapp", "com.instagram.android",
            "com.spotify.music", "com.android.chrome", "com.sec.android.app.sbrowser", "com.google.android.apps.maps")
        val picked = prefer.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
        (picked + apps).distinctBy { it.key }.take(8)
    }
    val customs = remember(Prefs.customModels.value) { Modeles.custom() }
    var busyId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }

    fun applyModel(m: Modele) {
        busyId = m.id
        scope.launch {
            val ok = Modeles.apply(ctx, m)
            busyId = null
            ctx.toast(if (ok) "Modèle « ${m.name} » appliqué ✓" else "Modèle appliqué (fond non modifié)")
        }
    }

    ScreenColumn("Modèles", "Change toute l'interface en un clic : fond, logos des applis, couleurs, verre, clavier.") {
        Modeles.list.forEach { m ->
            ModelCard(m, sample, applied = Prefs.currentModel.value == m.id, busy = busyId == m.id) { applyModel(m) }
        }

        GlassCard(title = "Mes modèles", icon = Icons.Rounded.Star, iconColor = IosYellow) {
            BodyText("Règle tout à ton goût (onglets Écran, Clavier, Fonds), puis enregistre ta création pour la réappliquer en 1 clic.")
            OutlinedTextField(
                value = newName, onValueChange = { newName = it.take(30) }, singleLine = true,
                label = { Text("Nom de mon modèle") }, modifier = Modifier.fillMaxWidth()
            )
            PrimaryButton("💾 Enregistrer ma configuration") {
                scope.launch {
                    val m = Modeles.saveCurrent(ctx, newName.trim().ifEmpty { "Mon modèle ${customs.size + 1}" })
                    newName = ""
                    ctx.toast("« ${m.name} » enregistré ✓")
                }
            }
        }
        customs.forEach { m ->
            ModelCard(m, sample, applied = Prefs.currentModel.value == m.id, busy = busyId == m.id, onDelete = {
                Modeles.delete(m.id); ctx.toast("Modèle supprimé")
            }) { applyModel(m) }
        }

        GlassCard(title = "Couleurs à l'intérieur des applis", icon = Icons.Rounded.ColorLens, iconColor = Color(0xFFFF9F0A)) {
            BodyText(
                "Android ne laisse aucune appli repeindre l'intérieur des autres applis. Mais deux outils Samsung le font, " +
                    "et les modèles de Maison les préparent :\n\n" +
                    "• Palette de couleurs : quand Maison change ton fond d'écran, One UI peut recolorer les menus, " +
                    "réglages, clavier Samsung et les applis compatibles (Google, Samsung…). Choisis la palette dans " +
                    "« Fond d'écran et style ».\n" +
                    "• Thèmes Galaxy / Theme Park : changent l'interface des applis Samsung (Téléphone, Messages, Réglages…), " +
                    "les icônes et l'écran verrouillé."
            )
            SecondaryButton("🎨 Palette de couleurs (Fond d'écran et style)", Modifier.fillMaxWidth()) {
                SystemTools.samsung.first { it.title.startsWith("Fond d'écran") }.open(ctx)
            }
            SecondaryButton("🖌️ Thèmes Galaxy", Modifier.fillMaxWidth()) {
                SystemTools.samsung.first { it.title == "Galaxy Themes" }.open(ctx)
            }
            SecondaryButton("🎨 Theme Park (créer mon thème Samsung)", Modifier.fillMaxWidth()) {
                SystemTools.openGoodLockModule(ctx, "com.samsung.android.themedesigner", "Theme Park")
            }
        }
    }
}

@Composable
private fun ModelCard(
    m: Modele,
    sample: List<AppEntry>,
    applied: Boolean,
    busy: Boolean,
    onDelete: (() -> Unit)? = null,
    onApply: () -> Unit
) {
    val wall by produceState<ImageBitmap?>(null, m.id, m.wallPath) {
        value = withContext(Dispatchers.Default) {
            try {
                if (m.wallPath != null) {
                    BitmapFactory.decodeFile(m.wallPath, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
                } else {
                    val spec = WallpaperGen.presets.firstOrNull { it.id == m.wall } ?: return@withContext null
                    WallpaperGen.render(spec, 400, 300).asImageBitmap()
                }
            } catch (e: Throwable) {
                null
            }
        }
    }
    val img = wall
    val backdrop = remember(img) { Backdrop { s -> if (img != null) drawImageCover(img, s) else drawAurora(s) } }
    val shape = IconShapes.of(m.shape)
    val tint = Color(m.tint)

    LiquidGlass(
        Modifier
            .fillMaxWidth()
            .then(if (applied) Modifier.border(2.dp, Color(m.accent), RoundedCornerShape(Prefs.gCorner.value.dp)) else Modifier)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BackdropHost(
                backdrop,
                Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        sample.drop(4).take(4).forEach { app ->
                            StyledIcon(app, 46.dp, style = m.style, tint = tint, shape = shape, ov = null)
                        }
                    }
                    LiquidGlass(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        blur = m.blur.dp, tintAlpha = m.glassTint, dark = m.darkGlass, border = m.border, highlight = true
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            sample.take(4).forEach { app ->
                                StyledIcon(app, 44.dp, style = m.style, tint = tint, shape = shape, ov = null)
                            }
                        }
                    }
                }
                if (sample.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aperçu…", color = Color.White)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${m.emoji}  ${m.name}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(m.desc, color = TextDim, fontSize = 13.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(Color(m.accent)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    when {
                        busy -> "Application…"
                        applied -> "✓ Appliqué — réappliquer"
                        else -> "Appliquer ce modèle"
                    },
                    Modifier.weight(1f), enabled = !busy, onClick = onApply
                )
                if (onDelete != null) SecondaryButton("Supprimer", onClick = onDelete)
            }
        }
    }
}
