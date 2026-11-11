package com.maisonchahyn.app.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/** Personnalisation d'une appli : nom, icône emoji/lettre sur couleur, ou photo. */
data class IconOverride(
    val label: String? = null,
    val emoji: String? = null,
    val color: Int? = null,
    val image: String? = null
) {
    fun isEmpty() = label == null && emoji == null && image == null
}

object IconOverrides {
    private var sp: SharedPreferences? = null
    val map = mutableStateMapOf<String, IconOverride>()
    private val images = HashMap<String, ImageBitmap>()

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("maison_icons", Context.MODE_PRIVATE)
        sp = p
        for ((k, v) in p.all) {
            try {
                val o = JSONObject(v as String)
                map[k] = IconOverride(
                    label = o.optString("label").ifEmpty { null },
                    emoji = o.optString("emoji").ifEmpty { null },
                    color = if (o.has("color")) o.getInt("color") else null,
                    image = o.optString("image").ifEmpty { null }
                )
            } catch (_: Throwable) {
            }
        }
    }

    fun set(key: String, ov: IconOverride?) {
        val e = sp?.edit()
        if (ov == null || ov.isEmpty()) {
            map.remove(key); e?.remove(key)
        } else {
            map[key] = ov
            val o = JSONObject()
            ov.label?.let { o.put("label", it) }
            ov.emoji?.let { o.put("emoji", it) }
            ov.color?.let { o.put("color", it) }
            ov.image?.let { o.put("image", it) }
            e?.putString(key, o.toString())
        }
        e?.apply()
    }

    fun label(app: AppEntry): String = map[app.key]?.label ?: app.label

    /** Copie l'image choisie, recadrée en carré 256 px. */
    suspend fun importImage(ctx: Context, key: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            val s = min(src.width, src.height)
            val sq = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
            val out = Bitmap.createScaledBitmap(sq, 256, 256, true)
            val dir = File(ctx.filesDir, "icons").apply { mkdirs() }
            val f = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + "_" + System.currentTimeMillis() + ".png")
            FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f.absolutePath
        } catch (e: Throwable) {
            null
        }
    }

    @Composable
    fun image(path: String): ImageBitmap? {
        val st = produceState(images[path], path) {
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    try {
                        BitmapFactory.decodeFile(path)?.asImageBitmap()
                    } catch (e: Throwable) {
                        null
                    }
                }
                value?.let { images[path] = it }
            }
        }
        return st.value
    }
}
