package com.chahyn.photo

import android.hardware.camera2.CameraMetadata

/**
 * Une « situation » photo : tout ce que l'appareil règle automatiquement pour elle.
 *
 * Les temps sont en millisecondes :
 *  - [handheldMaxMs] : pose la plus longue acceptable à main levée (au zoom ×1, stabilisation optique active) ;
 *  - [motionMaxMs]   : pose la plus longue pour figer le mouvement (null = sujet immobile) ;
 *  - [tripodMaxMs]   : pose la plus longue quand le téléphone est posé / sur trépied (0 = jamais de pose longue).
 */
data class Scene(
    val id: String,
    val emoji: String,
    val name: String,
    val what: String,
    val tip: String,
    val ev: Float = 0f,
    val handheldMaxMs: Float = 33f,
    val motionMaxMs: Float? = null,
    val tripodMaxMs: Float = 0f,
    val isoCap: Int = 1600,
    val fps: Fps = Fps.NORMAL,
    val awb: Int = CameraMetadata.CONTROL_AWB_MODE_AUTO,
    val hdr: Boolean = false,
    val faces: Boolean = false,
    val macro: Boolean = false,
    val infinity: Boolean = false,
    val look: String = "naturel",
    val hidden: Boolean = false,
    val frontOnly: Boolean = false,
)

/** Cadence d'aperçu : basse = l'exposition automatique peut allonger la pose ; haute = pose courte. */
enum class Fps { LOW, NORMAL, HIGH }

object Scenes {
    val auto = Scene(
        "auto", "✨", "Auto intelligent",
        "Analyse la scène en continu (lumière, visages, distance, couleurs, stabilité) et choisit la meilleure situation.",
        "Laisse-toi guider : le bandeau en haut t'explique ce qui a été détecté et réglé."
    )

    val equilibre = Scene(
        "equilibre", "📷", "Standard",
        "Réglages équilibrés : ISO bas privilégié, vitesse de sécurité 1/30 s.",
        "Touche l'écran pour choisir le point de netteté et d'exposition.",
        handheldMaxMs = 33f, isoCap = 1600, hidden = true
    )

    val all: List<Scene> = listOf(
        auto,
        Scene(
            "portrait", "🧑", "Portrait",
            "Priorité aux visages (mise au point + exposition), pose ≤ 1/125 s, peau douce et naturelle.",
            "Recule d'un pas et zoome ×2 ou ×3 : visage plus flatteur, arrière-plan plus flou. Lumière de côté = plus de relief.",
            handheldMaxMs = 16f, motionMaxMs = 8f, isoCap = 1600, faces = true, look = "peau"
        ),
        Scene(
            "paysage", "🏞️", "Paysage",
            "ISO minimum pour un maximum de détails, mise au point lointaine, couleurs fidèles.",
            "Place l'horizon sur une ligne de la grille et garde le niveau au vert. Un premier plan donne de la profondeur.",
            handheldMaxMs = 33f, isoCap = 400, tripodMaxMs = 500f, look = "eclatant"
        ),
        Scene(
            "coucher", "🌅", "Coucher de soleil",
            "Expose pour le ciel (−0,7 EV), balance « soleil » pour garder les teintes dorées, HDR si dispo.",
            "Ne vise pas le soleil en plein centre : mets-le sur un tiers. Les silhouettes à contre-jour font de très belles images.",
            ev = -0.7f, handheldMaxMs = 33f, isoCap = 800, tripodMaxMs = 1000f,
            awb = CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT, hdr = true, look = "dore"
        ),
        Scene(
            "contrejour", "🌤️", "Contre-jour",
            "HDR + légère surexposition (+0,3 EV) pour déboucher les ombres sans brûler le ciel.",
            "Touche le sujet sur l'écran pour l'exposer correctement. Si le ciel est blanc, baisse l'EV dans Pro.",
            ev = 0.3f, handheldMaxMs = 33f, isoCap = 800, hdr = true, faces = true
        ),
        Scene(
            "nuit", "🌙", "Nuit",
            "ISO limité, pose allongée automatiquement : jusqu'à 1/10 s à main levée et 2 s si le téléphone est posé.",
            "Tiens le téléphone à deux mains, coudes contre le corps, ou pose-le : la pose s'allonge toute seule pour une image propre.",
            ev = -0.3f, handheldMaxMs = 100f, tripodMaxMs = 2000f, isoCap = 3200, fps = Fps.LOW
        ),
        Scene(
            "etoiles", "🌌", "Ciel étoilé",
            "Mise au point à l'infini, pose longue (jusqu'à 10 s posé), ISO maîtrisé.",
            "Obligatoire : pose le téléphone (trépied, sol, mur) et active le retardateur 3 s. Loin des lampadaires.",
            handheldMaxMs = 125f, tripodMaxMs = 10000f, isoCap = 3200, fps = Fps.LOW, infinity = true
        ),
        Scene(
            "interieur", "🛋️", "Intérieur",
            "Pose jusqu'à 1/20 s pour garder un ISO bas, balance des blancs auto adaptée aux lampes.",
            "Rapproche-toi d'une fenêtre si possible : la lumière du jour donne toujours la meilleure qualité.",
            handheldMaxMs = 50f, motionMaxMs = null, isoCap = 1600, fps = Fps.LOW, faces = true
        ),
        Scene(
            "sport", "⚡", "Sport & action",
            "Vitesse ≥ 1/1000 s pour figer le mouvement, l'ISO monte si besoin.",
            "Suis le sujet en bougeant le téléphone avec lui, et déclenche un peu avant le moment clé.",
            handheldMaxMs = 1f, motionMaxMs = 1f, isoCap = 3200, fps = Fps.HIGH
        ),
        Scene(
            "enfants", "🐾", "Enfants & animaux",
            "Vitesse ≥ 1/500 s, priorité visages, ISO adapté : fini les photos floues qui bougent.",
            "Mets-toi à leur hauteur. Pour les animaux, la mise au point sur les yeux fait toute la différence (touche l'œil).",
            handheldMaxMs = 2f, motionMaxMs = 2f, isoCap = 3200, fps = Fps.HIGH, faces = true
        ),
        Scene(
            "nourriture", "🍝", "Nourriture",
            "Couleurs gourmandes, légère surexposition (+0,3 EV), mise au point rapprochée.",
            "Lumière naturelle de côté (fenêtre). Vue de dessus pour les assiettes, de 3/4 pour les burgers et boissons.",
            ev = 0.3f, handheldMaxMs = 33f, isoCap = 1200, look = "gourmand"
        ),
        Scene(
            "macro", "🌸", "Macro / très près",
            "Mise au point rapprochée, pose courte contre les micro-mouvements.",
            "Si l'image reste floue, recule un peu ou passe en ×0,6 : l'ultra grand-angle fait la macro sur Galaxy.",
            handheldMaxMs = 16f, isoCap = 1600, macro = true
        ),
        Scene(
            "neige", "❄️", "Neige & plage",
            "+0,7 EV pour que la neige/le sable restent blancs (et pas gris), ISO minimum.",
            "Par grand soleil, un léger −0,3 EV dans Pro évite les reflets brûlés.",
            ev = 0.7f, handheldMaxMs = 16f, isoCap = 200
        ),
        Scene(
            "concert", "🎤", "Concert & scène",
            "−0,7 EV pour ne pas brûler les projecteurs, vitesse ≥ 1/125 s.",
            "Zoome au maximum ×3 ou ×5 pour garder une bonne qualité, et déclenche quand l'artiste est éclairé.",
            ev = -0.7f, handheldMaxMs = 8f, motionMaxMs = 8f, isoCap = 3200
        ),
        Scene(
            "feux", "🎆", "Feux d'artifice",
            "Mise au point à l'infini, pose longue jusqu'à 2 s posé pour capter les traînées, ISO bas.",
            "Pose le téléphone et déclenche juste au départ de la fusée.",
            ev = -0.3f, handheldMaxMs = 66f, tripodMaxMs = 2000f, isoCap = 400, fps = Fps.LOW, infinity = true
        ),
        Scene(
            "architecture", "🏛️", "Architecture",
            "ISO bas, détails nets, grille et niveau pour des lignes bien droites.",
            "Garde le téléphone parfaitement vertical (niveau vert) pour éviter les bâtiments qui « tombent ».",
            handheldMaxMs = 33f, isoCap = 400, tripodMaxMs = 1000f, look = "net"
        ),
        Scene(
            "document", "📄", "Document & texte",
            "Contraste élevé, texte net, +0,3 EV pour un fond blanc.",
            "Mets-toi bien au-dessus, parallèle à la feuille (niveau « à plat » vert), sans ton ombre dessus.",
            ev = 0.3f, handheldMaxMs = 33f, isoCap = 800, look = "document"
        ),
        Scene(
            "selfie", "🤳", "Selfie",
            "Priorité visage, +0,3 EV, peau douce et naturelle.",
            "Tiens le téléphone un peu au-dessus des yeux, face à la lumière.",
            ev = 0.3f, handheldMaxMs = 33f, motionMaxMs = 16f, isoCap = 1600, faces = true, look = "peau",
            frontOnly = true
        ),
    )

    private val byIdMap = (all + equilibre).associateBy { it.id }
    fun byId(id: String): Scene = byIdMap[id] ?: auto
}
