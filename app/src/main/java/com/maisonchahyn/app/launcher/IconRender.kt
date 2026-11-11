package com.maisonchahyn.app.launcher

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.ui.Chips
import com.maisonchahyn.app.ui.LiquidGlass
import com.maisonchahyn.app.ui.SecondaryButton
import kotlinx.coroutines.launch

/** Les styles d'icônes proposés partout (écran d'accueil, modèles…). */
object IconStyles {
    val options = listOf(
        "couleur" to "En couleur",
        "teinte" to "Teintées (iOS 18)",
        "verre" to "Verre liquide",
        "neon" to "Néon",
        "pastel" to "Pastel",
        "inverse" to "Blanc épuré",
        "mono" to "Noir & blanc",
    )

    fun usesTint(style: String) = style in setOf("teinte", "neon", "pastel", "inverse")
    fun hasShadow(style: String) = style in setOf("couleur", "pastel", "inverse", "mono")
}

private val grayMatrix = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Une icône d'appli dessinée selon le style choisi (ou la personnalisation de l'utilisateur). */
@Composable
fun StyledIcon(
    app: AppEntry,
    size: Dp,
    modifier: Modifier = Modifier,
    style: String = Prefs.lIconStyle.value,
    tint: Color = Color(Prefs.lTintColor.value),
    shape: Shape = IconShapes.of(Prefs.lIconShape.value),
    ov: IconOverride? = IconOverrides.map[app.key],
) {
    val density = LocalDensity.current
    Box(modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        val img = ov?.image?.let { IconOverrides.image(it) }
        when {
            img != null -> Image(img, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            ov?.emoji != null -> {
                val c = Color(ov.color ?: 0xFF0A84FF.toInt())
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(listOf(lerp(c, Color.White, 0.15f), lerp(c, Color.Black, 0.25f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(ov.emoji, color = Color.White, fontSize = with(density) { (size * 0.48f).toSp() })
                }
            }
            app.fromPack && style != "mono" -> Image(app.icon, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else -> StyledLayers(app, size, style, tint, shape)
        }
    }
}

@Composable
private fun StyledLayers(app: AppEntry, size: Dp, style: String, tint: Color, shape: Shape) {
    val glyph = app.mono
    val fallback: @Composable (Color) -> Unit = { overlay ->
        Image(app.icon, null, Modifier.size(size), contentScale = ContentScale.Crop, colorFilter = grayMatrix)
        Box(Modifier.size(size).background(overlay))
    }
    when (style) {
        "teinte" -> {
            Box(Modifier.size(size).background(Brush.verticalGradient(listOf(Color(0xFF2C2C2E), Color(0xFF111113)))))
            if (glyph != null) Image(glyph, null, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
            else fallback(tint.copy(alpha = 0.25f))
        }

        "verre" -> {
            LiquidGlass(Modifier.size(size), shape = shape, blur = 14.dp, tintAlpha = 0.22f, border = 0.55f, highlight = true) {
                if (glyph != null) Image(glyph, null, Modifier.size(size), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.95f)))
                else Image(app.icon, null, Modifier.size(size), contentScale = ContentScale.Crop, alpha = 0.85f)
            }
        }

        "neon" -> {
            Box(Modifier.size(size).background(Color(0xFF07070C)))
            if (glyph != null) {
                Image(
                    glyph, null,
                    Modifier.size(size).blur(size * 0.08f, BlurredEdgeTreatment.Unbounded),
                    colorFilter = ColorFilter.tint(tint)
                )
                Image(glyph, null, Modifier.size(size), colorFilter = ColorFilter.tint(lerp(tint, Color.White, 0.35f)))
            } else fallback(tint.copy(alpha = 0.3f))
        }

        "pastel" -> {
            val bg = lerp(tint, Color.White, 0.72f)
            if (glyph != null) {
                Box(Modifier.size(size).background(Brush.verticalGradient(listOf(lerp(bg, Color.White, 0.3f), bg))))
                Image(glyph, null, Modifier.size(size), colorFilter = ColorFilter.tint(lerp(tint, Color.Black, 0.15f)))
            } else Image(app.icon, null, Modifier.size(size), contentScale = ContentScale.Crop)
        }

        "inverse" -> {
            Box(Modifier.size(size).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEDEDF2)))))
            if (glyph != null) Image(glyph, null, Modifier.size(size), colorFilter = ColorFilter.tint(lerp(tint, Color.Black, 0.55f)))
            else fallback(Color.White.copy(alpha = 0.35f))
        }

        "mono" -> Image(app.icon, null, Modifier.size(size), contentScale = ContentScale.Crop, colorFilter = grayMatrix)

        else -> Image(app.icon, app.label, Modifier.size(size), contentScale = ContentScale.Crop)
    }
}

/** État partagé de l'écran d'accueil : appli en cours de personnalisation. */
object LauncherUi {
    val customize = mutableStateOf<AppEntry?>(null)
}

val iconColors = listOf(
    0xFF0A84FF, 0xFF64D2FF, 0xFF30D158, 0xFFFFD60A, 0xFFFF9F0A, 0xFFFF375F,
    0xFFFF4FA3, 0xFFBF5AF2, 0xFF5E5CE6, 0xFF8E8E93, 0xFF1C1C1E, 0xFFFFFFFF
).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppCustomizeDialog(app: AppEntry, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cur = IconOverrides.map[app.key]
    var label by remember { mutableStateOf(cur?.label ?: app.label) }
    var mode by remember { mutableStateOf(if (cur?.image != null) "photo" else if (cur?.emoji != null) "emoji" else "origine") }
    var emoji by remember { mutableStateOf(cur?.emoji ?: app.label.take(1).uppercase()) }
    var color by remember { mutableIntStateOf(cur?.color ?: 0xFF0A84FF.toInt()) }
    var imagePath by remember { mutableStateOf(cur?.image) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val p = IconOverrides.importImage(ctx, app.key, uri)
            if (p != null) {
                imagePath = p; mode = "photo"
            }
        }
    }
    fun current() = IconOverride(
        label = label.trim().takeIf { it.isNotEmpty() && it != app.label },
        emoji = if (mode == "emoji") emoji.trim().ifEmpty { null } else null,
        color = if (mode == "emoji") color else null,
        image = if (mode == "photo") imagePath else null
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C2E),
        title = { Text("Personnaliser", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StyledIcon(app, 76.dp, ov = current())
                        Text(label.ifBlank { app.label }, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Nom affiché") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Logo", color = Color.White, fontSize = 15.sp)
                Chips(listOf("origine" to "D'origine", "emoji" to "Emoji / lettre", "photo" to "Photo"), mode) {
                    mode = it
                    if (it == "photo" && imagePath == null) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (mode == "emoji") {
                    OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(4) }, label = { Text("Emoji ou lettres") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        iconColors.forEach { c ->
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(if (c == color) 3.dp else 1.dp, if (c == color) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                                    .clickable { color = c }
                            )
                        }
                    }
                }
                if (mode == "photo") {
                    SecondaryButton("🖼️ Choisir une image", Modifier.fillMaxWidth()) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { IconOverrides.set(app.key, current()); onDismiss() }) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = { IconOverrides.set(app.key, null); onDismiss() }) { Text("Réinitialiser") }
        }
    )
}
