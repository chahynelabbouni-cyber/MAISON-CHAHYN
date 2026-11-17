/* Kinéto — application (assistant, éditeur, voix, export). */
(function () {
  'use strict';
  const K = window.Kineto;
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const el = (tag, attrs = {}, ...kids) => {
    const n = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (k === 'class') n.className = v;
      else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
      else if (v === true) n.setAttribute(k, '');
      else if (v !== false && v != null) n.setAttribute(k, v);
    }
    for (const kid of kids.flat()) if (kid != null) n.append(kid);
    return n;
  };

  /* ---------- stockage local (protégé) ---------- */
  const store = {
    get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* stockage indisponible */ } }
  };

  /* ---------- forfaits ----------
   * Tant que l'API (Cloudflare Worker + Supabase) n'est pas branchée, tout le monde est en Gratuit
   * et le quota est compté localement. Le serveur deviendra la seule source de vérité (phase C). */
  const PLANS = {
    free:    { name: 'Gratuit',  price: '0 €',          quota: 3,   res: 720,  styles: ['neon', 'papier'], watermark: true,
               perks: ['3 vidéos / mois', '720p', '2 styles (Néon, Papier)', 'Filigrane « Fait avec Kinéto »'] },
    creator: { name: 'Créateur', price: '9,90 € / mois', quota: 40,  res: 1080, styles: null, watermark: false, best: true,
               perks: ['40 vidéos / mois', '1080p', 'Les 4 styles', 'Sans filigrane'] },
    pro:     { name: 'Pro',      price: '19,90 € / mois', quota: 150, res: 1080, styles: null, watermark: false,
               perks: ['150 vidéos / mois', '1080p', 'Les 4 styles', 'Sans filigrane'] },
    pack:    { name: 'Pack 10 vidéos', price: '8,90 €', perks: ['10 vidéos sans abonnement', 'Sans date limite', '1080p, sans filigrane'] }
  };
  const planId = 'free';
  const plan = PLANS[planId];
  const styleAllowed = (id) => !plan.styles || plan.styles.includes(id);
  const monthKey = () => new Date().toISOString().slice(0, 7);
  function usedThisMonth() { const u = store.get('kineto.usage', {}); return u[monthKey()] || 0; }
  function addUsage() { const u = store.get('kineto.usage', {}); u[monthKey()] = (u[monthKey()] || 0) + 1; store.set('kineto.usage', u); }

  /* ---------- projet ---------- */
  let project = store.get('kineto.project', null);
  if (!project || !Array.isArray(project.scenes) || !project.scenes.length) project = K.sampleProject();
  if (!styleAllowed(project.style)) project.style = 'neon';
  let saveTimer = 0;
  function save() { clearTimeout(saveTimer); saveTimer = setTimeout(() => store.set('kineto.project', project), 300); }

  /* ================= ROUTAGE ================= */
  function route() {
    const studio = location.hash === '#studio';
    $('#home').hidden = studio;
    $('#studio').hidden = !studio;
    if (studio) { stopHome(); drawPreview(); } else { pause(); startHome(); }
    window.scrollTo(0, 0);
  }
  window.addEventListener('hashchange', route);
  $$('[data-scroll]').forEach((a) => a.addEventListener('click', (e) => {
    const id = a.dataset.scroll;
    if (location.hash === '#studio') { location.hash = '#home'; }
    e.preventDefault();
    requestAnimationFrame(() => document.getElementById(id)?.scrollIntoView());
  }));

  /* ================= ACCUEIL ================= */
  const homeCanvases = [];
  let homeRaf = 0;
  const visible = new Set();
  const io = 'IntersectionObserver' in window
    ? new IntersectionObserver((ents) => ents.forEach((e) => e.isIntersecting ? visible.add(e.target) : visible.delete(e.target)))
    : null;
  function addHomeCanvas(canvas, proj) {
    const ctx = canvas.getContext('2d');
    homeCanvases.push({ canvas, ctx, proj, dur: K.totalDuration(proj) });
    if (io) io.observe(canvas); else visible.add(canvas);
  }
  addHomeCanvas($('#heroCanvas'), K.sampleProject('neon'));
  for (const [id, S] of Object.entries(K.STYLES)) {
    const c = el('canvas', { width: 270, height: 480, 'aria-label': 'Style ' + S.name });
    $('#stylesGrid').append(el('div', { class: 'style-card' }, el('div', { class: 'phone' }, c), el('b', {}, S.name + (S.free ? '' : ' · payant'))));
    const p = K.sampleProject(id);
    p.scenes = [p.scenes[1]];
    addHomeCanvas(c, p);
  }
  function homeLoop(now) {
    const t = now / 1000;
    for (const h of homeCanvases) if (visible.has(h.canvas)) K.renderFrame(h.ctx, h.proj, t % h.dur, {});
    homeRaf = requestAnimationFrame(homeLoop);
  }
  function startHome() { if (!homeRaf) homeRaf = requestAnimationFrame(homeLoop); }
  function stopHome() { cancelAnimationFrame(homeRaf); homeRaf = 0; }

  $('#tryForm').addEventListener('submit', (e) => {
    e.preventDefault();
    const n = parseNum($('#tryValue').value);
    if (!n) { $('#tryValue').setCustomValidity('Écris un nombre, par exemple 73 %'); $('#tryValue').reportValidity(); return; }
    const phrase = $('#tryPhrase').value.trim();
    project = generate({ format: 'chiffre', sujet: phrase, value: $('#tryValue').value, phrase, style: project.style });
    save();
    location.hash = '#studio';
    openTab('scenes');
    renderScenes();
  });
  $('#tryValue').addEventListener('input', (e) => e.target.setCustomValidity(''));

  /* ---------- tarifs ---------- */
  function renderPlans(box) {
    box.replaceChildren(...['free', 'creator', 'pro', 'pack'].map((id) => {
      const p = PLANS[id];
      return el('div', { class: 'plan' + (p.best ? ' best' : '') },
        p.best ? el('span', { class: 'badge' }, 'Le plus choisi') : null,
        el('b', {}, p.name),
        el('div', { class: 'price' }, p.price),
        el('ul', {}, p.perks.map((x) => el('li', {}, x))),
        id === 'free'
          ? el('a', { class: 'btn btn-ghost', href: '#studio' }, id === planId ? 'Commencer' : 'Choisir')
          : el('button', { class: 'btn', disabled: true, title: 'Paiement bientôt disponible' }, 'Bientôt disponible'));
    }));
  }
  renderPlans($('#plansHome'));
  renderPlans($('#plansModal'));
  function openPricing(reason) {
    $('#pricingReason').textContent = reason || '';
    const d = $('#pricing');
    if (d.showModal) d.showModal(); else alert(reason);
  }

  /* ================= ONGLETS STUDIO ================= */
  function openTab(name) {
    $$('.tabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
    $$('.tab').forEach((p) => { p.hidden = p.dataset.panel !== name; });
    if (name === 'scenes') renderScenes();
    if (name === 'voix') updatePrompter(0);
    if (name === 'export') renderQuota();
  }
  $$('.tabs button').forEach((b) => b.addEventListener('click', () => openTab(b.dataset.tab)));

  /* ================= ASSISTANT KINO (règles locales) ================= */
  const FORMATS = { chiffre: 'Chiffre choc', evolution: 'Évolution', comparaison: 'Comparaison', top: 'Top / classement', mythe: 'Mythe ou réalité' };

  function parseNum(str) {
    const m = String(str || '').match(/-?\d[\d\s  .,]*/);
    if (!m) return null;
    let s = m[0].replace(/[\s  ]/g, '').replace(/[.,]$/, '');
    if (s.includes(',') && s.includes('.')) s = s.replace(/\./g, '').replace(',', '.');
    else if (s.includes(',')) s = s.replace(',', '.');
    else if (/^\-?\d{1,3}(\.\d{3})+$/.test(s)) s = s.replace(/\./g, '');
    const value = parseFloat(s);
    if (!isFinite(value)) return null;
    const unit = String(str).slice(m.index + m[0].length).trim().split(/\s+/).slice(0, 2).join(' ');
    return { value, unit };
  }

  function parsePairs(text) {
    return String(text || '').split(/\n|;/).map((l) => l.trim()).filter(Boolean).map((line) => {
      let label = line, raw = '';
      const sep = line.search(/[:=]/);
      if (sep >= 0) { label = line.slice(0, sep).trim(); raw = line.slice(sep + 1).trim(); }
      else {
        const m = line.match(/^(.*?)\s+(-?\d[\d\s.,]*\s*\S*)$/);
        if (m) { label = m[1].trim(); raw = m[2]; }
      }
      const n = raw ? parseNum(raw) : null;
      return { label: label.replace(/^\d+[.)]\s*/, ''), value: n ? n.value : null, unit: n ? n.unit : '', display: raw || null };
    });
  }

  const cap = (s) => { s = String(s || '').trim(); return s.charAt(0).toUpperCase() + s.slice(1); };
  const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
  const dur = (caption, min = 2.5) => Math.round(Math.max(min, caption.split(/\s+/).length * 0.4 + 1) * 10) / 10;
  const scene = (o) => ({ ...o, duration: o.duration || dur(o.caption || '') });

  function endScene(a, question) {
    const caption = pick(['Dis-moi en commentaire ce que tu en penses.', 'Abonne-toi pour d\'autres sujets comme celui-là.', 'Tu le savais ? Réponds en commentaire.']);
    return scene({ type: 'fin', title: question, sub: a.source ? 'Source : ' + a.source : '', caption, duration: 3.2 });
  }

  function generate(a) {
    const sujet = String(a.sujet || '').trim();
    const S = cap(sujet);
    const scenes = [];
    switch (a.format) {
      case 'chiffre': {
        const n = parseNum(a.value) || { value: 0, unit: '' };
        const shown = K.fmt(n.value, String(n.value).includes('.') ? 1 : 0) + (n.unit ? ' ' + n.unit : '');
        scenes.push(scene({ type: 'titre', title: pick([`Ce chiffre sur ${sujet} va te surprendre`, `${S} : le chiffre que peu de gens connaissent`]), caption: pick([`Tu ne vas pas croire ce chiffre sur ${sujet}.`, `Un seul chiffre sur ${sujet}, et il fait réfléchir.`]) }));
        scenes.push(scene({ type: 'chiffre', title: a.source ? 'Selon ' + a.source : S, value: n.value, unit: n.unit, sub: a.phrase, caption: `${shown} ${a.phrase}.`, duration: 4.5 }));
        scenes.push(endScene(a, 'Tu le savais ?'));
        break;
      }
      case 'evolution': {
        const items = parsePairs(a.series).filter((i) => i.value != null);
        const unit = (a.unit || items[0]?.unit || '').trim();
        const first = items[0], last = items[items.length - 1];
        scenes.push(scene({ type: 'titre', title: `Comment ${sujet} a changé`, sub: `De ${first.label} à ${last.label}`, caption: `Regarde comment ${sujet} a évolué entre ${first.label} et ${last.label}.` }));
        scenes.push(scene({ type: 'colonnes', title: S, unit, items: items.map(({ label, value }) => ({ label, value })), caption: `On passe de ${K.fmt(first.value)} ${unit} à ${K.fmt(last.value)} ${unit}.`.replace(/\s+\./, '.'), duration: Math.max(4, items.length * 0.7 + 2) }));
        if (first.value) {
          const pct = Math.round(((last.value - first.value) / Math.abs(first.value)) * 100);
          scenes.push(scene({ type: 'chiffre', title: `Entre ${first.label} et ${last.label}`, value: pct, unit: '%', sub: pct >= 0 ? 'de hausse' : 'de baisse', caption: `Soit ${pct >= 0 ? 'une hausse' : 'une baisse'} de ${Math.abs(pct)} pour cent.` }));
        }
        scenes.push(endScene(a, 'Et dans 5 ans ?'));
        break;
      }
      case 'comparaison': {
        const A = parsePairs(a.a)[0], B = parsePairs(a.b)[0];
        const unit = (a.unit || A.unit || B.unit || '').trim();
        scenes.push(scene({ type: 'titre', title: `${A.label} ou ${B.label} ?`, sub: S, caption: `${A.label} contre ${B.label} : lequel l'emporte ?` }));
        scenes.push(scene({ type: 'duel', title: S, unit, items: [{ label: A.label, value: A.value }, { label: B.label, value: B.value }], caption: `${A.label} : ${K.fmt(A.value)}. ${B.label} : ${K.fmt(B.value)}.`, duration: 4.5 }));
        const hi = A.value >= B.value ? A : B, lo = hi === A ? B : A;
        if (lo.value > 0 && hi.value / lo.value >= 1.1) {
          const r = Math.round((hi.value / lo.value) * 10) / 10;
          scenes.push(scene({ type: 'chiffre', title: hi.label, value: r, unit: 'fois plus', sub: 'que ' + lo.label, caption: `${hi.label}, c'est ${K.fmt(r, r % 1 ? 1 : 0)} fois plus que ${lo.label}.` }));
        }
        scenes.push(endScene(a, 'Tu aurais parié sur lequel ?'));
        break;
      }
      case 'top': {
        const items = parsePairs(a.items).slice(0, 6);
        const n = items.length;
        const listed = [...items].reverse();
        scenes.push(scene({ type: 'titre', title: `Top ${n} : ${sujet}`, caption: `Voici le top ${n} : ${sujet}. Le numéro un va te surprendre.` }));
        scenes.push(scene({ type: 'liste', title: `Top ${n}`, items: items.map(({ label, value, display }) => ({ label, value, display })), caption: listed.map((i, k) => `Numéro ${n - k}, ${i.label}.`).join(' '), duration: Math.max(4.5, n * 1.3 + 1) }));
        scenes.push(scene({ type: 'titre', title: `N°1 : ${items[0].label}`, sub: items[0].display || '', caption: `Et en première place : ${items[0].label}.` }));
        scenes.push(endScene(a, 'Tu es d\'accord avec ce classement ?'));
        break;
      }
      case 'mythe': {
        scenes.push(scene({ type: 'titre', title: 'Mythe ou réalité ?', sub: S, caption: `On entend souvent ça sur ${sujet}. Mais est-ce vrai ?` }));
        scenes.push(scene({ type: 'mythe', title: a.myth, sub: a.fact, caption: `On dit que ${a.myth.replace(/[.!]$/, '')}. En réalité, ${a.fact.replace(/[.!]$/, '')}.`, duration: Math.max(6, dur(a.myth + ' ' + a.fact) + 1) }));
        scenes.push(endScene(a, 'Tu y croyais ?'));
        break;
      }
    }
    return { style: a.style && styleAllowed(a.style) ? a.style : 'neon', scenes };
  }

  function stepsFor(a) {
    const s = [{ key: 'format', ask: 'Salut, moi c\'est Kino 👋 On fait quel type de vidéo ?', type: 'chips', options: Object.entries(FORMATS) },
               { key: 'sujet', ask: 'De quoi parle ta vidéo ? Quelques mots suffisent.', type: 'text', placeholder: 'ex. le sommeil des étudiants' }];
    const unitStep = { key: 'unit', ask: 'Quelle unité ? (laisse vide s\'il n\'y en a pas)', type: 'text', optional: true, placeholder: '%, €, km, millions…' };
    switch (a.format) {
      case 'chiffre':
        s.push({ key: 'value', ask: 'Quel est ton chiffre ?', type: 'text', placeholder: 'ex. 47 %  ou  2,5 millions', check: (v) => parseNum(v) ? '' : 'J\'ai besoin d\'un nombre, par exemple « 47 % ».' },
               { key: 'phrase', ask: 'Que signifie ce chiffre, en une phrase ?', type: 'text', placeholder: 'ex. des étudiants dorment moins de 6 h' });
        break;
      case 'evolution':
        s.push({ key: 'series', ask: 'Donne les étapes, une par ligne, sous la forme « date : valeur ».', type: 'area', placeholder: '2015 : 12\n2020 : 30\n2025 : 58',
                 check: (v) => parsePairs(v).filter((i) => i.value != null).length >= 2 ? '' : 'Il me faut au moins 2 lignes avec un nombre, par ex. « 2020 : 30 ».' }, unitStep);
        break;
      case 'comparaison': {
        const chk = (v) => parsePairs(v)[0]?.value != null ? '' : 'Écris-le sous la forme « Nom : valeur ».';
        s.push({ key: 'a', ask: 'Premier élément et sa valeur ?', type: 'text', placeholder: 'ex. Paris : 2,1 millions', check: chk },
               { key: 'b', ask: 'Deuxième élément et sa valeur ?', type: 'text', placeholder: 'ex. Marseille : 870 000', check: chk }, unitStep);
        break;
      }
      case 'top':
        s.push({ key: 'items', ask: 'Ton classement, une ligne par élément, du n°1 au dernier (3 à 6 lignes). La valeur est facultative.', type: 'area', placeholder: 'Japon : 84 ans\nSuisse : 83,8 ans\nEspagne : 83,3 ans',
                 check: (v) => parsePairs(v).length >= 2 ? '' : 'Il me faut au moins 2 lignes.' });
        break;
      case 'mythe':
        s.push({ key: 'myth', ask: 'Quelle idée reçue veux-tu démonter ?', type: 'text', placeholder: 'ex. On n\'utilise que 10 % de son cerveau' },
               { key: 'fact', ask: 'Et quelle est la réalité ?', type: 'text', placeholder: 'ex. On utilise toutes les zones du cerveau au cours d\'une journée' });
        break;
    }
    if (a.format) {
      s.push({ key: 'source', ask: 'Ta source ? C\'est facultatif mais ça rend la vidéo bien plus crédible.', type: 'text', optional: true, placeholder: 'ex. INSEE, 2024' });
      s.push({ key: 'style', ask: 'Dernière question : quel style ?', type: 'chips', options: Object.entries(K.STYLES).map(([id, S]) => [id, S.name]) });
    }
    return s;
  }

  let answers = {}, stepIdx = 0;
  const chat = $('#chat'), chatInput = $('#chatInput');
  function say(text, who = 'kino') { chat.append(el('div', { class: 'bubble ' + who }, text)); chat.scrollTop = chat.scrollHeight; }

  function ask() {
    const steps = stepsFor(answers);
    if (stepIdx >= steps.length) return finish();
    const st = steps[stepIdx];
    say(st.ask);
    chatInput.replaceChildren();
    if (st.type === 'chips') {
      chatInput.append(el('div', { class: 'chips' }, st.options.map(([id, label]) => {
        const locked = st.key === 'style' && !styleAllowed(id);
        return el('button', { class: 'chip' + (locked ? ' locked' : ''), type: 'button', onclick: () => {
          if (locked) return openPricing(`Le style ${label} est inclus dans les forfaits Créateur et Pro.`);
          answer(st, id, label);
        } }, label);
      })));
      return;
    }
    const input = st.type === 'area' ? el('textarea', { rows: 4, placeholder: st.placeholder || '' }) : el('input', { placeholder: st.placeholder || '', autocomplete: 'off' });
    const err = el('p', { class: 'err', role: 'alert' });
    const submit = () => {
      const v = input.value.trim();
      if (!v && !st.optional) { err.textContent = 'Réponds pour continuer.'; return; }
      const e = v && st.check ? st.check(v) : '';
      if (e) { err.textContent = e; return; }
      answer(st, v, v || '(passer)');
    };
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && (st.type !== 'area' || e.ctrlKey || e.metaKey)) { e.preventDefault(); submit(); } });
    chatInput.append(input, err, el('div', { class: 'row' },
      el('button', { class: 'btn btn-sm', type: 'button', onclick: submit }, 'Valider'),
      st.optional ? el('button', { class: 'btn btn-ghost btn-sm', type: 'button', onclick: () => answer(st, '', '(passer)') }, 'Passer') : null));
    input.focus({ preventScroll: true });
  }

  function answer(st, value, label) {
    say(label, 'me');
    answers[st.key] = value;
    stepIdx++;
    ask();
  }

  function finish() {
    chatInput.replaceChildren();
    try {
      project = generate(answers);
    } catch (e) {
      say('Oups, je n\'ai pas réussi à construire la vidéo avec ces réponses. Vérifie tes chiffres et recommence.');
      return;
    }
    save();
    player.t = 0;
    drawPreview();
    say(`C'est prêt : ${project.scenes.length} scènes. Regarde l'aperçu à droite, ajuste les scènes si besoin, puis enregistre ta voix.`);
    chatInput.append(el('div', { class: 'row' },
      el('button', { class: 'btn btn-sm', type: 'button', onclick: () => openTab('scenes') }, 'Ajuster les scènes'),
      el('button', { class: 'btn btn-ghost btn-sm', type: 'button', onclick: () => openTab('voix') }, 'Enregistrer ma voix')));
    play();
  }

  function restart() { answers = {}; stepIdx = 0; chat.replaceChildren(); ask(); }
  $('#chatRestart').addEventListener('click', restart);

  /* ================= ÉDITEUR DE SCÈNES ================= */
  const FIELDS = {
    titre:    [['title', 'Titre', 'area'], ['sub', 'Sous-titre']],
    chiffre:  [['title', 'Au-dessus du chiffre'], ['value', 'Chiffre', 'num'], ['unit', 'Unité'], ['sub', 'Phrase sous le chiffre', 'area']],
    colonnes: [['title', 'Titre'], ['unit', 'Unité'], ['items', 'Valeurs (une par ligne : « libellé : valeur »)', 'items']],
    barres:   [['title', 'Titre'], ['unit', 'Unité'], ['items', 'Valeurs (une par ligne : « libellé : valeur »)', 'items']],
    duel:     [['title', 'Titre'], ['unit', 'Unité'], ['items', 'Les 2 éléments (« nom : valeur »)', 'items']],
    liste:    [['title', 'Titre'], ['unit', 'Unité'], ['items', 'Éléments, du n°1 au dernier', 'items']],
    mythe:    [['title', 'Le mythe', 'area'], ['sub', 'La réalité', 'area']],
    fin:      [['title', 'Message final', 'area'], ['sub', 'Petite ligne (source…)']]
  };
  let openScene = 0;

  function itemsToText(items) { return (items || []).map((i) => i.label + (i.value != null ? ' : ' + (i.display || K.fmt(i.value, String(i.value).includes('.') ? 2 : 0)) : '')).join('\n'); }

  function renderStylePicker() {
    $('#stylePicker').replaceChildren(...Object.entries(K.STYLES).map(([id, S]) => {
      const locked = !styleAllowed(id);
      return el('button', { class: 'chip' + (project.style === id ? ' active' : '') + (locked ? ' locked' : ''), type: 'button', onclick: () => {
        if (locked) return openPricing(`Le style ${S.name} est inclus dans les forfaits Créateur et Pro.`);
        project.style = id; save(); renderStylePicker(); drawPreview();
      } }, S.name);
    }));
  }

  function renderScenes() {
    renderStylePicker();
    $('#totalDur').textContent = '· ' + fmtTime(K.totalDuration(project));
    const list = $('#sceneList');
    list.replaceChildren(...project.scenes.map((s, i) => {
      const head = el('div', { class: 'scene-head', onclick: (e) => { if (e.target.closest('.icon')) return; openScene = openScene === i ? -1 : i; seekTo(K.sceneStart(project, i) + 0.6); renderScenes(); } },
        el('span', { class: 'num' }, String(i + 1)),
        el('span', { class: 'ttl' }, `${K.TYPES[s.type] || s.type} · ${s.title || ''}`),
        el('button', { class: 'icon', type: 'button', title: 'Monter', 'aria-label': 'Monter la scène', disabled: i === 0, onclick: () => move(i, -1) }, '↑'),
        el('button', { class: 'icon', type: 'button', title: 'Descendre', 'aria-label': 'Descendre la scène', disabled: i === project.scenes.length - 1, onclick: () => move(i, 1) }, '↓'),
        el('button', { class: 'icon', type: 'button', title: 'Dupliquer', 'aria-label': 'Dupliquer la scène', onclick: () => { project.scenes.splice(i + 1, 0, structuredClone(s)); changed(); } }, '⧉'),
        el('button', { class: 'icon', type: 'button', title: 'Supprimer', 'aria-label': 'Supprimer la scène', onclick: () => { if (project.scenes.length > 1) { project.scenes.splice(i, 1); changed(); } } }, '✕'));
      const card = el('div', { class: 'scene' + (i === openScene ? ' current' : '') }, head);
      if (i === openScene) card.append(sceneForm(s));
      return card;
    }));
  }

  function sceneForm(s) {
    const body = el('div', { class: 'scene-body' });
    const typeSel = el('select', { onchange: () => { s.type = typeSel.value; if (!s.items && FIELDS[s.type].some((f) => f[2] === 'items')) s.items = [{ label: 'A', value: 10 }, { label: 'B', value: 20 }]; changed(); } },
      Object.entries(K.TYPES).map(([id, n]) => el('option', { value: id, selected: s.type === id }, n)));
    body.append(el('label', {}, 'Type', typeSel));
    const durIn = el('input', { type: 'number', min: 1, max: 30, step: 0.1, value: s.duration, oninput: () => { const v = parseFloat(durIn.value); if (v >= 0.5) { s.duration = Math.min(30, v); edited(); } } });
    body.append(el('label', {}, 'Durée (s)', durIn));
    for (const [key, label, kind] of FIELDS[s.type] || FIELDS.titre) {
      let input;
      if (kind === 'items') {
        input = el('textarea', { rows: 4 });
        input.value = itemsToText(s.items);
        input.addEventListener('input', () => { s.items = parsePairs(input.value).map(({ label, value, display }) => ({ label, value, display })); edited(); });
      } else if (kind === 'area') {
        input = el('textarea', { rows: 2 });
        input.value = s[key] || '';
        input.addEventListener('input', () => { s[key] = input.value; edited(); });
      } else {
        input = el('input', { type: 'text', inputmode: kind === 'num' ? 'decimal' : null, value: kind === 'num' ? String(s[key] ?? '').replace('.', ',') : (s[key] || '') });
        input.addEventListener('input', () => {
          if (kind === 'num') { const n = parseNum(input.value); if (n) s[key] = n.value; } else s[key] = input.value;
          edited();
        });
      }
      body.append(el('label', { class: kind === 'items' || kind === 'area' ? 'full' : '' }, label, input));
    }
    const capIn = el('textarea', { rows: 3 });
    capIn.value = s.caption || '';
    capIn.addEventListener('input', () => { s.caption = capIn.value; edited(); });
    body.append(el('label', { class: 'full' }, 'Texte lu à voix haute (sous-titres)', capIn));
    return body;
  }

  function move(i, d) { const j = i + d; [project.scenes[i], project.scenes[j]] = [project.scenes[j], project.scenes[i]]; openScene = j; changed(); }
  function edited() { save(); $('#totalDur').textContent = '· ' + fmtTime(K.totalDuration(project)); if (!player.playing) drawPreview(); }
  function changed() { save(); renderScenes(); drawPreview(); }

  $('#addType').append(...Object.entries(K.TYPES).map(([id, n]) => el('option', { value: id }, n)));
  $('#addScene').addEventListener('click', () => {
    const type = $('#addType').value;
    const s = { type, title: 'Nouveau titre', sub: '', caption: '', duration: 3 };
    if (type === 'chiffre') Object.assign(s, { value: 100, unit: '%' });
    if (FIELDS[type].some((f) => f[2] === 'items')) s.items = [{ label: 'A', value: 10 }, { label: 'B', value: 20 }];
    if (type === 'mythe') Object.assign(s, { title: 'Le mythe', sub: 'La réalité', duration: 6 });
    project.scenes.push(s);
    openScene = project.scenes.length - 1;
    changed();
  });

  /* ================= APERÇU ================= */
  const pCanvas = $('#previewCanvas'), pctx = pCanvas.getContext('2d');
  const audioEl = $('#audioEl');
  const player = { t: 0, playing: false, last: 0, raf: 0 };
  const voice = { blob: null, url: null, duration: 0 };

  function fmtTime(s) { s = Math.max(0, s); return Math.floor(s / 60) + ':' + String(Math.floor(s % 60)).padStart(2, '0'); }
  function drawPreview() {
    K.renderFrame(pctx, project, player.t, { watermark: plan.watermark });
    const total = K.totalDuration(project);
    $('#timeLbl').textContent = fmtTime(player.t) + ' / ' + fmtTime(total);
    $('#seek').value = Math.round((player.t / (total || 1)) * 1000);
  }
  function tick(now) {
    if (!player.playing) return;
    const total = K.totalDuration(project);
    if (voice.url && !audioEl.paused) player.t = audioEl.currentTime;
    else player.t += (now - player.last) / 1000;
    player.last = now;
    if (player.t >= total) { player.t = total; drawPreview(); pause(); player.t = 0; return; }
    drawPreview();
    player.raf = requestAnimationFrame(tick);
  }
  function play() {
    if (player.playing || rec.active) return;
    if (player.t >= K.totalDuration(project) - 0.05) player.t = 0;
    player.playing = true;
    player.last = performance.now();
    $('#playBtn').textContent = '❚❚';
    if (voice.url) { audioEl.currentTime = Math.min(player.t, voice.duration || player.t); audioEl.play().catch(() => {}); }
    player.raf = requestAnimationFrame(tick);
  }
  function pause() {
    player.playing = false;
    cancelAnimationFrame(player.raf);
    $('#playBtn').textContent = '▶';
    if (!audioEl.paused) audioEl.pause();
  }
  function seekTo(t) { player.t = Math.max(0, Math.min(t, K.totalDuration(project))); if (voice.url) audioEl.currentTime = player.t; drawPreview(); }
  $('#playBtn').addEventListener('click', () => player.playing ? pause() : play());
  $('#seek').addEventListener('input', (e) => seekTo((e.target.value / 1000) * K.totalDuration(project)));

  /* ================= VOIX ================= */
  const rec = { active: false, mr: null, stream: null, chunks: [], start: 0, marks: [], idx: 0, raf: 0 };
  const voiceMsg = (t, cls = '') => { const m = $('#voiceMsg'); m.textContent = t; m.className = 'msg ' + cls; };

  function updatePrompter(i) {
    const s = project.scenes[i];
    const n = project.scenes[i + 1];
    $('#pLabel').textContent = rec.active ? `● Scène ${i + 1} / ${project.scenes.length}` : 'Prompteur';
    if (rec.active) {
      $('#pNow').textContent = s ? (s.caption || s.title || '') : '';
      $('#pNext').textContent = n ? 'Ensuite : ' + (n.caption || n.title || '') : 'Dernière scène : appuie sur « Terminer ».';
    } else {
      $('#pNow').textContent = 'Appuie sur « Enregistrer » : lis le texte affiché, puis « Scène suivante ».';
      $('#pNext').textContent = s ? 'Première phrase : ' + (s.caption || s.title || '') : '';
    }
  }

  function audioMime() {
    const c = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/webm', 'audio/ogg;codecs=opus'];
    return window.MediaRecorder ? c.find((m) => MediaRecorder.isTypeSupported(m)) || '' : '';
  }

  async function startRec() {
    if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) return voiceMsg('Ton navigateur ne permet pas l\'enregistrement. Importe un fichier audio à la place.', 'bad');
    pause();
    try {
      rec.stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } });
    } catch (e) {
      return voiceMsg('Micro refusé ou indisponible. Autorise le micro dans ton navigateur (icône du cadenas), ou importe un audio.', 'bad');
    }
    const mime = audioMime();
    rec.mr = new MediaRecorder(rec.stream, mime ? { mimeType: mime } : undefined);
    rec.chunks = [];
    rec.mr.ondataavailable = (e) => e.data.size && rec.chunks.push(e.data);
    rec.mr.onstop = onRecStop;
    rec.marks = [];
    rec.idx = 0;
    rec.active = true;
    rec.mr.start(200);
    rec.start = performance.now();
    $('#recBtn').disabled = true; $('#recBtn').classList.add('rec');
    $('#nextBtn').disabled = false; $('#stopBtn').disabled = false;
    voiceMsg('Enregistrement en cours…');
    updatePrompter(0);
    const loop = () => {
      if (!rec.active) return;
      const el2 = (performance.now() - rec.start) / 1000 - (rec.marks[rec.idx - 1] || 0);
      const s = project.scenes[rec.idx];
      player.t = K.sceneStart(project, rec.idx) + Math.min(el2, (s ? s.duration : 1) - 0.05);
      drawPreview();
      rec.raf = requestAnimationFrame(loop);
    };
    rec.raf = requestAnimationFrame(loop);
  }

  function nextScene() {
    if (!rec.active) return;
    rec.marks.push((performance.now() - rec.start) / 1000);
    rec.idx++;
    if (rec.idx >= project.scenes.length) return stopRec();
    updatePrompter(rec.idx);
  }

  function stopRec() {
    if (!rec.active) return;
    if (rec.marks.length < project.scenes.length) rec.marks.push((performance.now() - rec.start) / 1000);
    rec.active = false;
    cancelAnimationFrame(rec.raf);
    rec.mr.stop();
    rec.stream.getTracks().forEach((t) => t.stop());
    $('#recBtn').disabled = false; $('#recBtn').classList.remove('rec');
    $('#nextBtn').disabled = true; $('#stopBtn').disabled = true;
  }

  function onRecStop() {
    const blob = new Blob(rec.chunks, { type: rec.mr.mimeType || 'audio/webm' });
    // cale chaque scène marquée sur la voix
    let prev = 0;
    rec.marks.forEach((m, i) => { if (project.scenes[i]) project.scenes[i].duration = Math.max(1, Math.round((m - prev) * 10) / 10); prev = m; });
    save();
    setVoice(blob, rec.marks[rec.marks.length - 1] || 0);
    updatePrompter(0);
    voiceMsg(`Voix enregistrée (${fmtTime(voice.duration)}). Les durées des scènes ont été calées sur ta voix.`, 'ok');
    player.t = 0;
    drawPreview();
  }

  function setVoice(blob, knownDuration) {
    if (voice.url) URL.revokeObjectURL(voice.url);
    voice.blob = blob;
    voice.url = URL.createObjectURL(blob);
    voice.duration = knownDuration || 0;
    audioEl.src = voice.url;
    $('#audioBox').hidden = false;
    if (!knownDuration) {
      audioEl.addEventListener('loadedmetadata', function once() {
        audioEl.removeEventListener('loadedmetadata', once);
        if (isFinite(audioEl.duration)) { voice.duration = audioEl.duration; voiceMsg(`Audio importé (${fmtTime(voice.duration)}). Vidéo : ${fmtTime(K.totalDuration(project))}.`, 'ok'); }
      });
    }
  }

  $('#recBtn').addEventListener('click', startRec);
  $('#nextBtn').addEventListener('click', nextScene);
  $('#stopBtn').addEventListener('click', stopRec);
  $('#audioFile').addEventListener('change', (e) => {
    const f = e.target.files[0];
    if (!f) return;
    if (f.size > 50 * 1024 * 1024) return voiceMsg('Fichier trop lourd (50 Mo max).', 'bad');
    pause();
    setVoice(f, 0);
    e.target.value = '';
  });
  $('#fitAudio').addEventListener('click', () => {
    const total = K.totalDuration(project);
    if (!voice.duration || !total) return voiceMsg('Durée de l\'audio inconnue, lance-le une fois puis réessaie.', 'warn');
    const k = voice.duration / total;
    project.scenes.forEach((s) => { s.duration = Math.max(1, Math.round(s.duration * k * 10) / 10); });
    changed();
    voiceMsg('Scènes calées sur la durée de l\'audio.', 'ok');
  });
  $('#dropAudio').addEventListener('click', () => {
    pause();
    if (voice.url) URL.revokeObjectURL(voice.url);
    voice.blob = voice.url = null; voice.duration = 0;
    audioEl.removeAttribute('src'); audioEl.load();
    $('#audioBox').hidden = true;
    voiceMsg('Voix supprimée.');
  });

  /* ================= EXPORT ================= */
  function renderQuota() {
    const left = Math.max(0, plan.quota - usedThisMonth());
    $('#quotaBox').replaceChildren(
      el('span', {}, el('b', {}, plan.name), ` · ${left} vidéo${left > 1 ? 's' : ''} restante${left > 1 ? 's' : ''} ce mois-ci`),
      el('button', { class: 'btn btn-ghost btn-sm', type: 'button', onclick: () => openPricing('') }, 'Voir les forfaits'));
    $('#exportInfo').textContent = `${plan.res}p · ${fmtTime(K.totalDuration(project))} · ${voice.url ? 'avec ta voix' : 'sans voix (tu peux en ajouter à l\'étape 3)'}${plan.watermark ? ' · filigrane' : ''}. L'export dure autant que la vidéo : garde cet onglet visible.`;
  }

  function videoMime(withAudio) {
    const c = withAudio
      ? ['video/mp4;codecs="avc1.42E01F,mp4a.40.2"', 'video/mp4;codecs=avc1,mp4a', 'video/mp4;codecs="avc1.4D401F,mp4a.40.2"', 'video/mp4', 'video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm']
      : ['video/mp4;codecs="avc1.42E01F"', 'video/mp4;codecs=avc1', 'video/mp4', 'video/webm;codecs=vp9', 'video/webm;codecs=vp8', 'video/webm'];
    return c.find((m) => MediaRecorder.isTypeSupported(m)) || '';
  }

  const exp = { running: false, cancel: false };
  async function exportVideo() {
    if (exp.running) return;
    const msg = (t, cls = '') => { const m = $('#exportMsg'); m.textContent = t; m.className = 'msg ' + cls; };
    if (usedThisMonth() >= plan.quota) return openPricing('Tu as utilisé tes ' + plan.quota + ' vidéos gratuites ce mois-ci.');
    if (!window.MediaRecorder || !HTMLCanvasElement.prototype.captureStream) return msg('Ton navigateur ne sait pas exporter de vidéo. Essaie avec Chrome, Edge, Firefox ou Safari récent.', 'bad');
    pause();
    exp.running = true; exp.cancel = false;
    $('#exportBtn').disabled = true; $('#cancelExport').hidden = false;
    $('#result').hidden = true; $('#progress').hidden = false; $('#progressBar').style.width = '0%';
    msg('Export en cours… garde cet onglet ouvert et visible.');

    const Wd = plan.res, Hd = Math.round(plan.res * 16 / 9);
    const canvas = document.createElement('canvas');
    canvas.width = Wd; canvas.height = Hd;
    const ctx = canvas.getContext('2d', { alpha: false });
    const total = K.totalDuration(project);
    const opts = { watermark: plan.watermark };
    K.renderFrame(ctx, project, 0, opts);
    const stream = canvas.captureStream(30);

    let actx = null, voiceEl = null;
    if (voice.url) {
      actx = new (window.AudioContext || window.webkitAudioContext)();
      voiceEl = new Audio(voice.url);
      const src = actx.createMediaElementSource(voiceEl);
      const dest = actx.createMediaStreamDestination();
      src.connect(dest);
      dest.stream.getAudioTracks().forEach((t) => stream.addTrack(t));
      await actx.resume();
    }

    const mime = videoMime(!!voice.url);
    let mr;
    try {
      mr = new MediaRecorder(stream, { mimeType: mime || undefined, videoBitsPerSecond: plan.res >= 1080 ? 8_000_000 : 5_000_000 });
    } catch (e) {
      mr = new MediaRecorder(stream);
    }
    const chunks = [];
    mr.ondataavailable = (e) => e.data.size && chunks.push(e.data);
    const done = new Promise((res) => { mr.onstop = res; });
    mr.start(500);
    if (voiceEl) await voiceEl.play().catch(() => {});
    const t0 = performance.now();

    await new Promise((resolve) => {
      const frame = () => {
        if (exp.cancel) return resolve();
        const t = voiceEl && !voiceEl.paused && !voiceEl.ended ? Math.max(voiceEl.currentTime, (performance.now() - t0) / 1000 - 0.5) : (performance.now() - t0) / 1000;
        K.renderFrame(ctx, project, Math.min(t, total), opts);
        $('#progressBar').style.width = Math.min(100, (t / total) * 100).toFixed(1) + '%';
        if (t >= total) return setTimeout(resolve, 250);
        requestAnimationFrame(frame);
      };
      requestAnimationFrame(frame);
    });

    mr.stop();
    await done;
    stream.getTracks().forEach((t) => t.stop());
    if (voiceEl) voiceEl.pause();
    if (actx) actx.close();
    exp.running = false;
    $('#exportBtn').disabled = false; $('#cancelExport').hidden = true; $('#progress').hidden = true;
    if (exp.cancel) return msg('Export annulé. Aucune vidéo décomptée.', 'warn');

    const type = (mr.mimeType || mime || 'video/webm').split(';')[0];
    const blob = new Blob(chunks, { type });
    const url = URL.createObjectURL(blob);
    const ext = type.includes('mp4') ? 'mp4' : 'webm';
    $('#resultVideo').src = url;
    const a = $('#downloadLink');
    if (a.href.startsWith('blob:')) URL.revokeObjectURL(a.href);
    a.href = url;
    a.download = `kineto-${new Date().toISOString().slice(0, 10)}.${ext}`;
    $('#result').hidden = false;
    addUsage();
    renderQuota();
    msg(`Vidéo prête (${(blob.size / 1024 / 1024).toFixed(1)} Mo).`, 'ok');
    const full = (mr.mimeType || mime || '').toLowerCase();
    const codec = $('#codecMsg');
    if (full.includes('avc1')) { codec.textContent = 'Format MP4 (H.264) : compatible TikTok, Instagram Reels et YouTube Shorts.'; codec.className = 'msg ok'; }
    else if (ext === 'mp4') { codec.textContent = 'Format MP4. Si TikTok refuse le fichier, ouvre-le dans CapCut puis exporte-le de nouveau.'; codec.className = 'msg warn'; }
    else { codec.textContent = 'Format WebM : ton navigateur ne sait pas faire de MP4. TikTok et Instagram ne l\'acceptent pas toujours : passe le fichier dans CapCut, ou exporte depuis Safari ou un Chrome récent.'; codec.className = 'msg warn'; }
  }
  $('#exportBtn').addEventListener('click', () => exportVideo().catch((e) => {
    exp.running = false;
    $('#exportBtn').disabled = false; $('#cancelExport').hidden = true; $('#progress').hidden = true;
    $('#exportMsg').textContent = 'L\'export a échoué : ' + (e && e.message ? e.message : 'erreur inconnue');
    $('#exportMsg').className = 'msg bad';
  }));
  $('#cancelExport').addEventListener('click', () => { exp.cancel = true; });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden && exp.running) { $('#exportMsg').textContent = 'Reviens sur cet onglet : l\'export se met en pause quand il est caché.'; $('#exportMsg').className = 'msg warn'; }
  });

  /* ================= DÉMARRAGE ================= */
  restart();
  route();
})();
