package com.chahyn.photo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.hardware.camera2.CameraMetadata
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Timer10
import androidx.compose.material.icons.rounded.Timer3
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.chahyn.photo.ui.Chips
import com.chahyn.photo.ui.IosGreen
import com.chahyn.photo.ui.IosYellow
import com.chahyn.photo.ui.PrimaryButton
import com.chahyn.photo.ui.SettingSwitch
import com.chahyn.photo.ui.TextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val Panel = Color(0xCC111114)
private val Yellow = IosYellow

@Composable
fun CameraScreen(engine: CameraEngine, motion: MotionSensor, activity: CameraActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // ---------- Autorisations ----------
    fun camGranted() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(camGranted()) }
    var asked by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        granted = camGranted()
    }
    fun askPermissions() {
        val list = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < 29) list += Manifest.permission.WRITE_EXTERNAL_STORAGE
        permLauncher.launch(list.toTypedArray())
    }
    LaunchedEffect(Unit) { if (!granted) askPermissions() }
    LaunchedEffect(granted) { engine.setPermission(granted) }

    // ---------- Format (nécessite de rouvrir le capteur) ----------
    val raw = Prefs.cRaw.value
    val res = Prefs.cRes.value
    val ratio = Prefs.cRatio.value
    LaunchedEffect(raw, res, ratio) {
        val changed = engine.wantRaw != raw || engine.resolution != res || engine.ratio != ratio
        engine.wantRaw = raw
        engine.resolution = res
        engine.ratio = ratio
        if (changed) engine.reopen()
    }

    // ---------- Analyse de la scène (mode Auto) ----------
    val detector = remember { SceneDetector() }
    var detection by remember { mutableStateOf(Detection()) }
    var frame by remember { mutableStateOf(FrameStats()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(450)
            val m = motion.state.value
            engine.tripod = m.stable
            engine.shake = m.shake
            if (!engine.ready.value || engine.capturing.value) continue
            val tv = engine.textureView ?: continue
            val bmp = try {
                if (tv.isAvailable) tv.getBitmap(72, if (engine.ratio == "16:9") 128 else 96) else null
            } catch (e: Throwable) {
                null
            } ?: continue
            val st = withContext(Dispatchers.Default) { FrameStats.from(bmp).also { bmp.recycle() } }
            frame = st
            detection = detector.update(DetectorInput(st, engine.live.value, m, engine.front.value))
        }
    }

    // ---------- Situation + style choisis ----------
    val sceneId = Prefs.cScene.value
    val isFront = engine.front.value
    val active: Scene = if (sceneId == "auto") detection.scene else Scenes.byId(sceneId)
    val lookId = Prefs.cLook.value
    val look = Looks.byId(if (lookId == Looks.AUTO) active.look else lookId)
    val amount = Prefs.cLookAmount.value

    // ---------- Réglages Pro ----------
    var evUser by remember { mutableFloatStateOf(0f) }
    var isoManual by remember { mutableStateOf<Int?>(null) }
    var shutterManual by remember { mutableStateOf<Long?>(null) }
    var focusManual by remember { mutableStateOf<Float?>(null) }
    var awbManual by remember { mutableStateOf<Int?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val ci = engine.info.value

    val params = CaptureParams(
        scene = active,
        evUser = evUser,
        iso = isoManual,
        shutterNs = shutterManual,
        focus = focusManual,
        awb = awbManual,
        flash = Prefs.cFlash.value,
        zoom = zoom,
        smartIso = Prefs.cSmartIso.value,
        allowTripod = Prefs.cTripod.value,
    )
    SideEffect { engine.update(params) }

    // ---------- Déclenchement ----------
    var countdown by remember { mutableIntStateOf(0) }
    val blink = remember { Animatable(0f) }
    val sound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    fun shoot() {
        if (engine.capturing.value || countdown > 0 || !engine.ready.value) return
        val meta = ShotMeta(scene = active.name, look = look.id, lookAmount = amount)
        scope.launch {
            val t = Prefs.cTimer.value
            for (i in t downTo 1) {
                countdown = i
                delay(1000)
            }
            countdown = 0
            if (Prefs.cSound.value) sound.play(MediaActionSound.SHUTTER_CLICK)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            engine.takePicture(meta)
            blink.snapTo(0.85f)
            blink.animateTo(0f, tween(260))
        }
    }
    SideEffect { activity.shutter = { shoot() } }

    // ---------- Enregistrement ----------
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    DisposableEffect(engine) {
        engine.onPhoto = { job ->
            scope.launch {
                val saved = withContext(Dispatchers.IO) { PhotoSaver.saveJpeg(ctx, job) }
                if (saved == null) ctx.toast("Impossible d'enregistrer la photo")
                else {
                    thumb = saved.thumb
                    lastUri = saved.uri
                }
            }
        }
        engine.onRaw = { job ->
            val uri = PhotoSaver.saveDng(ctx, job)
            mainHandler.post { ctx.toast(if (uri != null) "RAW (DNG) enregistré ✓" else "RAW non enregistré") }
        }
        engine.onMessage = { ctx.toast(it) }
        onDispose {
            engine.onPhoto = null
            engine.onRaw = null
            engine.onMessage = null
        }
    }

    // ---------- Interface ----------
    var tab by rememberSaveable { mutableStateOf("situations") }
    var showSettings by remember { mutableStateOf(false) }
    var showGuide by remember {
        mutableStateOf(!Prefs.cOnboarded.value || activity.intent?.getBooleanExtra("guide", false) == true)
    }
    val firstTime = remember { !Prefs.cOnboarded.value }
    var showTip by remember { mutableStateOf(false) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    val focusAlpha = remember { Animatable(0f) }
    val previewRatio = if (ratio == "16:9") 9f / 16f else 3f / 4f
    val livePaint = remember(look, amount) {
        Paint().apply { colorFilter = ColorMatrixColorFilter(look.matrix(amount)) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                hasFlash = ci?.hasFlash == true,
                rawCapable = ci?.rawCapable == true,
                onSettings = { showSettings = true },
                onGuide = { showGuide = true }
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(previewRatio)
                    .clip(RoundedCornerShape(if (ratio == "16:9") 0.dp else 4.dp))
            ) {
                AndroidView(
                    factory = { c -> TextureView(c).also { engine.attachView(it) } },
                    update = { tv -> tv.setLayerPaint(livePaint) },
                    modifier = Modifier.fillMaxSize()
                )
                // Gestes : toucher = mise au point / exposition, pincer = zoom
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { o ->
                                    focusAt = o
                                    engine.tapToFocus(o.x / size.width, o.y / size.height)
                                    scope.launch {
                                        focusAlpha.snapTo(1f)
                                        delay(1400)
                                        focusAlpha.animateTo(0f, tween(400))
                                    }
                                },
                                onDoubleTap = { if (Prefs.cScene.value != "auto") Prefs.cScene.value = "auto" }
                            )
                        }
                        .pointerInput(ci) {
                            detectTransformGestures { _, _, z, _ ->
                                val info = engine.info.value ?: return@detectTransformGestures
                                zoom = (zoom * z).coerceIn(info.zoomMin, info.zoomMax)
                            }
                        }
                )
                if (Prefs.cGrid.value) GridOverlay()
                if (Prefs.cLevel.value) LevelOverlay(motion)
                if (Prefs.cHisto.value) Histogram(frame.hist, Modifier.align(Alignment.BottomStart).padding(10.dp))
                FocusRing(focusAt, focusAlpha.value, engine.live.value.afLocked)
                if (countdown > 0) {
                    Text(
                        "$countdown", color = Color.White, fontSize = 110.sp, fontWeight = FontWeight.Thin,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = blink.value)))
                if (engine.capturing.value && engine.captureNote.value != null) {
                    Text(
                        engine.captureNote.value ?: "", color = Color.White, fontSize = 15.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Panel)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Bandeau : ce qui a été détecté et réglé
        SituationBanner(
            sceneId = sceneId,
            active = active,
            detection = detection,
            live = engine.live.value,
            expanded = showTip,
            tips = Prefs.cTips.value,
            onToggle = { showTip = !showTip },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 58.dp, start = 12.dp, end = 12.dp)
        )

        // Commandes du bas
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f), Color.Black)))
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (ci != null) ZoomPills(ci, zoom) { zoom = it }
            Spacer(Modifier.height(8.dp))
            when (tab) {
                "styles" -> LooksRow(lookId, amount)
                "pro" -> ProPanel(
                    ci = ci, live = engine.live.value,
                    ev = evUser, onEv = { evUser = it },
                    iso = isoManual, onIso = { isoManual = it },
                    shutter = shutterManual, onShutter = { shutterManual = it },
                    focus = focusManual, onFocus = { focusManual = it },
                    awb = awbManual, onAwb = { awbManual = it },
                )
                else -> ScenesRow(sceneId, detection, isFront)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                listOf("situations" to "SITUATIONS", "styles" to "STYLES", "pro" to "PRO").forEach { (id, label) ->
                    Text(
                        label,
                        color = if (tab == id) Yellow else Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { tab = id }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Galerie
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .clickable { openGallery(ctx, lastUri) },
                    contentAlignment = Alignment.Center
                ) {
                    val t = thumb
                    if (t != null) Image(t.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Rounded.PhotoLibrary, "Galerie", tint = Color.White)
                }
                // Déclencheur
                val busy = engine.capturing.value || countdown > 0
                Box(
                    Modifier
                        .size(82.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(7.dp)
                        .clip(CircleShape)
                        .background(if (busy) Color.White.copy(alpha = 0.45f) else Color.White)
                        .clickable(enabled = !busy) { shoot() }
                )
                // Avant / arrière
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable {
                            zoom = 1f
                            focusManual = null
                            engine.switchFacing()
                            if (Prefs.cScene.value == "selfie" && !engine.front.value) Prefs.cScene.value = "auto"
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Cameraswitch, "Changer de caméra", tint = Color.White) }
            }
        }

        // Fiche de la dernière photo
        ShotCard(
            shot = engine.lastShot.value,
            onView = { openGallery(ctx, lastUri) },
            onEdit = { EditActivity.open(ctx, lastUri) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 130.dp, start = 16.dp, end = 16.dp)
        )

        if (showSettings) SettingsSheet(ci) { showSettings = false }
        if (showGuide && granted) GuideSheet(firstTime) {
            showGuide = false
            Prefs.cOnboarded.value = true
        }

        // Autorisation / erreur
        if (!granted) {
            MessageOverlay(
                "📷 Autorise l'appareil photo",
                "Chahyn Photo a besoin de la caméra pour fonctionner. Les photos sont enregistrées dans ta Galerie, dossier « Chahyn Photo ».",
                if (asked) "Ouvrir les réglages" else "Autoriser"
            ) {
                if (asked && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                } else askPermissions()
            }
        } else engine.error.value?.let { err ->
            MessageOverlay("Oups", err, "Réessayer") {
                engine.error.value = null
                engine.reopen()
            }
        }
    }
}

private fun openGallery(ctx: android.content.Context, uri: Uri?) {
    try {
        val i = if (uri != null) Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        else Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        ctx.startActivity(i)
    } catch (e: Exception) {
        ctx.toast("Ouvre la Galerie › Albums › Chahyn Photo")
    }
}

// ======================================================================
// Barre du haut
// ======================================================================

@Composable
private fun TopBar(hasFlash: Boolean, rawCapable: Boolean, onSettings: () -> Unit, onGuide: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val flash = Prefs.cFlash.value
        TopIcon(
            when (flash) {
                "auto" -> Icons.Rounded.FlashAuto
                "on" -> Icons.Rounded.FlashOn
                "torch" -> Icons.Rounded.FlashlightOn
                else -> Icons.Rounded.FlashOff
            },
            active = flash != "off", enabled = hasFlash
        ) {
            Prefs.cFlash.value = when (flash) {
                "off" -> "auto"; "auto" -> "on"; "on" -> "torch"; else -> "off"
            }
        }
        val timer = Prefs.cTimer.value
        TopIcon(
            when (timer) {
                3 -> Icons.Rounded.Timer3
                10 -> Icons.Rounded.Timer10
                else -> Icons.Rounded.TimerOff
            },
            active = timer > 0
        ) { Prefs.cTimer.value = when (timer) { 0 -> 3; 3 -> 10; else -> 0 } }
        TopText(if (Prefs.cRatio.value == "16:9") "16:9" else "4:3", false) {
            Prefs.cRatio.value = if (Prefs.cRatio.value == "16:9") "4:3" else "16:9"
        }
        TopText(if (Prefs.cRes.value == "max") "MAX" else "12M", Prefs.cRes.value == "max") {
            Prefs.cRes.value = if (Prefs.cRes.value == "max") "12" else "max"
        }
        TopText("RAW", Prefs.cRaw.value, enabled = rawCapable) { Prefs.cRaw.value = !Prefs.cRaw.value }
        TopIcon(if (Prefs.cGrid.value) Icons.Rounded.GridOn else Icons.Rounded.GridOff, active = Prefs.cGrid.value) {
            Prefs.cGrid.value = !Prefs.cGrid.value
        }
        TopIcon(Icons.Rounded.HelpOutline, active = false, onClick = onGuide)
        TopIcon(Icons.Rounded.Settings, active = false, onClick = onSettings)
    }
}

@Composable
private fun TopIcon(icon: ImageVector, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = if (active) Yellow else Color.White, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun TopText(text: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(8.dp))
            .border(1.2.dp, if (active) Yellow else Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = if (active) Yellow else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ======================================================================
// Bandeau de situation
// ======================================================================

@Composable
private fun SituationBanner(
    sceneId: String,
    active: Scene,
    detection: Detection,
    live: LiveInfo,
    expanded: Boolean,
    tips: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier
) {
    val auto = sceneId == "auto"
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (auto) {
                Text("AUTO ", color = Yellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text("${active.emoji} ${active.name}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (live.iso > 0) {
                Text(
                    "   ISO ${live.iso} · ${Fmt.shutter(live.exposureNs)}",
                    color = TextDim, fontSize = 12.sp
                )
            }
        }
        val line = if (auto && detection.reasons.isNotEmpty()) detection.reasons.joinToString(" · ") else active.what
        Text(
            line, color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center,
            maxLines = if (expanded) 4 else 1, overflow = TextOverflow.Ellipsis
        )
        AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (auto) Text(active.what, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                if (tips) Text("💡 " + active.tip, color = Yellow, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ======================================================================
// Situations / Styles / Pro
// ======================================================================

@Composable
private fun ScenesRow(sceneId: String, detection: Detection, front: Boolean) {
    val list = Scenes.all.filter { !it.frontOnly || front }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(list, key = { it.id }) { s ->
            val sel = s.id == sceneId
            val label = if (s.id == "auto" && sel) "✨ Auto · ${detection.scene.emoji}" else "${s.emoji} ${s.name}"
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (sel) Yellow else Color.White.copy(alpha = 0.14f))
                    .clickable { Prefs.cScene.value = s.id }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    label, color = if (sel) Color.Black else Color.White,
                    fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LooksRow(lookId: String, amount: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val entries = listOf(Triple(Looks.AUTO, "Auto", 0xFFFFD60A)) + Looks.all.map { Triple(it.id, it.name, it.swatch) }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.first }) { (id, name, sw) ->
                val sel = id == lookId
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { Prefs.cLook.value = id }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (id == Looks.AUTO) Brush.sweepGradient(listOf(Color(0xFFFFD60A), Color(0xFFFF375F), Color(0xFF64D2FF), Color(0xFF34C759), Color(0xFFFFD60A)))
                                else Brush.linearGradient(listOf(Color(sw), Color(sw).copy(alpha = 0.55f)))
                            )
                            .border(if (sel) 3.dp else 1.dp, if (sel) Yellow else Color.White.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (id == Looks.AUTO) Text("✨", fontSize = 18.sp)
                    }
                    Text(name, color = if (sel) Yellow else Color.White, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        if (lookId != "naturel") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Intensité", color = TextDim, fontSize = 12.sp)
                Slider(
                    value = amount, onValueChange = { Prefs.cLookAmount.value = it }, valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(28.dp),
                    colors = camSliderColors()
                )
                Text("${(amount * 100).roundToInt()} %", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
            }
        }
    }
}

@Composable
private fun camSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Yellow,
    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent
)

private val awbChoices = listOf(
    null to "Auto",
    CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT to "☀️ Soleil",
    CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "☁️ Nuageux",
    CameraMetadata.CONTROL_AWB_MODE_SHADE to "🌳 Ombre",
    CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT to "💡 Ampoule",
    CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT to "🏢 Néon",
)

@Composable
private fun ProPanel(
    ci: CamInfo?,
    live: LiveInfo,
    ev: Float, onEv: (Float) -> Unit,
    iso: Int?, onIso: (Int?) -> Unit,
    shutter: Long?, onShutter: (Long?) -> Unit,
    focus: Float?, onFocus: (Float?) -> Unit,
    awb: Int?, onAwb: (Int?) -> Unit,
) {
    if (ci == null) return
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 230.dp)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "ISO ${live.iso}  ·  ${Fmt.shutter(live.exposureNs)}  ·  f/${"%.1f".format(live.aperture)}  ·  EV100 ${"%.1f".format(live.ev100)}  ·  MaP ${SceneDetector.distanceText(live.focusDiopters)}",
            color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        val manualOk = ci.manualSensor
        // EV
        ProRow("EV", Fmt.ev(ev), auto = null, onAuto = {}) {
            val lo = ci.evRange.lower * ci.evStep
            val hi = ci.evRange.upper * ci.evStep
            Slider(
                value = ev.coerceIn(lo, hi), onValueChange = { onEv((it / ci.evStep).roundToInt() * ci.evStep) },
                valueRange = lo..maxOf(hi, lo + 0.01f), colors = camSliderColors(), modifier = Modifier.height(30.dp)
            )
        }
        if (manualOk) {
            // ISO (échelle logarithmique)
            val iLo = ln(ci.isoRange.lower.toFloat())
            val iHi = ln(ci.isoRange.upper.toFloat())
            ProRow("ISO", iso?.toString() ?: "Auto ${live.iso}", auto = iso == null, onAuto = { onIso(null) }) {
                Slider(
                    value = ln((iso ?: live.iso.coerceAtLeast(ci.isoRange.lower)).toFloat()).coerceIn(iLo, iHi),
                    onValueChange = { onIso(roundIso(exp(it))) },
                    valueRange = iLo..iHi, colors = camSliderColors(), modifier = Modifier.height(30.dp)
                )
            }
            // Vitesse (échelle logarithmique), limitée à 30 s
            val sLo = ln(ci.expRange.lower.coerceAtLeast(125_000L).toFloat())
            val sHi = ln(minOf(ci.expRange.upper, ci.maxFrameNs, 30_000_000_000L).toFloat())
            ProRow("Vitesse", shutter?.let { Fmt.shutter(it) } ?: "Auto ${Fmt.shutter(live.exposureNs)}", auto = shutter == null, onAuto = { onShutter(null) }) {
                Slider(
                    value = ln((shutter ?: live.exposureNs.coerceAtLeast(1L)).toFloat()).coerceIn(sLo, sHi),
                    onValueChange = { onShutter(exp(it).roundToLong()) },
                    valueRange = sLo..maxOf(sHi, sLo + 0.01f), colors = camSliderColors(), modifier = Modifier.height(30.dp)
                )
            }
        }
        if (ci.minFocus > 0f) {
            ProRow("Netteté", focus?.let { SceneDetector.distanceText(it) } ?: "Auto", auto = focus == null, onAuto = { onFocus(null) }) {
                Slider(
                    value = (focus ?: live.focusDiopters).coerceIn(0f, ci.minFocus),
                    onValueChange = { onFocus(it) },
                    valueRange = 0f..ci.minFocus, colors = camSliderColors(), modifier = Modifier.height(30.dp)
                )
            }
        }
        Text("Balance des blancs", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(awbChoices.filter { (m, _) -> m == null || m in ci.awbModes }) { (mode, label) ->
                val sel = mode == awb
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (sel) Yellow else Color.White.copy(alpha = 0.14f))
                        .clickable { onAwb(mode) }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                ) { Text(label, color = if (sel) Color.Black else Color.White, fontSize = 12.sp) }
            }
        }
        Text(
            "Astuce : laisse ISO et Vitesse sur Auto — l'appli choisit déjà l'ISO le plus bas possible au déclenchement. " +
                "Toucher « Auto » remet un réglage en automatique.",
            color = TextDim, fontSize = 11.sp, lineHeight = 14.sp
        )
    }
}

private fun roundIso(v: Float): Int {
    val steps = intArrayOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800)
    return steps.minByOrNull { abs(ln(it.toFloat()) - ln(v)) } ?: v.roundToInt()
}

@Composable
private fun ProRow(label: String, value: String, auto: Boolean?, onAuto: () -> Unit, slider: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
            Text(value, color = if (auto == false) Yellow else TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (auto == false) {
                Text(
                    "AUTO", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Yellow)
                        .clickable(onClick = onAuto)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        slider()
    }
}

// ======================================================================
// Zoom
// ======================================================================

@Composable
private fun ZoomPills(ci: CamInfo, zoom: Float, onZoom: (Float) -> Unit) {
    val stops = buildList {
        if (ci.zoomMin < 0.95f) add(ci.zoomMin)
        listOf(1f, 2f, 3f, 5f, 10f).forEach { if (it <= ci.zoomMax + 0.01f && it >= ci.zoomMin) add(it) }
    }
    if (stops.size < 2) return
    val nearest = stops.minByOrNull { abs(ln(it) - ln(zoom)) }
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        stops.forEach { s ->
            val sel = s == nearest
            val label = if (sel) fmtZoom(zoom) + "×" else fmtZoom(s)
            Box(
                Modifier
                    .size(if (sel) 42.dp else 36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (sel) 0.22f else 0.10f))
                    .clickable { onZoom(s) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (sel) Yellow else Color.White, fontSize = if (sel) 12.sp else 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun fmtZoom(z: Float): String {
    val r = (z * 10).roundToInt()
    return if (r % 10 == 0) "${r / 10}" else "${r / 10},${r % 10}"
}

// ======================================================================
// Superpositions : grille, niveau, histogramme, mise au point
// ======================================================================

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(c, Offset(x, 0f), Offset(x, size.height), 1.2f)
            drawLine(c, Offset(0f, y), Offset(size.width, y), 1.2f)
        }
    }
}

@Composable
private fun LevelOverlay(motion: MotionSensor) {
    val m = motion.state.value
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        if (m.flat) {
            // Vue de dessus : bulle
            val ok = abs(m.tiltX) < 0.35f && abs(m.tiltY) < 0.35f
            val col = if (ok) IosGreen else Color.White
            drawCircle(col.copy(alpha = 0.8f), 34f, Offset(cx, cy), style = Stroke(2f))
            drawCircle(col, 9f, Offset(cx - m.tiltX * 14f, cy + m.tiltY * 14f))
        } else {
            val ok = abs(m.roll) < 1f
            val col = if (ok) IosGreen else Color.White.copy(alpha = 0.85f)
            val len = size.width * 0.16f
            rotate(-m.roll, Offset(cx, cy)) {
                drawLine(col, Offset(cx - len, cy), Offset(cx - 14f, cy), 3f)
                drawLine(col, Offset(cx + 14f, cy), Offset(cx + len, cy), 3f)
            }
            drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - len - 24f, cy), Offset(cx - len - 6f, cy), 2f)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(cx + len + 6f, cy), Offset(cx + len + 24f, cy), 2f)
        }
    }
}

@Composable
private fun Histogram(hist: FloatArray, modifier: Modifier) {
    Canvas(
        modifier
            .size(width = 110.dp, height = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        val bw = size.width / hist.size
        hist.forEachIndexed { i, v ->
            val h = v * size.height
            drawRect(Color.White.copy(alpha = 0.8f), Offset(i * bw, size.height - h), androidx.compose.ui.geometry.Size(bw + 0.5f, h))
        }
    }
}

@Composable
private fun FocusRing(at: Offset?, alpha: Float, locked: Boolean) {
    if (at == null || alpha <= 0f) return
    val d = LocalDensity.current
    val r = with(d) { 36.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((at.x - r).roundToInt(), (at.y - r).roundToInt()) }
            .size(72.dp)
            .alpha(alpha)
            .border(1.8.dp, if (locked) IosGreen else Yellow, RoundedCornerShape(10.dp))
    )
}

// ======================================================================
// Réglages
// ======================================================================

@Composable
private fun SettingsSheet(ci: CamInfo?, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClose)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color(0xFF16161A))
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Réglages photo", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
            }

            val sctx = LocalContext.current
            PrimaryButton("✏️ Retoucher une photo de la Galerie") { EditActivity.open(sctx) }

            Text("Résolution", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Chips(listOf("12" to "12 MP — recommandé", "max" to "Maximum"), Prefs.cRes.value) { Prefs.cRes.value = it }
            Text(
                "12 MP regroupe les pixels du capteur : moins de bruit, meilleure dynamique, surtout en faible lumière. " +
                    "« Maximum » sert pour recadrer des paysages en plein jour.",
                color = TextDim, fontSize = 13.sp
            )
            Text("Format", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Chips(listOf("4:3" to "4:3 (capteur entier)", "16:9" to "16:9"), Prefs.cRatio.value) { Prefs.cRatio.value = it }

            SettingSwitch(
                "RAW (DNG) en plus du JPEG",
                if (ci?.rawCapable == true) "Fichier brut pour retoucher dans Lightroom/Snapseed (≈ 25 Mo)" else "Non proposé par ce module photo",
                Prefs.cRaw.value, enabled = ci?.rawCapable == true
            ) { Prefs.cRaw.value = it }
            SettingSwitch(
                "ISO bas intelligent",
                "Au déclenchement, allonge la pose tant qu'elle reste nette pour réduire le bruit",
                Prefs.cSmartIso.value
            ) { Prefs.cSmartIso.value = it }
            SettingSwitch(
                "Pose longue si le téléphone est posé",
                "Détecte le trépied (gyroscope) : nuit jusqu'à 2 s, étoiles jusqu'à 10 s",
                Prefs.cTripod.value
            ) { Prefs.cTripod.value = it }
            SettingSwitch("Grille (règle des tiers)", null, Prefs.cGrid.value) { Prefs.cGrid.value = it }
            SettingSwitch("Niveau à bulle", "Ligne verte = parfaitement droit ; bulle quand tu vises à plat", Prefs.cLevel.value) { Prefs.cLevel.value = it }
            SettingSwitch("Histogramme", "Vérifie qu'aucune zone n'est brûlée ou bouchée", Prefs.cHisto.value) { Prefs.cHisto.value = it }
            SettingSwitch("Conseils de prise de vue", "Affichés quand tu touches le bandeau", Prefs.cTips.value) { Prefs.cTips.value = it }
            SettingSwitch("Son du déclencheur", null, Prefs.cSound.value) { Prefs.cSound.value = it }
            SettingSwitch("Touches de volume = déclencheur", null, Prefs.cVolumeShutter.value) { Prefs.cVolumeShutter.value = it }

            Text(
                "Les photos sont enregistrées dans Galerie › Albums › « Chahyn Photo ». Double-tape l'aperçu pour revenir en Auto.",
                color = TextDim, fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MessageOverlay(title: String, body: String, button: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(body, color = TextDim, fontSize = 15.sp, textAlign = TextAlign.Center)
            PrimaryButton(button, onClick = onClick)
        }
    }
}

// ======================================================================
// Fiche après la photo
// ======================================================================

@Composable
private fun ShotCard(shot: ShotInfo?, onView: () -> Unit, onEdit: () -> Unit, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(shot?.at) {
        if (shot == null) return@LaunchedEffect
        visible = true
        delay(6000)
        visible = false
    }
    AnimatedVisibility(visible && shot != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val s = shot ?: return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE6141418))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("📸 Photo enregistrée", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Situation : ${s.meta.scene}  ·  Style : ${Looks.byId(s.meta.look).name}" +
                    if (s.meta.look != "naturel") " ${(s.meta.lookAmount * 100).roundToInt()} %" else "",
                color = TextDim, fontSize = 12.sp
            )
            Text(
                buildString {
                    append("ISO ${s.iso} · ${Fmt.shutter(s.exposureNs)} · f/${"%.1f".format(s.aperture)}")
                    if (abs(s.zoom - 1f) > 0.05f) append(" · ${fmtZoom(s.zoom)}×")
                    if (s.flash) append(" · flash")
                    if (s.raw) append(" · + RAW")
                },
                color = Color.White, fontSize = 13.sp
            )
            s.meta.plan?.let { Text("✨ $it", color = Yellow, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    "Voir", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Color.White).clickable(onClick = onView)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
                Text(
                    "✏️ Retoucher", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Yellow).clickable(onClick = onEdit)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
                Text(
                    "OK", color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.clip(CircleShape).clickable { visible = false }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
