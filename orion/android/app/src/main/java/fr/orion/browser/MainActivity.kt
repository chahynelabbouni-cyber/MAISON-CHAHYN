package fr.orion.browser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var url: EditText
    private lateinit var trust: TextView
    private lateinit var vpn: TextView
    private lateinit var ai: View
    private lateinit var chat: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var progress: ProgressBar
    private val prefs by lazy { getSharedPreferences("orion", Context.MODE_PRIVATE) }
    private val history = JSONArray()
    private val io = Executors.newSingleThreadExecutor()

    private val engines = linkedMapOf(
        "Google 🇺🇸" to "https://www.google.com/search?gl=us&hl=en&pws=0&q=",
        "DuckDuckGo 🇺🇸" to "https://duckduckgo.com/?kl=us-en&q=",
        "Brave" to "https://search.brave.com/search?q=",
        "Google Scholar" to "https://scholar.google.com/scholar?hl=en&q=",
        "Wikipedia EN" to "https://en.wikipedia.org/w/index.php?search="
    )
    private val home get() = engines.values.first().substringBefore("search")

    private val trusted = Regex("""(\.gov|\.edu|\.gouv\.fr|\.europa\.eu|who\.int|nature\.com|science\.org|sciencedirect\.com|arxiv\.org|pubmed\.ncbi\.nlm\.nih\.gov|reuters\.com|apnews\.com|bbc\.com|bbc\.co\.uk|lemonde\.fr|nytimes\.com|wsj\.com|ft\.com|economist\.com|wikipedia\.org|insee\.fr|ieee\.org)$""")
    private val dubious = Regex("""(blogspot\.com|wordpress\.com|medium\.com|substack\.com|tiktok\.com|x\.com|twitter\.com|facebook\.com)$""")

    private val system = """Tu es l'assistant intégré du navigateur Orion. Réponds en français, clairement et de façon concise (lecture sur téléphone).
Privilégie les sources fiables : institutions, publications scientifiques, grands médias, sites officiels, notamment américains quand c'est pertinent.
Cite toujours tes sources avec leurs URL complètes. Signale quand une info est incertaine, contestée ou peu fiable.
Tu reçois le texte de la page ouverte comme contexte ; utilise la recherche web pour vérifier ou compléter."""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        web = findViewById(R.id.web); url = findViewById(R.id.url); trust = findViewById(R.id.trust)
        vpn = findViewById(R.id.vpn); ai = findViewById(R.id.ai); chat = findViewById(R.id.chat)
        chatScroll = findViewById(R.id.chatScroll); progress = findViewById(R.id.progress)

        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            loadWithOverviewMode = true; useWideViewPort = true
            builtInZoomControls = true; displayZoomControls = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.setBackgroundColor(Color.parseColor("#05060F"))
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val s = r.url.scheme ?: return false
                if (s == "http" || s == "https") return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, r.url)) }
                return true
            }
            override fun onPageStarted(v: WebView, u: String, f: android.graphics.Bitmap?) = sync(u)
            override fun doUpdateVisitedHistory(v: WebView, u: String, reload: Boolean) = sync(u)
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                progress.progress = p; progress.visibility = if (p < 100) View.VISIBLE else View.GONE
            }
        }
        web.setDownloadListener { u, _, _, _, _ -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } }

        url.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_DONE) { go(url.text.toString()); true } else false
        }
        findViewById<View>(R.id.menu).setOnClickListener { showMenu() }
        findViewById<View>(R.id.back).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<View>(R.id.back).setOnLongClickListener { if (web.canGoForward()) web.goForward(); true }
        findViewById<View>(R.id.home).setOnClickListener { web.loadUrl(home) }
        findViewById<View>(R.id.translate).setOnClickListener { translate() }
        vpn.setOnClickListener { toggleVpn() }
        findViewById<View>(R.id.aiBtn).setOnClickListener { ai.visibility = if (ai.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        findViewById<View>(R.id.aiClose).setOnClickListener { ai.visibility = View.GONE }
        val q = findViewById<EditText>(R.id.q)
        findViewById<View>(R.id.send).setOnClickListener {
            val t = q.text.toString().trim(); if (t.isNotEmpty()) { q.setText(""); hideKb(q); ask(t) }
        }
        val quick = findViewById<LinearLayout>(R.id.quick)
        listOf(
            "Résumer" to "Résume cette page en français en 5 points.",
            "Vérifier" to "Cette page est-elle fiable ? Vérifie avec d'autres sources reconnues.",
            "Veille 🇺🇸" to "Quelles sont les idées et innovations américaines récentes sur ce sujet ? Donne les sources.",
            "Expliquer simplement" to "Explique le contenu de cette page simplement, comme à un débutant."
        ).forEach { (label, prompt) ->
            quick.addView(TextView(this).apply {
                text = label; setTextColor(getColor(R.color.txt)); textSize = 13f
                setBackgroundResource(R.drawable.pill); setPadding(28, 14, 28, 14)
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(6, 6, 6, 6) }
                setOnClickListener { ask(prompt) }
            })
        }

        applyVpn()
        if (prefs.getString("key", "").isNullOrEmpty())
            addMsg(false, "Bienvenue ! Pour activer l'IA, ajoute ta clé API Claude via ⋮ → Réglages.")
        web.loadUrl(intent?.dataString ?: home)
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); i.dataString?.let { web.loadUrl(it) } }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            ai.visibility == View.VISIBLE -> ai.visibility = View.GONE
            web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    // ---------- Navigation ----------
    private fun engine() = engines.values.elementAt(prefs.getInt("engine", 0).coerceIn(0, engines.size - 1))

    private fun go(input: String) {
        val v = input.trim(); if (v.isEmpty()) return
        hideKb(url); url.clearFocus()
        val isUrl = v.startsWith("http://") || v.startsWith("https://") || Regex("""^[\w-]+(\.[\w-]+)+(/.*)?$""").matches(v)
        web.loadUrl(if (isUrl) (if (v.startsWith("http")) v else "https://$v") else engine() + URLEncoder.encode(v, "UTF-8"))
    }

    private fun sync(u: String) {
        if (!url.hasFocus()) url.setText(u)
        val h = runCatching { Uri.parse(u).host ?: "" }.getOrDefault("")
        trust.setTextColor(Color.parseColor(when {
            trusted.containsMatchIn(h) -> "#3DFF9A"
            dubious.containsMatchIn(h) -> "#FF5470"
            h.isEmpty() -> "#8A90B8"
            else -> "#FFC04D"
        }))
    }

    private fun translate() {
        val u = web.url ?: return
        if (!u.startsWith("http") || u.contains("translate.goog") || u.contains("translate.google")) return
        val lang = prefs.getString("lang", "fr") ?: "fr"
        web.loadUrl("https://translate.google.com/translate?sl=auto&tl=$lang&u=" + URLEncoder.encode(u, "UTF-8"))
        toast("Traduction de la page…")
    }

    // ---------- VPN / proxy ----------
    private fun applyVpn() {
        val proxy = prefs.getString("proxy", "") ?: ""
        val on = prefs.getBoolean("vpn", false) && proxy.isNotEmpty()
        vpn.setTextColor(Color.parseColor(if (on) "#3DFF9A" else "#8A90B8"))
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        val pc = ProxyController.getInstance()
        if (on) pc.setProxyOverride(ProxyConfig.Builder().addProxyRule(proxy).build(), { it.run() }) {}
        else pc.clearProxyOverride({ it.run() }) {}
    }

    private fun toggleVpn() {
        val proxy = prefs.getString("proxy", "") ?: ""
        if (proxy.isEmpty()) {
            AlertDialog.Builder(this).setTitle("VPN")
                .setMessage("Deux possibilités :\n\n1) Le plus simple : installe une appli VPN (Proton VPN, Mullvad…) et choisis « États-Unis ». Tout le téléphone, Orion compris, passe par les USA.\n\n2) Faire passer seulement Orion par un serveur : entre l'adresse proxy fournie par ton VPN dans Réglages (ex. socks5://hôte:1080).")
                .setPositiveButton("Réglages") { _, _ -> settings() }
                .setNeutralButton("Tester mon IP") { _, _ -> checkIp() }
                .setNegativeButton("Fermer", null).show()
            return
        }
        prefs.edit().putBoolean("vpn", !prefs.getBoolean("vpn", false)).apply()
        applyVpn(); web.postDelayed({ web.reload() }, 300)
        toast(if (prefs.getBoolean("vpn", false)) "VPN activé" else "VPN désactivé")
    }

    private fun checkIp() = web.loadUrl("https://ipinfo.io/json")

    // ---------- Menu & réglages ----------
    private fun showMenu() {
        val items = arrayOf("Moteur : " + engines.keys.elementAt(prefs.getInt("engine", 0)), "Recharger", "Page suivante",
            "Tester mon IP / pays", "Ouvrir dans un autre navigateur", "Réglages")
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> AlertDialog.Builder(this).setTitle("Moteur de recherche")
                    .setSingleChoiceItems(engines.keys.toTypedArray(), prefs.getInt("engine", 0)) { d, w ->
                        prefs.edit().putInt("engine", w).apply(); d.dismiss() }.show()
                1 -> web.reload()
                2 -> if (web.canGoForward()) web.goForward()
                3 -> checkIp()
                4 -> web.url?.let { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
                5 -> settings()
            }
        }.show()
    }

    private fun settings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }
        fun field(hint: String, value: String, pwd: Boolean = false) = EditText(this).apply {
            this.hint = hint; setText(value); textSize = 14f
            if (pwd) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            box.addView(TextView(this@MainActivity).apply { text = hint; textSize = 12f; setPadding(0, 18, 0, 0) })
            box.addView(this)
        }
        val key = field("Clé API Claude (console.anthropic.com)", prefs.getString("key", "")!!, true)
        val model = field("Modèle IA", prefs.getString("model", "claude-opus-5-5")!!)
        val proxy = field("Proxy / VPN (ex. socks5://hôte:1080)", prefs.getString("proxy", "")!!)
        val lang = field("Langue de traduction (fr, en, es…)", prefs.getString("lang", "fr")!!)
        AlertDialog.Builder(this).setTitle("Réglages").setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Enregistrer") { _, _ ->
                val p = proxy.text.toString().trim()
                prefs.edit().putString("key", key.text.toString().trim())
                    .putString("model", model.text.toString().trim().ifEmpty { "claude-opus-5-5" })
                    .putString("proxy", p).putBoolean("vpn", p.isNotEmpty())
                    .putString("lang", lang.text.toString().trim().ifEmpty { "fr" }).apply()
                applyVpn()
                if (p.isNotEmpty() && !WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))
                    toast("Proxy non supporté par ce WebView : mets à jour « Android System WebView »")
            }.setNegativeButton("Annuler", null).show()
    }

    // ---------- Assistant IA ----------
    private fun addMsg(user: Boolean, text: String): TextView {
        val tv = TextView(this).apply {
            setText(SpannableString(text)); textSize = 14f; setTextIsSelectable(true)
            setTextColor(getColor(R.color.txt)); setLinkTextColor(getColor(R.color.neon))
            setPadding(28, 20, 28, 20)
            setBackgroundColor(Color.parseColor(if (user) "#2A5EF0FF" else "#14FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(if (user) 80 else 0, 10, if (user) 0 else 40, 10) }
        }
        Linkify.addLinks(tv, Linkify.WEB_URLS)
        tv.movementMethod = LinkMovementMethod.getInstance()
        chat.addView(tv); chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        return tv
    }

    private fun ask(question: String) {
        val key = prefs.getString("key", "") ?: ""
        if (key.isEmpty()) { addMsg(false, "Ajoute ta clé API Claude dans ⋮ → Réglages."); return }
        addMsg(true, question)
        val wait = addMsg(false, "✦ réflexion…")
        val pageUrl = web.url ?: ""
        web.evaluateJavascript("(document.body ? document.body.innerText : '').slice(0, 12000)") { raw ->
            val page = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("")
            history.put(JSONObject().put("role", "user").put("content", "Page ouverte : $pageUrl\n---\n$page\n---\nQuestion : $question"))
            io.execute {
                val result = runCatching { callClaude(key) }
                runOnUiThread {
                    chat.removeView(wait)
                    result.onSuccess { (text, cites) ->
                        history.put(JSONObject().put("role", "assistant").put("content", text))
                        addMsg(false, text + if (cites.isNotEmpty()) "\n\nSources :\n" + cites.joinToString("\n") else "")
                    }.onFailure {
                        history.remove(history.length() - 1)
                        addMsg(false, "Erreur : " + (it.message ?: it.javaClass.simpleName))
                    }
                }
            }
        }
    }

    private fun callClaude(key: String): Pair<String, List<String>> {
        val msgs = JSONArray(); val start = maxOf(0, history.length() - 10)
        for (i in start until history.length()) msgs.put(history.get(i))
        if (msgs.length() > 0 && msgs.getJSONObject(0).getString("role") != "user") msgs.remove(0)
        val body = JSONObject()
            .put("model", prefs.getString("model", "claude-opus-5-5"))
            .put("max_tokens", 2048).put("system", system).put("messages", msgs)
            .put("tools", JSONArray().put(JSONObject().put("type", "web_search_20250305").put("name", "web_search").put("max_uses", 5)))
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 120000
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

    private fun hideKb(v: View) = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
