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

## 📷 Chahyn Photo (appli séparée)

L'appareil photo adaptatif est une **application indépendante**, dans le dossier `photo/` : voir [photo/README.md](photo/README.md).
Téléchargement : Releases › **📷 Chahyn Photo — appareil photo** › `ChahynPhoto.apk`.

## Installer sur le téléphone

1. Ouvre l'onglet **Releases** du dépôt GitHub (ou **Actions** → dernier build → *MaisonChahyn-apk*).
2. Télécharge **MaisonChahyn.apk** depuis ton Galaxy.
3. Ouvre le fichier → autorise « Installer des applis inconnues » pour ton navigateur → **Installer**.
4. Ouvre **Maison Chahyn** → **✨ Tout appliquer**.

## Limites d'Android (honnêtement)

Aucune appli (sans root) ne peut modifier seule la barre d'état, le volet de notifications ou l'écran verrouillé de One UI.
Maison fait tout ce qui est possible directement, et t'emmène en 1 clic vers les modules officiels **Samsung Good Lock** pour le reste.

## Développement

Kotlin + Jetpack Compose, minSdk 26, targetSdk 35. Chaque `push` compile l'APK via GitHub Actions (`.github/workflows/android.yml`).
