package com.chahyn.boost

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.util.Calendar

/** Profil de l'utilisateur (prénom, nom, surnom), sauvegardé sur le téléphone. */
object Profile {
    val prenom = mutableStateOf("")
    val nom = mutableStateOf("")
    val surnom = mutableStateOf("")
    val done = mutableStateOf(false)

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("profil", Context.MODE_PRIVATE)
        prenom.value = p.getString("prenom", "") ?: ""
        nom.value = p.getString("nom", "") ?: ""
        surnom.value = p.getString("surnom", "") ?: ""
        done.value = p.getBoolean("done", false)
    }

    fun save(ctx: Context, prenom: String, nom: String, surnom: String) {
        this.prenom.value = prenom.trim(); this.nom.value = nom.trim(); this.surnom.value = surnom.trim()
        done.value = true
        ctx.getSharedPreferences("profil", Context.MODE_PRIVATE).edit()
            .putString("prenom", this.prenom.value).putString("nom", this.nom.value)
            .putString("surnom", this.surnom.value).putBoolean("done", true).apply()
    }

    /** Comment l'appli t'appelle. */
    val appel: String get() = surnom.value.ifBlank { prenom.value }.ifBlank { "toi" }

    fun salut(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val s = when (h) {
            in 5..11 -> "Bonjour"
            in 12..17 -> "Bon après-midi"
            in 18..22 -> "Bonsoir"
            else -> "Bonne nuit"
        }
        return "$s ${appel} 👋"
    }
}
