package com.maisonchahyn.app.system

import android.Manifest
import android.app.Activity
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.maisonchahyn.app.MainActivity
import com.maisonchahyn.app.toast

object SystemTools {

    fun safeStart(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun openStore(ctx: Context, pkg: String) {
        val ok = safeStart(
            ctx,
            Intent(Intent.ACTION_VIEW, Uri.parse("samsungapps://ProductDetail/$pkg")),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://galaxystore.samsung.com/detail/$pkg"))
        )
        if (!ok) ctx.toast("Impossible d'ouvrir le Galaxy Store")
    }

    fun openApp(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return safeStart(ctx, i)
    }

    fun openAppOrStore(ctx: Context, pkg: String) {
        if (!openApp(ctx, pkg)) openStore(ctx, pkg)
    }

    const val GOOD_LOCK = "com.samsung.android.goodlock"

    /** Ouvre un module Good Lock ; sinon Good Lock ; sinon le Galaxy Store. */
    fun openGoodLockModule(ctx: Context, pkg: String, name: String) {
        if (openApp(ctx, pkg)) return
        if (isInstalled(ctx, pkg)) {
            // Module installé sans icône : on passe par Good Lock
            if (openApp(ctx, GOOD_LOCK)) {
                ctx.toast("Ouvre « $name » dans Good Lock"); return
            }
        }
        if (isInstalled(ctx, GOOD_LOCK)) {
            openApp(ctx, GOOD_LOCK)
            ctx.toast("Installe le module « $name » depuis Good Lock")
        } else {
            ctx.toast("Installe d'abord Good Lock (gratuit, par Samsung)")
            openStore(ctx, GOOD_LOCK)
        }
    }

    // ---------- Écran d'accueil par défaut ----------
    fun isDefaultLauncher(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName == ctx.packageName
    }

    /** Intention à lancer pour devenir l'écran d'accueil (boîte de dialogue système). */
    fun homeRoleIntent(ctx: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun openHomeSettings(ctx: Context) {
        safeStart(ctx, Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    }

    // ---------- Clavier ----------
    fun imeEnabled(ctx: Context): Boolean {
        val imm = ctx.getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
    }

    fun imeSelected(ctx: Context): Boolean {
        val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return cur.startsWith(ctx.packageName + "/")
    }

    fun openImeSettings(ctx: Context) {
        safeStart(ctx, Intent(Settings.ACTION_INPUT_METHOD_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    }

    fun showImePicker(ctx: Context) {
        ctx.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    // ---------- Réglages système ----------
    fun canWriteSettings(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    fun requestWriteSettings(ctx: Context) {
        safeStart(
            ctx,
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)),
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        )
    }

    fun hasSecureSettings(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun getSystemInt(ctx: Context, key: String, def: Int): Int = try {
        Settings.System.getInt(ctx.contentResolver, key, def)
    } catch (e: Exception) {
        def
    }

    fun putSystemInt(ctx: Context, key: String, v: Int): Boolean = try {
        Settings.System.putInt(ctx.contentResolver, key, v)
    } catch (e: Exception) {
        false
    }

    fun getGlobalFloat(ctx: Context, key: String, def: Float): Float = try {
        Settings.Global.getFloat(ctx.contentResolver, key, def)
    } catch (e: Exception) {
        def
    }

    fun putGlobalFloat(ctx: Context, key: String, v: Float): Boolean = try {
        Settings.Global.putFloat(ctx.contentResolver, key, v)
    } catch (e: Exception) {
        false
    }

    @SuppressLint("WrongConstant")
    fun expandNotifications(ctx: Context) {
        try {
            val sb = ctx.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Throwable) {
        }
    }

    fun openMaison(ctx: Context, tab: String) {
        val i = Intent(ctx, MainActivity::class.java)
            .putExtra("tab", tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        safeStart(ctx, i)
    }

    fun appDetails(ctx: Context, pkg: String) {
        safeStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    }

    fun uninstall(ctx: Context, pkg: String) {
        safeStart(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }

    fun component(pkg: String, cls: String) = ComponentName(pkg, cls)

    // ---------- Raccourcis Samsung ----------
    data class Shortcut(val emoji: String, val title: String, val desc: String, val open: (Context) -> Unit)

    val samsung: List<Shortcut> = listOf(
        Shortcut("🔓", "Good Lock", "L'outil officiel Samsung pour tout personnaliser") { openAppOrStore(it, GOOD_LOCK) },
        Shortcut("🎛️", "QuickStar — Centre de contrôle", "Panneau rapide transparent façon iPhone, icônes de la barre d'état") {
            openGoodLockModule(it, "com.samsung.android.qstuner", "QuickStar")
        },
        Shortcut("🔒", "LockStar — Écran de verrouillage", "Horloge, widgets et raccourcis de l'écran verrouillé") {
            openGoodLockModule(it, "com.samsung.android.lockstar", "LockStar")
        },
        Shortcut("➖", "NavStar — Barre de gestes", "Barre de navigation fine style iPhone") {
            openGoodLockModule(it, "com.samsung.android.app.navstar", "NavStar")
        },
        Shortcut("🎨", "Theme Park", "Crée ton propre thème One UI et tes packs d'icônes") {
            openGoodLockModule(it, "com.samsung.android.themedesigner", "Theme Park")
        },
        Shortcut("🔔", "NotiStar", "Historique et style des notifications") {
            openGoodLockModule(it, "com.samsung.android.app.notistar", "NotiStar")
        },
        Shortcut("⌨️", "Keys Cafe", "Effets et sons du clavier Samsung") {
            openGoodLockModule(it, "com.samsung.android.keyscafe", "Keys Cafe")
        },
        Shortcut("🔊", "Sound Assistant", "Sons, volume par appli, égaliseur") {
            openGoodLockModule(it, "com.samsung.android.soundassistant", "Sound Assistant")
        },
        Shortcut("🖌️", "Galaxy Themes", "Thèmes, icônes et AOD de la boutique Samsung") { ctx ->
            if (!openApp(ctx, "com.samsung.android.themestore")) {
                val ok = safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("themestore://MainPage")))
                if (!ok) openStore(ctx, "com.samsung.android.themestore")
            }
        },
        Shortcut("🖼️", "Fond d'écran et style", "L'éditeur de fond d'écran One UI") { ctx ->
            if (!openApp(ctx, "com.samsung.android.app.dressroom")) {
                safeStart(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fond d'écran"))
            }
        },
    )

    val androidShortcuts: List<Shortcut> = listOf(
        Shortcut("☀️", "Affichage", "Mode sombre, police, taille, fluidité") {
            safeStart(it, Intent(Settings.ACTION_DISPLAY_SETTINGS))
        },
        Shortcut("🏠", "Écran d'accueil par défaut", "Choisir Maison ou One UI Home") { openHomeSettings(it) },
        Shortcut("⌨️", "Claviers", "Activer / choisir un clavier") { openImeSettings(it) },
        Shortcut("🔈", "Sons et vibrations", "Sonnerie, vibrations, sons système") {
            safeStart(it, Intent(Settings.ACTION_SOUND_SETTINGS))
        },
        Shortcut("🔔", "Notifications", "Style des notifications et badges") {
            safeStart(it, Intent("android.settings.NOTIFICATION_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
        },
        Shortcut("♿", "Accessibilité", "Contraste, animations, visibilité") {
            safeStart(it, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        Shortcut("🛠️", "Options de développement", "Animations, débogage USB / sans fil") {
            safeStart(it, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        },
    )
}
