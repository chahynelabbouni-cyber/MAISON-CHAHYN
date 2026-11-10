package com.maisonchahyn.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf

class MaisonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}

/** État global léger : incrémenté à chaque retour dans l'appli pour rafraîchir les statuts. */
object AppState {
    val resumeTick = mutableIntStateOf(0)
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
