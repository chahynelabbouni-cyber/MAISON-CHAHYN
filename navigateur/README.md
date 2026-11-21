# Orion — navigateur intelligent (PC)

Navigateur futuriste avec :
- **Assistant IA Claude** intégré (panneau ✦ IA) : lit la page ouverte, fait des recherches web, **cite ses sources** et privilégie les sources fiables.
- **Indicateur de fiabilité** (pastille verte / orange / rouge) à côté de l'adresse.
- **Recherche orientée États-Unis** : Google US (gl=us, en anglais), DuckDuckGo US, Brave, Google Scholar, Wikipedia EN.
- **Traduction de toute la page** en un clic (⇄ Traduire).
- **VPN / proxy intégré** : faites passer uniquement Orion par un serveur (ex. aux USA), bouton VPN on/off, test de l'IP visible.
- Onglets, interface néon sombre.

## Installer et lancer
1. Installer [Node.js](https://nodejs.org) (version LTS).
2. Dans ce dossier : `npm install` puis `npm start`.
3. Créer un exécutable Windows : `npm run build:win` (résultat dans `dist/`).

## Configurer (⚙)
- **Clé API Claude** : à créer sur console.anthropic.com (payant à l'usage). Elle reste sur votre PC.
- **Proxy / VPN** : il faut un serveur. Deux options :
  - le plus simple : installer un VPN (Proton VPN, Mullvad…) sur le PC et choisir « États-Unis » — tout le PC passe par les USA ;
  - ou mettre l'adresse d'un proxy fourni par votre VPN (ex. `socks5://hôte:1080`) pour que **seul Orion** passe par les USA.
