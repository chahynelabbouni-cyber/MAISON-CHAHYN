package fr.orion.browser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private data class Tab(val web: WebView, val private: Boolean, val id: Int)

    private lateinit var content: FrameLayout
    private lateinit var url: EditText
    private lateinit var trustDot: View
    private lateinit var progress: ProgressBar
    private lateinit var tabsBtn: TextView
    private lateinit var panel: WebView

    private val tabs = mutableListOf<Tab>()
    private var cur: Tab? = null
    private var nextId = 1
    private val prefs by lazy { getSharedPreferences("orion", Context.MODE_PRIVATE) }
    private val io = Executors.newCachedThreadPool()
    private val chatHistory = JSONArray()
    private var tts: TextToSpeech? = null

    private val HOME = "file:///android_asset/home.html"
    private val UI = "file:///android_asset/ui.html"
    private val MOBILE_UA by lazy { WebSettings.getDefaultUserAgent(this) }
    private val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    // ---------- Moteurs / modes ----------
    private val trustedSites = "site:.gov OR site:.edu OR site:reuters.com OR site:apnews.com OR site:nature.com OR site:who.int OR site:wikipedia.org OR site:.gouv.fr"
    private fun searchUrl(mode: String, q: String): String? {
        val e = URLEncoder.encode(q, "UTF-8")
        return when (mode) {
            "us" -> "https://www.google.com/search?gl=us&hl=en&pws=0&q=$e"
            "fiable" -> "https://www.google.com/search?q=" + URLEncoder.encode("$q ($trustedSites)", "UTF-8")
            "science" -> "https://scholar.google.com/scholar?hl=en&q=$e"
            "actu" -> "https://news.google.com/search?q=$e&hl=en-US&gl=US&ceid=US:en"
            "reddit" -> "https://www.google.com/search?q=" + URLEncoder.encode("$q site:reddit.com", "UTF-8")
            "video" -> "https://m.youtube.com/results?search_query=$e"
            "code" -> "https://github.com/search?type=repositories&q=$e"
            "shop" -> "https://www.google.com/search?tbm=shop&q=$e"
            "prive" -> "https://duckduckgo.com/?q=$e"
            "ai" -> null
            else -> "https://www.google.com/search?q=$e"
        }
    }

    // ---------- Fiabilité ----------
    private val trusted = Regex("""(\.gov|\.edu|\.gouv\.fr|\.europa\.eu|who\.int|nature\.com|science\.org|sciencedirect\.com|arxiv\.org|nih\.gov|reuters\.com|apnews\.com|bbc\.com|bbc\.co\.uk|lemonde\.fr|nytimes\.com|wsj\.com|ft\.com|economist\.com|wikipedia\.org|insee\.fr|ieee\.org|technologyreview\.com|bloomberg\.com|lefigaro\.fr|francetvinfo\.fr)$""")
    private val dubious = Regex("""(blogspot\.com|wordpress\.com|medium\.com|substack\.com|tiktok\.com|x\.com|twitter\.com|facebook\.com|instagram\.com)$""")
    private fun trustOf(u: String?): Pair<String, String> {
        if (u == null || u.startsWith("file:")) return "" to "Page d'accueil Orion"
        val h = Uri.parse(u).host ?: return "" to ""
        return when {
            trusted.containsMatchIn(h) -> "ok" to "Source reconnue et fiable · $h"
            dubious.containsMatchIn(h) -> "bad" to "Contenu non vérifié, à recouper · $h"
            else -> "warn" to "Source non évaluée · $h"
        }
    }

    // ---------- Anti-pub ----------
    private val adHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com", "googletagmanager.com",
        "googletagservices.com", "adservice.google.com", "pagead2.googlesyndication.com", "amazon-adsystem.com", "adnxs.com",
        "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "scorecardresearch.com", "quantserve.com", "moatads.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "adsrvr.org", "smartadserver.com", "teads.tv",
        "hotjar.com", "mixpanel.com", "facebook.net", "ads-twitter.com", "adform.net", "yieldmo.com", "media.net", "zedo.com",
        "popads.net", "propellerads.com", "exoclick.com", "mgid.com", "revcontent.com", "sharethrough.com", "33across.com",
        "bidswitch.net", "indexww.com", "sovrn.com", "lijit.com", "contextweb.com", "advertising.com", "yieldlab.net"
    )
    private fun isAd(host: String?): Boolean {
        var h = host ?: return false
        while (true) {
            if (h in adHosts) return true
            val i = h.indexOf('.'); if (i < 0 || h.indexOf('.', i + 1) < 0) return false
            h = h.substring(i + 1)
        }
    }

    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { go(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.main)
        content = findViewById(R.id.content); url = findViewById(R.id.url); trustDot = findViewById(R.id.trust)
        progress = findViewById(R.id.progress); tabsBtn = findViewById(R.id.tabs); panel = findViewById(R.id.panel)

        // Marges système (barre d'état / navigation / clavier) : plus rien ne se chevauche
        val root = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            root.setPadding(b.left, b.top, b.right, b.bottom)
            panel.setPadding(0, 0, 0, 0)
            (panel.layoutParams as ViewGroup.MarginLayoutParams).apply { topMargin = b.top; bottomMargin = b.bottom }
            panel.requestLayout()
            WindowInsetsCompat.CONSUMED
        }

        url.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_ACTION_SEARCH) { go(url.text.toString()); true } else false
        }
        url.setOnFocusChangeListener { _, f -> if (!f) syncBar() }
        findViewById<View>(R.id.back).setOnClickListener { cur?.web?.let { if (it.canGoBack()) it.goBack() } }
        findViewById<View>(R.id.fwd).setOnClickListener { cur?.web?.let { if (it.canGoForward()) it.goForward() } }
        findViewById<View>(R.id.home).setOnClickListener { cur?.web?.loadUrl(HOME) }
        findViewById<View>(R.id.home).setOnLongClickListener { cur?.web?.reload(); toast("Rechargement"); true }
        tabsBtn.setOnClickListener { openPanel("tabs") }
        tabsBtn.setOnLongClickListener { newTab(HOME, false); true }
        findViewById<View>(R.id.menu).setOnClickListener { openPanel("menu") }
        findViewById<View>(R.id.aiBtn).setOnClickListener { openPanel("ai") }
        findViewById<View>(R.id.mic).setOnClickListener { startVoice() }

        setupPanel()
        applyVpn()
        tts = TextToSpeech(this) {}
        newTab(intent?.dataString ?: HOME, false)
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); i.dataString?.let { newTab(it, false) } }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            panel.visibility == View.VISIBLE -> panel.evaluateJavascript("closeAll()", null)
            cur?.web?.canGoBack() == true -> cur!!.web.goBack()
            tabs.size > 1 -> closeTab(cur!!)
            else -> super.onBackPressed()
        }
    }

    // ---------- Onglets ----------
    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab(link: String, private: Boolean) {
        val w = WebView(this)
        val t = Tab(w, private, nextId++)
        w.setBackgroundColor(Color.parseColor("#05060F"))
        w.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = !private
            loadWithOverviewMode = true; useWideViewPort = true
            builtInZoomControls = true; displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            userAgentString = if (prefs.getBoolean("desktop", false)) DESKTOP_UA else MOBILE_UA
            if (private) cacheMode = WebSettings.LOAD_NO_CACHE
        }
        applyDark(w)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, !private)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                when (u.scheme) {
                    "orion" -> { handleOrion(u); return true }
                    "http", "https", "file" -> return false
                    "intent" -> {
                        runCatching { startActivity(Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)) }
                        return true
                    }
                }
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
                return true
            }
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                if (prefs.getBoolean("adblock", true) && isAd(r.url.host))
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                return null
            }
            override fun onPageStarted(v: WebView, u: String, f: android.graphics.Bitmap?) { if (t == cur) syncBar() }
            override fun doUpdateVisitedHistory(v: WebView, u: String, reload: Boolean) { if (t == cur) syncBar() }
            override fun onPageFinished(v: WebView, u: String) {
                if (t == cur) syncBar()
                if (!t.private && u.startsWith("http") && !prefs.getBoolean("nohist", false)) addHistory(v.title ?: u, u)
                if (prefs.getBoolean("autotr", false)) autoTranslate(v, u)
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (t != cur) return
                progress.progress = p; progress.visibility = if (p in 1..99) View.VISIBLE else View.INVISIBLE
            }
            override fun onReceivedTitle(v: WebView, title: String?) { if (t == cur) syncBar() }
        }
        w.setDownloadListener { u, _, _, _, _ -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } }
        tabs.add(t); select(t)
        w.loadUrl(link)
    }

    private fun select(t: Tab) {
        content.removeAllViews()
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        content.addView(t.web, FrameLayout.LayoutParams(-1, -1))
        cur = t; syncBar()
    }

    private fun closeTab(t: Tab) {
        tabs.remove(t); t.web.destroy()
        if (t.private && tabs.none { it.private }) { CookieManager.getInstance().removeSessionCookies(null) }
        if (tabs.isEmpty()) newTab(HOME, false) else select(tabs.last())
    }

    private fun syncBar() {
        val t = cur ?: return
        tabsBtn.text = tabs.size.toString()
        val u = t.web.url
        if (!url.hasFocus()) url.setText(if (u == null || u.startsWith("file:")) "" else prettyUrl(u))
        url.hint = if (t.private) "🕶 Navigation privée" else "Rechercher ou saisir une adresse"
        val c = when (trustOf(u).first) { "ok" -> "#3DFF9A"; "warn" -> "#FFC04D"; "bad" -> "#FF5470"; else -> "#5EF0FF" }
        (trustDot.background.mutate() as GradientDrawable).setColor(Color.parseColor(c))
    }

    private fun prettyUrl(u: String): String {
        val p = Uri.parse(u)
        val q = p.getQueryParameter("q") ?: p.getQueryParameter("search_query")
        if (q != null && (p.host?.contains("google") == true || p.host?.contains("duckduckgo") == true || p.host?.contains("youtube") == true))
            return q.replace(Regex("""\s*\(site:.*\)$|\s*site:reddit\.com$"""), "")
        return (p.host ?: u).removePrefix("www.") + (p.path?.takeIf { it.length > 1 } ?: "")
    }

    private fun go(input: String) {
        val v = input.trim(); if (v.isEmpty()) return
        hideKb(); url.clearFocus()
        val isUrl = v.startsWith("http://") || v.startsWith("https://") || Regex("""^[\w-]+(\.[\w-]+)+(:\d+)?(/.*)?$""").matches(v)
        if (isUrl) { cur?.web?.loadUrl(if (v.startsWith("http")) v else "https://$v"); return }
        val mode = prefs.getString("engine", "web")!!
        val s = searchUrl(mode, v)
        if (s == null) openPanel("ai", "", v) else cur?.web?.loadUrl(s)
    }

    private fun handleOrion(u: Uri) {
        when (u.host) {
            "search" -> {
                val q = u.getQueryParameter("q") ?: return
                val m = u.getQueryParameter("m") ?: "web"
                val s = searchUrl(m, q)
                if (s == null) openPanel("ai", "", q) else cur?.web?.loadUrl(s)
            }
            "ai" -> openPanel("ai")
            "veille" -> openPanel("ai", "", "Fais-moi une veille des innovations, idées et tendances américaines les plus récentes et les plus prometteuses (tech, business, IA, santé). Ce qu'on ne voit pas encore en France. Avec sources.")
            "vpn" -> openPanel("vpn")
        }
    }

    // ---------- Panneau UI (menu, onglets, IA, VPN, réglages) ----------
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupPanel() {
        panel.setBackgroundColor(Color.TRANSPARENT)
        panel.settings.javaScriptEnabled = true
        panel.settings.domStorageEnabled = true
        panel.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = true
        }
        panel.addJavascriptInterface(Bridge(), "Orion")
        panel.loadUrl(UI)
    }

    private fun openPanel(id: String, arg: String = "", q: String = "") {
        hideKb()
        panel.visibility = View.VISIBLE
        panel.evaluateJavascript("openPanel(${JSONObject.quote(id)},${JSONObject.quote(arg)},${JSONObject.quote(q)})", null)
    }

    private fun closePanel() { panel.visibility = View.GONE; hideKb() }

    private fun state(): String {
        val t = cur
        val (tr, label) = trustOf(t?.web?.url)
        val o = JSONObject()
            .put("title", if (t?.web?.url?.startsWith("file:") != false) "Accueil Orion" else t?.web?.title)
            .put("url", t?.web?.url ?: "").put("trust", tr).put("trustLabel", label)
            .put("key", prefs.getString("key", "")).put("gkey", prefs.getString("gkey", "")).put("provider", prefs.getString("provider", "gemini")).put("model", prefs.getString("model", "claude-opus-5-5"))
            .put("engine", prefs.getString("engine", "web")).put("lang", prefs.getString("lang", "fr"))
            .put("adblock", prefs.getBoolean("adblock", true)).put("autotr", prefs.getBoolean("autotr", false))
            .put("dark", prefs.getBoolean("dark", false)).put("desktop", prefs.getBoolean("desktop", false))
            .put("nohist", prefs.getBoolean("nohist", false))
            .put("vpn", prefs.getString("vpn", "off")).put("proxy", prefs.getString("proxy", ""))
            .put("bookmarks", JSONArray(prefs.getString("bookmarks", "[]")))
            .put("history", JSONArray(prefs.getString("history", "[]")))
        val arr = JSONArray()
        tabs.forEach {
            val u = it.web.url ?: ""
            arr.put(JSONObject().put("i", it.id).put("private", it.private).put("active", it == cur)
                .put("title", if (u.startsWith("file:")) "Accueil Orion" else it.web.title).put("url", if (u.startsWith("file:")) "" else u))
        }
        return o.put("tabs", arr).toString()
    }

    inner class Bridge {
        @JavascriptInterface fun state(): String {
            var s = "{}"; val lock = Object()
            synchronized(lock) {
                runOnUiThread { synchronized(lock) { s = this@MainActivity.state(); lock.notify() } }
                lock.wait(2000)
            }
            return s
        }
        @JavascriptInterface fun close() = runOnUiThread { closePanel() }
        @JavascriptInterface fun action(a: String, b: String) = runOnUiThread { doAction(a, b) }
        @JavascriptInterface fun saveSettings(json: String) = runOnUiThread {
            val o = JSONObject(json)
            prefs.edit().putString("key", o.optString("key")).putString("gkey", o.optString("gkey")).putString("provider", o.optString("provider")).putString("model", o.optString("model"))
                .putString("engine", o.optString("engine")).putString("lang", o.optString("lang"))
                .putBoolean("adblock", o.optBoolean("adblock")).putBoolean("autotr", o.optBoolean("autotr"))
                .putBoolean("dark", o.optBoolean("dark")).putBoolean("nohist", o.optBoolean("nohist")).apply()
            if (o.optBoolean("nohist")) prefs.edit().putString("history", "[]").apply()
            tabs.forEach { applyDark(it.web) }
            toast("Réglages enregistrés")
        }
        @JavascriptInterface fun ask(q: String, usePage: Boolean) = runOnUiThread { askAi(q, usePage) }
    }

    private fun doAction(a: String, b: String) {
        val w = cur?.web
        when (a) {
            "translate" -> { closePanel(); translate() }
            "reader" -> { closePanel(); w?.evaluateJavascript(READER_JS, null) }
            "speak" -> { closePanel(); speakPage() }
            "say" -> speak(b)
            "copy" -> { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Orion", b)); toast("Copié") }
            "find" -> { closePanel(); findInPage() }
            "archive" -> { closePanel(); w?.url?.takeIf { it.startsWith("http") }?.let { w.loadUrl("https://web.archive.org/web/2/$it") } }
            "multi" -> { closePanel(); multiSearch() }
            "bookmark" -> { addBookmark(); toast("⭐ Ajouté aux favoris") }
            "share" -> { closePanel(); w?.url?.let { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, it), "Partager")) } }
            "toggle" -> {
                prefs.edit().putBoolean(b, !prefs.getBoolean(b, b == "adblock")).apply()
                val on = prefs.getBoolean(b, false)
                when (b) {
                    "desktop" -> tabs.forEach { it.web.settings.userAgentString = if (on) DESKTOP_UA else MOBILE_UA; it.web.reload() }
                    "dark" -> tabs.forEach { applyDark(it.web) }
                    "autotr" -> if (on) cur?.web?.let { autoTranslate(it, it.url ?: "") }
                }
                val names = mapOf("adblock" to "Anti-pub", "autotr" to "Traduction auto", "desktop" to "Mode PC", "dark" to "Mode sombre")
                toast("${names[b]} : " + if (on) "activé" else "désactivé")
                panel.evaluateJavascript("refresh();drawMenu()", null)
            }
            "private" -> { closePanel(); newTab(HOME, true); toast("🕶 Onglet privé : rien n'est enregistré") }
            "newtab" -> { closePanel(); newTab(HOME, false) }
            "switch" -> { tabs.find { it.id.toString() == b }?.let { select(it) }; closePanel() }
            "closetab" -> { tabs.find { it.id.toString() == b }?.let { closeTab(it) }; panel.evaluateJavascript("refresh();drawTabs()", null) }
            "closeall" -> { tabs.toList().forEach { tabs.remove(it); it.web.destroy() }; newTab(HOME, false); closePanel() }
            "open" -> { closePanel(); w?.loadUrl(b) }
            "voice" -> { closePanel(); startVoice() }
            "ip" -> { closePanel(); w?.loadUrl("https://ipinfo.io/json") }
            "clear" -> {
                CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData()
                tabs.forEach { it.web.clearCache(true); it.web.clearHistory() }
                prefs.edit().putString("history", "[]").apply(); toast("🧹 Cookies, cache et historique effacés"); closePanel()
            }
            "newchat" -> while (chatHistory.length() > 0) chatHistory.remove(0)
            "vpn" -> setVpn(b)
            "tor_install" -> openStore("org.torproject.android")
            "tor_start" -> startOrbot()
            "proton" -> openStore("ch.protonvpn.android")
        }
    }

    // ---------- Modules ----------
    private fun translate() {
        val w = cur?.web ?: return; val u = w.url ?: return
        if (!u.startsWith("http") || u.contains("translate.goog") || u.contains("translate.google")) return
        w.loadUrl(translateUrl(u)); toast("🌍 Traduction de la page…")
    }
    private fun translateUrl(u: String) = "https://translate.google.com/translate?sl=auto&tl=${prefs.getString("lang", "fr")}&u=" + URLEncoder.encode(u, "UTF-8")

    private fun autoTranslate(w: WebView, u: String) {
        if (!u.startsWith("http") || u.contains("translate.goog") || u.contains("translate.google") || u.contains("google.com/search")) return
        val lang = prefs.getString("lang", "fr")!!
        w.evaluateJavascript("(document.documentElement.lang||'').slice(0,2).toLowerCase()") { r ->
            val l = r.trim('"')
            if (l.isNotEmpty() && l != lang && w.url == u) w.loadUrl(translateUrl(u))
        }
    }

    private fun speakPage() {
        val w = cur?.web ?: return
        if (tts?.isSpeaking == true) { tts?.stop(); toast("Lecture arrêtée"); return }
        w.evaluateJavascript("[(document.documentElement.lang||'fr').slice(0,2), (document.querySelector('article,main')||document.body).innerText.slice(0,20000)]") { r ->
            val a = runCatching { JSONArray(r) }.getOrNull() ?: return@evaluateJavascript
            tts?.language = Locale(a.getString(0))
            speak(a.getString(1)); toast("🔊 Lecture… (appuie encore sur Écouter pour arrêter)")
        }
    }
    private fun speak(text: String) {
        text.chunked(3500).forEachIndexed { i, c -> tts?.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "o$i") }
    }

    private fun findInPage() {
        val w = cur?.web ?: return
        val input = EditText(this).apply { hint = "Mot à trouver"; setSingleLine() }
        val d = AlertDialog.Builder(this).setTitle("🔍 Chercher dans la page").setView(input)
            .setPositiveButton("Suivant", null).setNegativeButton("Fermer") { _, _ -> w.clearMatches() }.create()
        w.setFindListener { ord, n, done -> if (done) d.setTitle(if (n == 0) "🔍 Aucun résultat" else "🔍 ${ord + 1} / $n") }
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (d.window != null && input.tag == input.text.toString()) w.findNext(true)
                else { input.tag = input.text.toString(); w.findAllAsync(input.text.toString()) }
            }
        }
        d.show()
    }

    private fun multiSearch() {
        val w = cur?.web ?: return
        val q = url.text.toString().ifBlank { w.title ?: "" }
        val opts = arrayOf("🔎 Google", "🇺🇸 Google USA", "✅ Sources fiables", "🔬 Science", "📰 Actu US", "💬 Avis Reddit", "▶ YouTube", "🕶 DuckDuckGo", "✦ Orion IA")
        val modes = arrayOf("web", "us", "fiable", "science", "actu", "reddit", "video", "prive", "ai")
        val input = EditText(this).apply { setText(q); setSingleLine() }
        AlertDialog.Builder(this).setTitle("🔀 Relancer la recherche avec…").setView(input)
            .setItems(opts) { _, i ->
                val s = searchUrl(modes[i], input.text.toString())
                if (s == null) openPanel("ai", "", input.text.toString()) else newTab(s, cur?.private == true)
            }.show()
    }

    private fun startVoice() {
        runCatching {
            voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR").putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle, Orion t'écoute…"))
        }.onFailure { toast("Reconnaissance vocale indisponible") }
    }

    private fun applyDark(w: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(w.settings, prefs.getBoolean("dark", false))
    }

    private fun addHistory(title: String, u: String) {
        val a = JSONArray(prefs.getString("history", "[]"))
        if (a.length() > 0 && a.getJSONObject(0).optString("url") == u) return
        val n = JSONArray().put(JSONObject().put("title", title).put("url", u))
        for (i in 0 until minOf(a.length(), 199)) n.put(a.get(i))
        prefs.edit().putString("history", n.toString()).apply()
    }
    private fun addBookmark() {
        val w = cur?.web ?: return; val u = w.url ?: return
        if (!u.startsWith("http")) return
        val a = JSONArray(prefs.getString("bookmarks", "[]"))
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("url") == u) return
        val n = JSONArray().put(JSONObject().put("title", w.title).put("url", u))
        for (i in 0 until a.length()) n.put(a.get(i))
        prefs.edit().putString("bookmarks", n.toString()).apply()
    }

    // ---------- VPN ----------
    private fun setVpn(v: String) {
        when {
            v == "off" -> prefs.edit().putString("vpn", "off").apply()
            v == "tor" -> prefs.edit().putString("vpn", "tor").apply()
            v.startsWith("custom:") -> {
                val p = v.removePrefix("custom:").trim()
                if (p.isEmpty()) { toast("Entre une adresse de proxy"); return }
                prefs.edit().putString("vpn", "custom").putString("proxy", p).apply()
            }
        }
        applyVpn()
        panel.evaluateJavascript("refresh();drawVpn()", null)
        toast(when (prefs.getString("vpn", "off")) {
            "tor" -> "🧅 Tor activé (Orbot doit être démarré)"
            "custom" -> "🛡 Proxy activé"
            else -> "VPN Orion désactivé"
        })
        cur?.web?.reload()
    }

    private fun applyVpn() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        val pc = ProxyController.getInstance()
        val rule = when (prefs.getString("vpn", "off")) {
            "tor" -> "socks://127.0.0.1:9050"
            "custom" -> prefs.getString("proxy", "")!!.replace("socks5://", "socks://")
            else -> ""
        }
        if (rule.isNotEmpty()) pc.setProxyOverride(ProxyConfig.Builder().addProxyRule(rule).build(), { it.run() }) {}
        else pc.clearProxyOverride({ it.run() }) {}
    }

    private fun startOrbot() {
        val i = Intent("org.torproject.android.intent.action.START").setPackage("org.torproject.android")
            .putExtra("org.torproject.android.intent.extra.PACKAGE_NAME", packageName)
        runCatching { sendBroadcast(i) }
        val launch = packageManager.getLaunchIntentForPackage("org.torproject.android")
        if (launch != null) startActivity(launch) else openStore("org.torproject.android")
    }

    private fun openStore(pkg: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) }
            .onFailure { cur?.web?.loadUrl("https://play.google.com/store/apps/details?id=$pkg"); closePanel() }
    }

    // ---------- IA ----------
    private val system = """Tu es Orion, l'IA intégrée d'un navigateur. Réponds en français, de façon claire, structurée et concise (lecture sur téléphone : paragraphes courts, listes, **gras** pour l'essentiel).
Règles :
- Utilise la recherche web dès que la question porte sur des faits, l'actualité, des chiffres ou des tendances.
- Privilégie les sources fiables : institutions, publications scientifiques, grands médias, sites officiels. Quand c'est pertinent, va chercher les sources américaines (souvent en avance) et traduis l'essentiel.
- Cite tes sources avec leurs URL complètes. Signale clairement ce qui est incertain, contesté ou peu fiable.
- Si on te fournit le texte d'une page ouverte, sers-t'en comme contexte principal."""

    private fun askAi(q: String, usePage: Boolean) {
        val key = prefs.getString("key", "") ?: ""
        val w = cur?.web
        val pageUrl = w?.url ?: ""
        val send = { page: String ->
            val c = if (usePage && page.isNotBlank() && pageUrl.startsWith("http")) "Page ouverte : $pageUrl\n---\n$page\n---\nQuestion : $q" else q
            chatHistory.put(JSONObject().put("role", "user").put("content", c))
            io.execute {
                val r = runCatching { if (prefs.getString("provider", "gemini") == "claude") callClaude(key) else callGemini(prefs.getString("gkey", "")!!) }
                runOnUiThread {
                    val out = JSONObject()
                    r.onSuccess { (text, src) ->
                        chatHistory.put(JSONObject().put("role", "assistant").put("content", text))
                        out.put("text", text).put("sources", JSONArray(src))
                    }.onFailure {
                        chatHistory.remove(chatHistory.length() - 1)
                        out.put("error", it.message ?: it.javaClass.simpleName)
                    }
                    panel.evaluateJavascript("onAnswer($out)", null)
                }
            }
        }
        if (usePage && w != null && pageUrl.startsWith("http"))
            w.evaluateJavascript("(document.body?document.body.innerText:'').slice(0,12000)") { raw ->
                send(runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault(""))
            }
        else send("")
    }

    private fun callGemini(key: String): Pair<String, List<String>> {
        if (key.isEmpty()) throw Exception("Ajoute ta clé Gemini gratuite dans ☰ → Réglages")
        val contents = JSONArray(); val start = maxOf(0, chatHistory.length() - 10)
        for (i in start until chatHistory.length()) {
            val m = chatHistory.getJSONObject(i)
            contents.put(JSONObject().put("role", if (m.getString("role") == "assistant") "model" else "user")
                .put("parts", JSONArray().put(JSONObject().put("text", m.getString("content")))))
        }
        val body = JSONObject().put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val model = prefs.getString("gmodel", "gemini-flash-latest")
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 180000
        c.setRequestProperty("x-goog-api-key", key)
        c.setRequestProperty("content-type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = c.responseCode in 200..299
        val txt = (if (ok) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        val json = JSONObject(txt)
        if (!ok) throw Exception(json.optJSONObject("error")?.optString("message") ?: "HTTP ${c.responseCode}")
        val cand = json.optJSONArray("candidates")?.optJSONObject(0) ?: throw Exception("Pas de réponse")
        val sb = StringBuilder()
        cand.optJSONObject("content")?.optJSONArray("parts")?.let { for (i in 0 until it.length()) sb.append(it.getJSONObject(i).optString("text")) }
        val cites = linkedSetOf<String>()
        cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")?.let {
            for (i in 0 until it.length()) it.getJSONObject(i).optJSONObject("web")?.let { w ->
                val u = w.optString("uri"); val t = w.optString("title")
                if (u.isNotEmpty()) cites.add(if (t.isNotEmpty()) "$t : $u" else u)
            }
        }
        return sb.toString().trim() to cites.toList()
    }

    private fun callClaude(key: String): Pair<String, List<String>> {
        val msgs = JSONArray(); val start = maxOf(0, chatHistory.length() - 10)
        for (i in start until chatHistory.length()) msgs.put(chatHistory.get(i))
        while (msgs.length() > 0 && msgs.getJSONObject(0).getString("role") != "user") msgs.remove(0)
        val body = JSONObject()
            .put("model", prefs.getString("model", "claude-opus-5-5"))
            .put("max_tokens", 3000).put("system", system).put("messages", msgs)
            .put("tools", JSONArray().put(JSONObject().put("type", "web_search_20250305").put("name", "web_search").put("max_uses", 6)))
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 180000
        c.setRequestProperty("x-api-key", key)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.setRequestProperty("content-type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = c.responseCode in 200..299
        val txt = (if (ok) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        val json = JSONObject(txt)
        if (!ok) throw Exception(json.optJSONObject("error")?.optString("message") ?: "HTTP ${c.responseCode}")
        val content = json.getJSONArray("content")
        val sb = StringBuilder(); val cites = linkedSetOf<String>()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            if (b.optString("type") == "text") {
                sb.append(b.optString("text"))
                b.optJSONArray("citations")?.let { a -> for (j in 0 until a.length()) a.getJSONObject(j).optString("url").takeIf { it.isNotEmpty() }?.let(cites::add) }
            }
        }
        return sb.toString().trim() to cites.toList()
    }

    private fun hideKb() = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(url.windowToken, 0)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        // Mode lecture : garde le texte principal, typographie propre, thème sombre
        private const val READER_JS = """(function(){
var c=document.querySelector('article')||document.querySelector('main');
if(!c){var best=null,score=0;document.querySelectorAll('div,section').forEach(function(d){var s=0;d.querySelectorAll(':scope>p').forEach(function(p){s+=p.innerText.length});if(s>score){score=s;best=d}});c=best||document.body}
var t=document.title,h=c.cloneNode(true);h.querySelectorAll('script,style,nav,aside,footer,form,iframe,button,svg,[class*=ad],[id*=ad]').forEach(function(e){e.remove()});
document.head.innerHTML='<meta name=viewport content="width=device-width,initial-scale=1"><style>body{background:#0b0d1e;color:#e6e9ff;font:19px/1.7 Georgia,serif;max-width:720px;margin:0 auto;padding:28px 20px 80px}h1{font:700 26px/1.3 system-ui;color:#5ef0ff}a{color:#a66bff}img{max-width:100%;height:auto;border-radius:12px}p{margin:0 0 1.1em}</style>';
document.body.innerHTML='<h1>'+t+'</h1>'+h.innerHTML;window.scrollTo(0,0)})()"""
    }
}
