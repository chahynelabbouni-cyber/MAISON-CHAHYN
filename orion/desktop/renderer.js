const $ = (s) => document.querySelector(s);
const cfg = Object.assign({ apiKey: '', model: 'claude-opus-5-5', proxy: '', lang: 'fr', vpn: false },
  JSON.parse(localStorage.getItem('orion') || '{}'));
const save = () => localStorage.setItem('orion', JSON.stringify(cfg));

const ENGINES = {
  us: (q) => `https://www.google.com/search?q=${q}&gl=us&hl=en&pws=0`,
  ddg: (q) => `https://duckduckgo.com/?q=${q}&kl=us-en`,
  brave: (q) => `https://search.brave.com/search?q=${q}`,
  scholar: (q) => `https://scholar.google.com/scholar?q=${q}&hl=en`,
  wiki: (q) => `https://en.wikipedia.org/w/index.php?search=${q}`
};

// Fiabilité indicative de la source (heuristique, à compléter)
const TRUSTED = /(\.gov|\.edu|\.gouv\.fr|\.europa\.eu|who\.int|nih\.gov|nature\.com|science\.org|sciencedirect\.com|arxiv\.org|pubmed|reuters\.com|apnews\.com|bbc\.|lemonde\.fr|nytimes\.com|wsj\.com|ft\.com|economist\.com|mit\.edu|stanford\.edu|harvard\.edu|wikipedia\.org|insee\.fr|ieee\.org)$/;
const DUBIOUS = /(blogspot\.|wordpress\.com|medium\.com|substack\.com|tiktok\.com|x\.com|twitter\.com|facebook\.com)$/;
function trustOf(url) {
  try { const h = new URL(url).hostname;
    if (TRUSTED.test(h)) return ['ok', 'Source reconnue'];
    if (DUBIOUS.test(h)) return ['bad', 'Contenu non vérifié : à recouper'];
    return ['warn', 'Source non évaluée'];
  } catch { return ['', '']; }
}

// ---------- Onglets ----------
let tabs = [], active = null;
function newTab(url = ENGINES.us('')) {
  const wv = document.createElement('webview');
  wv.setAttribute('partition', 'persist:web');
  wv.setAttribute('allowpopups', '');
  wv.src = url;
  const el = document.createElement('div');
  el.className = 'tab'; el.innerHTML = '<span>Nouvel onglet</span><b>✕</b>';
  const t = { wv, el };
  el.onclick = () => select(t);
  el.querySelector('b').onclick = (e) => { e.stopPropagation(); close(t); };
  wv.addEventListener('page-title-updated', (e) => el.querySelector('span').textContent = e.title);
  wv.addEventListener('did-navigate', () => t === active && sync());
  wv.addEventListener('did-navigate-in-page', () => t === active && sync());
  $('#views').appendChild(wv);
  $('#tabs').insertBefore(el, $('#newTab'));
  tabs.push(t); select(t);
}
function select(t) {
  tabs.forEach((x) => { x.wv.classList.toggle('active', x === t); x.el.classList.toggle('active', x === t); });
  active = t; sync();
}
function close(t) {
  t.wv.remove(); t.el.remove(); tabs = tabs.filter((x) => x !== t);
  tabs.length ? select(tabs[tabs.length - 1]) : newTab();
}
function sync() {
  let u = ''; try { u = active.wv.getURL(); } catch {}
  $('#url').value = u;
  const [c, tip] = trustOf(u); $('#trust').className = c; $('#trust').title = tip;
}

function go(input) {
  const v = input.trim(); if (!v) return;
  const isUrl = /^https?:\/\//i.test(v) || /^[\w-]+(\.[\w-]+)+(\/.*)?$/.test(v);
  active.wv.loadURL(isUrl ? (v.startsWith('http') ? v : 'https://' + v) : ENGINES[$('#engine').value](encodeURIComponent(v)));
}

$('#url').addEventListener('keydown', (e) => e.key === 'Enter' && go(e.target.value));
$('#back').onclick = () => active.wv.canGoBack() && active.wv.goBack();
$('#fwd').onclick = () => active.wv.canGoForward() && active.wv.goForward();
$('#reload').onclick = () => active.wv.reload();
$('#newTab').onclick = () => newTab();

// ---------- Traduction de page ----------
$('#translate').onclick = () => {
  const u = active.wv.getURL();
  if (!u.startsWith('http')) return;
  active.wv.loadURL(`https://translate.google.com/translate?sl=auto&tl=${cfg.lang}&u=${encodeURIComponent(u)}`);
};

// ---------- VPN / Proxy ----------
async function applyVpn() {
  await window.orion.setProxy(cfg.vpn && cfg.proxy ? cfg.proxy : '');
  $('#vpnBtn').classList.toggle('on', cfg.vpn && !!cfg.proxy);
  $('#vpnLabel').textContent = cfg.vpn && cfg.proxy ? 'VPN on' : 'VPN off';
}
$('#vpnBtn').onclick = async () => {
  if (!cfg.proxy) { openSettings(); return; }
  cfg.vpn = !cfg.vpn; save(); await applyVpn(); active.wv.reload();
};

// ---------- Réglages ----------
function openSettings() {
  $('#apiKey').value = cfg.apiKey; $('#model').value = cfg.model; $('#proxy').value = cfg.proxy; $('#lang').value = cfg.lang;
  $('#settings').showModal();
}
$('#setBtn').onclick = openSettings;
$('#closeSet').onclick = () => $('#settings').close();
$('#saveSet').onclick = async () => {
  cfg.apiKey = $('#apiKey').value.trim(); cfg.model = $('#model').value.trim() || 'claude-opus-5-5';
  cfg.proxy = $('#proxy').value.trim(); cfg.lang = $('#lang').value.trim() || 'fr';
  if (cfg.proxy) cfg.vpn = true;
  save(); await applyVpn(); $('#settings').close();
};
$('#checkIp').onclick = async () => {
  $('#ipOut').textContent = '…';
  try { const d = await window.orion.checkIp(); $('#ipOut').textContent = `${d.ip} — ${d.city || ''} ${d.country || ''}`; }
  catch (e) { $('#ipOut').textContent = 'Échec : ' + e.message; }
};

// ---------- Assistant IA ----------
$('#aiBtn').onclick = () => $('#ai').classList.toggle('hidden');
const history = [];
const SYSTEM = `Tu es l'assistant intégré du navigateur Orion. Réponds en français, clairement.
Règles : privilégie les sources fiables (institutions, publications scientifiques, grands médias, sites officiels, notamment américains quand c'est pertinent).
Cite toujours tes sources avec leurs URL. Signale quand une information est incertaine, contestée ou provient d'une source peu fiable.
Tu reçois le texte de la page ouverte comme contexte ; utilise la recherche web pour vérifier ou compléter.`;

function addMsg(role, text) {
  const d = document.createElement('div'); d.className = 'msg ' + role;
  d.innerHTML = text.replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]))
    .replace(/(https?:\/\/[^\s)\]]+)/g, '<a href="#" data-u="$1">$1</a>');
  d.querySelectorAll('a').forEach((a) => a.onclick = (e) => { e.preventDefault(); newTab(a.dataset.u); });
  $('#chat').appendChild(d); $('#chat').scrollTop = 1e9; return d;
}

async function ask(question) {
  if (!cfg.apiKey) { addMsg('bot', 'Ajoute ta clé API Claude dans ⚙ Réglages.'); return; }
  addMsg('user', question);
  let page = '';
  try { page = await active.wv.executeJavaScript('document.body.innerText.slice(0,15000)'); } catch {}
  const content = `Page ouverte : ${active.wv.getURL()}\n---\n${page}\n---\nQuestion : ${question}`;
  history.push({ role: 'user', content });
  const wait = addMsg('bot', '✦ réflexion…');
  try {
    const r = await window.orion.askClaude({ apiKey: cfg.apiKey, model: cfg.model, system: SYSTEM, messages: history.slice(-10) });
    const text = r.content.filter((b) => b.type === 'text').map((b) => b.text).join('');
    const cites = [...new Set(r.content.flatMap((b) => (b.citations || []).map((c) => c.url)).filter(Boolean))];
    wait.remove();
    addMsg('bot', text + (cites.length ? '\n\nSources :\n' + cites.join('\n') : ''));
    history.push({ role: 'assistant', content: text });
  } catch (e) { wait.remove(); history.pop(); addMsg('bot', 'Erreur : ' + e.message); }
}
$('#ask').onsubmit = (e) => { e.preventDefault(); const q = $('#q').value.trim(); if (q) { $('#q').value = ''; ask(q); } };
$('#q').addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); $('#ask').requestSubmit(); } });
document.querySelectorAll('.quick button').forEach((b) => b.onclick = () => ask(b.dataset.q));

// ---------- Démarrage ----------
applyVpn().then(() => newTab());
