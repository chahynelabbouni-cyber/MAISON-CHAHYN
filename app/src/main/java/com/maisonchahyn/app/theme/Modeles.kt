package com.maisonchahyn.app.theme

import android.content.Context
import android.graphics.BitmapFactory
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.wallpaper.WallpaperGen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un modèle = toute l'interface d'un coup : fond, icônes, couleurs, verre, clavier. */
data class Modele(
    val id: String,
    val name: String,
    val emoji: String,
    val desc: String,
    val wall: String,
    val shape: String,
    val style: String,
    val tint: Int,
    val accent: Int,
    val blur: Int = 24,
    val glassTint: Float = 0.16f,
    val darkGlass: Boolean = false,
    val border: Float = 0.45f,
    val corner: Int = 26,
    val kb: String = "verre",
    val columns: Int = 4,
    val iconSize: Int = 60,
    val labels: Boolean = true,
    val wallPath: String? = null,
)

object Modeles {
    private fun c(v: Long) = v.toInt()

    val list: List<Modele> = listOf(
        Modele("iphone", "iPhone Liquid Glass", "📱", "Le look iOS 26 : verre liquide, icônes en couleur, dock flou",
            "liquide_bleu", "squircle", "couleur", c(0xFF0A84FF), c(0xFF0A84FF)),
        Modele("ios_teinte", "iOS 18 sombre teinté", "🌙", "Icônes sombres teintées bleu glacier, ambiance nuit",
            "minuit", "squircle", "teinte", c(0xFF64D2FF), c(0xFF64D2FF), darkGlass = true, glassTint = 0.25f, kb = "verre_sombre"),
        Modele("verre_total", "Tout en verre", "🫧", "Icônes en verre transparent sur une aurore",
            "aurore", "squircle", "verre", c(0xFFFFFFFF), c(0xFFBF5AF2), blur = 30, glassTint = 0.20f, border = 0.6f, kb = "verre_sombre"),
        Modele("neon", "Néon Cyber", "⚡", "Pictogrammes lumineux sur fond noir, style gamer",
            "neon", "cercle", "neon", c(0xFFFF2BD6), c(0xFFFF2BD6), darkGlass = true, glassTint = 0.3f, kb = "nuit_bleue", corner = 20),
        Modele("pastel", "Pastel doux", "🍬", "Icônes pastel lavande, interface toute douce",
            "lavande", "squircle", "pastel", c(0xFFBF5AF2), c(0xFFAF52DE), glassTint = 0.30f, kb = "verre"),
        Modele("rose", "Rose bonbon", "🌸", "Rose, gouttes et verre liquide rose",
            "liquide_rose", "goutte", "pastel", c(0xFFFF4FA3), c(0xFFFF4FA3), kb = "rose"),
        Modele("noir_blanc", "Noir & blanc", "🖤", "Minimaliste : icônes monochromes, graphite",
            "graphite", "squircle", "mono", c(0xFFFFFFFF), c(0xFF8E8E93), darkGlass = true, glassTint = 0.25f, kb = "sombre"),
        Modele("epure", "Blanc épuré", "🤍", "Icônes blanches minimalistes sur sable",
            "sable", "arrondi", "inverse", c(0xFF5E5CE6), c(0xFF5E5CE6), glassTint = 0.35f, kb = "clair"),
        Modele("ocean", "Océan", "🌊", "Bleus profonds, icônes rondes teintées turquoise",
            "ocean", "cercle", "teinte", c(0xFF48CAE4), c(0xFF00B4D8), kb = "nuit_bleue"),
        Modele("sunset", "Coucher de soleil", "🌅", "Orange et rose chaleureux, icônes en couleur",
            "sunset", "squircle", "couleur", c(0xFFFF9F0A), c(0xFFFF9F0A), kb = "verre"),
        Modele("menthe", "Menthe zen", "🌿", "Frais et apaisant, icônes pastel vertes",
            "menthe", "arrondi", "pastel", c(0xFF30D158), c(0xFF30D158), glassTint = 0.3f, kb = "verre"),
        Modele("luxe", "Luxe or", "👑", "Noir profond et pictogrammes dorés",
            "minuit", "carre", "teinte", c(0xFFFFD60A), c(0xFFFFD60A), darkGlass = true, glassTint = 0.3f, kb = "sombre", corner = 18),
    )

    suspend fun apply(ctx: Context, m: Modele): Boolean {
        Prefs.lIconShape.value = m.shape
        Prefs.lIconStyle.value = m.style
        Prefs.lTintColor.value = m.tint
        Prefs.uiAccent.value = m.accent
        Prefs.gBlur.value = m.blur
        Prefs.gTint.value = m.glassTint
        Prefs.gDark.value = m.darkGlass
        Prefs.gBorder.value = m.border
        Prefs.gCorner.value = m.corner
        Prefs.gHighlight.value = true
        Prefs.kTheme.value = m.kb
        Prefs.lColumns.value = m.columns
        Prefs.lIconSize.value = m.iconSize
        Prefs.lShowLabels.value = m.labels
        Prefs.lUseSystemWallpaper.value = false
        Prefs.currentModel.value = m.id
        val bmp = (if (m.wallPath != null) {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(m.wallPath) }
        } else {
            val spec = WallpaperGen.presets.firstOrNull { it.id == m.wall } ?: WallpaperGen.presets.first()
            WallpaperGen.renderFull(ctx, spec)
        }) ?: return true
        return WallpaperGen.apply(ctx, bmp, WallpaperGen.BOTH, m.name)
    }

    // ---------- Mes modèles ----------
    fun custom(json: String = Prefs.customModels.value): List<Modele> = try {
        val a = JSONArray(json)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Modele(
                id = o.getString("id"), name = o.getString("name"), emoji = o.optString("emoji", "⭐"),
                desc = o.optString("desc", "Ma création"), wall = o.optString("wall", "aurore"),
                shape = o.getString("shape"), style = o.getString("style"), tint = o.getInt("tint"),
                accent = o.getInt("accent"), blur = o.getInt("blur"), glassTint = o.getDouble("glassTint").toFloat(),
                darkGlass = o.getBoolean("darkGlass"), border = o.getDouble("border").toFloat(), corner = o.getInt("corner"),
                kb = o.getString("kb"), columns = o.getInt("columns"), iconSize = o.getInt("iconSize"),
                labels = o.getBoolean("labels"), wallPath = o.optString("wallPath").ifEmpty { null }
            )
        }
    } catch (e: Throwable) {
        emptyList()
    }

    private fun save(list: List<Modele>) {
        val a = JSONArray()
        list.forEach { m ->
            a.put(JSONObject().apply {
                put("id", m.id); put("name", m.name); put("emoji", m.emoji); put("desc", m.desc); put("wall", m.wall)
                put("shape", m.shape); put("style", m.style); put("tint", m.tint); put("accent", m.accent)
                put("blur", m.blur); put("glassTint", m.glassTint.toDouble()); put("darkGlass", m.darkGlass)
                put("border", m.border.toDouble()); put("corner", m.corner); put("kb", m.kb); put("columns", m.columns)
                put("iconSize", m.iconSize); put("labels", m.labels); m.wallPath?.let { put("wallPath", it) }
            })
        }
        Prefs.customModels.value = a.toString()
    }

    /** Enregistre toute la configuration actuelle (y compris le fond d'écran) comme modèle. */
    suspend fun saveCurrent(ctx: Context, name: String): Modele {
        val id = "perso_" + System.currentTimeMillis()
        val wallPath = withContext(Dispatchers.IO) {
            try {
                val src = File(Prefs.wallpaperPath.value)
                if (!src.exists()) null else {
                    val dir = File(ctx.filesDir, "modeles").apply { mkdirs() }
                    val dst = File(dir, "$id.jpg")
                    src.copyTo(dst, overwrite = true)
                    dst.absolutePath
                }
            } catch (e: Throwable) {
                null
            }
        }
        val m = Modele(
            id = id, name = name, emoji = "⭐", desc = "Ma création", wall = "aurore",
            shape = Prefs.lIconShape.value, style = Prefs.lIconStyle.value, tint = Prefs.lTintColor.value,
            accent = Prefs.uiAccent.value, blur = Prefs.gBlur.value, glassTint = Prefs.gTint.value,
            darkGlass = Prefs.gDark.value, border = Prefs.gBorder.value, corner = Prefs.gCorner.value,
            kb = Prefs.kTheme.value, columns = Prefs.lColumns.value, iconSize = Prefs.lIconSize.value,
            labels = Prefs.lShowLabels.value, wallPath = wallPath
        )
        save(custom() + m)
        return m
    }

    fun delete(id: String) {
        custom().firstOrNull { it.id == id }?.wallPath?.let { try { File(it).delete() } catch (_: Throwable) {} }
        save(custom().filter { it.id != id })
    }
}
