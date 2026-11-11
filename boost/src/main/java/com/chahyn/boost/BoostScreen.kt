package com.chahyn.boost

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chahyn.boost.BoostTools.Command
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun BoostScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = AppState.resumeTick.intValue
    var refresh by remember { mutableIntStateOf(0) }

    val health = remember(tick, refresh) { BoostTools.health(ctx) }
    val canWrite = remember(tick) { SystemTools.canWriteSettings(ctx) }
    val secure = remember(tick) { SystemTools.hasSecureSettings(ctx) }
    val allFiles = remember(tick) { BoostTools.hasAllFiles() }
    val usage = remember(tick) { BoostTools.hasUsageAccess(ctx) }
    val dnd = remember(tick) { BoostTools.hasDndAccess(ctx) }
    var active by remember(tick) { mutableStateOf(BoostTools.activeMode(ctx)) }

    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var command by remember { mutableStateOf("") }

    // Nettoyage
    var scanning by remember { mutableStateOf(false) }
    var scanPath by remember { mutableStateOf("") }
    val junk = remember { mutableStateListOf<BoostTools.Junk>() }
    val selectedKinds = remember { mutableStateListOf<BoostTools.Kind>() }
    var scanned by remember { mutableStateOf(false) }

    // Applis inutilisées
    var unused by remember { mutableStateOf<List<BoostTools.AppUse>?>(null) }

    fun runMode(id: String) {
        log = BoostTools.applyMode(ctx, id)
        active = BoostTools.activeMode(ctx)
        refresh++
    }

    fun startScan() {
        if (scanning) return
        scanning = true; scanned = false
        scope.launch {
            val r = withContext(Dispatchers.IO) { BoostTools.scan(ctx) { p -> scanPath = p } }
            junk.clear(); junk.addAll(r)
            selectedKinds.clear(); selectedKinds.addAll(BoostTools.Kind.entries.filter { it.preChecked })
            scanning = false; scanned = true
        }
    }

    fun loadUnused() {
        scope.launch { unused = withContext(Dispatchers.IO) { BoostTools.unusedApps(ctx) } }
    }

    fun execute(text: String) {
        when (val c = BoostTools.parse(text)) {
            is Command.ApplyMode -> runMode(c.mode.id)
            Command.Clean -> { log = listOf("🔍 Analyse du stockage… vérifie la liste puis appuie sur Supprimer"); startScan() }
            Command.Ram -> { log = listOf("✅ RAM libérée (${BoostTools.freeRam(ctx)} applis fermées)"); refresh++ }
            Command.Unused -> { log = listOf("🔍 Recherche des applis que tu n'utilises plus…"); if (usage) loadUnused() else BoostTools.requestUsageAccess(ctx) }
            Command.DeviceCare -> BoostTools.openDeviceCare(ctx)
            Command.Unknown -> log = listOf(
                "🤔 Je n'ai pas compris. Essaie :",
                "« je veux jouer » · « supprime tout ce qui est inutile »",
                "« économise la batterie » · « libère la RAM » · « mode nuit »",
                "« quelles applis je n'utilise pas » · « remets normal »"
            )
        }
    }

    ScreenColumn("Boost Chahyn", "Dis ce que tu veux, Maison s'occupe du reste.") {
        // ---------- Commande ----------
        GlassCard(title = "Que veux-tu faire ?", icon = Icons.Rounded.RocketLaunch, iconColor = IosPink) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                placeholder = { Text("ex : je veux jouer, fais de la place…", color = TextDim) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { execute(command); command = "" }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                    cursorColor = Accent
                )
            )
            PrimaryButton("⚡ Exécuter", Modifier.fillMaxWidth(), enabled = command.isNotBlank()) { execute(command); command = "" }
            if (log.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) { log.forEach { Text(it, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp) } }
            }
        }

        // ---------- Santé ----------
        GlassCard(title = "État du téléphone", icon = Icons.Rounded.Memory, iconColor = IosTeal) {
            Gauge("RAM", health.ramUsed, health.ramTotal)
            Gauge("Stockage", health.storageUsed, health.storageTotal)
            Text(
                "🔋 ${health.batteryPct} %" + (if (health.charging) " ⚡" else "") + "   🌡️ ${health.batteryTempC} °C" +
                    if (health.batteryTempC >= 40f) "  — chaud, laisse-le refroidir" else "",
                color = Color.White, fontSize = 14.sp
            )
            SecondaryButton("🚀 Libérer la RAM maintenant", Modifier.fillMaxWidth()) {
                ctx.toast("${BoostTools.freeRam(ctx)} applis en arrière-plan fermées"); refresh++
            }
        }

        // ---------- Modes ----------
        GlassCard(title = "Modes en 1 clic", icon = Icons.Rounded.SportsEsports, iconColor = IosIndigo) {
            BoostTools.modes.forEach { m ->
                val on = active == m.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                        .clickable { runMode(m.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(m.emoji, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title + if (on) "  • actif" else "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(m.desc, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
            SecondaryButton("🎮 Ouvrir Game Booster Samsung", Modifier.fillMaxWidth()) { BoostTools.openGameBooster(ctx) }
        }

        // ---------- Nettoyage ----------
        GlassCard(title = "Grand nettoyage", icon = Icons.Rounded.CleaningServices, iconColor = IosGreen) {
            if (!allFiles) {
                BodyText("Pour trouver les fichiers cachés inutiles partout dans ton téléphone, autorise « Accès à tous les fichiers ».")
                PrimaryButton("Autoriser l'accès aux fichiers", Modifier.fillMaxWidth()) { BoostTools.requestAllFiles(ctx) }
            }
            PrimaryButton(if (scanning) "Analyse…" else "🔍 Analyser le stockage", Modifier.fillMaxWidth(), enabled = !scanning) { startScan() }
            if (scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = IosGreen)
                Text(scanPath, color = TextDim, fontSize = 11.sp, maxLines = 1)
            }
            if (scanned) {
                val byKind = junk.groupBy { it.kind }
                if (byKind.isEmpty()) BodyText("✨ Rien à nettoyer, ton téléphone est propre.")
                BoostTools.Kind.entries.forEach { k ->
                    val items = byKind[k] ?: return@forEach
                    val checked = k in selectedKinds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (checked) selectedKinds.remove(k) else selectedKinds.add(k) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked, { if (it) selectedKinds.add(k) else selectedKinds.remove(k) },
                            colors = CheckboxDefaults.colors(checkedColor = IosGreen, uncheckedColor = Color.White.copy(alpha = 0.5f))
                        )
                        Column(Modifier.weight(1f)) {
                            Text("${k.emoji} ${k.label}", color = Color.White, fontSize = 14.sp)
                            Text(
                                "${items.size} élément(s) · ${BoostTools.fmt(items.sumOf { it.size })}" +
                                    if (!k.preChecked) "\n" + items.take(3).joinToString("\n") { "  • " + it.file.name } else "",
                                color = TextDim, fontSize = 12.sp, lineHeight = 15.sp
                            )
                        }
                    }
                }
                val sel = junk.filter { it.kind in selectedKinds }
                if (sel.isNotEmpty()) {
                    PrimaryButton("🗑️ Supprimer ${BoostTools.fmt(sel.sumOf { it.size })}", Modifier.fillMaxWidth()) {
                        scope.launch {
                            val freed = withContext(Dispatchers.IO) { BoostTools.delete(sel) }
                            junk.removeAll(sel)
                            log = listOf("✅ ${BoostTools.fmt(freed)} libérés (${sel.size} éléments supprimés)")
                            ctx.toast("${BoostTools.fmt(freed)} libérés ✓"); refresh++
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            BodyText("Le cache des autres applis est protégé par Android : l'outil Samsung le vide en 1 clic.")
            SecondaryButton("🧽 Entretien de l'appareil (cache de toutes les applis)", Modifier.fillMaxWidth()) { BoostTools.openDeviceCare(ctx) }
        }

        // ---------- Applis inutilisées ----------
        GlassCard(title = "Applis qui ne te servent à rien", icon = Icons.Rounded.Apps, iconColor = IosOrange) {
            if (!usage) {
                BodyText("Autorise « Accès aux données d'utilisation » pour voir les applis que tu n'as pas ouvertes depuis 30 jours.")
                PrimaryButton("Autoriser", Modifier.fillMaxWidth()) { BoostTools.requestUsageAccess(ctx) }
            } else {
                SecondaryButton("🔍 Chercher (pas ouvertes depuis 30 j)", Modifier.fillMaxWidth()) { loadUnused() }
                unused?.let { list ->
                    if (list.isEmpty()) BodyText("✨ Tu utilises toutes tes applis.")
                    list.take(40).forEach { a ->
                        val days = if (a.lastUsed == 0L) null else TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - a.lastUsed)
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.label, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                Text(
                                    (if (days == null) "jamais ouverte" else "pas ouverte depuis $days j") + " · " + BoostTools.fmt(a.size),
                                    color = TextDim, fontSize = 12.sp
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IosPink.copy(alpha = 0.3f))
                                    .clickable { SystemTools.uninstall(ctx, a.pkg) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text("Supprimer", color = Color.White, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }

        // ---------- Permissions ----------
        GlassCard(title = "Puissance débloquée", icon = Icons.Rounded.AdminPanelSettings, iconColor = Color(0xFF30D158)) {
            PermRow("Réglages système (luminosité, veille)", canWrite) { SystemTools.requestWriteSettings(ctx) }
            PermRow("Ne pas déranger", dnd) { BoostTools.requestDndAccess(ctx) }
            PermRow("Accès à tous les fichiers (nettoyage)", allFiles) { BoostTools.requestAllFiles(ctx) }
            PermRow("Données d'utilisation (applis inutiles)", usage) { BoostTools.requestUsageAccess(ctx) }
            PermRow("Mode avancé ADB (120 Hz, animations)", secure) {
                val cmd = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"
                ctx.getSystemService(android.content.ClipboardManager::class.java)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("adb", cmd))
                ctx.toast("Commande ADB copiée : lance-la une fois depuis un ordinateur")
            }
        }
    }
}

@Composable
private fun Gauge(label: String, used: Long, total: Long) {
    val f = if (total > 0) used.toFloat() / total else 0f
    Column {
        Row {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${BoostTools.fmt(used)} / ${BoostTools.fmt(total)}", color = TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (f > 0.85f) IosPink else if (f > 0.7f) IosOrange else IosGreen,
            trackColor = Color.White.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !ok, onClick = onFix)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (ok) "✅" else "⬜", fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (!ok) Text("Activer ›", color = Accent, fontSize = 13.sp)
        Spacer(Modifier.size(2.dp))
    }
}
