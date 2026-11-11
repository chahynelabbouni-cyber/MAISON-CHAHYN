package com.chahyn.boost

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

// ======================================================================
// Premier lancement : on fait connaissance
// ======================================================================

@Composable
fun OnboardingScreen() {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var prenom by remember { mutableStateOf("") }
    var nom by remember { mutableStateOf("") }
    var surnom by remember { mutableStateOf("") }
    val tick = AppState.resumeTick.intValue

    ScreenColumn(if (step == 0) "Bienvenue 👋" else if (step == 1) "On fait connaissance" else "Dernière étape") {
        when (step) {
            0 -> {
                Spacer(Modifier.height(30.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("⚡", fontSize = 90.sp) }
                Text("Boost Chahyn", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                BodyText("Ton assistant qui transforme ton téléphone en machine : un seul bouton pour tout optimiser, des modes pour chaque moment, et un agent à qui tu parles normalement.")
                PrimaryButton("C'est parti", Modifier.fillMaxWidth()) { step = 1 }
            }
            1 -> {
                BodyText("Comment tu t'appelles ? L'appli sera personnalisée pour toi.")
                Field(prenom, "Prénom") { prenom = it }
                Field(nom, "Nom") { nom = it }
                Field(surnom, "Comment je t'appelle ? (facultatif)") { surnom = it }
                PrimaryButton("Continuer", Modifier.fillMaxWidth(), enabled = prenom.isNotBlank()) { step = 2 }
            }
            else -> {
                BodyText("Enchanté ${surnom.ifBlank { prenom }} ! Pour avoir accès à tout le téléphone, active ces 4 autorisations (une seule fois). Appuie sur chacune, active l'interrupteur, puis reviens.")
                PermList(tick)
                PrimaryButton("Terminer 🚀", Modifier.fillMaxWidth()) { Profile.save(ctx, prenom, nom, surnom) }
                Text("Tu pourras les activer plus tard depuis l'accueil.", color = TextDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PermList(tick: Int) {
    val ctx = LocalContext.current
    BoostTools.perms.forEach { p ->
        val ok = remember(tick) { p.ok(ctx) }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (ok) IosGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.07f))
                .clickable(enabled = !ok) { p.ask(ctx) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (ok) "✅" else "🔑", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(p.why, color = TextDim, fontSize = 12.sp)
            }
            if (!ok) Text("Activer ›", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ======================================================================
// Accueil : le gros bouton
// ======================================================================

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = AppState.resumeTick.intValue
    var refresh by remember { mutableIntStateOf(0) }
    val h = remember(tick, refresh) { BoostTools.health(ctx) }
    var running by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf("") }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var editing by remember { mutableStateOf(false) }
    val missing = remember(tick) { BoostTools.perms.count { !it.ok(ctx) } }
    val secure = remember(tick) { SystemTools.hasSecureSettings(ctx) }

    ScreenColumn(Profile.salut(), "Prêt à booster ton téléphone ?") {
        // Note de santé
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { h.score / 100f }, modifier = Modifier.size(96.dp), strokeWidth = 9.dp,
                    color = if (h.score >= 75) IosGreen else if (h.score >= 50) IosOrange else IosPink,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
                Text("${h.score}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Santé du téléphone", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("💾 ${BoostTools.fmt(h.storageTotal - h.storageUsed)} libres", color = TextDim, fontSize = 13.sp)
                Text("🧠 RAM ${(h.ramUsed * 100 / h.ramTotal.coerceAtLeast(1))} % utilisée", color = TextDim, fontSize = 13.sp)
                Text("🔋 ${h.batteryPct} %  ·  🌡️ ${h.batteryTempC} °C", color = TextDim, fontSize = 13.sp)
            }
        }

        // LE bouton
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF2D55), Color(0xFF7B2BFF))))
                .clickable(enabled = !running) {
                    running = true; log = emptyList()
                    scope.launch {
                        val res = withContext(Dispatchers.IO) { BoostTools.optimizeAll(ctx) { step = it } }
                        log = res; running = false; refresh++
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (running) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Text(step, color = Color.White, fontSize = 12.sp, maxLines = 1)
            } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚡ TOUT OPTIMISER", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("RAM + cache caché + fichiers inutiles, en 1 clic", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
        LogBox(log)

        // Modes rapides
        Text("Modes rapides", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("jeu", "travail", "film", "batterie", "sommeil", "normal").forEach { id ->
                val m = BoostTools.modes.first { it.id == id }
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { log = BoostTools.applyMode(ctx, id); refresh++ }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(m.emoji, fontSize = 26.sp)
                    Text(m.title, color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Parler à l'agent
        GlassCard(title = "Demande à ton agent 🤖") {
            BodyText("Dis-lui simplement ce que tu veux : « fais de la place », « je vais jouer », « supprime les doublons »…")
            SecondaryButton("Parler à l'agent", Modifier.fillMaxWidth()) { AppState.tab.value = "agent" }
        }

        // Autorisations
        GlassCard(title = if (missing == 0) "Accès complet ✅" else "Débloque tout ($missing à activer)") {
            PermList(tick)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (secure) IosGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.07f))
                    .clickable(enabled = !secure) {
                        val cmd = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"
                        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("adb", cmd))
                        ctx.toast("Commande copiée : lance-la une fois depuis un ordinateur")
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (secure) "✅" else "💻", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mode avancé (facultatif)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (secure) "120 Hz forcé et animations débloqués" else "Pour le 120 Hz forcé et les animations : appuie pour copier la commande à lancer une fois depuis un PC", color = TextDim, fontSize = 12.sp)
                }
            }
        }

        // Profil
        GlassCard(title = "Mon profil") {
            if (!editing) {
                Text("${Profile.prenom.value} ${Profile.nom.value}".trim() + if (Profile.surnom.value.isNotBlank()) " · « ${Profile.surnom.value} »" else "", color = Color.White, fontSize = 15.sp)
                Text("📈 ${BoostTools.fmt(BoostTools.totalFreed(ctx))} libérés depuis le début", color = TextDim, fontSize = 13.sp)
                SecondaryButton("Modifier", Modifier.fillMaxWidth()) { editing = true }
            } else {
                var p by remember { mutableStateOf(Profile.prenom.value) }
                var n by remember { mutableStateOf(Profile.nom.value) }
                var s by remember { mutableStateOf(Profile.surnom.value) }
                Field(p, "Prénom") { p = it }
                Field(n, "Nom") { n = it }
                Field(s, "Comment je t'appelle ?") { s = it }
                PrimaryButton("Enregistrer", Modifier.fillMaxWidth(), enabled = p.isNotBlank()) { Profile.save(ctx, p, n, s); editing = false }
            }
        }
    }
}

// ======================================================================
// Agent
// ======================================================================

data class Msg(val me: Boolean, val lines: List<String>, val confirm: List<BoostTools.Junk> = emptyList())

object AgentState {
    val msgs = mutableStateListOf<Msg>()
}

@Composable
fun AgentScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showTasks by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val all = remember { mutableStateOf<List<Task>>(emptyList()) }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        all.value = withContext(Dispatchers.IO) { Agent.allTasks(ctx) }
        if (AgentState.msgs.isEmpty()) AgentState.msgs += Msg(false, listOf(
            "Salut ${Profile.appel} ! Je suis ton agent Boost 🤖",
            "Je connais ${all.value.size} tâches. Dis-moi ce que tu veux, avec tes mots.",
            "Tu peux en demander plusieurs à la fois : « mode jeu et libère la RAM »."
        ))
    }
    LaunchedEffect(AgentState.msgs.size) { if (AgentState.msgs.isNotEmpty()) listState.animateScrollToItem(AgentState.msgs.size - 1) }

    fun send(text: String) {
        if (text.isBlank() || busy) return
        AgentState.msgs += Msg(true, listOf(text))
        busy = true
        scope.launch {
            val rep = withContext(Dispatchers.IO) { Agent.handle(ctx, text) }
            AgentState.msgs += Msg(false, rep.lines, rep.confirm)
            busy = false
            rep.goTab?.let { if (it != "agent") ctx.toast("Détails dans l'onglet ${it.replaceFirstChar { c -> c.uppercase() }}") }
        }
    }

    fun runTask(t: Task) {
        AgentState.msgs += Msg(true, listOf("${t.emoji} ${t.title}"))
        showTasks = false; busy = true
        scope.launch {
            val rep = withContext(Dispatchers.IO) { try { t.run(ctx) } catch (e: Throwable) { Reply(listOf("❌ ${e.message}")) } }
            AgentState.msgs += Msg(false, rep.lines, rep.confirm); busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A0B2E), Bg))).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Agent 🤖", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${all.value.size} tâches disponibles", color = TextDim, fontSize = 13.sp)
            }
            SecondaryButton(if (showTasks) "Fermer" else "Toutes les tâches") { showTasks = !showTasks }
        }

        if (showTasks) {
            Column(Modifier.padding(horizontal = 16.dp)) { Field(query, "Chercher une tâche") { query = it } }
            val q = norm(query)
            val filtered = all.value.filter { q.isBlank() || norm(it.title).contains(q) || norm(it.category).contains(q) }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp, top = 8.dp)) {
                var lastCat = ""
                filtered.forEach { t ->
                    if (t.category != lastCat) {
                        lastCat = t.category
                        val cat = t.category
                        item { Text(cat, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { runTask(t) }.padding(vertical = 9.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(t.emoji, fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(t.title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("▶", color = TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 12.dp), state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AgentState.msgs) { m -> Bubble(m) }
                if (busy) item { Text("…", color = TextDim, fontSize = 22.sp, modifier = Modifier.padding(8.dp)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("⚡ Fais de la place", "🎮 Mode jeu", "💼 Mode travail", "👯 Supprime les doublons", "🧹 Vide le cache caché", "🗂️ Applis inutiles", "🏆 Comment va mon téléphone ?", "↩️ Remets normal").forEach { s ->
                    Box(
                        Modifier.clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape).clickable { send(s.drop(2)) }.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text(s, color = Color.White, fontSize = 13.sp) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 96.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Field(input, "Écris ta demande…") { input = it } }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Accent).clickable { send(input); input = "" },
                    contentAlignment = Alignment.Center
                ) { Text("➤", color = Color.White, fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var handled by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.me) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .fillMaxWidth(if (m.me) 0.8f else 0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (m.me) Accent else Color.White.copy(alpha = 0.10f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            m.lines.forEach { Text(it, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp) }
            if (m.confirm.isNotEmpty() && !handled) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("🗑️ Oui, supprime") {
                        handled = true
                        scope.launch {
                            val freed = withContext(Dispatchers.IO) { BoostTools.delete(m.confirm) }
                            AgentState.msgs += Msg(false, listOf("✅ ${BoostTools.fmt(freed)} libérés"))
                        }
                    }
                    SecondaryButton("Non") { handled = true; ctx.toast("Rien n'a été supprimé") }
                }
            }
        }
    }
}

// ======================================================================
// Modes
// ======================================================================

@Composable
fun ModesScreen() {
    val ctx = LocalContext.current
    val tick = AppState.resumeTick.intValue
    var version by remember { mutableIntStateOf(0) }
    var active by remember(tick) { mutableStateOf(BoostTools.activeMode(ctx)) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    val all = remember(version) { BoostTools.allModes(ctx) }
    val customIds = remember(version) { BoostTools.customModes(ctx).map { it.id }.toSet() }

    ScreenColumn("Modes", "Un appui = tout se règle. Changer de mode remet tout à zéro avant.") {
        if (!creating) PrimaryButton("➕ Créer mon propre mode", Modifier.fillMaxWidth()) { creating = true }
        else ModeEditor(onDone = { m ->
            creating = false
            if (m != null) { BoostTools.addCustom(ctx, m); version++; log = listOf("✨ Mode « ${m.title} » créé") + BoostTools.applyMode(ctx, m.id); active = m.id }
        })
        LogBox(log)
        all.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    val on = active == m.id
                    val custom = m.id in customIds
                    Box(Modifier.weight(1f)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (on) Brush.linearGradient(listOf(Color(0xFFFF2D55), Color(0xFF7B2BFF))) else Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x0FFFFFFF))))
                                .border(if (custom) 1.dp else 0.dp, if (custom) Accent.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(22.dp))
                                .clickable {
                                    log = BoostTools.applyMode(ctx, m.id)
                                    active = BoostTools.activeMode(ctx)
                                }
                                .padding(14.dp)
                        ) {
                            Text(m.emoji, fontSize = 30.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(m.title + if (on) " ✓" else "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(m.desc, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 14.sp, maxLines = 3)
                        }
                        if (custom) Text(
                            "✕", color = Color.White, fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f)).clickable { BoostTools.deleteCustom(ctx, m.id); version++ }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeEditor(onDone: (BoostTools.Mode?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var baseId by remember { mutableStateOf("jeu") }
    val base = BoostTools.modes.first { it.id == baseId }
    var notifs by remember(baseId) { mutableStateOf((base.dnd ?: android.app.NotificationManager.INTERRUPTION_FILTER_ALL) == android.app.NotificationManager.INTERRUPTION_FILTER_ALL) }
    var silent by remember(baseId) { mutableStateOf(base.ringer == android.media.AudioManager.RINGER_MODE_VIBRATE) }
    var ram by remember(baseId) { mutableStateOf(base.ram) }
    var hz by remember(baseId) { mutableStateOf(base.hz120 == true) }
    var bright by remember(baseId) { mutableStateOf((base.bright ?: 60).toFloat()) }

    GlassCard(title = "Nouveau mode") {
        Field(name, "Nom du mode (ex : Jeu avec notifs)") { name = it }
        Text("Basé sur :", color = TextDim, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoostTools.modes.filter { it.id != "normal" }.forEach { m ->
                Box(
                    Modifier.clip(CircleShape).background(if (m.id == baseId) Accent else Color.White.copy(alpha = 0.1f))
                        .clickable { baseId = m.id }.padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("${m.emoji} ${m.title}", color = Color.White, fontSize = 13.sp) }
            }
        }
        EditorSwitch("🔔 Garder les notifications", notifs) { notifs = it }
        EditorSwitch("📳 Silencieux (vibreur)", silent) { silent = it }
        EditorSwitch("🚀 Libérer la RAM", ram) { ram = it }
        EditorSwitch("⚡ Écran 120 Hz", hz) { hz = it }
        Text("☀️ Luminosité : ${bright.toInt()} %", color = Color.White, fontSize = 14.sp)
        androidx.compose.material3.Slider(value = bright, onValueChange = { bright = it }, valueRange = 0f..100f,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Créer et activer", Modifier.weight(1f)) {
                val m0 = base.copy(
                    id = "c_" + System.currentTimeMillis(),
                    title = name.ifBlank { base.title + " perso" },
                    dnd = if (notifs) android.app.NotificationManager.INTERRUPTION_FILTER_ALL else android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    ringer = if (silent) android.media.AudioManager.RINGER_MODE_VIBRATE else android.media.AudioManager.RINGER_MODE_NORMAL,
                    ram = ram, hz120 = hz, bright = bright.toInt(), tip = null,
                )
                val m = m0.copy(desc = BoostTools.describe(m0), keywords = listOf(norm(m0.title)))
                onDone(m)
            }
            SecondaryButton("Annuler") { onDone(null) }
        }
    }
}

@Composable
private fun EditorSwitch(label: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { on(!v) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(v, on, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = IosGreen))
    }
}

// ======================================================================
// Stockage : nettoyage + explorateur + applis
// ======================================================================

@Composable
fun StorageScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = AppState.resumeTick.intValue
    val allFiles = remember(tick) { BoostTools.hasAllFiles() }
    val usage = remember(tick) { BoostTools.hasUsageAccess(ctx) }
    var section by remember { mutableStateOf("nettoyage") }

    ScreenColumn("Stockage", "Accès à tout ton stockage, de A à Z.") {
        if (!allFiles) GlassCard(title = "🔑 Accès complet nécessaire") {
            BodyText("Active « Accès à tous les fichiers » pour que Boost voie et nettoie tout le téléphone.")
            PrimaryButton("Activer", Modifier.fillMaxWidth()) { BoostTools.requestAllFiles(ctx) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("nettoyage" to "🧹 Nettoyer", "explorer" to "📂 Explorer", "applis" to "📱 Applis").forEach { (id, l) ->
                Box(
                    Modifier.weight(1f).clip(CircleShape)
                        .background(if (section == id) Accent else Color.White.copy(alpha = 0.10f))
                        .clickable { section = id }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(l, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        when (section) {
            "explorer" -> Explorer()
            "applis" -> UnusedApps(usage)
            else -> Cleaner()
        }
        GlassCard(title = "🧽 Cache de toutes les applis") {
            BodyText("Android protège le cache interne des autres applis (et le dossier Android/data). L'outil officiel Samsung le vide en 1 clic.")
            SecondaryButton("Ouvrir l'Entretien Samsung", Modifier.fillMaxWidth()) { BoostTools.openDeviceCare(ctx) }
        }
    }
}

object CleanState {
    val junk = mutableStateListOf<BoostTools.Junk>()
    val selected = mutableStateListOf<BoostTools.Junk>()
    val scanned = mutableStateOf(false)
}

@Composable
private fun Cleaner() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var path by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<BoostTools.Kind?>(null) }
    var msg by remember { mutableStateOf("") }

    PrimaryButton(if (scanning) "Analyse en cours…" else "🔍 Analyser tout le téléphone", Modifier.fillMaxWidth(), enabled = !scanning) {
        scanning = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { BoostTools.scan(ctx) { path = it } }
            CleanState.junk.clear(); CleanState.junk.addAll(r)
            CleanState.selected.clear(); CleanState.selected.addAll(r.filter { it.kind.safe })
            CleanState.scanned.value = true; scanning = false
        }
    }
    if (scanning) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        Text(path, color = TextDim, fontSize = 11.sp, maxLines = 1)
    }
    if (msg.isNotEmpty()) Text(msg, color = IosGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    if (!CleanState.scanned.value) return

    val byKind = CleanState.junk.groupBy { it.kind }
    if (byKind.isEmpty()) BodyText("✨ Tout est propre !")
    BoostTools.Kind.entries.forEach { k ->
        val items = byKind[k] ?: return@forEach
        val selCount = items.count { it in CleanState.selected }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    selCount == items.size,
                    { on -> if (on) CleanState.selected.addAll(items.filter { it !in CleanState.selected }) else CleanState.selected.removeAll(items) },
                    colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = Color.White.copy(alpha = 0.5f))
                )
                Column(Modifier.weight(1f).clickable { open = if (open == k) null else k }) {
                    Text("${k.emoji} ${k.label}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${items.size} · ${BoostTools.fmt(items.sumOf { it.size })}" + (if (k.safe) " · sans risque" else " · à vérifier") + "  ${if (open == k) "▲" else "▼"}",
                        color = if (k.safe) IosGreen else IosOrange, fontSize = 12.sp
                    )
                }
            }
            if (open == k) items.take(150).forEach { j ->
                val sel = j in CleanState.selected
                Row(
                    Modifier.fillMaxWidth().clickable { if (sel) CleanState.selected.remove(j) else CleanState.selected.add(j) }.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (sel) "☑️" else "⬜", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(j.file.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Text(j.file.parent?.removePrefix(BoostTools.root().absolutePath) ?: "", color = TextDim, fontSize = 10.sp, maxLines = 1)
                    }
                    Text(BoostTools.fmt(j.size), color = TextDim, fontSize = 12.sp)
                }
            }
        }
    }
    val sel = CleanState.selected.toList()
    if (sel.isNotEmpty()) PrimaryButton("🗑️ Supprimer la sélection (${BoostTools.fmt(sel.sumOf { it.size })})", Modifier.fillMaxWidth()) {
        scope.launch {
            val freed = withContext(Dispatchers.IO) { BoostTools.delete(sel) }
            CleanState.junk.removeAll(sel); CleanState.selected.clear()
            msg = "✅ ${BoostTools.fmt(freed)} libérés"
        }
    }
}

@Composable
private fun Explorer() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var dir by remember { mutableStateOf(BoostTools.root()) }
    var entries by remember { mutableStateOf<List<BoostTools.Entry>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(dir, reload) {
        entries = null
        entries = withContext(Dispatchers.IO) { BoostTools.list(dir) }
    }
    GlassCard {
        Text("📂 " + (dir.absolutePath.removePrefix(BoostTools.root().absolutePath).ifEmpty { "Stockage interne" }), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (dir != BoostTools.root()) SecondaryButton("⬆️ Dossier précédent", Modifier.fillMaxWidth()) { dir = dir.parentFile ?: BoostTools.root() }
        val list = entries
        if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        else {
            val total = list.sumOf { it.size }.coerceAtLeast(1)
            if (list.isEmpty()) BodyText("Dossier vide")
            list.take(200).forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = e.isDir) { dir = e.file }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (e.isDir) "📁" else "📄", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.file.name, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        LinearProgressIndicator(
                            progress = { e.size.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = Accent, trackColor = Color.White.copy(alpha = 0.08f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(BoostTools.fmt(e.size), color = TextDim, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("🗑️", fontSize = 16.sp, modifier = Modifier.clickable { toDelete = e.file })
                }
            }
        }
        toDelete?.let { f ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IosPink.copy(alpha = 0.2f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Supprimer définitivement « ${f.name} » ?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Supprimer") {
                        scope.launch {
                            val s = withContext(Dispatchers.IO) { BoostTools.dirSize(f).also { f.deleteRecursively() } }
                            ctx.toast("${BoostTools.fmt(s)} libérés"); toDelete = null; reload++
                        }
                    }
                    SecondaryButton("Annuler") { toDelete = null }
                }
            }
        }
    }
}

@Composable
private fun UnusedApps(usage: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(30) }
    var list by remember { mutableStateOf<List<BoostTools.AppUse>?>(null) }
    val tick = AppState.resumeTick.intValue
    if (!usage) {
        GlassCard {
            BodyText("Active « Données d'utilisation » pour voir les applis qui ne te servent à rien.")
            PrimaryButton("Activer", Modifier.fillMaxWidth()) { BoostTools.requestUsageAccess(ctx) }
        }
        return
    }
    LaunchedEffect(days, tick) { list = null; list = withContext(Dispatchers.IO) { BoostTools.unusedApps(ctx, days) } }
    GlassCard {
        Text("Pas ouvertes depuis :", color = Color.White, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90, 180).forEach { d ->
                Box(
                    Modifier.clip(CircleShape).background(if (days == d) Accent else Color.White.copy(alpha = 0.1f)).clickable { days = d }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("$d j", color = Color.White, fontSize = 13.sp) }
            }
        }
        val l = list
        if (l == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        else if (l.isEmpty()) BodyText("✨ Tu utilises toutes tes applis.")
        else {
            Text("${l.size} applis · ${BoostTools.fmt(l.sumOf { it.size })} récupérables", color = IosOrange, fontSize = 13.sp)
            l.forEach { a ->
                val d = if (a.lastUsed == 0L) null else TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - a.lastUsed)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.label, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        Text((if (d == null) "jamais ouverte" else "il y a $d j") + " · " + BoostTools.fmt(a.size), color = TextDim, fontSize = 12.sp)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(IosPink.copy(alpha = 0.3f)).clickable { SystemTools.uninstall(ctx, a.pkg) }.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("Supprimer", color = Color.White, fontSize = 12.sp) }
                }
            }
        }
    }
}
