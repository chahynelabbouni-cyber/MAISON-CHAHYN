package com.maisonchahyn.app.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.maisonchahyn.app.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val key: String,
    val label: String,
    val pkg: String,
    val cls: String,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** Pictogramme seul (couche monochrome, ou premier plan de l'icône) pour les styles teinté/verre/néon. */
    val mono: ImageBitmap?,
    val category: Int,
    val fromPack: Boolean = false
)

object AppRepository {
    val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private const val ICON_PX = 192

    suspend fun load(ctx: Context) {
        val list = withContext(Dispatchers.Default) {
            val la = ctx.getSystemService(LauncherApps::class.java)
            val um = ctx.getSystemService(UserManager::class.java)
            val me = Process.myUserHandle()
            val dpi = ctx.resources.displayMetrics.densityDpi
            val useSystem = Prefs.lSystemIcons.value
            val pack = Prefs.lIconPack.value.takeIf { it.isNotEmpty() && it != ctx.packageName }
            val out = mutableListOf<AppEntry>()
            val users = try { um.userProfiles } catch (e: Exception) { listOf(me) }
            for (user in users) {
                val acts = try { la.getActivityList(null, user) } catch (e: Exception) { emptyList() }
                for (info in acts) {
                    try {
                        val cn = info.componentName
                        val key = cn.flattenToShortString() + if (user == me) "" else "@" + user.hashCode()
                        val themed = info.getIcon(dpi)
                        // Logo d'origine en couleur (contourne les icônes du thème Samsung)
                        val raw = if (useSystem || user != me) themed else (originalIcon(ctx, info, dpi) ?: themed)
                        val packIcon = pack?.let { IconPacks.drawableFor(ctx, it, cn) }
                        val base = packIcon ?: raw
                        val icon = if (user != me) ctx.packageManager.getUserBadgedIcon(base, user) else base
                        out += AppEntry(
                            key = key,
                            label = info.label?.toString() ?: cn.packageName,
                            pkg = cn.packageName,
                            cls = cn.className,
                            user = user,
                            icon = (if (packIcon != null) renderPack(icon, ICON_PX) else renderIcon(icon, ICON_PX)).asImageBitmap(),
                            mono = renderMono(raw, ICON_PX)?.asImageBitmap(),
                            category = info.applicationInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED,
                            fromPack = packIcon != null
                        )
                    } catch (_: Throwable) {
                    }
                }
            }
            val collator = Collator.getInstance(Locale.FRENCH)
            out.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
        apps.value = list
    }

    /** Rendu « plein cadre » : les icônes adaptatives remplissent toute la forme, comme sur iPhone. */
    fun renderIcon(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (d is AdaptiveIconDrawable) {
            val inset = size / 4
            val bg = d.background
            if (bg != null) {
                bg.setBounds(-inset, -inset, size + inset, size + inset); bg.draw(c)
            } else c.drawColor(Color.WHITE)
            d.foreground?.let {
                it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c)
            }
        } else {
            drawLegacyFull(d, size, c)
        }
        return bmp
    }

    /** Charge le logo directement depuis l'appli (sans les icônes de thème One UI). */
    private fun originalIcon(ctx: Context, info: LauncherActivityInfo, dpi: Int): Drawable? = try {
        val pm = ctx.packageManager
        val ai = info.applicationInfo
        val act = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.activityInfo
        else pm.getActivityInfo(info.componentName, 0)
        val id = act.iconResource.takeIf { it != 0 } ?: ai.icon
        if (id == 0) null else pm.getResourcesForApplication(ai).getDrawableForDensity(id, dpi, null)
    } catch (e: Throwable) {
        null
    }

    /**
     * Ancienne icône (non adaptative) : on recadre sur le logo visible et on le fait remplir
     * toute la forme, avec la couleur de fond du logo — plus de cadre blanc.
     */
    private fun drawLegacyFull(d: Drawable, size: Int, c: Canvas) {
        val tmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(tmp))
        var l = size; var t = size; var r = -1; var b = -1
        val px = IntArray(size * size)
        tmp.getPixels(px, 0, size, 0, 0, size, size)
        for (y in 0 until size) for (x in 0 until size) {
            if ((px[y * size + x] ushr 24) > 40) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            }
        }
        if (r <= l || b <= t) {
            c.drawColor(Color.WHITE); return
        }
        // Couleur de fond = pixel juste à l'intérieur du bord supérieur du logo
        val cx = (l + r) / 2
        val sample = px[(t + (b - t) / 12).coerceAtMost(size - 1) * size + cx]
        val bg = if ((sample ushr 24) > 200) sample or 0xFF000000.toInt() else Color.WHITE
        c.drawColor(bg)
        val w = r - l; val h = b - t
        val over = 0.06f
        val src = android.graphics.Rect(l, t, r + 1, b + 1)
        val dst = android.graphics.RectF(-size * over, -size * over, size * (1 + over), size * (1 + over))
        if (w.toFloat() / h > 1.15f || h.toFloat() / w > 1.15f) {
            // logo pas carré : on le centre sans le déformer
            val scale = size * 0.78f / maxOf(w, h)
            val dw = w * scale; val dh = h * scale
            dst.set((size - dw) / 2, (size - dh) / 2, (size + dw) / 2, (size + dh) / 2)
        }
        c.drawBitmap(tmp, src, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
    }

    /** Icône d'un pack : dessinée telle quelle, plein cadre. */
    fun renderPack(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }

    fun renderMono(d: Drawable, size: Int): Bitmap? {
        if (d !is AdaptiveIconDrawable) return null
        val inset = size / 4
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val m = d.monochrome
            if (m != null) {
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                m.setBounds(-inset, -inset, size + inset, size + inset)
                m.draw(Canvas(bmp))
                return bmp
            }
        }
        // Repli : la silhouette du premier plan (si ce n'est pas un carré plein)
        val fg = d.foreground ?: return null
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        fg.setBounds(-inset, -inset, size + inset, size + inset)
        fg.draw(Canvas(bmp))
        val small = Bitmap.createScaledBitmap(bmp, 32, 32, true)
        var filled = 0
        for (y in 0 until 32) for (x in 0 until 32) if ((small.getPixel(x, y) ushr 24) > 40) filled++
        return if (filled > 32 * 32 * 0.80 || filled < 8) null else bmp
    }

    fun categoryName(cat: Int): String = when (cat) {
        ApplicationInfo.CATEGORY_GAME -> "Jeux"
        ApplicationInfo.CATEGORY_AUDIO -> "Musique & audio"
        ApplicationInfo.CATEGORY_VIDEO -> "Vidéo"
        ApplicationInfo.CATEGORY_IMAGE -> "Photo"
        ApplicationInfo.CATEGORY_SOCIAL -> "Réseaux sociaux"
        ApplicationInfo.CATEGORY_NEWS -> "Actualités"
        ApplicationInfo.CATEGORY_MAPS -> "Navigation"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivité"
        else -> "Utilitaires & autres"
    }
}

/** Le dock en verre (4 à 5 applis). */
object Dock {
    private val defaults = listOf(
        listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer"),
        listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
        listOf("com.sec.android.app.sbrowser", "com.android.chrome"),
        listOf("com.sec.android.app.camera", "com.android.camera2", "com.google.android.GoogleCamera"),
    )
    const val MAX = 5

    fun keys(apps: List<AppEntry>): List<String> {
        val s = Prefs.lDockApps.value
        if (s.isNotEmpty()) return s.split(",").filter { it.isNotBlank() && it != "-" }
        return defaults.mapNotNull { cands ->
            cands.firstNotNullOfOrNull { p -> apps.firstOrNull { it.pkg == p }?.key }
        }
    }

    private fun save(list: List<String>) {
        Prefs.lDockApps.value = if (list.isEmpty()) "-" else list.joinToString(",")
    }

    fun add(apps: List<AppEntry>, key: String): Boolean {
        val k = keys(apps).toMutableList()
        if (key in k) return true
        if (k.size >= MAX) return false
        k += key; save(k); return true
    }

    fun remove(apps: List<AppEntry>, key: String) = save(keys(apps).filter { it != key })

    fun reset() {
        Prefs.lDockApps.value = ""
    }
}
