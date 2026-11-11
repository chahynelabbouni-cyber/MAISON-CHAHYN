package com.chahyn.boost

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

object SystemTools {
    fun safeStart(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i); return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun openApp(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return safeStart(ctx, i)
    }

    fun openStore(ctx: Context, pkg: String) {
        safeStart(
            ctx,
            Intent(Intent.ACTION_VIEW, Uri.parse("samsungapps://ProductDetail/$pkg")),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        )
    }

    fun canWriteSettings(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    fun requestWriteSettings(ctx: Context) {
        safeStart(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)))
    }

    fun hasSecureSettings(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun getSystemInt(ctx: Context, key: String, def: Int): Int =
        try { Settings.System.getInt(ctx.contentResolver, key, def) } catch (e: Exception) { def }

    fun putSystemInt(ctx: Context, key: String, v: Int): Boolean =
        try { Settings.System.putInt(ctx.contentResolver, key, v) } catch (e: Exception) { false }

    fun getGlobalFloat(ctx: Context, key: String, def: Float): Float =
        try { Settings.Global.getFloat(ctx.contentResolver, key, def) } catch (e: Exception) { def }

    fun putGlobalFloat(ctx: Context, key: String, v: Float): Boolean =
        try { Settings.Global.putFloat(ctx.contentResolver, key, v) } catch (e: Exception) { false }

    fun uninstall(ctx: Context, pkg: String) {
        safeStart(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }
}
