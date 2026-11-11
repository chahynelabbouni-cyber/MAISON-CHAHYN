package com.maisonchahyn.app.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.camera2.DngCreator
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Enregistre les photos dans la Galerie (Images › Chahyn Photo). */
object PhotoSaver {
    const val FOLDER = "Chahyn Photo"

    /** Au-delà, le style n'est pas recalculé (mémoire) : la photo est gardée telle quelle. */
    private const val MAX_STYLE_PIXELS = 52_000_000L

    data class Saved(val uri: Uri, val thumb: Bitmap?, val styled: Boolean)

    private fun baseName(): String =
        "CHAHYN_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    fun saveJpeg(ctx: Context, job: PhotoJob): Saved? {
        val look = Looks.byId(job.meta.look)
        var bytes = job.jpeg
        var styled = false
        if (!look.isNeutral && job.meta.lookAmount > 0.01f) {
            applyLook(ctx, bytes, look, job.meta)?.let { bytes = it; styled = true }
        }
        val name = baseName() + ".jpg"
        val uri = write(ctx, name, "image/jpeg") { it.write(bytes) } ?: return null
        return Saved(uri, thumbnail(bytes), styled)
    }

    /** Applique le style en pleine résolution puis recopie les métadonnées EXIF d'origine. */
    private fun applyLook(ctx: Context, src: ByteArray, look: Look, meta: ShotMeta): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(src, 0, src.size, bounds)
            if (bounds.outWidth.toLong() * bounds.outHeight > MAX_STYLE_PIXELS) return null
            val bmp = BitmapFactory.decodeByteArray(src, 0, src.size) ?: return null
            val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(look.matrix(meta.lookAmount))
            }
            Canvas(out).drawBitmap(bmp, 0f, 0f, paint)
            bmp.recycle()
            val tmpIn = File(ctx.cacheDir, "exif_src.jpg").apply { writeBytes(src) }
            val tmpOut = File(ctx.cacheDir, "styled.jpg")
            FileOutputStream(tmpOut).use { out.compress(Bitmap.CompressFormat.JPEG, 98, it) }
            out.recycle()
            copyExif(tmpIn, tmpOut, "Chahyn Photo — ${meta.scene} · ${look.name}")
            val result = tmpOut.readBytes()
            tmpIn.delete(); tmpOut.delete()
            result
        } catch (e: Throwable) {
            null // mémoire insuffisante, etc. : on garde l'original
        }
    }

    private val exifTags = listOf(
        ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE, ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH, ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_SCENE_CAPTURE_TYPE, ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    )

    private fun copyExif(from: File, to: File, description: String) {
        try {
            val a = ExifInterface(from.absolutePath)
            val b = ExifInterface(to.absolutePath)
            for (t in exifTags) a.getAttribute(t)?.let { b.setAttribute(t, it) }
            b.setAttribute(ExifInterface.TAG_SOFTWARE, "Chahyn Photo")
            b.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, description)
            b.saveAttributes()
        } catch (_: Exception) {
        }
    }

    fun saveDng(ctx: Context, job: RawJob): Uri? {
        return try {
            val dng = DngCreator(job.chars, job.result)
            dng.setOrientation(exifOrientation(job.meta.orientation))
            val uri = write(ctx, baseName() + ".dng", "image/x-adobe-dng") { dng.writeImage(it, job.image) }
            dng.close()
            uri
        } catch (e: Exception) {
            null
        } finally {
            job.image.close()
        }
    }

    private fun exifOrientation(deg: Int) = when ((deg % 360 + 360) % 360) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }

    private fun write(ctx: Context, name: String, mime: String, body: (OutputStream) -> Unit): Uri? {
        return if (Build.VERSION.SDK_INT >= 29) {
            val cr = ctx.contentResolver
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v)
                ?: return null
            try {
                cr.openOutputStream(uri)?.use(body) ?: throw IllegalStateException("flux indisponible")
                v.clear()
                v.put(MediaStore.Images.Media.IS_PENDING, 0)
                cr.update(uri, v, null, null)
                uri
            } catch (e: Exception) {
                cr.delete(uri, null, null)
                null
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
                dir.mkdirs()
                val f = File(dir, name)
                FileOutputStream(f).use(body)
                MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf(mime), null)
                Uri.fromFile(f)
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Petite vignette pour le bouton Galerie, dans le bon sens. */
    private fun thumbnail(jpeg: ByteArray): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 200) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
        val rot = when (ExifInterface(ByteArrayInputStream(jpeg)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (bmp != null && rot != 0f) {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        } else bmp
    } catch (e: Throwable) {
        null
    }
}
