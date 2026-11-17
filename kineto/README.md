# Kinéto

Générateur de vidéos animées verticales (style motion design TikTok), 100 % dans le navigateur.

## Contenu
- `site/` : le site (HTML/CSS/JS sans framework), à déployer tel quel sur Cloudflare Pages (dossier de sortie : `kineto/site`, aucune commande de build).
  - `engine.js` : moteur de rendu canvas (8 types de scènes × 4 styles, sous-titres mot à mot). Aucun appel serveur.
  - `app.js` : assistant Kino (règles locales), éditeur de scènes, voix (micro + marquage des scènes, ou import), export vidéo.
  - `_headers` : en-têtes de sécurité (CSP…) pour Cloudflare Pages.
  - `legal/` : pages légales **à rédiger**.
- `build.py` : génère `dist/index.html` en un seul fichier (aperçu hors ligne).

## Tester en local
Ouvre `dist/index.html` dans Chrome, ou lance `python3 -m http.server -d site` puis va sur http://localhost:8000 (le micro demande HTTPS ou localhost).

## Limites connues
- **Quotas et forfaits pour l'instant côté navigateur** : tout le monde est en Gratuit (3 vidéos/mois, 720p, filigrane). Le paiement affiche « Bientôt disponible ». Le vrai contrôle viendra du serveur (Cloudflare Worker + Supabase, puis PayPal).
- **Filigrane dessiné dans le navigateur** : un utilisateur technique peut le retirer.
- **Format** : MP4 H.264 quand le navigateur le permet, sinon MP4 générique ou WebM ; l'utilisateur est prévenu si TikTok risque de refuser le fichier.
- **Export en temps réel**, onglet visible.
