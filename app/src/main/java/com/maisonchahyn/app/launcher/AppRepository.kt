package com.maisonchahyn.app.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
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
    val mono: ImageBitmap?,
    val category: Int
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
            val out = mutableListOf<AppEntry>()
            val users = try { um.userProfiles } catch (e: Exception) { listOf(me) }
            for (user in users) {
                val acts = try { la.getActivityList(null, user) } catch (e: Exception) { emptyList() }
                for (info in acts) {
                    try {
                        val cn = info.componentName
                        val key = cn.flattenToShortString() + if (user == me) "" else "@" + user.hashCode()
                        val raw = info.getIcon(dpi)
                        val icon = if (user != me) ctx.packageManager.getUserBadgedIcon(raw, user) else raw
                        out += AppEntry(
                            key = key,
                            label = info.label?.toString() ?: cn.packageName,
                            pkg = cn.packageName,
                            cls = cn.className,
                            user = user,
                            icon = renderIcon(icon, ICON_PX).asImageBitmap(),
                            mono = renderMono(raw, ICON_PX)?.asImageBitmap(),
                            category = info.applicationInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
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
            c.drawColor(Color.WHITE)
            val pad = (size * 0.14f).toInt()
            d.setBounds(pad, pad, size - pad, size - pad)
            d.draw(c)
        }
        return bmp
    }

    fun renderMono(d: Drawable, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (d !is AdaptiveIconDrawable) return null
        val m = d.monochrome ?: return null
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inset = size / 4
        m.setBounds(-inset, -inset, size + inset, size + inset)
        m.draw(Canvas(bmp))
        return bmp
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
