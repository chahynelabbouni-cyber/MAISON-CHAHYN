package com.chahyn.boost

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.app.NotificationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.text.Normalizer

/** Réponse de l'agent : texte + éventuellement des fichiers à confirmer avant suppression. */
data class Reply(val lines: List<String>, val confirm: List<BoostTools.Junk> = emptyList(), val goTab: String? = null)

data class Task(
    val id: String,
    val emoji: String,
    val title: String,
    val category: String,
    val keywords: List<String>,
    val run: (Context) -> Reply,
)

fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('’', '\'')

/**
 * L'agent : comprend les demandes en français, enchaîne plusieurs actions
 * (« mode jeu et vide le cache »), et pilote toute l'appli.
 * Fonctionne 100 % hors-ligne, sans envoyer tes données nulle part.
 */
object Agent {

    private fun r(vararg l: String) = Reply(l.toList())
    private fun ok(b: Boolean, done: String, need: String = "permission manquante (voir Accueil → Autorisations)") =
        r(if (b) "✅ $done" else "⬜ $done — $need")

    private fun page(id: String, emoji: String, title: String, kw: List<String>, vararg actions: String) =
        Task(id, emoji, "Ouvrir : $title", "Paramètres", kw) { ctx ->
            val okk = SystemTools.safeStart(ctx, *actions.map { Intent(it) }.toTypedArray(), Intent(Settings.ACTION_SETTINGS))
            r(if (okk) "📂 J'ouvre « $title »" else "❌ Impossible d'ouvrir « $title »")
        }

    private fun pkgTask(id: String, emoji: String, title: String, kw: List<String>, pkg: String) =
        Task(id, emoji, "Ouvrir : $title", "Applis Samsung", kw) { ctx ->
            if (SystemTools.openApp(ctx, pkg)) r("📲 J'ouvre $title") else { SystemTools.openStore(ctx, pkg); r("📥 $title n'est pas installée, j'ouvre la boutique") }
        }

    /** Toutes les tâches fixes (hors applis installées, ajoutées dynamiquement). */
    val staticTasks: List<Task> by lazy {
        val t = mutableListOf<Task>()

        // ---- Le bouton magique ----
        t += Task("tout", "⚡", "Tout optimiser en 1 clic", "Optimisation",
            listOf("tout optimiser", "optimise", "optimis", "boost tout", "fais tout", "accelere", "rapide", "booste", "ameliore", "nettoie tout", "fais de la place", "libere de la place", "inutile", "sert a rien", "servent a rien", "menage")) { ctx ->
            Reply(BoostTools.optimizeAll(ctx) {})
        }
        t += Task("ram", "🚀", "Libérer la RAM", "Optimisation", listOf("ram", "memoire vive", "ferme les applis", "lent", "rame", "lag", "freeze", "bug")) { ctx ->
            r("🚀 ${BoostTools.freeRam(ctx)} applis en arrière-plan fermées")
        }
        t += Task("scan", "🔍", "Analyser tout le stockage", "Stockage", listOf("analyse", "scan", "qu'est-ce qui prend", "prend de la place", "stockage plein", "plein")) { ctx ->
            val j = BoostTools.scan(ctx)
            val lines = mutableListOf("🔍 Analyse terminée :")
            j.groupBy { it.kind }.forEach { (k, l) -> lines += "${k.emoji} ${k.label} : ${l.size} · ${BoostTools.fmt(l.sumOf { it.size })}" }
            if (j.isEmpty()) lines += "✨ Rien à nettoyer"
            Reply(lines, goTab = "stockage")
        }
        t += Task("devicecare", "🧽", "Vider le cache de toutes les applis (Samsung)", "Stockage",
            listOf("cache des applis", "cache de toutes", "entretien", "device care", "maintenance", "cache cache", "cache des autres")) { ctx ->
            BoostTools.openDeviceCare(ctx); r("🧽 J'ouvre l'Entretien de l'appareil : appuie sur « Optimiser maintenant »")
        }

        // ---- Nettoyage par catégorie ----
        BoostTools.Kind.entries.forEach { k ->
            t += Task("clean_${k.name.lowercase()}", k.emoji, "Supprimer : ${k.label}", "Stockage",
                k.keywords.map { "supprim $it" } + k.keywords.map { "efface $it" } + k.keywords.map { "nettoie $it" } + k.keywords.map { "vide $it" } + k.keywords) { ctx ->
                val items = BoostTools.scan(ctx).filter { it.kind == k }
                when {
                    items.isEmpty() -> r("✨ Aucun élément « ${k.label} » trouvé")
                    k.safe -> r("✅ ${k.label} : ${BoostTools.fmt(BoostTools.delete(items))} libérés (${items.size} éléments)")
                    else -> Reply(
                        listOf("👀 J'ai trouvé ${items.size} élément(s) « ${k.label} » (${BoostTools.fmt(items.sumOf { it.size })}).") +
                            items.take(5).map { "  • ${it.file.name}" } + listOf("Je supprime ? Confirme avec le bouton."),
                        confirm = items
                    )
                }
            }
        }

        // ---- Applis inutilisées ----
        listOf(7, 30, 90, 180).forEach { d ->
            t += Task("unused_$d", "🗂️", "Applis pas ouvertes depuis $d jours", "Applis",
                listOf("applis inutil", "appli inutil", "applis que j'utilise pas", "sers pas", "sert pas", "desinstall", "$d jours")) { ctx ->
                val l = if (BoostTools.hasUsageAccess(ctx)) BoostTools.unusedApps(ctx, d) else null
                if (l == null) { BoostTools.requestUsageAccess(ctx); r("🔑 Autorise d'abord « Données d'utilisation » pour Boost Chahyn") }
                else if (l.isEmpty()) r("✨ Tu as utilisé toutes tes applis ces $d derniers jours")
                else Reply(listOf("🗂️ ${l.size} applis pas ouvertes depuis $d j (${BoostTools.fmt(l.sumOf { it.size })}) :") +
                    l.take(10).map { "  • ${it.label} — ${BoostTools.fmt(it.size)}" } + listOf("👉 Onglet Stockage → Applis pour les supprimer"), goTab = "stockage")
            }
        }

        // ---- Modes ----
        BoostTools.modes.forEach { m ->
            t += Task("mode_${m.id}", m.emoji, "Mode ${m.title}", "Modes", m.keywords.map { "mode $it" } + m.keywords) { ctx ->
                Reply(BoostTools.applyMode(ctx, m.id))
            }
        }

        // ---- Luminosité ----
        (0..100 step 10).forEach { p ->
            t += Task("bright_$p", "☀️", "Luminosité à $p %", "Écran", listOf("luminosite $p", "lumiere $p")) { ctx ->
                ok(BoostTools.setBrightnessPct(ctx, p), "Luminosité à $p %")
            }
        }
        t += Task("bright_max", "🌞", "Luminosité au maximum", "Écran", listOf("luminosite max", "plus lumineux", "plus clair", "monte la luminosite")) { ctx -> ok(BoostTools.setBrightnessPct(ctx, 100), "Luminosité au maximum") }
        t += Task("bright_min", "🌑", "Luminosité au minimum", "Écran", listOf("luminosite min", "moins lumineux", "plus sombre", "baisse la luminosite")) { ctx -> ok(BoostTools.setBrightnessPct(ctx, 0), "Luminosité au minimum") }
        t += Task("bright_auto_on", "🔆", "Luminosité automatique", "Écran", listOf("luminosite auto", "luminosite adaptative")) { ctx -> ok(BoostTools.setAutoBrightness(ctx, true), "Luminosité automatique activée") }
        t += Task("bright_auto_off", "🔅", "Luminosité manuelle", "Écran", listOf("luminosite manuelle", "desactive luminosite auto")) { ctx -> ok(BoostTools.setAutoBrightness(ctx, false), "Luminosité manuelle") }

        // ---- Mise en veille ----
        listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000).forEach { ms ->
            val label = BoostTools.fmtMs(ms)
            t += Task("timeout_$ms", "⏱️", "Mise en veille après $label", "Écran", listOf("veille $label", "ecran s'eteint $label", "mise en veille ${label.replace(" ", "")}")) { ctx ->
                ok(BoostTools.setTimeout(ctx, ms), "Mise en veille après $label")
            }
        }

        // ---- Fluidité (ADB) ----
        listOf(0f to "aucune", 0.5f to "rapides", 1f to "normales", 1.5f to "douces").forEach { (v, n) ->
            t += Task("anim_$v", "✨", "Animations $n", "Écran", listOf("animation $n", "animations $n")) { ctx ->
                ok(BoostTools.setAnim(ctx, v), "Animations $n", "mode avancé ADB nécessaire (Accueil → Autorisations)")
            }
        }
        t += Task("hz120", "⚡", "Écran à 120 Hz", "Écran", listOf("120", "120 hz", "fluide", "fluidite max", "hz max")) { ctx -> ok(BoostTools.setHighRefresh(ctx, true), "Écran à 120 Hz", "mode avancé ADB nécessaire") }
        t += Task("hz60", "🐢", "Écran à 60 Hz", "Écran", listOf("60 hz", "60hz", "standard hz")) { ctx -> ok(BoostTools.setHighRefresh(ctx, false), "Écran à 60 Hz", "mode avancé ADB nécessaire") }

        // ---- Interrupteurs ----
        fun toggle(id: String, emoji: String, name: String, kwOn: List<String>, kwOff: List<String>, f: (Context, Boolean) -> Boolean) {
            t += Task("${id}_on", emoji, "$name : activer", "Réglages", kwOn) { ctx -> ok(f(ctx, true), "$name activé(e)") }
            t += Task("${id}_off", emoji, "$name : désactiver", "Réglages", kwOff) { ctx -> ok(f(ctx, false), "$name désactivé(e)") }
        }
        toggle("rot", "🔄", "Rotation auto", listOf("active la rotation", "rotation on", "tourne l'ecran"), listOf("bloque la rotation", "coupe la rotation", "desactive la rotation")) { c, b -> BoostTools.setRotation(c, b) }
        toggle("touch", "🔊", "Sons des touches", listOf("active les sons des touches"), listOf("coupe les sons des touches", "sons des touches off")) { c, b -> BoostTools.setTouchSounds(c, b) }
        toggle("haptic", "📳", "Vibrations au toucher", listOf("active les vibrations au toucher", "retour haptique on"), listOf("coupe les vibrations au toucher", "retour haptique off")) { c, b -> BoostTools.setHaptics(c, b) }
        toggle("battpct", "💯", "Pourcentage de batterie", listOf("affiche le pourcentage", "pourcentage batterie"), listOf("cache le pourcentage")) { c, b -> BoostTools.setBatteryPercent(c, b) }
        toggle("torch", "🔦", "Lampe torche", listOf("allume la lampe", "lampe torche", "flash on", "allume la torche"), listOf("eteins la lampe", "eteint la lampe", "flash off", "eteins la torche")) { c, b -> BoostTools.setTorch(c, b) }

        // ---- Notifications et son ----
        t += Task("dnd_on", "🔕", "Ne pas déranger", "Son", listOf("ne pas deranger", "coupe les notifs", "coupe les notifications", "dnd")) { ctx -> ok(BoostTools.setDnd(ctx, NotificationManager.INTERRUPTION_FILTER_PRIORITY), "Ne pas déranger activé") }
        t += Task("dnd_total", "⛔", "Silence total", "Son", listOf("silence total", "aucune notification", "rien du tout")) { ctx -> ok(BoostTools.setDnd(ctx, NotificationManager.INTERRUPTION_FILTER_NONE), "Silence total") }
        t += Task("dnd_alarms", "⏰", "Réveils seulement", "Son", listOf("reveils seulement", "alarmes seulement")) { ctx -> ok(BoostTools.setDnd(ctx, NotificationManager.INTERRUPTION_FILTER_ALARMS), "Seuls les réveils sonneront") }
        t += Task("dnd_off", "🔔", "Réactiver les notifications", "Son", listOf("reactive les notifs", "remets les notifications", "desactive ne pas deranger", "notifications on")) { ctx -> ok(BoostTools.setDnd(ctx, NotificationManager.INTERRUPTION_FILTER_ALL), "Notifications réactivées") }
        t += Task("ring_normal", "🔔", "Sonnerie normale", "Son", listOf("sonnerie normale", "active la sonnerie", "mode sonore")) { ctx -> ok(BoostTools.setRinger(ctx, AudioManager.RINGER_MODE_NORMAL), "Sonnerie activée") }
        t += Task("ring_vibrate", "📳", "Mode vibreur", "Son", listOf("vibreur", "vibration seulement", "mode vibration")) { ctx -> ok(BoostTools.setRinger(ctx, AudioManager.RINGER_MODE_VIBRATE), "Mode vibreur") }
        t += Task("ring_silent", "🔇", "Mode silencieux", "Son", listOf("mode silencieux", "mets en silencieux", "silencieux complet")) { ctx -> ok(BoostTools.setRinger(ctx, AudioManager.RINGER_MODE_SILENT), "Mode silencieux") }
        listOf(
            Triple(AudioManager.STREAM_MUSIC, "media", "Volume média"),
            Triple(AudioManager.STREAM_RING, "sonnerie", "Volume sonnerie"),
            Triple(AudioManager.STREAM_ALARM, "alarme", "Volume alarme"),
            Triple(AudioManager.STREAM_NOTIFICATION, "notification", "Volume notifications"),
        ).forEach { (stream, key, name) ->
            (0..100 step 10).forEach { p ->
                t += Task("vol_${key}_$p", "🔉", "$name à $p %", "Son", listOf("volume $key $p", "$key $p")) { ctx ->
                    ok(BoostTools.setVolumePct(ctx, stream, p), "$name à $p %")
                }
            }
        }

        // ---- Infos ----
        t += Task("info_batt", "🔋", "État de la batterie", "Infos", listOf("batterie", "combien de batterie", "niveau de batterie")) { ctx ->
            val h = BoostTools.health(ctx); r("🔋 ${h.batteryPct} %${if (h.charging) " (en charge)" else ""} · 🌡️ ${h.batteryTempC} °C")
        }
        t += Task("info_temp", "🌡️", "Température", "Infos", listOf("temperature", "chaud", "chauffe", "surchauffe")) { ctx ->
            val h = BoostTools.health(ctx)
            r("🌡️ Batterie à ${h.batteryTempC} °C", if (h.batteryTempC >= 40) "🥵 C'est chaud : enlève la coque, baisse la luminosité, ferme les jeux. Je libère la RAM." else "😎 Température normale")
                .also { if (h.batteryTempC >= 40) BoostTools.freeRam(ctx) }
        }
        t += Task("info_ram", "🧠", "Mémoire RAM utilisée", "Infos", listOf("combien de ram", "ram utilisee", "memoire utilisee")) { ctx ->
            val h = BoostTools.health(ctx); r("🧠 RAM : ${BoostTools.fmt(h.ramUsed)} / ${BoostTools.fmt(h.ramTotal)}")
        }
        t += Task("info_storage", "💾", "Espace de stockage", "Infos", listOf("combien d'espace", "espace libre", "stockage restant", "combien de place")) { ctx ->
            val h = BoostTools.health(ctx); r("💾 ${BoostTools.fmt(h.storageTotal - h.storageUsed)} libres sur ${BoostTools.fmt(h.storageTotal)}")
        }
        t += Task("info_score", "🏆", "Note de santé du téléphone", "Infos", listOf("note", "score", "sante", "etat du telephone", "comment va")) { ctx ->
            val h = BoostTools.health(ctx); r("🏆 Ton téléphone est à ${h.score}/100", if (h.score < 70) "👉 Dis « tout optimiser » pour le booster" else "💪 En pleine forme")
        }
        t += Task("info_device", "📱", "Infos du téléphone", "Infos", listOf("quel telephone", "modele", "version android", "infos telephone")) { _ ->
            r("📱 ${Build.MANUFACTURER} ${Build.MODEL}", "🤖 Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "⏱️ Allumé depuis ${BoostTools.uptime()}")
        }
        t += Task("info_apps", "🔢", "Nombre d'applis", "Infos", listOf("combien d'applis", "nombre d'applis")) { ctx ->
            r("🔢 ${BoostTools.installedUserApps(ctx).size} applis installées par toi")
        }
        t += Task("info_freed", "📈", "Espace libéré au total", "Infos", listOf("combien j'ai libere", "total libere", "statistiques")) { ctx ->
            r("📈 Boost Chahyn t'a libéré ${BoostTools.fmt(BoostTools.totalFreed(ctx))} au total")
        }
        t += Task("info_restart", "🔁", "Conseil redémarrage", "Infos", listOf("redemarr", "reboot", "eteindre")) { _ ->
            r("⏱️ Allumé depuis ${BoostTools.uptime()}", "💡 Redémarre une fois par semaine (bouton latéral + volume bas → Redémarrer). Aucune appli ne peut le faire à ta place.")
        }

        // ---- Pages de réglages ----
        t += page("wifi", "📶", "Wi-Fi", listOf("wifi", "wi-fi"), Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
        t += page("internet", "🌐", "Internet (panneau)", listOf("internet", "connexion", "reseau"), Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        t += page("bt", "🎧", "Bluetooth", listOf("bluetooth", "ecouteurs", "airpods", "buds"), Settings.ACTION_BLUETOOTH_SETTINGS)
        t += page("nfc", "💳", "NFC et paiement", listOf("nfc", "paiement", "sans contact"), Settings.Panel.ACTION_NFC, Settings.ACTION_NFC_SETTINGS)
        t += page("data", "📡", "Données mobiles", listOf("donnees mobiles", "4g", "5g", "forfait"), Settings.ACTION_DATA_ROAMING_SETTINGS)
        t += page("datausage", "📊", "Consommation de données", listOf("consommation de donnees", "data usage"), "android.settings.DATA_USAGE_SETTINGS")
        t += page("hotspot", "📲", "Partage de connexion", listOf("partage de connexion", "hotspot", "point d'acces", "modem"), "android.settings.TETHER_SETTINGS")
        t += page("airplane", "✈️", "Mode avion", listOf("mode avion", "avion"), Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        t += page("vpn", "🛡️", "VPN", listOf("vpn"), Settings.ACTION_VPN_SETTINGS)
        t += page("location", "📍", "Localisation", listOf("localisation", "gps on", "position"), Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        t += page("display", "🖥️", "Affichage", listOf("affichage", "mode sombre", "dark mode", "taille police"), Settings.ACTION_DISPLAY_SETTINGS)
        t += page("night", "🌙", "Confort visuel / filtre lumière bleue", listOf("lumiere bleue", "confort des yeux", "night light"), "android.settings.NIGHT_DISPLAY_SETTINGS")
        t += page("sound", "🔊", "Sons et vibrations", listOf("sons et vibrations", "parametres son"), Settings.ACTION_SOUND_SETTINGS)
        t += page("volpanel", "🎚️", "Panneau volume", listOf("panneau volume"), Settings.Panel.ACTION_VOLUME)
        t += page("notifs", "🔔", "Notifications", listOf("parametres notifications", "gerer les notifications"), "android.settings.NOTIFICATION_SETTINGS")
        t += page("battery", "🔋", "Batterie (réglages)", listOf("parametres batterie", "utilisation batterie", "qui consomme"), Intent.ACTION_POWER_USAGE_SUMMARY)
        t += page("saver", "🪫", "Économie d'énergie", listOf("economie d'energie", "economiseur"), Settings.ACTION_BATTERY_SAVER_SETTINGS)
        t += page("storage", "💾", "Stockage (réglages)", listOf("parametres stockage"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        t += page("apps", "📱", "Applications", listOf("liste des applis", "gestion des applis", "parametres applis"), Settings.ACTION_APPLICATION_SETTINGS)
        t += page("defaults", "⭐", "Applis par défaut", listOf("applis par defaut", "navigateur par defaut"), Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        t += page("security", "🔐", "Sécurité", listOf("securite", "empreinte", "verrouillage", "code"), Settings.ACTION_SECURITY_SETTINGS)
        t += page("privacy", "🕵️", "Confidentialité", listOf("confidentialite", "vie privee", "permissions"), Settings.ACTION_PRIVACY_SETTINGS)
        t += page("accounts", "👤", "Comptes", listOf("comptes", "compte google", "compte samsung"), Settings.ACTION_SYNC_SETTINGS)
        t += page("access", "♿", "Accessibilité", listOf("accessibilite"), Settings.ACTION_ACCESSIBILITY_SETTINGS)
        t += page("lang", "🌍", "Langue et clavier", listOf("langue", "clavier"), Settings.ACTION_LOCALE_SETTINGS)
        t += page("date", "📅", "Date et heure", listOf("date", "heure", "fuseau"), Settings.ACTION_DATE_SETTINGS)
        t += page("dev", "🛠️", "Options de développement", listOf("developpeur", "options de dev", "debogage"), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        t += page("about", "ℹ️", "À propos du téléphone", listOf("a propos", "numero de serie", "imei"), Settings.ACTION_DEVICE_INFO_SETTINGS)
        t += page("update", "⬆️", "Mise à jour logicielle", listOf("mise a jour", "update", "maj"), "android.settings.SYSTEM_UPDATE_SETTINGS", Settings.ACTION_DEVICE_INFO_SETTINGS)
        t += page("cast", "📺", "Diffuser l'écran", listOf("diffuser", "cast", "smart view", "tele"), Settings.ACTION_CAST_SETTINGS)
        t += page("wallpaper", "🖼️", "Fond d'écran", listOf("fond d'ecran", "wallpaper"), Intent.ACTION_SET_WALLPAPER)
        t += page("home", "🏠", "Écran d'accueil par défaut", listOf("ecran d'accueil", "launcher"), Settings.ACTION_HOME_SETTINGS)
        t += page("usage", "📈", "Bien-être numérique", listOf("bien-etre", "temps d'ecran", "digital wellbeing"), "android.settings.DIGITAL_WELLBEING_SETTINGS", Settings.ACTION_USAGE_ACCESS_SETTINGS)
        t += page("print", "🖨️", "Impression", listOf("imprim"), Settings.ACTION_PRINT_SETTINGS)
        t += page("dream", "💤", "Écran de veille", listOf("economiseur d'ecran"), Settings.ACTION_DREAM_SETTINGS)
        t += page("biometric", "👆", "Biométrie", listOf("biometrie", "visage", "reconnaissance faciale"), Settings.ACTION_BIOMETRIC_ENROLL, Settings.ACTION_SECURITY_SETTINGS)
        t += page("settings", "⚙️", "Paramètres", listOf("parametres", "reglages"), Settings.ACTION_SETTINGS)
        t += Task("perm_files", "🔑", "Autoriser l'accès à tous les fichiers", "Autorisations", listOf("acces aux fichiers", "acces a tout")) { ctx -> BoostTools.requestAllFiles(ctx); r("🔑 Active l'interrupteur pour Boost Chahyn") }
        t += Task("perm_write", "🔑", "Autoriser les réglages du téléphone", "Autorisations", listOf("autorise les reglages")) { ctx -> SystemTools.requestWriteSettings(ctx); r("🔑 Active l'interrupteur pour Boost Chahyn") }
        t += Task("perm_dnd", "🔑", "Autoriser Ne pas déranger", "Autorisations", listOf("autorise ne pas deranger")) { ctx -> BoostTools.requestDndAccess(ctx); r("🔑 Active Boost Chahyn dans la liste") }
        t += Task("perm_usage", "🔑", "Autoriser les données d'utilisation", "Autorisations", listOf("autorise donnees d'utilisation")) { ctx -> BoostTools.requestUsageAccess(ctx); r("🔑 Active Boost Chahyn dans la liste") }

        // ---- Applis Samsung ----
        t += pkgTask("gamebooster", "🎮", "Game Booster", listOf("game booster", "game launcher", "gaming hub"), "com.samsung.android.game.gamehome")
        t += pkgTask("goodlock", "🔓", "Good Lock", listOf("good lock", "goodlock"), "com.samsung.android.goodlock")
        t += pkgTask("myfiles", "🗂️", "Mes fichiers", listOf("mes fichiers", "gestionnaire de fichiers"), "com.sec.android.app.myfiles")
        t += pkgTask("filesgo", "📁", "Files by Google", listOf("files by google", "files google"), "com.google.android.apps.nbu.files")
        t += pkgTask("gallery", "🖼️", "Galerie", listOf("galerie", "mes photos"), "com.sec.android.gallery3d")
        t += pkgTask("camera", "📷", "Appareil photo", listOf("appareil photo", "ouvre la camera"), "com.sec.android.app.camera")
        t += pkgTask("themes", "🎨", "Galaxy Themes", listOf("galaxy themes", "themes"), "com.samsung.android.themestore")
        t += pkgTask("store", "🛍️", "Galaxy Store", listOf("galaxy store"), "com.sec.android.app.samsungapps")
        t += pkgTask("play", "▶️", "Play Store", listOf("play store", "playstore"), "com.android.vending")
        t += pkgTask("smartthings", "🏡", "SmartThings", listOf("smartthings", "maison connectee"), "com.samsung.android.oneconnect")
        t += pkgTask("health", "❤️", "Samsung Health", listOf("samsung health", "podometre", "sante samsung"), "com.sec.android.app.shealth")
        t += pkgTask("wear", "⌚", "Galaxy Wearable", listOf("montre", "wearable", "watch"), "com.samsung.android.app.watchmanager")
        t += pkgTask("notes", "📝", "Samsung Notes", listOf("notes", "bloc-notes"), "com.samsung.android.app.notes")
        t += pkgTask("calc", "🧮", "Calculatrice", listOf("calculatrice", "calcul"), "com.sec.android.app.popupcalculator")
        t += pkgTask("clock", "⏰", "Horloge / réveil", listOf("horloge", "alarme", "minuteur", "chrono"), "com.sec.android.app.clockpackage")
        t += pkgTask("calendar", "📅", "Calendrier", listOf("calendrier", "agenda"), "com.samsung.android.calendar")
        t += pkgTask("secure", "🔒", "Dossier sécurisé", listOf("dossier securise", "secure folder"), "com.samsung.knox.securefolder")
        t += pkgTask("recorder", "🎙️", "Dictaphone", listOf("dictaphone", "enregistreur", "enregistrer ma voix"), "com.sec.android.app.voicenote")
        t
    }

    /** Toutes les tâches, applis installées comprises (ouvrir / infos / désinstaller pour chacune). */
    fun allTasks(ctx: Context): List<Task> {
        val apps = try { BoostTools.launchableApps(ctx) } catch (_: Throwable) { emptyList() }
        val dyn = apps.flatMap { a ->
            val n = norm(a.label)
            listOf(
                Task("open_${a.pkg}", "📲", "Ouvrir ${a.label}", "Mes applis", listOf("ouvre $n", "lance $n", "demarre $n")) { c ->
                    r(if (SystemTools.openApp(c, a.pkg)) "📲 J'ouvre ${a.label}" else "❌ Impossible d'ouvrir ${a.label}")
                },
                Task("info_${a.pkg}", "ℹ️", "Infos / vider le cache de ${a.label}", "Mes applis", listOf("infos $n", "cache $n", "vide le cache de $n", "force $n")) { c ->
                    SystemTools.safeStart(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                    r("ℹ️ Fiche de ${a.label} : Stockage → « Vider le cache »")
                },
                Task("del_${a.pkg}", "🗑️", "Désinstaller ${a.label}", "Mes applis", listOf("desinstalle $n", "supprime $n", "enleve $n")) { c ->
                    SystemTools.uninstall(c, a.pkg); r("🗑️ Confirme la suppression de ${a.label}")
                },
            )
        }
        return staticTasks + dyn
    }

    // ================= Compréhension =================

    private val splitter = Regex("\\s+(?:et|puis|ensuite|apres|aussi|et puis)\\s+|[,;.!]+")

    /** Comprend une phrase (éventuellement plusieurs demandes) et exécute. */
    fun handle(ctx: Context, text: String): Reply {
        val all = allTasks(ctx)
        val parts = splitter.split(norm(text)).map { it.trim() }.filter { it.length > 1 }
        val lines = mutableListOf<String>()
        val confirm = mutableListOf<BoostTools.Junk>()
        var tab: String? = null
        val done = mutableSetOf<String>()
        for (p in parts.ifEmpty { listOf(norm(text)) }) {
            val task = numeric(p, all) ?: best(p, all)
            if (task == null || task.id in done) continue
            done += task.id
            val res = try { task.run(ctx) } catch (e: Throwable) { r("❌ ${task.title} : ${e.message}") }
            lines += res.lines; confirm += res.confirm; tab = res.goTab ?: tab
        }
        if (lines.isEmpty()) return r(
            "🤔 Je n'ai pas compris. Tu peux me dire par exemple :",
            "• « fais de la place » ou « tout optimiser »",
            "• « mode jeu », « mode travail », « je vais dormir »",
            "• « supprime les doublons », « vide les captures d'écran »",
            "• « luminosité 40 », « volume 70 », « ouvre WhatsApp »",
            "• « mode jeu et libère la RAM » (plusieurs à la fois)"
        )
        return Reply(lines, confirm, tab)
    }

    /** Demandes avec un nombre libre : « luminosité 37 », « volume 65 ». */
    private fun numeric(p: String, all: List<Task>): Task? {
        val n = Regex("(\\d{1,3})").find(p)?.value?.toIntOrNull() ?: return null
        val key = when {
            "lumi" in p -> "bright"
            "volume" in p && "sonnerie" in p -> "vol_sonnerie"
            "volume" in p && "alarme" in p -> "vol_alarme"
            "volume" in p && "notif" in p -> "vol_notification"
            "volume" in p || "son " in p -> "vol_media"
            else -> return null
        }
        val pct = n.coerceIn(0, 100)
        val bright = key == "bright"
        return Task("num_$key$pct", "🎚️", "", "", emptyList()) { c ->
            if (bright) ok(BoostTools.setBrightnessPct(c, pct), "Luminosité à $pct %")
            else {
                val stream = when (key) {
                    "vol_sonnerie" -> AudioManager.STREAM_RING
                    "vol_alarme" -> AudioManager.STREAM_ALARM
                    "vol_notification" -> AudioManager.STREAM_NOTIFICATION
                    else -> AudioManager.STREAM_MUSIC
                }
                ok(BoostTools.setVolumePct(c, stream, pct), "Volume à $pct %")
            }
        }.takeIf { all.isNotEmpty() }
    }

    private fun best(p: String, all: List<Task>): Task? {
        var top: Task? = null
        var score = 0
        for (t in all) {
            var s = 0
            for (k in t.keywords) {
                val kn = norm(k)
                if (kn.isNotEmpty() && p.contains(kn)) s = maxOf(s, kn.length)
            }
            if (s > score) { score = s; top = t }
        }
        return if (score >= 3) top else null
    }
}
