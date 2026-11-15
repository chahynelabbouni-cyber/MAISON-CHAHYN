package com.chahyn.combat

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray

/** Rappels du coach : notifications + alarmes programmées (fonctionnent appli fermée). */
object Notif {
    private const val CHANNEL = "rappels"

    fun createChannel(c: Context) {
        val ch = NotificationChannel(CHANNEL, "Rappels du coach", NotificationManager.IMPORTANCE_HIGH)
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 300, 120, 300)
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun show(c: Context, title: String, body: String) {
        createChannel(c)
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFFFF3B47.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        c.getSystemService(NotificationManager::class.java)
            .notify((System.currentTimeMillis() % 1_000_000).toInt(), n)
    }

    fun schedule(c: Context, json: String) {
        c.getSharedPreferences("alarmes", Context.MODE_PRIVATE).edit().putString("json", json).apply()
        apply(c, json)
    }

    fun reschedule(c: Context) {
        val json = c.getSharedPreferences("alarmes", Context.MODE_PRIVATE).getString("json", null) ?: return
        apply(c, json)
    }

    private fun apply(c: Context, json: String) {
        val am = c.getSystemService(AlarmManager::class.java)
        val prefs = c.getSharedPreferences("alarmes", Context.MODE_PRIVATE)
        for (i in 0 until prefs.getInt("n", 0)) am.cancel(pending(c, i, null, null))
        val arr = JSONArray(json)
        val now = System.currentTimeMillis()
        var n = 0
        for (i in 0 until minOf(arr.length(), 80)) {
            val o = arr.getJSONObject(i)
            val ts = o.getLong("ts")
            if (ts <= now) continue
            val pi = pending(c, n, o.getString("title"), o.getString("body"))
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ts, pi)
            n++
        }
        prefs.edit().putInt("n", n).apply()
    }

    private fun pending(c: Context, i: Int, title: String?, body: String?): PendingIntent {
        val intent = Intent(c, AlarmReceiver::class.java).setAction("rappel_$i")
        if (title != null) intent.putExtra("t", title).putExtra("b", body)
        return PendingIntent.getBroadcast(
            c, i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Notif.show(c, i.getStringExtra("t") ?: "Rappel", i.getStringExtra("b") ?: "")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) Notif.reschedule(c)
    }
}
