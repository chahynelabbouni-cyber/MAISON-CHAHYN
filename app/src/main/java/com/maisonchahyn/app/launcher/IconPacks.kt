package com.maisonchahyn.app.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Packs d'icônes (format ADW / Nova / Apex) installés depuis le Play Store,
 * par ex. les packs « iOS 26 », « Liquid Glass », « Pixel »…
 */
object IconPacks {
    data class Pack(val pkg: String, val label: String, val icon: ImageBitmap?)

    private val actions = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME", "com.fede.launcher.THEME_ICONPACK", "com.lge.launcher2.THEME"
    )

    fun list(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        val found = linkedMapOf<String, Pack>()
        val intents = actions.map { Intent(it) } +
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME")
        for (i in intents) {
            val res = try { pm.queryIntentActivities(i, 0) } catch (e: Exception) { emptyList() }
            for (ri in res) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg in found) continue
                val icon = try { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() } catch (e: Throwable) { null }
                found[pkg] = Pack(pkg, ri.loadLabel(pm).toString(), icon)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    private var cachePkg: String? = null
    private var cacheMap: Map<String, String> = emptyMap()
    private var cacheByPkg: Map<String, String> = emptyMap()

    @Synchronized
    private fun ensure(ctx: Context, pkg: String) {
        if (pkg == cachePkg) return
        val map = HashMap<String, String>()
        val byPkg = HashMap<String, String>()
        try {
            val res = ctx.packageManager.getResourcesForApplication(pkg)
            val id = res.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (id != 0) res.getXml(id) else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), "UTF-8")
                }
            }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null) {
                        val c = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        map[c] = dr
                        val p = c.substringBefore("/")
                        if (p !in byPkg) byPkg[p] = dr
                    }
                }
                ev = parser.next()
            }
        } catch (_: Throwable) {
        }
        cachePkg = pkg
        cacheMap = map
        cacheByPkg = byPkg
    }

    fun drawableFor(ctx: Context, pkg: String, cn: ComponentName): Drawable? {
        ensure(ctx, pkg)
        val cls = cn.className
        val name = cacheMap[cn.packageName + "/" + cls]
            ?: cacheMap[cn.packageName + "/" + cls.removePrefix(cn.packageName)]
            ?: cacheByPkg[cn.packageName]
            ?: return null
        return try {
            val res = ctx.packageManager.getResourcesForApplication(pkg)
            val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 }
                ?: res.getIdentifier(name, "mipmap", pkg).takeIf { it != 0 }
                ?: return null
            res.getDrawable(id, null)
        } catch (e: Throwable) {
            null
        }
    }

    fun invalidate() {
        cachePkg = null
    }
}
