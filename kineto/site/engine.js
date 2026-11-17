/* Kinéto — moteur de rendu (canvas). Aucun appel serveur.
 * Repère logique : 1080 × 1920. L'appelant règle l'échelle via renderFrame. */
(function (global) {
  'use strict';

  const W = 1080, H = 1920;
  const FONT = '"Inter","Segoe UI",system-ui,-apple-system,Roboto,Helvetica,Arial,sans-serif';

  const STYLES = {
    neon:    { name: 'Néon',    bg: ['#0b0820', '#22104a'], fg: '#ffffff', accent: '#39ff88', accent2: '#ff3dbb', muted: '#b4a9e6', glow: true,  free: true },
    papier:  { name: 'Papier',  bg: ['#f6f1e6', '#e8dfca'], fg: '#1b1b1b', accent: '#e4572e', accent2: '#2a6f97', muted: '#6b6355', glow: false, free: true },
    ocean:   { name: 'Océan',   bg: ['#021b2b', '#06506e'], fg: '#eafcff', accent: '#2ee6d6', accent2: '#ffd166', muted: '#93c6d2', glow: true,  free: false },
    solaire: { name: 'Solaire', bg: ['#ff8a1f', '#b0002e'], fg: '#ffffff', accent: '#ffe45c', accent2: '#2b0040', muted: '#ffe0cc', glow: false, free: false }
  };

  const TYPES = {
    titre: 'Titre', chiffre: 'Chiffre', colonnes: 'Évolution (colonnes)', barres: 'Barres',
    duel: 'Comparaison', liste: 'Liste / top', mythe: 'Mythe / réalité', fin: 'Fin'
  };

  /* ---------- outils ---------- */
  const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
  const easeOut = (t) => 1 - Math.pow(1 - t, 3);
  const easeBack = (t) => { const c = 1.70158; return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2); };
  const prog = (t, start, dur) => easeOut(clamp((t - start) / dur));

  function decimals(v) {
    const s = String(v);
    const i = s.indexOf('.');
    return i < 0 ? 0 : Math.min(2, s.length - i - 1);
  }
  function fmt(v, dec = 0) {
    return Number(v).toLocaleString('fr-FR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
  }
  function setFont(c, size, weight = 900) { c.font = `${weight} ${size}px ${FONT}`; }

  function wrap(c, text, maxW) {
    const words = String(text || '').split(/\s+/).filter(Boolean);
    const lines = [];
    let line = '';
    for (const w of words) {
      const test = line ? line + ' ' + w : w;
      if (c.measureText(test).width <= maxW || !line) line = test;
      else { lines.push(line); line = w; }
    }
    if (line) lines.push(line);
    return lines;
  }

  function fit(c, text, maxW, maxLines, size, min, weight = 900) {
    for (let s = size; s >= min; s -= 4) {
      setFont(c, s, weight);
      const lines = wrap(c, text, maxW);
      if (lines.length <= maxLines && lines.every((l) => c.measureText(l).width <= maxW)) return { lines, size: s };
    }
    setFont(c, min, weight);
    return { lines: wrap(c, text, maxW), size: min };
  }

  /* Dessine un bloc de lignes centré verticalement sur y. Renvoie la hauteur. */
  function drawBlock(c, block, x, y, weight = 900, lh = 1.12) {
    const { lines, size } = block;
    setFont(c, size, weight);
    c.textAlign = 'center';
    c.textBaseline = 'middle';
    const h = lines.length * size * lh;
    let yy = y - h / 2 + (size * lh) / 2;
    for (const l of lines) { c.fillText(l, x, yy); yy += size * lh; }
    return h;
  }

  function glow(c, S, color, blur = 40) {
    if (S.glow) { c.shadowColor = color; c.shadowBlur = blur; } else { c.shadowBlur = 0; }
  }
  function noGlow(c) { c.shadowBlur = 0; }

  function roundRect(c, x, y, w, h, r) {
    r = Math.min(r, w / 2, h / 2);
    c.beginPath();
    c.moveTo(x + r, y);
    c.arcTo(x + w, y, x + w, y + h, r);
    c.arcTo(x + w, y + h, x, y + h, r);
    c.arcTo(x, y + h, x, y, r);
    c.arcTo(x, y, x + w, y, r);
    c.closePath();
  }

  function itemsOf(s) { return Array.isArray(s.items) ? s.items.filter((i) => i && (i.label || i.value != null)) : []; }

  /* ---------- fond ---------- */
  function background(c, S, T) {
    const g = c.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, S.bg[0]);
    g.addColorStop(1, S.bg[1]);
    c.fillStyle = g;
    c.fillRect(0, 0, W, H);
    // formes douces qui dérivent
    const blobs = [
      [0.2, 0.25, 420, S.accent], [0.85, 0.6, 520, S.accent2], [0.4, 0.92, 380, S.accent]
    ];
    c.globalAlpha = 0.12;
    blobs.forEach(([bx, by, r, col], i) => {
      const x = bx * W + Math.sin(T * 0.4 + i * 2) * 60;
      const y = by * H + Math.cos(T * 0.3 + i) * 80;
      const rg = c.createRadialGradient(x, y, 0, x, y, r);
      rg.addColorStop(0, col);
      rg.addColorStop(1, 'rgba(0,0,0,0)');
      c.fillStyle = rg;
      c.fillRect(x - r, y - r, r * 2, r * 2);
    });
    c.globalAlpha = 1;
  }

  /* ---------- scènes ---------- */
  const R = {};

  R.titre = (c, s, t, d, S) => {
    const a = prog(t, 0, 0.5);
    const title = fit(c, s.title || '', 920, 5, 150, 64);
    const th = title.lines.length * title.size * 1.12;
    const sub = s.sub ? fit(c, s.sub, 900, 3, 64, 36, 600) : null;
    const total = th + (sub ? 70 + sub.lines.length * sub.size * 1.12 : 0);
    const top = 860 - total / 2;
    c.save();
    c.translate(540, top + th / 2);
    const sc = 0.85 + 0.15 * easeBack(clamp(t / 0.6));
    c.scale(sc, sc);
    c.globalAlpha = a;
    c.fillStyle = S.fg;
    glow(c, S, S.accent, 30);
    drawBlock(c, title, 0, 0);
    c.restore();
    noGlow(c);
    if (sub) {
      c.globalAlpha = prog(t, 0.35, 0.5);
      c.fillStyle = S.muted;
      drawBlock(c, sub, 540, top + th + 70 + (sub.lines.length * sub.size * 1.12) / 2, 600);
      c.globalAlpha = 1;
    }
    // trait d'accent
    c.fillStyle = S.accent;
    const lw = 220 * prog(t, 0.2, 0.6);
    c.fillRect(540 - lw / 2, top - 70, lw, 12);
  };

  R.chiffre = (c, s, t, d, S) => {
    if (s.title) {
      c.globalAlpha = prog(t, 0, 0.4);
      c.fillStyle = S.muted;
      drawBlock(c, fit(c, s.title, 900, 3, 70, 40, 700), 540, 560, 700);
      c.globalAlpha = 1;
    }
    const target = Number(s.value) || 0;
    const dec = decimals(target);
    const k = easeOut(clamp(t / Math.min(1.8, Math.max(0.8, d * 0.55))));
    const txt = fmt(target * k, dec) + (s.unit ? ' ' + s.unit : '');
    const finalTxt = fmt(target, dec) + (s.unit ? ' ' + s.unit : '');
    // taille calculée sur la valeur finale pour éviter que le texte « saute »
    const b = fit(c, finalTxt, 960, 2, 320, 90);
    c.save();
    c.translate(540, 900);
    const pop = t > d * 0.55 ? 1 + 0.05 * Math.sin(clamp((t - d * 0.55) / 0.3) * Math.PI) : 1;
    c.scale(pop, pop);
    c.fillStyle = S.accent;
    glow(c, S, S.accent, 60);
    drawBlock(c, { lines: wrap((setFont(c, b.size), c), txt, 960), size: b.size }, 0, 0);
    c.restore();
    noGlow(c);
    if (s.sub) {
      c.globalAlpha = prog(t, 0.7, 0.5);
      c.fillStyle = S.fg;
      drawBlock(c, fit(c, s.sub, 900, 4, 76, 40, 800), 540, 1260, 800);
      c.globalAlpha = 1;
    }
  };

  function heading(c, s, S, t, y = 380) {
    if (!s.title) return;
    c.globalAlpha = prog(t, 0, 0.4);
    c.fillStyle = S.fg;
    drawBlock(c, fit(c, s.title, 920, 3, 92, 50), 540, y);
    c.globalAlpha = 1;
  }

  R.colonnes = (c, s, t, d, S) => {
    heading(c, s, S, t);
    const items = itemsOf(s).slice(0, 7);
    if (!items.length) return;
    const max = Math.max(...items.map((i) => Math.abs(Number(i.value) || 0)), 1);
    const n = items.length, gap = 28, area = 920;
    const bw = (area - gap * (n - 1)) / n;
    const base = 1480, maxH = 760;
    items.forEach((it, i) => {
      const p = prog(t, 0.3 + i * 0.22, 0.8);
      const v = Number(it.value) || 0;
      const h = Math.max(6, (Math.abs(v) / max) * maxH * p);
      const x = 80 + i * (bw + gap);
      c.fillStyle = i === n - 1 ? S.accent : S.accent2;
      glow(c, S, c.fillStyle, 25);
      roundRect(c, x, base - h, bw, h, Math.min(18, bw / 4));
      c.fill();
      noGlow(c);
      c.globalAlpha = p;
      c.fillStyle = S.fg;
      const vt = fmt(v * p, decimals(v)) + (s.unit ? (s.unit.length <= 2 ? ' ' + s.unit : '') : '');
      drawBlock(c, fit(c, vt, bw + gap - 4, 1, 52, 22), x + bw / 2, base - h - 40);
      c.fillStyle = S.muted;
      drawBlock(c, fit(c, it.label || '', bw + gap - 4, 2, 44, 20, 700), x + bw / 2, base + 60, 700);
      c.globalAlpha = 1;
    });
  };

  R.barres = (c, s, t, d, S) => {
    heading(c, s, S, t);
    const items = itemsOf(s).slice(0, 6);
    if (!items.length) return;
    const max = Math.max(...items.map((i) => Math.abs(Number(i.value) || 0)), 1);
    const rowH = Math.min(190, 1000 / items.length);
    const top = 620;
    items.forEach((it, i) => {
      const p = prog(t, 0.3 + i * 0.2, 0.8);
      const y = top + i * rowH;
      const v = Number(it.value) || 0;
      c.globalAlpha = clamp(p * 2);
      c.fillStyle = S.fg;
      setFont(c, 46, 800);
      c.textAlign = 'left';
      c.textBaseline = 'alphabetic';
      c.fillText(fit(c, it.label || '', 600, 1, 46, 26, 800).lines[0] || '', 80, y);
      const bw = Math.max(10, (Math.abs(v) / max) * 700 * p);
      c.fillStyle = i === 0 ? S.accent : S.accent2;
      glow(c, S, c.fillStyle, 20);
      roundRect(c, 80, y + 20, bw, 60, 14);
      c.fill();
      noGlow(c);
      c.fillStyle = S.fg;
      setFont(c, 48, 900);
      c.textBaseline = 'middle';
      c.fillText(fmt(v * p, decimals(v)) + (s.unit ? ' ' + s.unit : ''), 80 + bw + 24, y + 52);
      c.globalAlpha = 1;
    });
  };

  R.duel = (c, s, t, d, S) => {
    heading(c, s, S, t, 360);
    const [a, b] = itemsOf(s);
    if (!a || !b) return;
    const va = Number(a.value) || 0, vb = Number(b.value) || 0;
    const max = Math.max(Math.abs(va), Math.abs(vb), 1);
    const base = 1420, maxH = 700;
    [[a, va, 300, S.accent2, 0.3], [b, vb, 780, S.accent, 0.6]].forEach(([it, v, x, col, st]) => {
      const p = prog(t, st, 0.9);
      const h = Math.max(8, (Math.abs(v) / max) * maxH * p);
      c.fillStyle = col;
      glow(c, S, col, 30);
      roundRect(c, x - 130, base - h, 260, h, 24);
      c.fill();
      noGlow(c);
      c.fillStyle = S.fg;
      c.globalAlpha = p;
      drawBlock(c, fit(c, fmt(v * p, decimals(v)) + (s.unit ? ' ' + s.unit : ''), 420, 1, 80, 34), x, base - h - 60);
      c.fillStyle = S.muted;
      drawBlock(c, fit(c, it.label || '', 420, 2, 56, 28, 800), x, base + 80, 800);
      c.globalAlpha = 1;
    });
    c.globalAlpha = prog(t, 0.2, 0.4);
    c.fillStyle = S.fg;
    setFont(c, 70, 900);
    c.textAlign = 'center';
    c.fillText('VS', 540, 760);
    c.globalAlpha = 1;
  };

  R.liste = (c, s, t, d, S) => {
    heading(c, s, S, t, 360);
    const items = itemsOf(s).slice(0, 6);
    const n = items.length;
    if (!n) return;
    const rowH = Math.min(180, 1050 / n);
    const step = Math.min(0.6, (d * 0.7) / n);
    items.forEach((it, i) => {
      const p = prog(t, 0.3 + i * step, 0.5);
      if (p <= 0) return;
      const y = 600 + i * rowH;
      c.save();
      c.globalAlpha = p;
      c.translate((1 - p) * 120, 0);
      c.fillStyle = i === 0 ? S.accent : S.accent2;
      glow(c, S, c.fillStyle, 20);
      c.beginPath();
      c.arc(140, y, 54, 0, Math.PI * 2);
      c.fill();
      noGlow(c);
      c.fillStyle = S.bg[0];
      setFont(c, 56, 900);
      c.textAlign = 'center';
      c.textBaseline = 'middle';
      c.fillText(String(i + 1), 140, y + 2);
      c.textAlign = 'left';
      c.fillStyle = S.fg;
      const hasVal = it.value != null && it.value !== '';
      const lab = fit(c, it.label || '', hasVal ? 560 : 780, 1, 58, 28, 800);
      setFont(c, lab.size, 800);
      c.fillText(lab.lines[0] || '', 230, y);
      if (hasVal) {
        c.textAlign = 'right';
        c.fillStyle = S.muted;
        setFont(c, 48, 700);
        c.fillText(String(it.display || fmt(it.value, decimals(it.value))) + (s.unit ? ' ' + s.unit : ''), 1000, y);
      }
      c.restore();
    });
  };

  R.mythe = (c, s, t, d, S) => {
    const split = d * 0.48;
    const tag = (label, col, y, a) => {
      c.globalAlpha = a;
      setFont(c, 64, 900);
      const w = c.measureText(label).width + 80;
      c.fillStyle = col;
      roundRect(c, 540 - w / 2, y - 55, w, 110, 55);
      c.fill();
      c.fillStyle = S.bg[0];
      c.textAlign = 'center';
      c.textBaseline = 'middle';
      c.fillText(label, 540, y + 3);
      c.globalAlpha = 1;
    };
    if (t < split) {
      const a = prog(t, 0, 0.4) * (1 - clamp((t - split + 0.25) / 0.25));
      tag('MYTHE', S.accent2, 620, a);
      c.globalAlpha = a;
      c.fillStyle = S.fg;
      const b = fit(c, s.title || '', 900, 5, 100, 48);
      const h = drawBlock(c, b, 540, 960);
      // barré
      const sp = prog(t, split * 0.55, 0.4);
      c.strokeStyle = S.accent2;
      c.lineWidth = 16;
      c.lineCap = 'round';
      c.beginPath();
      c.moveTo(110, 960 + h / 2);
      c.lineTo(110 + 860 * sp, 960 - h / 2);
      c.stroke();
      c.globalAlpha = 1;
    } else {
      const tt = t - split;
      tag('RÉALITÉ', S.accent, 620, prog(tt, 0, 0.4));
      c.globalAlpha = prog(tt, 0.15, 0.5);
      c.fillStyle = S.fg;
      glow(c, S, S.accent, 25);
      drawBlock(c, fit(c, s.sub || '', 900, 6, 92, 44), 540, 980);
      noGlow(c);
      c.globalAlpha = 1;
    }
  };

  R.fin = (c, s, t, d, S) => {
    const a = prog(t, 0, 0.5);
    c.globalAlpha = a;
    c.fillStyle = S.fg;
    glow(c, S, S.accent, 30);
    drawBlock(c, fit(c, s.title || '', 920, 4, 120, 56), 540, 820);
    noGlow(c);
    if (s.sub) {
      c.globalAlpha = prog(t, 0.4, 0.5);
      c.fillStyle = S.muted;
      drawBlock(c, fit(c, s.sub, 900, 3, 50, 30, 600), 540, 1180, 600);
    }
    c.globalAlpha = 1;
  };

  /* ---------- sous-titres mot à mot ---------- */
  function captions(c, text, t, d, S) {
    const words = String(text || '').split(/\s+/).filter(Boolean);
    if (!words.length) return;
    const per = (d * 0.94) / words.length;
    const idx = Math.min(words.length - 1, Math.floor(t / per));
    const start = Math.floor(idx / 4) * 4;
    const chunk = words.slice(start, start + 4);
    let size = 62;
    setFont(c, size, 900);
    const space = 18;
    let widths = chunk.map((w) => c.measureText(w).width);
    let total = widths.reduce((a, b) => a + b, 0) + space * (chunk.length - 1);
    if (total > 940) {
      size = Math.floor(size * 940 / total);
      setFont(c, size, 900);
      widths = chunk.map((w) => c.measureText(w).width);
      total = widths.reduce((a, b) => a + b, 0) + space * (chunk.length - 1);
    }
    const y = 1650;
    c.fillStyle = 'rgba(0,0,0,0.55)';
    roundRect(c, 540 - total / 2 - 30, y - size * 0.75, total + 60, size * 1.5, 22);
    c.fill();
    let x = 540 - total / 2;
    c.textAlign = 'left';
    c.textBaseline = 'middle';
    chunk.forEach((w, i) => {
      c.fillStyle = start + i === idx ? S.accent : '#ffffff';
      if (start + i > idx) c.globalAlpha = 0.55;
      c.fillText(w, x, y + 2);
      c.globalAlpha = 1;
      x += widths[i] + space;
    });
  }

  /* ---------- projet ---------- */
  function totalDuration(p) {
    return (p.scenes || []).reduce((a, s) => a + Math.max(0.5, Number(s.duration) || 3), 0);
  }

  function sceneAt(p, time) {
    let acc = 0;
    const sc = p.scenes || [];
    for (let i = 0; i < sc.length; i++) {
      const d = Math.max(0.5, Number(sc[i].duration) || 3);
      if (time < acc + d || i === sc.length - 1) return { index: i, scene: sc[i], t: Math.max(0, time - acc), d, start: acc };
      acc += d;
    }
    return null;
  }

  function sceneStart(p, index) {
    let acc = 0;
    for (let i = 0; i < index; i++) acc += Math.max(0.5, Number(p.scenes[i].duration) || 3);
    return acc;
  }

  /* Dessine l'image du projet à l'instant `time`. Le canvas peut avoir n'importe quelle taille 9:16. */
  function renderFrame(c, p, time, opts = {}) {
    const k = c.canvas.width / W;
    c.setTransform(k, 0, 0, k, 0, 0);
    const S = STYLES[p.style] || STYLES.neon;
    background(c, S, time);
    const cur = sceneAt(p, time);
    if (cur) {
      const fn = R[cur.scene.type] || R.titre;
      c.save();
      c.globalAlpha = 1;
      const fadeIn = clamp(cur.t / 0.25);
      const fadeOut = clamp((cur.d - cur.t) / 0.2);
      c.globalAlpha = Math.min(fadeIn, cur.index === p.scenes.length - 1 ? 1 : fadeOut);
      // les fonctions de scène gèrent leur propre alpha : on applique le fondu via un calque
      if (c.globalAlpha < 1) {
        const a = c.globalAlpha;
        c.globalAlpha = 1;
        fn(c, cur.scene, cur.t, cur.d, S);
        c.globalAlpha = 1 - a;
        background(c, S, time);
      } else {
        fn(c, cur.scene, cur.t, cur.d, S);
      }
      c.restore();
      c.globalAlpha = 1;
      noGlow(c);
      if (opts.captions !== false) captions(c, cur.scene.caption, cur.t, cur.d, S);
    }
    // barre de progression
    const total = totalDuration(p) || 1;
    c.fillStyle = S.accent;
    c.globalAlpha = 0.85;
    c.fillRect(0, 0, W * clamp(time / total), 10);
    c.globalAlpha = 1;
    if (opts.watermark) {
      setFont(c, 38, 800);
      c.textAlign = 'right';
      c.textBaseline = 'alphabetic';
      c.fillStyle = 'rgba(255,255,255,0.85)';
      c.strokeStyle = 'rgba(0,0,0,0.35)';
      c.lineWidth = 6;
      c.strokeText('Fait avec Kinéto', 1040, 1870);
      c.fillText('Fait avec Kinéto', 1040, 1870);
    }
  }

  function sampleProject(style = 'neon') {
    return {
      style,
      scenes: [
        { type: 'titre', title: 'Combien d\'heures dort un étudiant ?', sub: 'Exemple fictif', caption: 'Tu dors assez ? La plupart des étudiants, non.', duration: 3.2 },
        { type: 'chiffre', title: 'Par nuit, en moyenne', value: 6.2, unit: 'h', sub: 'contre 8 h recommandées', caption: 'Six heures et quelques en moyenne, bien loin des huit recommandées.', duration: 4.2 },
        { type: 'colonnes', title: 'Heures de sommeil', unit: 'h', items: [{ label: 'Lycée', value: 7.4 }, { label: 'Licence', value: 6.6 }, { label: 'Master', value: 6.2 }], caption: 'Et plus les études avancent, moins on dort.', duration: 4.2 },
        { type: 'fin', title: 'Et toi, tu dors combien ?', sub: 'Exemple fictif · chiffres inventés', caption: 'Dis-le en commentaire.', duration: 3 }
      ]
    };
  }

  global.Kineto = { W, H, STYLES, TYPES, renderFrame, totalDuration, sceneAt, sceneStart, sampleProject, fmt };
})(window);
