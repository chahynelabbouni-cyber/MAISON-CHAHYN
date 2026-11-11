# Maison Chahyn 🏠✨

**Personnalise ton Samsung Galaxy (S26 Ultra) de A à Z, façon iPhone — tout en une seule appli, tout en français.**

## Ce que fait l'appli

| Onglet | Ce que tu peux faire |
|---|---|
| **Accueil** | « ✨ Tout appliquer » : style iPhone complet en 1 clic + liste de progression |
| **Modèles** | 12 modèles prêts (iPhone Liquid Glass, iOS 18 teinté, Néon, Pastel, Luxe or…) qui changent tout d’un coup : fond, logos, couleurs, verre, clavier ; enregistre tes propres modèles |
| **Écran** | Écran d'accueil iPhone : icônes squircle, dock en verre liquide, widget horloge, recherche, bibliothèque d'apps, 7 styles d’icônes, packs d’icônes du Play Store, logo et nom personnalisés par appli (emoji, lettre, photo), applis masquées |
| **Clavier** | Clavier AZERTY façon iPhone : thèmes en verre, accents (appui long), emojis, curseur en glissant sur l'espace, majuscules auto, « . » double espace |
| **Fonds** | 12 fonds d'écran « verre liquide » générés, ta galerie, ton propre dégradé ; réglages du verre liquide (flou, opacité, bordure, reflets) |
| **Système** | Luminosité, mise en veille, rotation, sonnerie… directement ; raccourcis Good Lock (QuickStar, LockStar, NavStar, Theme Park…) ; mode avancé ADB |

## 📷 Chahyn Photo — l'appareil photo qui se règle tout seul

Installé avec Maison, il a **sa propre icône « Chahyn Photo »** (et un bouton dans l'onglet Accueil).

| | |
|---|---|
| **Auto intelligent** | Analyse l'aperçu en continu : lumière réelle (EV), visages, distance de mise au point, couleurs (ciel, végétation, tons chauds), contre-jour, stabilité du téléphone (gyroscope). Le bandeau du haut explique ce qui est détecté et réglé. |
| **17 situations** | Portrait, Paysage, Coucher de soleil, Contre-jour, Nuit, Ciel étoilé, Intérieur, Sport, Enfants & animaux, Nourriture, Macro, Neige & plage, Concert, Feux d'artifice, Architecture, Document, Selfie — chacune règle EV, ISO max, vitesse, mise au point, balance des blancs, HDR, priorité visage. |
| **Exposition optimisée** | Au déclenchement : ISO le plus bas possible tant que la pose reste nette à main levée (moins de bruit), vitesse rapide pour figer le mouvement, et pose longue automatique quand le téléphone est posé (nuit 2 s, étoiles 10 s). |
| **Qualité max** | Réduction de bruit, netteté, aberrations et pixels chauds en « haute qualité », stabilisation optique, JPEG qualité 100, 12 MP regroupés (ou résolution max), **RAW DNG** en option. |
| **13 styles en direct** | Naturel, Éclatant, Peau douce, Heure dorée, Cinéma, Film mat, Nordique, Pop, Gourmand, Net, N&B, N&B doux, Document — avec intensité réglable, ou « Auto » selon la situation. |
| **Pro** | EV, ISO, vitesse (jusqu'à 30 s), mise au point manuelle, balance des blancs. |
| **Aides** | Toucher = mise au point + exposition, pincer = zoom (0,6× → 10× selon les modules exposés), grille, niveau à bulle (ligne et vue de dessus), histogramme, retardateur 3/10 s, volume = déclencheur, conseils pour chaque situation. |

| **Guide intégré** | Bouton ❓ : mode d'emploi complet (chaque situation, chaque style, chaque bouton, conseils S26 Ultra). Affiché au premier lancement. |
| **Fiche après chaque photo** | ISO, vitesse, ouverture, zoom, situation, style et réglage automatique appliqué — avec « Voir » et « Retoucher ». |
| **Retouche Chahyn** | Améliore n'importe quelle photo de la Galerie : Partager › « Retouche Chahyn ». Amélioration auto (niveaux, dominantes, éclat), 13 styles, luminosité, contraste, saturation, chaleur, teinte, fondu ; maintenir = avant/après. Une copie est créée, l'original n'est jamais modifié. |

Photos enregistrées dans **Galerie › Albums › Chahyn Photo**.

> Honnêtement : Samsung réserve certains traitements (fusion multi-images IA, capteur 200 MP natif, certains téléobjectifs) à son appli Appareil photo et à **Expert RAW**. Chahyn Photo exploite tout ce qu'Android expose (Camera2) et le règle au mieux pour chaque situation.

## Installer sur le téléphone

1. Ouvre l'onglet **Releases** du dépôt GitHub (ou **Actions** → dernier build → *MaisonChahyn-apk*).
2. Télécharge **MaisonChahyn.apk** depuis ton Galaxy.
3. Ouvre le fichier → autorise « Installer des applis inconnues » pour ton navigateur → **Installer**.
4. Ouvre **Maison Chahyn** → **✨ Tout appliquer**.

## Limites d'Android (honnêtement)

Aucune appli (sans root) ne peut modifier seule la barre d'état, le volet de notifications ou l'écran verrouillé de One UI.
Maison fait tout ce qui est possible directement, et t'emmène en 1 clic vers les modules officiels **Samsung Good Lock** pour le reste.

## Développement

Kotlin + Jetpack Compose + Camera2, minSdk 26, targetSdk 35. Chaque `push` compile l'APK via GitHub Actions (`.github/workflows/android.yml`).
