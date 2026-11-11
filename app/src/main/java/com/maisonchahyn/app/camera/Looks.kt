package com.maisonchahyn.app.camera

import android.graphics.ColorMatrix

/**
 * Un « style » (rendu) appliqué en direct sur l'aperçu et sur la photo finale.
 * Tout est exprimé autour du neutre : saturation 1, contraste 1, etc.
 */
data class Look(
    val id: String,
    val name: String,
    val swatch: Long,
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    val brightness: Float = 0f, // -1..1 (en fraction de 255)
    val warmth: Float = 0f,     // -1..1 : bleu ↔ orange
    val tint: Float = 0f,       // -1..1 : vert ↔ magenta
    val fade: Float = 0f,       // 0..1 : noirs relevés (effet film)
    val teal: Float = 0f,       // 0..1 : ombres bleu-vert (cinéma)
    val desc: String = "",
) {
    val isNeutral: Boolean
        get() = saturation == 1f && contrast == 1f && brightness == 0f && warmth == 0f &&
            tint == 0f && fade == 0f && teal == 0f

    /** Matrice de couleur, [amount] de 0 (neutre) à 1 (style complet). */
    fun matrix(amount: Float): ColorMatrix {
        val a = amount.coerceIn(0f, 1f)
        fun mix(neutral: Float, v: Float) = neutral + (v - neutral) * a

        val m = ColorMatrix()
        // Saturation
        m.setSaturation(mix(1f, saturation))
        // Température / teinte
        val w = mix(0f, warmth)
        val t = mix(0f, tint)
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f + 0.18f * w + 0.04f * t, 0f, 0f, 0f, 0f,
                    0f, 1f + 0.02f * w - 0.08f * t, 0f, 0f, 0f,
                    0f, 0f, 1f - 0.18f * w + 0.04f * t, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        // Contraste + luminosité
        val c = mix(1f, contrast)
        val b = mix(0f, brightness) * 255f
        val off = 128f * (1f - c) + b
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, off,
                    0f, c, 0f, 0f, off,
                    0f, 0f, c, 0f, off,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        // Ombres bleu-vert (approximation « teal & orange »)
        val tl = mix(0f, teal)
        if (tl > 0f) {
            m.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f + 0.06f * tl, 0f, 0f, 0f, -10f * tl,
                        0f, 1f, 0f, 0f, 6f * tl,
                        0f, 0f, 1f - 0.04f * tl, 0f, 14f * tl,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        // Fondu (noirs relevés)
        val f = mix(0f, fade)
        if (f > 0f) {
            val s = 1f - f * 0.85f
            val o = 255f * f * 0.6f
            m.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        s, 0f, 0f, 0f, o,
                        0f, s, 0f, 0f, o,
                        0f, 0f, s, 0f, o,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        return m
    }
}

object Looks {
    /** Pseudo-style : suit la situation détectée. */
    const val AUTO = "auto"

    val all: List<Look> = listOf(
        Look("naturel", "Naturel", 0xFF8E8E93, desc = "Aucune retouche : la photo la plus fidèle et la plus propre. Idéal si tu retouches ensuite."),
        Look("eclatant", "Éclatant", 0xFF34C759, saturation = 1.22f, contrast = 1.07f, desc = "Couleurs plus vives et un peu plus de punch : paysages, voyages, ciel bleu."),
        Look("peau", "Peau douce", 0xFFFFB199, saturation = 0.96f, contrast = 0.95f, brightness = 0.02f, warmth = 0.18f, tint = 0.10f, desc = "Contraste adouci et teint légèrement réchauffé : portraits et selfies flatteurs."),
        Look("dore", "Heure dorée", 0xFFFFB340, saturation = 1.12f, contrast = 1.04f, warmth = 0.55f, desc = "Lumière dorée de fin de journée : couchers de soleil, ambiances chaleureuses."),
        Look("cinema", "Cinéma", 0xFF2E8C8C, saturation = 0.86f, contrast = 1.12f, warmth = 0.20f, teal = 1f, desc = "Ombres bleu-vert, tons chauds, couleurs retenues : rendu de film, ville, nuit."),
        Look("film", "Film mat", 0xFFC8B79E, saturation = 0.88f, contrast = 0.94f, warmth = 0.15f, fade = 0.10f, desc = "Noirs relevés et couleurs douces, façon argentique : lifestyle, mode, café."),
        Look("froid", "Nordique", 0xFF64D2FF, saturation = 0.90f, contrast = 1.04f, warmth = -0.45f, desc = "Tons froids et purs : neige, mer, architecture moderne."),
        Look("pop", "Pop", 0xFFFF375F, saturation = 1.40f, contrast = 1.12f, desc = "Couleurs très saturées : street art, fêtes, objets colorés."),
        Look("gourmand", "Gourmand", 0xFFFF9F0A, saturation = 1.22f, contrast = 1.05f, brightness = 0.02f, warmth = 0.22f, desc = "Plus lumineux, chaud et appétissant : plats, desserts, boissons."),
        Look("net", "Net & précis", 0xFFBFC7D5, saturation = 1.02f, contrast = 1.12f, desc = "Contraste renforcé, couleurs justes : architecture, produits, détails."),
        Look("nb", "Noir & blanc", 0xFF3A3A3C, saturation = 0f, contrast = 1.16f, desc = "Noir et blanc contrasté : portraits de caractère, rue, graphisme."),
        Look("nbdoux", "N&B doux", 0xFF9A9A9E, saturation = 0f, contrast = 0.94f, fade = 0.06f, desc = "Noir et blanc doux et mat : ambiance poétique, mariages."),
        Look("document", "Document", 0xFFFFFFFF, saturation = 0.55f, contrast = 1.32f, brightness = 0.04f, desc = "Fond blanc et texte bien noir : feuilles, tickets, tableaux."),
    )

    private val map = all.associateBy { it.id }
    fun byId(id: String): Look = map[id] ?: all.first()
}
