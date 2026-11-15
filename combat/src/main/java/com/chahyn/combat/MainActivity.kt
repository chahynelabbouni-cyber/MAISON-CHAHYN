package com.chahyn.combat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notif.createChannel(this)
        web = WebView(this)
        web.setBackgroundColor(0xFF0A0A0F.toInt())
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("file:")) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }
        }
        setContentView(web)
        if (savedInstanceState == null) web.loadUrl("file:///android_asset/index.html")
        else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Gestion simple du bouton retour")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.handleBack && window.handleBack()) ? 'true' : 'false'") { r ->
            if (!r.contains("true")) finish()
        }
    }

    @Deprecated("API simple et suffisante ici")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            web.evaluateJavascript("window.onNativeVoice && window.onNativeVoice(${JSONObject.quote(text)})", null)
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun startVoice() {
            runOnUiThread {
                val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à ton coach")
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(i, VOICE)
                } catch (e: Exception) {
                    web.evaluateJavascript("toast('Reconnaissance vocale indisponible : installe l\\'appli Google')", null)
                }
            }
        }

        @JavascriptInterface
        fun requestNotif() {
            if (Build.VERSION.SDK_INT >= 33) runOnUiThread {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
            }
        }

        @JavascriptInterface
        fun notifGranted(): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        @JavascriptInterface
        fun notify(title: String, body: String) {
            Notif.show(this@MainActivity, title, body)
        }

        @JavascriptInterface
        fun schedule(json: String) {
            Notif.schedule(this@MainActivity, json)
        }
    }

    companion object { const val VOICE = 42 }
}
