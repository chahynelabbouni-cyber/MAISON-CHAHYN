package com.maisonchahyn.app.camera

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Statistiques d'une miniature de l'aperçu (environ 72×96 pixels). */
data class FrameStats(
    val mean: Float = 0.5f,          // luminance moyenne 0..1
    val clipped: Float = 0f,         // part de pixels brûlés
    val deep: Float = 0f,            // part de pixels bouchés (noirs)
    val contrast: Float = 0.2f,      // écart-type de luminance
    val saturation: Float = 0.3f,    // saturation moyenne
    val warm: Float = 0f,            // part de tons chauds (orangés)
    val sky: Float = 0f,             // part de bleu ciel dans la moitié haute
    val green: Float = 0f,           // part de végétation
    val centerVsEdge: Float = 0f,    // luminance centre − bords (négatif = sujet sombre sur fond clair)
    val hist: FloatArray = FloatArray(64),
) {
    companion object {
        fun from(bmp: Bitmap): FrameStats {
            val w = bmp.width
            val h = bmp.height
            if (w == 0 || h == 0) return FrameStats()
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val hist = FloatArray(64)
            val hsv = FloatArray(3)
            var sum = 0.0
            var sum2 = 0.0
            var clipped = 0
            var deep = 0
            var satSum = 0.0
            var warm = 0
            var sky = 0
            var upper = 0
            var green = 0
            var cSum = 0.0
            var cN = 0
            var eSum = 0.0
            var eN = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val c = px[y * w + x]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val l = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                    sum += l
                    sum2 += l * l
                    hist[(l * 63.999f).toInt().coerceIn(0, 63)] += 1f
                    if (l > 0.96f) clipped++
                    if (l < 0.06f) deep++
                    android.graphics.Color.RGBToHSV(r, g, b, hsv)
                    val hue = hsv[0]
                    val sat = hsv[1]
                    val v = hsv[2]
                    satSum += sat
                    if (sat > 0.35f && v > 0.3f) {
                        if (hue < 50f || hue > 340f) warm++
                        if (hue in 70f..165f) green++
                    }
                    if (y < h / 2) {
                        upper++
                        if (hue in 185f..250f && sat > 0.18f && v > 0.45f) sky++
                    }
                    val inCenter = x > w / 4 && x < w * 3 / 4 && y > h / 4 && y < h * 3 / 4
                    if (inCenter) {
                        cSum += l; cN++
                    } else {
                        eSum += l; eN++
                    }
                }
            }
            val n = (w * h).toFloat()
            val mean = (sum / n).toFloat()
            val variance = (sum2 / n - (sum / n) * (sum / n)).toFloat()
            val maxBin = hist.maxOrNull() ?: 1f
            for (i in hist.indices) hist[i] = hist[i] / max(1f, maxBin)
            return FrameStats(
                mean = mean,
                clipped = clipped / n,
                deep = deep / n,
                contrast = sqrt(max(0f, variance)),
                saturation = (satSum / n).toFloat(),
                warm = warm / n,
                sky = if (upper > 0) sky / upper.toFloat() else 0f,
                green = green / n,
                centerVsEdge = ((if (cN > 0) cSum / cN else 0.0) - (if (eN > 0) eSum / eN else 0.0)).toFloat(),
                hist = hist,
            )
        }
    }
}

data class Detection(
    val scene: Scene = Scenes.equilibre,
    val reasons: List<String> = emptyList(),
)

data class DetectorInput(
    val frame: FrameStats,
    val live: LiveInfo,
    val motion: MotionState,
    val front: Boolean,
)

/**
 * Le cerveau du mode Auto : choisit la situation à partir de la lumière mesurée (EV),
 * des visages, de la distance de mise au point, des couleurs et de la stabilité du téléphone.
 * Une situation doit être vue 2 fois de suite avant d'être adoptée (pas de clignotement).
 */
class SceneDetector {
    private var current = Detection()
    private var candidate: String? = null
    private var count = 0

    fun update(i: DetectorInput): Detection {
        val next = classify(i)
        if (next.scene.id == current.scene.id) {
            current = next // met à jour les raisons
            candidate = null
            count = 0
        } else if (next.scene.id == candidate) {
            count++
            if (count >= 2) {
                current = next; candidate = null; count = 0
            }
        } else {
            candidate = next.scene.id
            count = 1
        }
        return current
    }

    private fun classify(i: DetectorInput): Detection {
        val f = i.frame
        val l = i.live
        val ev = l.ev100
        val evTxt = "EV ${"%.0f".format(ev)}"
        val why = mutableListOf<String>()
        fun pick(id: String) = Detection(Scenes.byId(id), why.toList())

        if (i.front) {
            why += if (l.faces > 0) "Visage détecté" else "Caméra avant"
            if (ev < 4f) why += "Peu de lumière ($evTxt)"
            return pick("selfie")
        }

        val stable = i.motion.stable
        val diopters = l.focusDiopters
        val bigFace = l.faces > 0 && l.faceArea > 0.025f

        // 1. Très peu de lumière
        if (ev < 2.5f) {
            why += "Lumière très faible ($evTxt)"
            if (stable) why += "Téléphone posé → pose longue"
            if (f.clipped > 0.03f && f.deep > 0.45f && !stable) {
                why += "Projecteurs dans le noir"
                return pick("concert")
            }
            return pick("nuit")
        }
        // 2. Faible lumière
        if (ev < 5.5f) {
            if (f.clipped > 0.04f && f.deep > 0.40f) {
                why += "Scène sombre + projecteurs ($evTxt)"
                return pick("concert")
            }
            why += "Lumière faible ($evTxt)"
            if (bigFace) why += "Visage détecté"
            return if (ev < 4f) {
                if (stable) why += "Téléphone posé → pose longue"
                pick("nuit")
            } else pick("interieur")
        }
        // 3. Contre-jour : beaucoup de blanc brûlé ET d'ombres, sujet central plus sombre
        val backlit = f.clipped > 0.08f && f.deep > 0.18f || (f.clipped > 0.12f && f.centerVsEdge < -0.12f)
        if (backlit) {
            why += "Fond très lumineux, sujet sombre"
            if (bigFace) why += "Visage détecté"
            return pick("contrejour")
        }
        // 4. Visages
        if (bigFace) {
            why += if (l.faces > 1) "${l.faces} visages détectés" else "Visage détecté"
            if (i.motion.shake > 0.6f) {
                why += "Mouvement → vitesse rapide"
                return pick("enfants")
            }
            return pick("portrait")
        }
        // 5. Très près
        if (diopters >= 7f) {
            why += "Sujet à ${distanceText(diopters)}"
            return pick("macro")
        }
        // 6. Texte / document (peu de couleur, très contrasté, proche)
        if (f.saturation < 0.10f && f.contrast > 0.28f && diopters in 2f..7f) {
            why += "Peu de couleurs, fort contraste, proche"
            return pick("document")
        }
        // 7. Nourriture (proche, couleurs chaudes et saturées)
        if (diopters in 2f..7f && f.warm > 0.28f && f.saturation > 0.32f) {
            why += "Sujet proche aux couleurs chaudes"
            return pick("nourriture")
        }
        // 8. Neige / plage
        if (ev > 12.5f && f.mean > 0.68f && f.saturation < 0.18f) {
            why += "Scène très claire ($evTxt)"
            return pick("neige")
        }
        // 9. Coucher de soleil
        if (f.warm > 0.30f && ev in 6f..12.5f && diopters < 1f) {
            why += "Ciel aux teintes chaudes"
            return pick("coucher")
        }
        // 10. Paysage
        if (diopters < 0.35f && ev > 10f && (f.sky + f.green) > 0.30f) {
            why += "Mise au point lointaine, ciel/végétation"
            return pick("paysage")
        }
        // 11. Le téléphone bouge beaucoup (on suit un sujet)
        if (i.motion.shake > 1.0f) {
            why += "Téléphone en mouvement → vitesse rapide"
            return pick("sport")
        }
        why += "Lumière correcte ($evTxt)"
        if (stable) why += "Téléphone stable"
        return Detection(Scenes.equilibre, why.toList())
    }

    companion object {
        fun distanceText(diopters: Float): String {
            if (diopters <= 0.05f) return "l'infini"
            val m = 1f / diopters
            return if (m < 1f) "${(m * 100).toInt()} cm" else "${"%.1f".format(m)} m"
        }

        /** EV100 = log2(N²/t) − log2(ISO/100). */
        fun ev100(aperture: Float, exposureNs: Long, iso: Int): Float {
            if (exposureNs <= 0 || iso <= 0) return 10f
            val t = exposureNs / 1e9
            val v = ln(aperture.toDouble() * aperture / t) / ln(2.0) - ln(iso / 100.0) / ln(2.0)
            return v.toFloat()
        }

        fun nearlySame(a: Double, b: Double) = abs(ln(a / b) / ln(2.0)) < 0.4
    }
}
