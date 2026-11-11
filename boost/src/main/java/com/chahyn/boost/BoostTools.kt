package com.chahyn.boost

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import java.io.File
import java.util.Locale

/**
 * Le moteur « Boost » : modes de performance, nettoyage du stockage,
 * détection des applis inutilisées et interprétation des commandes en français.
 */
object BoostTools {

    // ================= Permissions =================

    fun hasAllFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    fun requestAllFiles(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SystemTools.safeStart(
                ctx,
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            )
        }
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageAccess(ctx: Context) {
        SystemTools.safeStart(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun hasDndAccess(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun requestDndAccess(ctx: Context) {
        SystemTools.safeStart(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    // ================= État du téléphone =================

    data class Health(
        val ramUsed: Long, val ramTotal: Long,
        val storageUsed: Long, val storageTotal: Long,
        val batteryPct: Int, val batteryTempC: Float, val charging: Boolean
    )

    fun health(ctx: Context): Health {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.totalBytes
        val free = st.availableBytes
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return Health(
            mi.totalMem - mi.availMem, mi.totalMem,
            total - free, total,
            if (level >= 0 && scale > 0) level * 100 / scale else -1, temp,
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        )
    }

    // ================= Libérer la RAM =================

    /** Ferme les processus en arrière-plan des autres applis. Renvoie le nombre d'applis visées. */
    fun freeRam(ctx: Context): Int {
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return 0
        val pm = ctx.packageManager
        val launcher = SystemTools.run {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.resolveActivity(i, 0)?.activityInfo?.packageName
        }
        var n = 0
        @Suppress("DEPRECATION")
        val apps = try { pm.getInstalledApplications(0) } catch (e: Exception) { emptyList() }
        for (a in apps) {
            if (a.packageName == ctx.packageName || a.packageName == launcher) continue
            if (a.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            try {
                am.killBackgroundProcesses(a.packageName); n++
            } catch (_: Throwable) {
            }
        }
        System.gc()
        return n
    }

    // ================= Modes =================

    data class Mode(
        val id: String, val emoji: String, val title: String, val desc: String,
        val keywords: List<String>
    )

    val modes = listOf(
        Mode("jeu", "🎮", "Mode Jeu", "120 Hz, luminosité max, animations coupées, RAM libérée, Ne pas déranger",
            listOf("jeu", "jouer", "gaming", "game", "fortnite", "cod", "pubg", "genshin", "perf", "max", "turbo", "bête", "bete")),
        Mode("batterie", "🔋", "Économie extrême", "60 Hz, luminosité basse, veille rapide, animations réduites",
            listOf("batterie", "économ", "econom", "autonomie", "tenir", "charge")),
        Mode("film", "🎬", "Mode Film", "120 Hz, luminosité haute, pas de mise en veille, Ne pas déranger",
            listOf("film", "série", "serie", "netflix", "vidéo", "video", "youtube", "regarder")),
        Mode("nuit", "🌙", "Mode Nuit", "Luminosité au minimum, Ne pas déranger, sons des touches coupés",
            listOf("nuit", "dormir", "dodo", "sommeil", "lit")),
        Mode("focus", "🎯", "Mode Focus", "Ne pas déranger, animations rapides, RAM libérée",
            listOf("focus", "travail", "bosser", "concentr", "étudier", "etudier", "réviser", "reviser")),
        Mode("normal", "↩️", "Revenir à la normale", "Restaure tes réglages d'avant le boost",
            listOf("normal", "remet", "restaure", "annule", "stop", "arrête", "arrete", "défaut", "defaut")),
    )

    private const val SNAP = "boost_snapshot"

    private fun saveSnapshot(ctx: Context) {
        val sp = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE)
        if (sp.getBoolean("saved", false)) return // on garde l'état d'origine, pas celui d'un autre mode
        val r = ctx.contentResolver
        sp.edit().apply {
            putBoolean("saved", true)
            putInt("bright", SystemTools.getSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS, 128))
            putInt("brightMode", SystemTools.getSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS_MODE, 1))
            putInt("timeout", SystemTools.getSystemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, 30000))
            putInt("touch", SystemTools.getSystemInt(ctx, Settings.System.SOUND_EFFECTS_ENABLED, 1))
            putFloat("anim", SystemTools.getGlobalFloat(ctx, Settings.Global.WINDOW_ANIMATION_SCALE, 1f))
            putInt("rr", try { Settings.Secure.getInt(r, "refresh_rate_mode", 1) } catch (e: Exception) { 1 })
            putFloat("peak", try { Settings.System.getFloat(r, "peak_refresh_rate", 120f) } catch (e: Exception) { 120f })
            putInt("dnd", ctx.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: 1)
        }.apply()
    }

    private fun setAnim(ctx: Context, v: Float): Boolean =
        SystemTools.putGlobalFloat(ctx, Settings.Global.WINDOW_ANIMATION_SCALE, v) &&
            SystemTools.putGlobalFloat(ctx, Settings.Global.TRANSITION_ANIMATION_SCALE, v) &&
            SystemTools.putGlobalFloat(ctx, Settings.Global.ANIMATOR_DURATION_SCALE, v)

    /** 0 = 60 Hz, 1 = adaptatif (jusqu'à 120 Hz) sur Samsung. */
    private fun setRefresh(ctx: Context, high: Boolean): Boolean {
        var ok = false
        try { ok = Settings.Secure.putInt(ctx.contentResolver, "refresh_rate_mode", if (high) 1 else 0) } catch (_: Throwable) {}
        try {
            Settings.System.putFloat(ctx.contentResolver, "peak_refresh_rate", if (high) 120f else 60f)
            Settings.System.putFloat(ctx.contentResolver, "min_refresh_rate", if (high) 120f else 0f)
            ok = true
        } catch (_: Throwable) {}
        return ok
    }

    private fun setBright(ctx: Context, v: Int) {
        SystemTools.putSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        SystemTools.putSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS, v)
    }

    private fun setDnd(ctx: Context, filter: Int): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false
        return try { nm.setInterruptionFilter(filter); true } catch (_: Throwable) { false }
    }

    /** Applique un mode. Renvoie le journal de ce qui a été fait / bloqué. */
    fun applyMode(ctx: Context, id: String): List<String> {
        val log = mutableListOf<String>()
        val write = SystemTools.canWriteSettings(ctx)
        val secure = SystemTools.hasSecureSettings(ctx)
        val dnd = hasDndAccess(ctx)
        fun need(what: String) = log.add("⬜ $what — permission manquante")

        if (id == "normal") return restore(ctx)
        saveSnapshot(ctx)

        when (id) {
            "jeu" -> {
                if (write) { setBright(ctx, 255); SystemTools.putSystemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, 600000); log += "✅ Luminosité max, veille 10 min" } else need("Luminosité")
                if (secure && setAnim(ctx, 0f)) log += "✅ Animations coupées (réactivité max)" else need("Animations (ADB)")
                if (setRefresh(ctx, true)) log += "✅ Écran forcé à 120 Hz" else need("120 Hz (ADB)")
                if (dnd && setDnd(ctx, NotificationManager.INTERRUPTION_FILTER_PRIORITY)) log += "✅ Ne pas déranger activé" else need("Ne pas déranger")
                log += "✅ RAM libérée (${freeRam(ctx)} applis en arrière-plan fermées)"
                log += "💡 Ouvre Game Booster depuis ton jeu pour le mode « Performance »"
            }
            "batterie" -> {
                if (write) { setBright(ctx, 40); SystemTools.putSystemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, 15000); log += "✅ Luminosité basse, veille 15 s" } else need("Luminosité")
                if (secure && setAnim(ctx, 0.5f)) log += "✅ Animations réduites" else need("Animations (ADB)")
                if (setRefresh(ctx, false)) log += "✅ Écran à 60 Hz" else need("60 Hz (ADB)")
                log += "✅ RAM libérée (${freeRam(ctx)} applis fermées)"
                log += "💡 Active aussi « Économie d'énergie » dans le volet rapide"
            }
            "film" -> {
                if (write) { setBright(ctx, 230); SystemTools.putSystemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, 1800000); log += "✅ Luminosité haute, veille 30 min" } else need("Luminosité")
                if (setRefresh(ctx, true)) log += "✅ Écran à 120 Hz" else need("120 Hz (ADB)")
                if (dnd && setDnd(ctx, NotificationManager.INTERRUPTION_FILTER_PRIORITY)) log += "✅ Ne pas déranger activé" else need("Ne pas déranger")
            }
            "nuit" -> {
                if (write) { setBright(ctx, 2); SystemTools.putSystemInt(ctx, Settings.System.SOUND_EFFECTS_ENABLED, 0); log += "✅ Luminosité minimum, sons des touches coupés" } else need("Luminosité")
                if (dnd && setDnd(ctx, NotificationManager.INTERRUPTION_FILTER_ALARMS)) log += "✅ Ne pas déranger (réveils seulement)" else need("Ne pas déranger")
            }
            "focus" -> {
                if (dnd && setDnd(ctx, NotificationManager.INTERRUPTION_FILTER_PRIORITY)) log += "✅ Ne pas déranger activé" else need("Ne pas déranger")
                if (secure && setAnim(ctx, 0.5f)) log += "✅ Animations rapides" else need("Animations (ADB)")
                log += "✅ RAM libérée (${freeRam(ctx)} applis fermées)"
            }
        }
        ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE).edit().putString("active", id).apply()
        return log
    }

    fun activeMode(ctx: Context): String? =
        ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE).getString("active", null)

    fun restore(ctx: Context): List<String> {
        val sp = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE)
        if (!sp.getBoolean("saved", false)) return listOf("ℹ️ Aucun boost actif, rien à restaurer")
        val log = mutableListOf<String>()
        if (SystemTools.canWriteSettings(ctx)) {
            SystemTools.putSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS, sp.getInt("bright", 128))
            SystemTools.putSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS_MODE, sp.getInt("brightMode", 1))
            SystemTools.putSystemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, sp.getInt("timeout", 30000))
            SystemTools.putSystemInt(ctx, Settings.System.SOUND_EFFECTS_ENABLED, sp.getInt("touch", 1))
            log += "✅ Luminosité, veille et sons restaurés"
        }
        if (SystemTools.hasSecureSettings(ctx)) {
            setAnim(ctx, sp.getFloat("anim", 1f))
            try { Settings.Secure.putInt(ctx.contentResolver, "refresh_rate_mode", sp.getInt("rr", 1)) } catch (_: Throwable) {}
            try {
                Settings.System.putFloat(ctx.contentResolver, "peak_refresh_rate", sp.getFloat("peak", 120f))
                Settings.System.putFloat(ctx.contentResolver, "min_refresh_rate", 0f)
            } catch (_: Throwable) {}
            log += "✅ Animations et fréquence d'écran restaurées"
        }
        if (setDnd(ctx, sp.getInt("dnd", NotificationManager.INTERRUPTION_FILTER_ALL))) log += "✅ Notifications restaurées"
        sp.edit().clear().apply()
        return log
    }

    // ================= Nettoyage =================

    enum class Kind(val label: String, val emoji: String, val preChecked: Boolean) {
        CACHE("Caches et miniatures", "🧹", true),
        TEMP("Fichiers temporaires et journaux", "📄", true),
        EMPTY("Dossiers vides", "📁", true),
        APK("Fichiers d'installation (.apk) déjà utilisés", "📦", true),
        TRASH("Corbeilles cachées", "🗑️", true),
        BIG("Gros fichiers (> 100 Mo) — à vérifier", "🐘", false),
        OLD_DL("Téléchargements de plus de 90 jours — à vérifier", "⏳", false),
    }

    data class Junk(val file: File, val size: Long, val kind: Kind)

    private val cacheDirNames = setOf(".thumbnails", ".cache", "cache", ".tmp", "tmp", "temp", ".temp", "LOST.DIR", ".Trash", ".trashed_files")
    private val tempExt = setOf("tmp", "temp", "log", "bak", "old", "dmp", "part", "crdownload", "chck")

    private fun dirSize(f: File): Long =
        if (f.isFile) f.length() else f.listFiles()?.sumOf { dirSize(it) } ?: 0L

    /** Analyse le stockage interne. Ne touche à rien. */
    fun scan(ctx: Context, progress: (String) -> Unit = {}): List<Junk> {
        val out = mutableListOf<Junk>()
        // Cache de Maison elle-même
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).forEach { d ->
            val s = dirSize(d); if (s > 0) out += Junk(d, s, Kind.CACHE)
        }
        if (!hasAllFiles()) return out
        val root = Environment.getExternalStorageDirectory()
        val now = System.currentTimeMillis()
        val dl = File(root, Environment.DIRECTORY_DOWNLOADS).absolutePath

        fun walk(dir: File, depth: Int) {
            if (depth > 12) return
            val kids = dir.listFiles() ?: return
            if (depth <= 2) progress(dir.absolutePath.removePrefix(root.absolutePath).ifEmpty { "/" })
            if (kids.isEmpty() && depth > 0) { out += Junk(dir, 0, Kind.EMPTY); return }
            for (f in kids) {
                val name = f.name
                // Android/data et obb : protégé par le système, et on n'y touche jamais
                if (depth == 1 && dir.name == "Android" && (name == "data" || name == "obb")) continue
                if (f.isDirectory) {
                    if (name == ".Trash" || name == ".trashed_files" || name.startsWith(".trashed")) {
                        val s = dirSize(f); if (s > 0) out += Junk(f, s, Kind.TRASH); continue
                    }
                    if (name.lowercase(Locale.ROOT) in cacheDirNames) {
                        val s = dirSize(f); if (s > 0) out += Junk(f, s, Kind.CACHE); continue
                    }
                    walk(f, depth + 1)
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val len = f.length()
                    when {
                        name.startsWith(".trashed-") -> out += Junk(f, len, Kind.TRASH)
                        ext in tempExt || name == "Thumbs.db" || name == ".DS_Store" -> out += Junk(f, len, Kind.TEMP)
                        ext == "apk" || ext == "apks" || ext == "xapk" -> out += Junk(f, len, Kind.APK)
                        len > 100L * 1024 * 1024 -> out += Junk(f, len, Kind.BIG)
                        f.absolutePath.startsWith(dl) && now - f.lastModified() > 90L * 86400_000 -> out += Junk(f, len, Kind.OLD_DL)
                    }
                }
            }
        }
        walk(root, 0)
        return out
    }

    /** Supprime la sélection. Renvoie les octets libérés. */
    fun delete(items: List<Junk>): Long {
        var freed = 0L
        for (j in items) {
            try {
                if (j.file.deleteRecursively()) freed += j.size
            } catch (_: Throwable) {
            }
        }
        return freed
    }

    // ================= Applis inutilisées =================

    data class AppUse(val pkg: String, val label: String, val lastUsed: Long, val size: Long)

    fun unusedApps(ctx: Context, days: Int = 30): List<AppUse> {
        if (!hasUsageAccess(ctx)) return emptyList()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = usm.queryAndAggregateUsageStats(now - 365L * 86400_000, now)
        val ssm = ctx.getSystemService(StorageStatsManager::class.java)
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val apps = try { pm.getInstalledApplications(0) } catch (e: Exception) { emptyList() }
        return apps.asSequence()
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ctx.packageName }
            .mapNotNull { a ->
                val last = stats[a.packageName]?.lastTimeUsed ?: 0L
                if (now - last < days * 86400_000L) return@mapNotNull null
                val size = try {
                    val s = ssm?.queryStatsForUid(a.storageUuid, a.uid)
                    (s?.appBytes ?: 0) + (s?.dataBytes ?: 0) + (s?.cacheBytes ?: 0)
                } catch (_: Throwable) { 0L }
                AppUse(a.packageName, a.loadLabel(pm).toString(), last, size)
            }
            .sortedByDescending { it.size }
            .toList()
    }

    /** Ouvre l'outil Samsung qui vide le cache de TOUTES les applis (Entretien de l'appareil). */
    fun openDeviceCare(ctx: Context) {
        val ok = SystemTools.safeStart(
            ctx,
            Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity"),
            Intent("com.samsung.android.sm.ACTION_DASHBOARD"),
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        )
        if (!ok) SystemTools.safeStart(ctx, Intent(Settings.ACTION_SETTINGS))
    }

    fun openGameBooster(ctx: Context) {
        if (!SystemTools.openApp(ctx, "com.samsung.android.game.gamehome"))
            SystemTools.openStore(ctx, "com.samsung.android.game.gamehome")
    }

    // ================= Commandes en français =================

    sealed class Command {
        data class ApplyMode(val mode: Mode) : Command()
        object Clean : Command()
        object Ram : Command()
        object Unused : Command()
        object DeviceCare : Command()
        object Unknown : Command()
    }

    fun parse(text: String): Command {
        val t = text.lowercase(Locale.FRENCH)
        if (listOf("supprim", "nettoi", "nettoy", "vide", "inutile", "place", "espace", "fichier", "caché", "cache", "ménage", "menage", "poubelle").any { it in t })
            return if (listOf("appli", "app ").any { it in t } && "fichier" !in t) Command.Unused else Command.Clean
        if (listOf("ram", "mémoire", "memoire", "ferme", "lent", "rame", "lag").any { it in t }) return Command.Ram
        if (listOf("appli", "désinstall", "desinstall", "sers pas", "sert pas", "utilise pas").any { it in t }) return Command.Unused
        if (listOf("entretien", "device care", "tout le cache").any { it in t }) return Command.DeviceCare
        modes.firstOrNull { m -> m.keywords.any { it in t } }?.let { return Command.ApplyMode(it) }
        return Command.Unknown
    }

    fun fmt(bytes: Long): String {
        val b = bytes.toDouble()
        return when {
            b >= 1e9 -> String.format(Locale.FRENCH, "%.1f Go", b / 1e9)
            b >= 1e6 -> String.format(Locale.FRENCH, "%.0f Mo", b / 1e6)
            b >= 1e3 -> String.format(Locale.FRENCH, "%.0f Ko", b / 1e3)
            else -> "$bytes o"
        }
    }
}
