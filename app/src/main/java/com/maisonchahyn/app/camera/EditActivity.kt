package com.maisonchahyn.app.camera

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.toast
import com.maisonchahyn.app.ui.IosYellow
import com.maisonchahyn.app.ui.MaisonTheme
import com.maisonchahyn.app.ui.PrimaryButton
import com.maisonchahyn.app.ui.SecondaryButton
import com.maisonchahyn.app.ui.TextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * « Retouche Chahyn » : améliore une photo de la Galerie (Partager / Modifier avec…).
 * L'original n'est jamais touché : une copie est enregistrée dans l'album Chahyn Photo.
 */
class EditActivity : ComponentActivity() {
    private val incoming = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        enableEdgeToEdge()
        incoming.value = uriFrom(intent)
        setContent { MaisonTheme { EditScreen(incoming.value, onClose = { finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        uriFrom(intent)?.let { incoming.value = it }
    }

    private fun uriFrom(i: Intent?): Uri? {
        if (i == null) return null
        if (i.action == Intent.ACTION_SEND) {
            return if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else legacyStream(i)
        }
        return i.data
    }

    @Suppress("DEPRECATION")
    private fun legacyStream(i: Intent): Uri? = i.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri

    companion object {
        fun open(ctx: android.content.Context, uri: Uri? = null) {
            val i = Intent(ctx, EditActivity::class.java)
            if (uri != null) i.setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(i)
        }
    }
}

private val Yellow = IosYellow

@Composable
private fun EditScreen(initial: Uri?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf(initial) }
    LaunchedEffect(initial) { if (initial != null) uri = initial }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) uri = u else if (uri == null) onClose()
    }
    LaunchedEffect(Unit) {
        if (uri == null) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var analysis by remember { mutableStateOf<Enhance.Analysis?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        loading = true
        val (bmp, an) = withContext(Dispatchers.Default) {
            val b = PhotoSaver.loadPreview(ctx, u)
            b to b?.let { Enhance.analyse(it) }
        }
        preview = bmp
        analysis = an
        loading = false
        if (bmp == null) ctx.toast("Impossible d'ouvrir cette photo")
    }

    // Réglages
    var auto by remember { mutableStateOf(true) }
    var lookId by remember { mutableStateOf("naturel") }
    var amount by remember { mutableFloatStateOf(1f) }
    var bright by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var sat by remember { mutableFloatStateOf(1f) }
    var warm by remember { mutableFloatStateOf(0f) }
    var tint by remember { mutableFloatStateOf(0f) }
    var fade by remember { mutableFloatStateOf(0f) }
    var showOriginal by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }

    fun buildMatrix(): android.graphics.ColorMatrix {
        val m = android.graphics.ColorMatrix()
        if (auto) analysis?.let { m.postConcat(it.matrix) }
        m.postConcat(Looks.byId(lookId).matrix(amount))
        val manual = Look("manuel", "Manuel", 0, saturation = sat, contrast = contrast, brightness = bright, warmth = warm, tint = tint, fade = fade)
        if (!manual.isNeutral) m.postConcat(manual.matrix(1f))
        return m
    }
    val cm = buildMatrix()
    val composeFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(cm.array))

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E12))) {
        // Barre du haut
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Retouche Chahyn", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("L'original n'est jamais modifié", color = TextDim, fontSize = 12.sp)
            }
            SecondaryButton("Autre photo") {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        // Photo (maintenir = original)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            val p = preview
            if (p != null) {
                Image(
                    p.asImageBitmap(), null,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (showOriginal) null else composeFilter,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    if (showOriginal) "ORIGINAL" else "Maintiens pour voir l'original",
                    color = if (showOriginal) Yellow else Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            } else {
                Text(if (loading) "Chargement…" else "Choisis une photo", color = TextDim, fontSize = 16.sp)
            }
        }

        // Commandes
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (auto) Yellow.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                    .clickable { auto = !auto }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("✨ Amélioration auto", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(analysis?.summary?.ifEmpty { null } ?: "Niveaux, couleurs et éclat corrigés", color = TextDim, fontSize = 12.sp)
                }
                Text(if (auto) "ACTIVÉE" else "COUPÉE", color = if (auto) Yellow else TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Looks.all, key = { it.id }) { l ->
                    val sel = l.id == lookId
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable { lookId = l.id }.padding(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(l.swatch), Color(l.swatch).copy(alpha = 0.55f))))
                                .border(if (sel) 3.dp else 1.dp, if (sel) Yellow else Color.White.copy(alpha = 0.5f), CircleShape)
                        )
                        Text(l.name, color = if (sel) Yellow else Color.White, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                if (lookId != "naturel") Adjust("Intensité du style", amount, 0f..1f, "${(amount * 100).roundToInt()} %") { amount = it }
                Adjust("Luminosité", bright, -0.25f..0.25f, signed(bright * 400)) { bright = it }
                Adjust("Contraste", contrast, 0.7f..1.4f, signed((contrast - 1f) * 100)) { contrast = it }
                Adjust("Saturation", sat, 0f..1.8f, signed((sat - 1f) * 100)) { sat = it }
                Adjust("Chaleur", warm, -1f..1f, signed(warm * 100)) { warm = it }
                Adjust("Teinte (vert ↔ magenta)", tint, -1f..1f, signed(tint * 100)) { tint = it }
                Adjust("Fondu (effet film)", fade, 0f..0.3f, "${(fade * 333).roundToInt()}") { fade = it }
                Text(
                    "Réinitialiser les réglages", color = Yellow, fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        bright = 0f; contrast = 1f; sat = 1f; warm = 0f; tint = 0f; fade = 0f; amount = 1f; lookId = "naturel"; auto = true
                    }.padding(vertical = 6.dp)
                )
            }

            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    if (saving) "Enregistrement en pleine résolution…" else "Enregistrer une copie",
                    modifier = Modifier.weight(1f),
                    enabled = !saving && uri != null && preview != null
                ) {
                    val u = uri ?: return@PrimaryButton
                    saving = true
                    val matrix = buildMatrix()
                    val desc = "Retouche Chahyn" + (if (auto) " · auto" else "") + " · " + Looks.byId(lookId).name
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { PhotoSaver.saveEdited(ctx, u, matrix, desc) }
                        saving = false
                        if (saved != null) {
                            savedUri = saved.uri
                            ctx.toast("Copie enregistrée dans Chahyn Photo ✓")
                        } else ctx.toast("Impossible d'enregistrer (format non pris en charge ?)")
                    }
                }
            }
            savedUri?.let { s ->
                Text(
                    "Voir la copie enregistrée ›", color = Yellow, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(s, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            } catch (_: Exception) {
                            }
                        }
                        .padding(4.dp)
                )
            }
        }
    }
}

private fun signed(v: Float): String {
    val r = v.roundToInt()
    return if (r > 0) "+$r" else "$r"
}

@Composable
private fun Adjust(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(shown, color = TextDim, fontSize = 12.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range,
            modifier = Modifier.height(30.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Yellow,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f)
            )
        )
    }
}
