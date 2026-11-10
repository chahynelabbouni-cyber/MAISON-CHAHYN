package com.maisonchahyn.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.maisonchahyn.app.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Moteur « Verre liquide » : l'arrière-plan (fond d'écran) est dessiné une fois par l'hôte,
 * puis chaque surface en verre redessine la portion qui se trouve derrière elle, floutée,
 * teintée, avec reflets et bordure lumineuse — comme le Liquid Glass d'iOS.
 */
class Backdrop(val isEmpty: Boolean = false, val drawer: DrawScope.(Size) -> Unit) {
    internal var coords: LayoutCoordinates? = null
    internal var size by mutableStateOf(Size.Zero)
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun BackdropHost(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(modifier.onGloballyPositioned {
            backdrop.coords = it
            backdrop.size = it.size.toSize()
        }) {
            if (!backdrop.isEmpty) {
                Canvas(Modifier.matchParentSize()) { backdrop.drawer(this, size) }
            }
            content()
        }
    }
}

@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Prefs.gCorner.value.dp),
    blur: Dp = Prefs.gBlur.value.dp,
    tintAlpha: Float = Prefs.gTint.value,
    dark: Boolean = Prefs.gDark.value,
    border: Float = Prefs.gBorder.value,
    highlight: Boolean = Prefs.gHighlight.value,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val backdrop = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val base = if (dark) Color.Black else Color.White
    Box(
        modifier
            .onGloballyPositioned { me ->
                val host = backdrop?.coords
                pos = try {
                    if (host != null && host.isAttached) host.localPositionOf(me, Offset.Zero) else me.positionInRoot()
                } catch (e: Exception) {
                    me.positionInRoot()
                }
            }
            .clip(shape)
    ) {
        if (backdrop != null && !backdrop.isEmpty) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .then(if (blur > 0.dp) Modifier.blur(blur, BlurredEdgeTreatment(shape)) else Modifier)
            ) {
                val full = backdrop.size
                if (full.width > 0f) {
                    translate(-pos.x, -pos.y) { backdrop.drawer(this, full) }
                }
            }
        }
        Box(Modifier.matchParentSize().background(base.copy(alpha = tintAlpha.coerceIn(0f, 1f))))
        if (highlight) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.20f),
                            0.42f to Color.Transparent,
                            1f to Color.White.copy(alpha = 0.06f)
                        )
                    )
            )
        }
        if (border > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = border),
                                Color.White.copy(alpha = border * 0.12f),
                                Color.White.copy(alpha = border * 0.7f)
                            )
                        ),
                        shape
                    )
            )
        }
        content()
    }
}

fun DrawScope.drawImageCover(img: ImageBitmap, s: Size) {
    if (img.width == 0 || img.height == 0) return
    val scale = max(s.width / img.width, s.height / img.height)
    val dw = img.width * scale
    val dh = img.height * scale
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(((s.width - dw) / 2f).roundToInt(), ((s.height - dh) / 2f).roundToInt()),
        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        filterQuality = FilterQuality.Low
    )
}

private fun DrawScope.blob(c: Color, x: Float, y: Float, r: Float, s: Size) {
    val center = Offset(s.width * x, s.height * y)
    val rad = s.width * r
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.75f), c.copy(alpha = 0f)), center, rad), rad, center)
}

fun DrawScope.drawAurora(s: Size) {
    drawRect(
        Brush.linearGradient(
            listOf(Color(0xFF0B1026), Color(0xFF1E1650), Color(0xFF06182A)),
            Offset.Zero, Offset(s.width, s.height)
        ), size = s
    )
    blob(Color(0xFF7B5CFF), 0.15f, 0.10f, 0.80f, s)
    blob(Color(0xFF00C2FF), 0.92f, 0.35f, 0.65f, s)
    blob(Color(0xFFFF4FA3), 0.20f, 0.70f, 0.65f, s)
    blob(Color(0xFF5B2DFF), 0.85f, 0.92f, 0.55f, s)
}

/** Arrière-plan de l'appli Maison : ton fond d'écran (assombri) ou une aurore. */
fun hubBackdrop(img: ImageBitmap?): Backdrop = Backdrop { s ->
    if (img != null) {
        drawImageCover(img, s)
        drawRect(Color.Black.copy(alpha = 0.40f), size = s)
    } else drawAurora(s)
}

/** Fond d'écran choisi dans Maison, gardé en mémoire pour le verre liquide. */
object WallpaperStore {
    val image = mutableStateOf<ImageBitmap?>(null)
    private var loadedKey = "?"

    suspend fun ensure(context: Context) {
        val key = Prefs.wallpaperPath.value + "#" + Prefs.wallpaperVersion.value
        if (key == loadedKey) return
        loadedKey = key
        val path = Prefs.wallpaperPath.value
        image.value = withContext(Dispatchers.IO) {
            try {
                val f = File(path)
                if (path.isEmpty() || !f.exists()) return@withContext null
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                var sample = 1
                while (o.outWidth / (sample * 2) >= 1080) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            } catch (e: Throwable) {
                null
            }
        }
    }
}
