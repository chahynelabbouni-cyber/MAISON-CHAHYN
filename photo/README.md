# 📷 Chahyn Photo

**L'appareil photo qui se règle tout seul, pour Galaxy S26 Ultra.** Application indépendante (`com.chahyn.photo`).


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

## Installer

1. Sur le Galaxy (connecté à GitHub) : Releases › **📷 Chahyn Photo — appareil photo**.
2. Télécharge **ChahynPhoto.apk** → ouvre-le → autorise « Installer des applis inconnues » → **Installer**.
3. Ouvre **Chahyn Photo** : le guide s'affiche au premier lancement.

Lien direct (dernière version) : `https://github.com/chahynelabbouni-cyber/MAISON-CHAHYN/releases/latest/download/ChahynPhoto.apk`

## Développement

Kotlin + Jetpack Compose + Camera2, minSdk 26, targetSdk 35. Compilé par GitHub Actions avec Maison (`./gradlew :photo:assembleRelease`).
