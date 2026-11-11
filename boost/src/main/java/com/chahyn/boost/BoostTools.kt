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
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

/** Le moteur : permissions, réglages, modes, nettoyage, explorateur, applis. */
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

    data class Perm(val label: String, val why: String, val ok: (Context) -> Boolean, val ask: (Context) -> Unit)

    val perms = listOf(
        Perm("Réglages du téléphone", "Luminosité, mise en veille, sons", { SystemTools.canWriteSettings(it) }, { SystemTools.requestWriteSettings(it) }),
        Perm("Accès à tous les fichiers", "Pour nettoyer tout le stockage de A à Z", { hasAllFiles() }, { requestAllFiles(it) }),
        Perm("Ne pas déranger", "Pour couper les notifications dans les modes", { hasDndAccess(it) }, { requestDndAccess(it) }),
        Perm("Données d'utilisation", "Pour trouver les applis qui ne te servent à rien", { hasUsageAccess(it) }, { requestUsageAccess(it) }),
    )

    // ================= État du téléphone =================

    data class Health(
        val ramUsed: Long, val ramTotal: Long,
        val storageUsed: Long, val storageTotal: Long,
        val batteryPct: Int, val batteryTempC: Float, val charging: Boolean
    ) {
        /** Note globale sur 100. */
        val score: Int
            get() {
                val ram = 1f - ramUsed.toFloat() / ramTotal.coerceAtLeast(1)
                val sto = 1f - storageUsed.toFloat() / storageTotal.coerceAtLeast(1)
                val temp = ((45f - batteryTempC) / 15f).coerceIn(0f, 1f)
                return (ram * 35 + sto.coerceAtMost(0.5f) * 2 * 45 + temp * 20).toInt().coerceIn(1, 100)
            }
    }

    fun health(ctx: Context): Health {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val st = StatFs(Environment.getDataDirectory().path)
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return Health(
            mi.totalMem - mi.availMem, mi.totalMem,
            st.totalBytes - st.availableBytes, st.totalBytes,
            if (level >= 0 && scale > 0) level * 100 / scale else -1,
            (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        )
    }

    fun uptime(): String {
        val h = SystemClock.elapsedRealtime() / 3_600_000
        return if (h >= 24) "${h / 24} j ${h % 24} h" else "$h h"
    }

    // ================= RAM =================

    fun freeRam(ctx: Context): Int {
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return 0
        val pm = ctx.packageManager
        val launcher = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        var n = 0
        for (a in installedUserApps(ctx)) {
            if (a.packageName == launcher) continue
            try { am.killBackgroundProcesses(a.packageName); n++ } catch (_: Throwable) {}
        }
        System.gc()
        return n
    }

    // ================= Réglages unitaires =================

    fun setBrightnessPct(ctx: Context, pct: Int): Boolean {
        if (!SystemTools.canWriteSettings(ctx)) return false
        SystemTools.putSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        return SystemTools.putSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS, (pct.coerceIn(0, 100) * 254 / 100) + 1)
    }

    fun setAutoBrightness(ctx: Context, on: Boolean) = SystemTools.putSystemInt(
        ctx, Settings.System.SCREEN_BRIGHTNESS_MODE,
        if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    )

    fun setTimeout(ctx: Context, ms: Int) = SystemTools.putSystemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, ms)
    fun setTouchSounds(ctx: Context, on: Boolean) = SystemTools.putSystemInt(ctx, Settings.System.SOUND_EFFECTS_ENABLED, if (on) 1 else 0)
    fun setHaptics(ctx: Context, on: Boolean) = SystemTools.putSystemInt(ctx, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (on) 1 else 0)
    fun setRotation(ctx: Context, on: Boolean) = SystemTools.putSystemInt(ctx, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
    fun setBatteryPercent(ctx: Context, on: Boolean) = SystemTools.putSystemInt(ctx, "display_battery_percentage", if (on) 1 else 0)

    fun setAnim(ctx: Context, v: Float): Boolean =
        SystemTools.putGlobalFloat(ctx, Settings.Global.WINDOW_ANIMATION_SCALE, v) &&
            SystemTools.putGlobalFloat(ctx, Settings.Global.TRANSITION_ANIMATION_SCALE, v) &&
            SystemTools.putGlobalFloat(ctx, Settings.Global.ANIMATOR_DURATION_SCALE, v)

    /** Samsung : refresh_rate_mode 0 = 60 Hz, 1 = adaptatif 120 Hz. */
    fun setHighRefresh(ctx: Context, high: Boolean): Boolean {
        var ok = false
        try { ok = Settings.Secure.putInt(ctx.contentResolver, "refresh_rate_mode", if (high) 1 else 0) } catch (_: Throwable) {}
        try {
            Settings.System.putFloat(ctx.contentResolver, "peak_refresh_rate", if (high) 120f else 60f)
            Settings.System.putFloat(ctx.contentResolver, "min_refresh_rate", if (high) 120f else 0f)
            ok = true
        } catch (_: Throwable) {}
        return ok
    }

    fun setDnd(ctx: Context, filter: Int): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false
        return try { nm.setInterruptionFilter(filter); true } catch (_: Throwable) { false }
    }

    fun setRinger(ctx: Context, mode: Int): Boolean = try {
        ctx.getSystemService(AudioManager::class.java)?.ringerMode = mode; true
    } catch (_: Throwable) { false }

    fun setVolumePct(ctx: Context, stream: Int, pct: Int): Boolean = try {
        val am = ctx.getSystemService(AudioManager::class.java)!!
        am.setStreamVolume(stream, am.getStreamMaxVolume(stream) * pct.coerceIn(0, 100) / 100, 0); true
    } catch (_: Throwable) { false }

    fun setTorch(ctx: Context, on: Boolean): Boolean = try {
        val cm = ctx.getSystemService(CameraManager::class.java)!!
        val id = cm.cameraIdList.first()
        cm.setTorchMode(id, on); true
    } catch (_: Throwable) { false }

    // ================= Modes =================

    /** Ce qu'un mode règle ; null = ne pas toucher. */
    data class Mode(
        val id: String, val emoji: String, val title: String, val desc: String,
        val keywords: List<String>,
        val bright: Int? = null, val timeoutMs: Int? = null, val anim: Float? = null,
        val hz120: Boolean? = null, val dnd: Int? = null, val ringer: Int? = null,
        val media: Int? = null, val touch: Boolean? = null, val haptic: Boolean? = null,
        val rotation: Boolean? = null, val ram: Boolean = false, val tip: String? = null,
    )

    private val PRIO = NotificationManager.INTERRUPTION_FILTER_PRIORITY
    private val ALARMS = NotificationManager.INTERRUPTION_FILTER_ALARMS
    private val NONE = NotificationManager.INTERRUPTION_FILTER_NONE
    private val ALL = NotificationManager.INTERRUPTION_FILTER_ALL

    val modes = listOf(
        Mode("jeu", "🎮", "Jeu", "120 Hz, luminosité max, zéro animation, RAM libérée, pas de notifications",
            listOf("jeu", "jouer", "gaming", "game", "fortnite", "cod", "pubg", "genshin", "brawl", "clash", "fifa", "fc"),
            bright = 100, timeoutMs = 600_000, anim = 0f, hz120 = true, dnd = PRIO, touch = false, rotation = true, ram = true,
            tip = "Lance ton jeu depuis Game Booster et choisis « Performance »"),
        Mode("ultra", "🔥", "Bête de course", "Tout au maximum : performances pures, rien d'autre ne tourne",
            listOf("bete", "turbo", "max", "ultra", "puissance", "boost", "machine", "fond"),
            bright = 100, timeoutMs = 1_800_000, anim = 0f, hz120 = true, dnd = NONE, touch = false, haptic = false, ram = true),
        Mode("travail", "💼", "Travail", "Notifications prioritaires seulement, écran confort, sons coupés",
            listOf("travail", "bureau", "boulot", "taff", "bosser", "pro"),
            bright = 60, timeoutMs = 300_000, anim = 0.5f, dnd = PRIO, ringer = AudioManager.RINGER_MODE_VIBRATE, touch = false, ram = true),
        Mode("reunion", "🤝", "Réunion", "Vibreur, aucune notification, écran discret",
            listOf("reunion", "meeting", "rdv", "rendez", "entretien", "cours", "classe"),
            bright = 35, dnd = PRIO, ringer = AudioManager.RINGER_MODE_VIBRATE, touch = false, haptic = false),
        Mode("etude", "📚", "Études / Focus", "Ne pas déranger, animations rapides, RAM libérée",
            listOf("etude", "etudier", "reviser", "revision", "focus", "concentr", "exam", "devoirs"),
            bright = 55, dnd = PRIO, anim = 0.5f, touch = false, ram = true),
        Mode("lecture", "📖", "Lecture", "Écran doux, veille longue, sans rotation",
            listOf("lecture", "lire", "livre", "ebook", "manga", "article"),
            bright = 35, timeoutMs = 600_000, rotation = false, dnd = PRIO),
        Mode("film", "🎬", "Film / Série", "120 Hz, luminosité haute, son fort, pas de veille, pas de notifications",
            listOf("film", "serie", "netflix", "video", "youtube", "regarder", "disney", "prime", "anime"),
            bright = 85, timeoutMs = 1_800_000, hz120 = true, dnd = PRIO, media = 80, rotation = true),
        Mode("musique", "🎧", "Musique", "Volume média élevé, écran au minimum",
            listOf("musique", "spotify", "son", "ecouter", "deezer", "podcast"),
            bright = 15, timeoutMs = 15_000, media = 75),
        Mode("photo", "📸", "Photo / Vidéo", "Luminosité max, 120 Hz, RAM libérée pour l'appareil photo",
            listOf("photo", "camera", "filmer", "selfie", "tournage"),
            bright = 100, hz120 = true, timeoutMs = 300_000, ram = true),
        Mode("sport", "🏃", "Sport", "Son fort, écran rapide à s'éteindre, notifications coupées",
            listOf("sport", "salle", "muscu", "courir", "running", "footing", "gym"),
            bright = 70, timeoutMs = 15_000, media = 90, dnd = PRIO),
        Mode("voiture", "🚗", "Voiture", "Luminosité max, rotation, son fort, veille longue",
            listOf("voiture", "conduire", "route", "gps", "waze", "maps"),
            bright = 100, timeoutMs = 1_800_000, rotation = true, media = 85, ringer = AudioManager.RINGER_MODE_NORMAL),
        Mode("soiree", "🎉", "Soirée", "Son fort, sonnerie active, écran lumineux",
            listOf("soiree", "fete", "party", "boite", "sortie"),
            bright = 80, media = 100, ringer = AudioManager.RINGER_MODE_NORMAL, dnd = ALL),
        Mode("voyage", "✈️", "Voyage", "Économie d'énergie, RAM libérée, écran moyen",
            listOf("voyage", "avion", "train", "vacances", "trajet"),
            bright = 50, timeoutMs = 30_000, anim = 0.5f, hz120 = false, ram = true),
        Mode("batterie", "🔋", "Économie", "60 Hz, écran bas, veille rapide",
            listOf("batterie", "econom", "autonomie", "tenir", "charge", "eco"),
            bright = 30, timeoutMs = 15_000, anim = 0.5f, hz120 = false, ram = true,
            tip = "Active aussi « Économie d'énergie » dans le volet rapide"),
        Mode("survie", "🪫", "Batterie de survie", "Le strict minimum pour tenir le plus longtemps possible",
            listOf("survie", "urgence", "presque plus", "plus de batterie", "critique", "extreme"),
            bright = 5, timeoutMs = 15_000, anim = 0f, hz120 = false, haptic = false, touch = false, media = 0, ram = true),
        Mode("nuit", "🌙", "Nuit", "Luminosité au minimum, sons coupés",
            listOf("nuit", "soir", "noir", "sombre"),
            bright = 2, touch = false, dnd = PRIO, timeoutMs = 30_000),
        Mode("sommeil", "😴", "Sommeil", "Seulement les réveils, tout le reste coupé",
            listOf("dormir", "dodo", "sommeil", "lit", "coucher", "reveil"),
            bright = 1, dnd = ALARMS, touch = false, haptic = false, media = 0, ram = true),
        Mode("discret", "🤫", "Silencieux", "Tout en silence et vibreur",
            listOf("silence", "silencieux", "discret", "chut", "vibreur"),
            ringer = AudioManager.RINGER_MODE_VIBRATE, touch = false, media = 0),
        Mode("normal", "↩️", "Revenir à la normale", "Restaure tes réglages d'avant",
            listOf("normal", "remet", "restaure", "annule", "stop", "arrete", "defaut", "comme avant")),
    )

    private const val SNAP = "boost_snapshot"

    private fun saveSnapshot(ctx: Context) {
        val sp = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE)
        if (sp.getBoolean("saved", false)) return
        val r = ctx.contentResolver
        val am = ctx.getSystemService(AudioManager::class.java)
        sp.edit().apply {
            putBoolean("saved", true)
            putInt("bright", SystemTools.getSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS, 128))
            putInt("brightMode", SystemTools.getSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS_MODE, 1))
            putInt("timeout", SystemTools.getSystemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, 30000))
            putInt("touch", SystemTools.getSystemInt(ctx, Settings.System.SOUND_EFFECTS_ENABLED, 1))
            putInt("haptic", SystemTools.getSystemInt(ctx, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1))
            putInt("rot", SystemTools.getSystemInt(ctx, Settings.System.ACCELEROMETER_ROTATION, 0))
            putFloat("anim", SystemTools.getGlobalFloat(ctx, Settings.Global.WINDOW_ANIMATION_SCALE, 1f))
            putInt("rr", try { Settings.Secure.getInt(r, "refresh_rate_mode", 1) } catch (e: Exception) { 1 })
            putFloat("peak", try { Settings.System.getFloat(r, "peak_refresh_rate", 120f) } catch (e: Exception) { 120f })
            putInt("dnd", ctx.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: ALL)
            putInt("ringer", am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL)
            putInt("media", am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 5)
        }.apply()
    }

    fun applyMode(ctx: Context, id: String): List<String> {
        if (id == "normal") return restore(ctx)
        val m = modes.firstOrNull { it.id == id } ?: return listOf("Mode inconnu")
        saveSnapshot(ctx)
        val log = mutableListOf("${m.emoji} Mode ${m.title} activé")
        val write = SystemTools.canWriteSettings(ctx)
        val secure = SystemTools.hasSecureSettings(ctx)
        fun r(ok: Boolean, done: String, need: String) { log += if (ok) "✅ $done" else "⬜ $done — $need" }
        m.bright?.let { r(write && setBrightnessPct(ctx, it), "Luminosité $it %", "autorise « Réglages du téléphone »") }
        m.timeoutMs?.let { r(write && setTimeout(ctx, it), "Veille ${fmtMs(it)}", "autorise « Réglages du téléphone »") }
        m.touch?.let { r(write && setTouchSounds(ctx, it), "Sons des touches ${onOff(it)}", "autorise « Réglages du téléphone »") }
        m.haptic?.let { r(write && setHaptics(ctx, it), "Vibrations au toucher ${onOff(it)}", "autorise « Réglages du téléphone »") }
        m.rotation?.let { r(write && setRotation(ctx, it), "Rotation auto ${onOff(it)}", "autorise « Réglages du téléphone »") }
        m.anim?.let { r(secure && setAnim(ctx, it), if (it == 0f) "Animations coupées" else "Animations ×$it", "mode avancé ADB") }
        m.hz120?.let { r(setHighRefresh(ctx, it), if (it) "Écran 120 Hz" else "Écran 60 Hz", "mode avancé ADB") }
        m.dnd?.let { r(setDnd(ctx, it), if (it == ALL) "Notifications activées" else "Ne pas déranger", "autorise « Ne pas déranger »") }
        m.ringer?.let { r(setRinger(ctx, it), ringerName(it), "autorise « Ne pas déranger »") }
        m.media?.let { r(setVolumePct(ctx, AudioManager.STREAM_MUSIC, it), "Volume média $it %", "refusé") }
        if (m.ram) log += "✅ RAM libérée (${freeRam(ctx)} applis fermées)"
        m.tip?.let { log += "💡 $it" }
        ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE).edit().putString("active", id).apply()
        return log
    }

    fun activeMode(ctx: Context): String? = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE).getString("active", null)

    fun restore(ctx: Context): List<String> {
        val sp = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE)
        if (!sp.getBoolean("saved", false)) return listOf("ℹ️ Aucun mode actif, tout est déjà normal")
        if (SystemTools.canWriteSettings(ctx)) {
            SystemTools.putSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS, sp.getInt("bright", 128))
            SystemTools.putSystemInt(ctx, Settings.System.SCREEN_BRIGHTNESS_MODE, sp.getInt("brightMode", 1))
            setTimeout(ctx, sp.getInt("timeout", 30000))
            SystemTools.putSystemInt(ctx, Settings.System.SOUND_EFFECTS_ENABLED, sp.getInt("touch", 1))
            SystemTools.putSystemInt(ctx, Settings.System.HAPTIC_FEEDBACK_ENABLED, sp.getInt("haptic", 1))
            SystemTools.putSystemInt(ctx, Settings.System.ACCELEROMETER_ROTATION, sp.getInt("rot", 0))
        }
        if (SystemTools.hasSecureSettings(ctx)) {
            setAnim(ctx, sp.getFloat("anim", 1f))
            try { Settings.Secure.putInt(ctx.contentResolver, "refresh_rate_mode", sp.getInt("rr", 1)) } catch (_: Throwable) {}
            try {
                Settings.System.putFloat(ctx.contentResolver, "peak_refresh_rate", sp.getFloat("peak", 120f))
                Settings.System.putFloat(ctx.contentResolver, "min_refresh_rate", 0f)
            } catch (_: Throwable) {}
        }
        setDnd(ctx, sp.getInt("dnd", ALL))
        setRinger(ctx, sp.getInt("ringer", AudioManager.RINGER_MODE_NORMAL))
        try { ctx.getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_MUSIC, sp.getInt("media", 5), 0) } catch (_: Throwable) {}
        sp.edit().clear().apply()
        return listOf("↩️ Tout est revenu comme avant")
    }

    private fun onOff(b: Boolean) = if (b) "activés" else "coupés"
    fun ringerName(m: Int) = when (m) {
        AudioManager.RINGER_MODE_SILENT -> "Mode silencieux"
        AudioManager.RINGER_MODE_VIBRATE -> "Mode vibreur"
        else -> "Sonnerie activée"
    }

    fun fmtMs(ms: Int) = if (ms >= 60_000) "${ms / 60_000} min" else "${ms / 1000} s"

    // ================= Nettoyage =================

    enum class Kind(val label: String, val emoji: String, val safe: Boolean, val keywords: List<String>) {
        CACHE("Caches et miniatures cachés", "🧹", true, listOf("cache", "miniature", "thumbnail")),
        TEMP("Fichiers temporaires", "📄", true, listOf("temporaire", "tmp", "temp")),
        LOGS("Journaux et rapports de bug", "🧾", true, listOf("journal", "journaux", "log", "rapport")),
        EMPTY("Dossiers vides", "📁", true, listOf("dossier vide", "dossiers vides", "vide")),
        TRASH("Corbeilles cachées", "🗑️", true, listOf("corbeille", "poubelle", "trash")),
        APK("Fichiers d'installation .apk", "📦", true, listOf("apk", "installation", "installeur")),
        STATUS("Statuts WhatsApp vus", "🟢", true, listOf("statut", "status")),
        DUP("Fichiers en double", "👯", false, listOf("double", "doublon", "duplique", "pareil")),
        SCREEN("Captures d'écran", "📱", false, listOf("capture", "screenshot", "screen")),
        WA_MEDIA("Médias WhatsApp / Telegram", "💬", false, listOf("whatsapp", "telegram", "messagerie")),
        BIG("Gros fichiers (> 100 Mo)", "🐘", false, listOf("gros", "lourd", "volumineux", "gros fichier")),
        OLD_DL("Vieux téléchargements (> 90 j)", "⏳", false, listOf("telechargement", "download", "vieux")),
        VIDEO_OLD("Vidéos de plus d'un an", "🎞️", false, listOf("video ancienne", "vieilles videos", "anciennes videos")),
    }

    data class Junk(val file: File, val size: Long, val kind: Kind)

    private val cacheDirNames = setOf(".thumbnails", ".cache", "cache", "caches", ".tmp", "tmp", "temp", ".temp", "lost.dir", ".glidecache", ".exocache", "code_cache", ".fresco")
    private val tempExt = setOf("tmp", "temp", "bak", "old", "part", "crdownload", "chck", "partial", "download")
    private val logExt = setOf("log", "dmp", "trace", "anr")
    private val videoExt = setOf("mp4", "mov", "mkv", "3gp", "webm", "avi")

    fun dirSize(f: File): Long = if (f.isFile) f.length() else f.listFiles()?.sumOf { dirSize(it) } ?: 0L

    /** Analyse tout le stockage interne. Ne supprime rien. */
    fun scan(ctx: Context, progress: (String) -> Unit = {}): List<Junk> {
        val out = mutableListOf<Junk>()
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).forEach { d -> dirSize(d).let { if (it > 0) out += Junk(d, it, Kind.CACHE) } }
        if (!hasAllFiles()) return out
        val root = Environment.getExternalStorageDirectory()
        val now = System.currentTimeMillis()
        val dl = File(root, Environment.DIRECTORY_DOWNLOADS).absolutePath.lowercase()
        val bySize = HashMap<Long, MutableList<File>>()

        fun walk(dir: File, depth: Int) {
            if (depth > 14) return
            val kids = dir.listFiles() ?: return
            if (depth <= 3) progress(dir.absolutePath.removePrefix(root.absolutePath).ifEmpty { "/" })
            if (kids.isEmpty() && depth > 0) { out += Junk(dir, 0, Kind.EMPTY); return }
            val path = dir.absolutePath.lowercase()
            val inMessenger = "whatsapp/media" in path || "telegram" in path
            for (f in kids) {
                val name = f.name
                val low = name.lowercase(Locale.ROOT)
                if (depth == 1 && dir.name == "Android" && (name == "data" || name == "obb")) continue
                if (f.isDirectory) {
                    when {
                        low == ".statuses" -> dirSize(f).let { if (it > 0) out += Junk(f, it, Kind.STATUS) }
                        low.startsWith(".trash") || low.startsWith(".trashed") || low == ".recycle" || low == "recycle bin" ->
                            dirSize(f).let { if (it > 0) out += Junk(f, it, Kind.TRASH) }
                        low in cacheDirNames -> dirSize(f).let { if (it > 0) out += Junk(f, it, Kind.CACHE) }
                        else -> walk(f, depth + 1)
                    }
                } else {
                    val ext = low.substringAfterLast('.', "")
                    val len = f.length()
                    val age = now - f.lastModified()
                    when {
                        low.startsWith(".trashed-") -> out += Junk(f, len, Kind.TRASH)
                        ext in logExt -> out += Junk(f, len, Kind.LOGS)
                        ext in tempExt || low == "thumbs.db" || low == ".ds_store" -> out += Junk(f, len, Kind.TEMP)
                        ext == "apk" || ext == "apks" || ext == "xapk" -> out += Junk(f, len, Kind.APK)
                        len > 100L * 1024 * 1024 -> out += Junk(f, len, Kind.BIG)
                        "screenshot" in path -> out += Junk(f, len, Kind.SCREEN)
                        inMessenger && !low.startsWith(".") -> out += Junk(f, len, Kind.WA_MEDIA)
                        path.startsWith(dl) && age > 90L * 86_400_000 -> out += Junk(f, len, Kind.OLD_DL)
                        ext in videoExt && age > 365L * 86_400_000 -> out += Junk(f, len, Kind.VIDEO_OLD)
                    }
                    if (len > 512 * 1024 && !low.startsWith(".")) bySize.getOrPut(len) { mutableListOf() }.add(f)
                }
            }
        }
        walk(root, 0)

        // Doublons : même taille puis même empreinte ; on garde le plus ancien
        progress("Recherche des doublons…")
        for ((size, files) in bySize) {
            if (files.size < 2) continue
            files.groupBy { hash(it) }.forEach { (h, same) ->
                if (h == null || same.size < 2) return@forEach
                same.sortedBy { it.lastModified() }.drop(1).forEach { out += Junk(it, size, Kind.DUP) }
            }
        }
        return out
    }

    private fun hash(f: File): String? = try {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(f).use { s ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = s.read(buf); if (n <= 0) break
                md.update(buf, 0, n); total += n
                if (total > 8L * 1024 * 1024) break // 8 premiers Mo suffisent avec la taille identique
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Throwable) { null }

    fun delete(items: List<Junk>): Long {
        var freed = 0L
        for (j in items) try { if (j.file.deleteRecursively()) freed += j.size } catch (_: Throwable) {}
        return freed
    }

    /** Le bouton magique : RAM + nettoyage sûr. */
    fun optimizeAll(ctx: Context, progress: (String) -> Unit): List<String> {
        val log = mutableListOf<String>()
        progress("Libération de la RAM…")
        log += "🚀 RAM libérée : ${freeRam(ctx)} applis en arrière-plan fermées"
        progress("Analyse du stockage…")
        val junk = scan(ctx, progress)
        val safe = junk.filter { it.kind.safe }
        progress("Suppression…")
        val freed = delete(safe)
        safe.groupBy { it.kind }.forEach { (k, l) -> log += "${k.emoji} ${k.label} : ${l.size} · ${fmt(l.sumOf { it.size })}" }
        log += "✅ Total libéré : ${fmt(freed)}"
        val review = junk.filter { !it.kind.safe }
        if (review.isNotEmpty()) log += "👀 ${fmt(review.sumOf { it.size })} à vérifier toi-même dans Stockage (doublons, gros fichiers…)"
        if (!hasAllFiles()) log += "⚠️ Autorise « Accès à tous les fichiers » pour nettoyer tout le téléphone"
        log += "💡 Pour le cache des autres applis : Stockage → Entretien Samsung"
        ctx.getSharedPreferences("boost_stats", Context.MODE_PRIVATE).let {
            it.edit().putLong("freed", it.getLong("freed", 0) + freed).putLong("last", System.currentTimeMillis()).apply()
        }
        return log
    }

    fun totalFreed(ctx: Context) = ctx.getSharedPreferences("boost_stats", Context.MODE_PRIVATE).getLong("freed", 0)

    // ================= Explorateur =================

    data class Entry(val file: File, val size: Long, val isDir: Boolean, val count: Int)

    fun list(dir: File): List<Entry> =
        (dir.listFiles() ?: emptyArray()).map {
            Entry(it, dirSize(it), it.isDirectory, if (it.isDirectory) it.list()?.size ?: 0 else 0)
        }.sortedByDescending { it.size }

    fun root(): File = Environment.getExternalStorageDirectory()

    // ================= Applis =================

    fun installedUserApps(ctx: Context): List<ApplicationInfo> {
        @Suppress("DEPRECATION")
        val apps = try { ctx.packageManager.getInstalledApplications(0) } catch (e: Exception) { emptyList() }
        return apps.filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ctx.packageName }
    }

    data class LaunchApp(val pkg: String, val label: String)

    fun launchableApps(ctx: Context): List<LaunchApp> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(i, 0)
            .map { LaunchApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    data class AppUse(val pkg: String, val label: String, val lastUsed: Long, val size: Long)

    fun unusedApps(ctx: Context, days: Int = 30): List<AppUse> {
        if (!hasUsageAccess(ctx)) return emptyList()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = usm.queryAndAggregateUsageStats(now - 365L * 86_400_000, now)
        val ssm = ctx.getSystemService(StorageStatsManager::class.java)
        val pm = ctx.packageManager
        return installedUserApps(ctx).mapNotNull { a ->
            val last = stats[a.packageName]?.lastTimeUsed ?: 0L
            if (now - last < days * 86_400_000L) return@mapNotNull null
            val size = try {
                val s = ssm?.queryStatsForUid(a.storageUuid, a.uid)
                (s?.appBytes ?: 0) + (s?.dataBytes ?: 0) + (s?.cacheBytes ?: 0)
            } catch (_: Throwable) { 0L }
            AppUse(a.packageName, a.loadLabel(pm).toString(), last, size)
        }.sortedByDescending { it.size }
    }

    fun openDeviceCare(ctx: Context) {
        val ok = SystemTools.safeStart(
            ctx,
            Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity"),
            Intent("com.samsung.android.sm.ACTION_DASHBOARD"),
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        )
        if (!ok) SystemTools.safeStart(ctx, Intent(Settings.ACTION_SETTINGS))
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
