package com.maisonchahyn.app.camera

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import kotlin.math.max

/**
 * Amélioration automatique : analyse l'histogramme de chaque couleur et
 *  - rétablit des noirs profonds et des blancs propres (étirement des niveaux),
 *  - corrige en partie les dominantes de couleur (sans effacer l'ambiance d'un coucher de soleil),
 *  - éclaircit légèrement les photos sous-exposées et ajoute un peu d'éclat.
 */
object Enhance {
    data class Analysis(val matrix: ColorMatrix, val summary: String)

    fun analyse(bmp: Bitmap): Analysis {
        // échantillonnage : ~40 000 pixels suffisent
        val step = max(1, ((bmp.width.toLong() * bmp.height) / 40_000L).toInt().let { kotlin.math.sqrt(it.toDouble()).toInt() })
        val hr = IntArray(256)
        val hg = IntArray(256)
        val hb = IntArray(256)
        var n = 0
        var lumSum = 0.0
        val row = IntArray(bmp.width)
        var y = 0
        while (y < bmp.height) {
            bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
            var x = 0
            while (x < bmp.width) {
                val c = row[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                hr[r]++; hg[g]++; hb[b]++
                lumSum += 0.299 * r + 0.587 * g + 0.114 * b
                n++
                x += step
            }
            y += step
        }
        if (n == 0) return Analysis(ColorMatrix(), "")
        fun pct(h: IntArray, p: Double): Int {
            val target = n * p
            var acc = 0
            for (i in 0..255) {
                acc += h[i]
                if (acc >= target) return i
            }
            return 255
        }
        val lo = intArrayOf(pct(hr, 0.005), pct(hg, 0.005), pct(hb, 0.005))
        val hi = intArrayOf(pct(hr, 0.995), pct(hg, 0.995), pct(hb, 0.995))
        val gLo = lo.min()
        val gHi = hi.max()

        val scale = FloatArray(3)
        val off = FloatArray(3)
        for (i in 0..2) {
            // 50 % correction par couleur (dominantes) + 50 % correction globale (contraste)
            val l = 0.5f * lo[i] + 0.5f * gLo
            val h = 0.5f * hi[i] + 0.5f * gHi
            val s = (255f / max(40f, h - l)).coerceIn(1f, 1.6f)
            scale[i] = s
            off[i] = -l * s
        }
        val m = ColorMatrix(
            floatArrayOf(
                scale[0], 0f, 0f, 0f, off[0],
                0f, scale[1], 0f, 0f, off[1],
                0f, 0f, scale[2], 0f, off[2],
                0f, 0f, 0f, 1f, 0f
            )
        )
        // Photo sombre : un peu plus de lumière
        val mean = (lumSum / n / 255.0).toFloat()
        val lift = when {
            mean < 0.28f -> 18f
            mean < 0.38f -> 9f
            else -> 0f
        }
        if (lift > 0f) {
            m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, lift, 0f, 1f, 0f, 0f, lift, 0f, 0f, 1f, 0f, lift, 0f, 0f, 0f, 1f, 0f)))
        }
        // Un peu d'éclat
        m.postConcat(ColorMatrix().apply { setSaturation(1.08f) })

        val parts = mutableListOf<String>()
        if (gLo > 12 || gHi < 243) parts += "noirs et blancs rétablis"
        if ((hi.max() - hi.min()) > 14 || (lo.max() - lo.min()) > 14) parts += "dominante corrigée"
        if (lift > 0f) parts += "photo éclaircie"
        parts += "éclat +8 %"
        return Analysis(m, parts.joinToString(" · "))
    }
}
