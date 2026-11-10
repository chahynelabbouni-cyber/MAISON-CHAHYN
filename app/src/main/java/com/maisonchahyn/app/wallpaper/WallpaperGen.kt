package com.maisonchahyn.app.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import com.maisonchahyn.app.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class Blob(val x: Float, val y: Float, val r: Float, val color: Int)

data class WallSpec(
    val id: String,
    val name: String,
    val base: List<Int>,
    val blobs: List<Blob>,
    val ribbons: Boolean = false,
    val ribbonColor: Int = 0xFFFFFFFF.toInt()
)

object WallpaperGen {
    const val HOME = WallpaperManager.FLAG_SYSTEM
    const val LOCK = WallpaperManager.FLAG_LOCK
    const val BOTH = HOME or LOCK

    private fun c(v: Long) = v.toInt()

    val presets: List<WallSpec> = listOf(
        WallSpec("aurore", "Aurore", listOf(c(0xFF0B1026), c(0xFF241A5E)), listOf(
            Blob(0.18f, 0.14f, 0.62f, c(0xFF7B5CFF)), Blob(0.88f, 0.36f, 0.55f, c(0xFF00C2FF)),
            Blob(0.25f, 0.74f, 0.60f, c(0xFFFF4FA3)), Blob(0.82f, 0.92f, 0.50f, c(0xFF5B2DFF))
        )),
        WallSpec("liquide_bleu", "Verre liquide bleu", listOf(c(0xFF001A3D), c(0xFF0047AB)), listOf(
            Blob(0.2f, 0.2f, 0.6f, c(0xFF2F80FF)), Blob(0.85f, 0.55f, 0.55f, c(0xFF00D1FF)),
            Blob(0.3f, 0.9f, 0.5f, c(0xFF5E5CE6))
        ), ribbons = true),
        WallSpec("liquide_rose", "Verre liquide rose", listOf(c(0xFF2A0A2E), c(0xFF8E2DE2)), listOf(
            Blob(0.15f, 0.25f, 0.6f, c(0xFFFF4FA3)), Blob(0.9f, 0.4f, 0.5f, c(0xFFFF9F0A)),
            Blob(0.4f, 0.85f, 0.55f, c(0xFFAF52DE))
        ), ribbons = true),
        WallSpec("ocean", "Océan", listOf(c(0xFF021B33), c(0xFF03396C)), listOf(
            Blob(0.2f, 0.18f, 0.6f, c(0xFF00B4D8)), Blob(0.85f, 0.5f, 0.55f, c(0xFF0077B6)),
            Blob(0.3f, 0.85f, 0.6f, c(0xFF48CAE4))
        )),
        WallSpec("sunset", "Coucher de soleil", listOf(c(0xFF2B0F3A), c(0xFFFF7A59)), listOf(
            Blob(0.2f, 0.2f, 0.6f, c(0xFFFF4F79)), Blob(0.85f, 0.45f, 0.55f, c(0xFFFFB347)),
            Blob(0.35f, 0.85f, 0.6f, c(0xFFFFD60A))
        )),
        WallSpec("lavande", "Lavande", listOf(c(0xFFE9E4FF), c(0xFFC9D6FF)), listOf(
            Blob(0.2f, 0.2f, 0.6f, c(0xFFB9A6FF)), Blob(0.85f, 0.5f, 0.55f, c(0xFFFFC2E2)),
            Blob(0.3f, 0.88f, 0.6f, c(0xFFA6C8FF))
        )),
        WallSpec("menthe", "Menthe", listOf(c(0xFFE6FFF6), c(0xFFB8F2E6)), listOf(
            Blob(0.2f, 0.2f, 0.6f, c(0xFF7CE3C3)), Blob(0.85f, 0.5f, 0.5f, c(0xFFFFF3A6)),
            Blob(0.35f, 0.88f, 0.6f, c(0xFF64D2FF))
        )),
        WallSpec("minuit", "Minuit", listOf(c(0xFF000000), c(0xFF0A0A14)), listOf(
            Blob(0.2f, 0.25f, 0.55f, c(0xFF1C2B6B)), Blob(0.85f, 0.7f, 0.55f, c(0xFF3A1C6B))
        )),
        WallSpec("neon", "Néon", listOf(c(0xFF05010F), c(0xFF150530)), listOf(
            Blob(0.15f, 0.2f, 0.5f, c(0xFFFF2BD6)), Blob(0.9f, 0.45f, 0.5f, c(0xFF00F0FF)),
            Blob(0.3f, 0.9f, 0.5f, c(0xFF7B2BFF))
        ), ribbons = true, ribbonColor = c(0xFF9EF6FF)),
        WallSpec("sable", "Sable", listOf(c(0xFFF5E9DA), c(0xFFE8CBA8)), listOf(
            Blob(0.2f, 0.2f, 0.6f, c(0xFFFFD1A6)), Blob(0.85f, 0.5f, 0.55f, c(0xFFE8A87C)),
            Blob(0.3f, 0.88f, 0.6f, c(0xFFFFE8C2))
        )),
        WallSpec("graphite", "Graphite", listOf(c(0xFF1C1C1E), c(0xFF3A3A3C)), listOf(
            Blob(0.2f, 0.2f, 0.6f, c(0xFF48484A)), Blob(0.85f, 0.7f, 0.55f, c(0xFF2C3E50))
        ), ribbons = true, ribbonColor = c(0xFFDDDDDD)),
        WallSpec("peche", "Pêche", listOf(c(0xFFFFE5D9), c(0xFFFFCAD4)), listOf(
            Blob(0.2f, 0.2f, 0.6f, c(0xFFFFB4A2)), Blob(0.85f, 0.55f, 0.5f, c(0xFFF4ACB7)),
            Blob(0.35f, 0.9f, 0.6f, c(0xFFFFD6A5))
        )),
    )

    fun custom(c1: Int, c2: Int, c3: Int, ribbons: Boolean): WallSpec = WallSpec(
        "perso", "Mon dégradé", listOf(darken(c1), darken(c2)), listOf(
            Blob(0.18f, 0.18f, 0.62f, c1), Blob(0.88f, 0.48f, 0.55f, c2), Blob(0.3f, 0.88f, 0.62f, c3)
        ), ribbons = ribbons
    )

    private fun darken(c: Int): Int {
        val r = ((c shr 16) and 0xFF) * 45 / 100
        val g = ((c shr 8) and 0xFF) * 45 / 100
        val b = (c and 0xFF) * 45 / 100
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun render(spec: WallSpec, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.35f, h.toFloat(), spec.base.toIntArray(), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val m = max(w, h).toFloat()
        for (b in spec.blobs) {
            val cx = b.x * w
            val cy = b.y * h
            val r = b.r * m
            p.shader = RadialGradient(cx, cy, r, intArrayOf(b.color, b.color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            cv.drawCircle(cx, cy, r, p)
        }
        p.shader = null
        if (spec.ribbons) drawRibbons(cv, w, h, spec.ribbonColor)
        return bmp
    }

    /** Rubans de verre : courbes lumineuses floutées, effet « liquide ». */
    private fun drawRibbons(cv: Canvas, w: Int, h: Int, color: Int) {
        val rgb = color and 0x00FFFFFF
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val ribbons = listOf(
            floatArrayOf(-0.1f, 0.30f, 0.35f, 0.10f, 0.65f, 0.55f, 1.1f, 0.35f, 0.16f),
            floatArrayOf(-0.1f, 0.62f, 0.30f, 0.45f, 0.70f, 0.85f, 1.1f, 0.66f, 0.10f),
            floatArrayOf(-0.1f, 0.88f, 0.40f, 0.72f, 0.60f, 1.05f, 1.1f, 0.84f, 0.07f),
        )
        for (r in ribbons) {
            val path = Path().apply {
                moveTo(r[0] * w, r[1] * h)
                cubicTo(r[2] * w, r[3] * h, r[4] * w, r[5] * h, r[6] * w, r[7] * h)
            }
            val sw = r[8] * w
            // halo
            p.strokeWidth = sw
            p.maskFilter = BlurMaskFilter(sw * 0.6f, BlurMaskFilter.Blur.NORMAL)
            p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
                intArrayOf(rgb or 0x10000000, rgb or 0x55000000, rgb or 0x18000000), null, Shader.TileMode.CLAMP)
            cv.drawPath(path, p)
            // reflet net
            p.strokeWidth = sw * 0.08f
            p.maskFilter = BlurMaskFilter(sw * 0.05f + 1f, BlurMaskFilter.Blur.NORMAL)
            p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
                intArrayOf(rgb or 0x00000000, rgb or 0x99000000.toInt(), rgb or 0x00000000), null, Shader.TileMode.CLAMP)
            cv.save()
            cv.translate(0f, -sw * 0.3f)
            cv.drawPath(path, p)
            cv.restore()
        }
    }

    fun screenSize(ctx: Context): Pair<Int, Int> {
        val wm = ctx.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = ctx.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
        val sw = minOf(w, h).coerceAtLeast(720)
        val sh = maxOf(w, h).coerceAtLeast(1280)
        return sw to sh
    }

    suspend fun renderFull(ctx: Context, spec: WallSpec): Bitmap = withContext(Dispatchers.Default) {
        val (w, h) = screenSize(ctx)
        render(spec, w, h)
    }

    suspend fun loadFromUri(ctx: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val (sw, sh) = screenSize(ctx)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (o.outWidth / (sample * 2) >= sw && o.outHeight / (sample * 2) >= sh) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            // Recadrage au format de l'écran
            val target = sw.toFloat() / sh
            val cur = bmp.width.toFloat() / bmp.height
            if (cur > target) {
                val nw = (bmp.height * target).roundToInt()
                Bitmap.createBitmap(bmp, (bmp.width - nw) / 2, 0, nw, bmp.height)
            } else {
                val nh = (bmp.width / target).roundToInt()
                Bitmap.createBitmap(bmp, 0, (bmp.height - nh) / 2, bmp.width, nh)
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** Enregistre (pour le verre liquide de l'écran d'accueil) puis applique au téléphone. */
    suspend fun apply(ctx: Context, bmp: Bitmap, which: Int, name: String): Boolean {
        val path = withContext(Dispatchers.IO) {
            try {
                val f = File(ctx.filesDir, "wallpaper.jpg")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 94, it) }
                f.absolutePath
            } catch (e: Throwable) {
                null
            }
        }
        val ok = withContext(Dispatchers.IO) {
            try {
                WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, which)
                true
            } catch (e: Throwable) {
                false
            }
        }
        if (path != null && (which and HOME) != 0) {
            Prefs.wallpaperPath.value = path
            Prefs.wallpaperVersion.value = Prefs.wallpaperVersion.value + 1
            Prefs.wallpaperName.value = name
        }
        return ok
    }
}
