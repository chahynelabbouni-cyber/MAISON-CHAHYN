package com.maisonchahyn.app.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maisonchahyn.app.ui.IosYellow
import com.maisonchahyn.app.ui.PrimaryButton
import com.maisonchahyn.app.ui.TextDim

/** Le mode d'emploi complet de Chahyn Photo, intégré à l'appli. */
@Composable
fun GuideSheet(firstTime: Boolean, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF0E0E12))) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 40.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (firstTime) "Bienvenue 📷" else "Guide Chahyn Photo",
                    color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
            }
            P("Chahyn Photo règle ton S26 Ultra tout seul pour chaque situation. Tu vises, tu déclenches : l'appli s'occupe du reste. Tout est expliqué ci-dessous — touche un titre pour l'ouvrir.")

            Section("🚀 En 3 secondes", open = true) {
                Step("1", "Vise", "Laisse « ✨ Auto » sélectionné. L'appli analyse la scène plusieurs fois par seconde.")
                Step("2", "Regarde le bandeau en haut", "Il dit ce qui a été reconnu (ex. « 🌙 Nuit · Téléphone posé → pose longue ») et l'ISO / la vitesse en cours. Touche-le pour lire le conseil du moment.")
                Step("3", "Déclenche", "Bouton blanc ou touche de volume. La photo part dans Galerie › Albums › Chahyn Photo. Une fiche te montre les réglages utilisés.")
            }

            Section("🧠 Ce que fait le mode Auto") {
                P("À chaque instant, il mesure :")
                Bullet("La lumière réelle (EV) à partir de l'ISO, de la vitesse et de l'ouverture du capteur.")
                Bullet("Les visages (nombre et taille).")
                Bullet("La distance de mise au point (très près, proche, loin, infini).")
                Bullet("Les couleurs de l'image : ciel bleu, végétation, tons chauds, peu de couleurs (texte).")
                Bullet("Le contre-jour : zones brûlées + zones sombres, sujet plus sombre que le fond.")
                Bullet("Les mouvements du téléphone (gyroscope) : tenu, en mouvement, ou posé/sur trépied.")
                P("Il choisit alors une des situations ci-dessous. Une situation doit être vue 2 fois de suite pour être adoptée, pour éviter que ça clignote.")
                P("Au moment du déclenchement, il recalcule le meilleur couple ISO/vitesse : ISO le plus bas possible tant que la photo reste nette à main levée (moins de bruit, plus de détails), vitesse rapide si le sujet bouge, pose longue si le téléphone est posé.")
            }

            Section("🎯 Les situations (${Scenes.all.size - 1})") {
                P("Tu peux en forcer une dans l'onglet SITUATIONS. Double-tape l'aperçu pour revenir en Auto.")
                Scenes.all.filter { it.id != "auto" }.forEach { s ->
                    Item("${s.emoji} ${s.name}", s.what, s.tip)
                }
            }

            Section("🎨 Les styles (${Looks.all.size})") {
                P("Onglet STYLES. Le style se voit en direct dans l'aperçu et est appliqué en pleine résolution sur la photo (les métadonnées sont conservées). Le curseur Intensité dose l'effet. « ✨ Auto » choisit le style adapté à la situation (Peau douce en portrait, Heure dorée au coucher de soleil, Gourmand pour la nourriture…).")
                Looks.all.forEach { l -> Item(l.name, l.desc, null) }
                P("Conseil qualité : si tu comptes retoucher, reste en Naturel (et active le RAW).")
            }

            Section("🎛️ Mode Pro") {
                Item("EV (exposition)", "Rend la photo plus claire (+) ou plus sombre (−). Le premier réglage à toucher si l'image est trop claire ou trop sombre.", "Ciel blanc ? baisse à −0,3 / −0,7. Visage sombre à contre-jour ? monte à +0,3 / +0,7.")
                Item("ISO", "Sensibilité du capteur. Bas (50–200) = image propre ; haut (1600+) = plus de bruit (grain).", "Laisse sur Auto : l'appli choisit déjà l'ISO le plus bas possible au déclenchement.")
                Item("Vitesse", "Durée de la pose. Rapide (1/1000) fige le mouvement ; lente (1/10, 1 s…) capte plus de lumière mais floute si ça bouge.", "Au-delà de 1/15 s, pose le téléphone. Traînées de phares, eau soyeuse : 1 à 4 s, téléphone posé, retardateur 3 s.")
                Item("Netteté (mise au point)", "Choisis manuellement la distance nette, de très près à l'infini.", "Utile pour les étoiles, à travers une vitre ou un grillage.")
                Item("Balance des blancs", "Corrige la couleur de la lumière : Soleil, Nuageux, Ombre, Ampoule, Néon.", "Garder « Soleil » au coucher de soleil conserve les couleurs dorées.")
                P("Le bouton jaune AUTO à côté d'un réglage le remet en automatique.")
            }

            Section("🔘 Les boutons") {
                Item("⚡ Flash", "Coupé → Auto → Forcé → Lampe continue.", "Évite le flash pour les portraits : il aplatit les visages. Préfère le mode Nuit.")
                Item("⏱️ Retardateur", "Coupé → 3 s → 10 s.", "3 s pour les poses longues sans bouger le téléphone en appuyant.")
                Item("4:3 / 16:9", "Format de la photo. 4:3 utilise tout le capteur (meilleure qualité) ; 16:9 pour l'écran / les stories.", null)
                Item("12M / MAX", "12 MP regroupe les pixels : moins de bruit, meilleure dynamique (recommandé). MAX = plus de pixels pour recadrer en plein jour.", null)
                Item("RAW", "Enregistre aussi un fichier brut DNG (≈ 25 Mo) pour retoucher dans Lightroom ou Snapseed sans perte.", null)
                Item("Grille", "Règle des tiers : place l'horizon ou le sujet sur les lignes.", null)
                Item("⚙️ Réglages", "Résolution, format, RAW, ISO bas intelligent, pose longue sur trépied, niveau, histogramme, son, touches de volume.", null)
                Item("❓ Guide", "Ce mode d'emploi.", null)
                Item("Vignette en bas à gauche", "Ouvre ta dernière photo. La fiche qui apparaît après chaque photo propose aussi « Retoucher ».", null)
                Item("🔄 Caméra avant/arrière", "Change de caméra (la situation Selfie est choisie automatiquement).", null)
            }

            Section("👆 Gestes") {
                Bullet("Toucher l'aperçu : mise au point et exposition à cet endroit (carré jaune, vert quand c'est net). Reste 8 s, puis retour automatique.")
                Bullet("Pincer : zoom. Les pastilles 0,6 · 1 · 2 · 3 · 5 · 10 donnent les zooms les plus nets.")
                Bullet("Double-toucher : retour en ✨ Auto.")
                Bullet("Touches de volume : déclencheur (désactivable).")
                Bullet("Ligne du niveau : verte quand le téléphone est parfaitement droit. À plat (vue de dessus), une bulle apparaît.")
            }

            Section("🏆 Les meilleures photos avec ton S26 Ultra") {
                Bullet("Essuie l'objectif avant de photographier : un voile de doigt = photo floue et halos. C'est le conseil n°1.")
                Bullet("Reste en 12 MP et en 4:3 : c'est là que le capteur donne le plus de détails propres.")
                Bullet("Pour zoomer, utilise les pastilles 3× et 5× (vrais téléobjectifs) plutôt qu'un zoom intermédiaire. Au-delà de 10×, la qualité baisse.")
                Bullet("Tiens le téléphone à deux mains, coudes contre le corps, et déclenche en douceur (ou avec le volume).")
                Bullet("De nuit, pose le téléphone : l'appli le détecte et allonge la pose pour une image beaucoup plus propre.")
                Bullet("La lumière fait 80 % de la photo : lumière de fenêtre de côté pour les portraits, heure dorée (1 h avant le coucher du soleil) dehors.")
                Bullet("Touche le sujet sur l'écran quand il n'est pas au centre ou quand le fond est très lumineux.")
                Bullet("Regarde l'histogramme (Réglages) : s'il touche le bord droit, le ciel est brûlé → baisse l'EV.")
            }

            Section("✏️ Retoucher les photos de ta Galerie") {
                P("« Retouche Chahyn » améliore n'importe quelle photo déjà prise (même avec l'appareil photo Samsung). L'original n'est jamais modifié : une copie est enregistrée dans l'album Chahyn Photo.")
                Bullet("Depuis la Galerie Samsung : ouvre une photo → Partager → « Retouche Chahyn ».")
                Bullet("Ou : ⋮ → Ouvrir avec / Modifier dans une autre appli → « Retouche Chahyn ».")
                Bullet("Ou depuis l'appli Maison (Accueil → Retoucher une photo), ou depuis la fiche après une photo.")
                Item("✨ Amélioration auto", "Analyse les couleurs de la photo : rétablit les noirs et les blancs, corrige les dominantes de couleur, ajoute un peu d'éclat.", null)
                Item("Styles + réglages", "Les 13 styles, plus Luminosité, Contraste, Saturation, Chaleur, Teinte, Fondu.", "Maintiens le doigt sur la photo pour voir l'original.")
            }

            Section("📁 Où sont mes photos ?") {
                P("Galerie › Albums › Chahyn Photo (dossier Images/Chahyn Photo). Les RAW (.dng) y sont aussi. Les photos sont enregistrées en JPEG qualité maximale avec toutes les métadonnées (date, ISO, vitesse, GPS si activé…).")
            }

            Section("ℹ️ Limites (honnêtement)") {
                P("Samsung réserve certains traitements à son appli Appareil photo et à Expert RAW : fusion de plusieurs images par IA, capteur 200 MP natif, certains passages automatiques entre objectifs. Chahyn Photo utilise tout ce qu'Android met à disposition des applis (Camera2) et le règle au mieux pour chaque situation. Pour une photo extrême (nuit noire à main levée, zoom 100×), l'appareil photo Samsung peut rester meilleur.")
            }

            Spacer(Modifier.height(4.dp))
            PrimaryButton(if (firstTime) "C'est parti 📷" else "Retour à l'appareil photo", onClick = onClose)
        }
    }
}

@Composable
private fun Section(title: String, open: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(open) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = TextDim)
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun P(text: String) = Text(text, color = TextDim, fontSize = 14.sp, lineHeight = 19.sp)

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•  ", color = IosYellow, fontSize = 14.sp)
        Text(text, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun Step(n: String, title: String, text: String) {
    Row {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(IosYellow),
            contentAlignment = Alignment.Center
        ) { Text(n, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = TextDim, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun Item(title: String, text: String, tip: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (text.isNotEmpty()) Text(text, color = TextDim, fontSize = 14.sp, lineHeight = 19.sp)
        if (tip != null) Text("💡 $tip", color = IosYellow, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
