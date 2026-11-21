const { app, BrowserWindow, ipcMain, session } = require('electron');
const path = require('path');

function createWindow() {
  const win = new BrowserWindow({
    width: 1400, height: 900, backgroundColor: '#05060f', title: 'Orion',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), webviewTag: true, contextIsolation: true }
  });
  win.setMenuBarVisibility(false);
  win.loadFile('index.html');
}

// Proxy / VPN : appliqué à la session des onglets (partition persist:web)
ipcMain.handle('set-proxy', async (_e, proxyUrl) => {
  const ses = session.fromPartition('persist:web');
  const cfg = proxyUrl ? { proxyRules: proxyUrl, proxyBypassRules: '<local>' } : { mode: 'direct' };
  await ses.setProxy(cfg);
  await ses.closeAllConnections();
  return true;
});

// Vérifie le pays/IP vu par les sites (à travers le proxy)
ipcMain.handle('check-ip', async () => {
  const ses = session.fromPartition('persist:web');
  const r = await ses.fetch('https://ipinfo.io/json');
  return r.json();
});

// Appel à Claude depuis le processus principal (la clé ne quitte pas votre PC)
ipcMain.handle('ask-claude', async (_e, { apiKey, model, system, messages }) => {
  const r = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: { 'x-api-key': apiKey, 'anthropic-version': '2023-06-01', 'content-type': 'application/json' },
    body: JSON.stringify({
      model, max_tokens: 2048, system, messages,
      tools: [{ type: 'web_search_20250305', name: 'web_search', max_uses: 5 }]
    })
  });
  const data = await r.json();
  if (!r.ok) throw new Error(data?.error?.message || ('Erreur ' + r.status));
  return data;
});

// Les liens « nouvelle fenêtre » des sites s'ouvrent dans un nouvel onglet Orion
app.on('web-contents-created', (_e, wc) => {
  if (wc.getType() !== 'webview') return;
  wc.setWindowOpenHandler(({ url }) => {
    const host = wc.hostWebContents;
    if (host) host.executeJavaScript(`newTab(${JSON.stringify(url)})`);
    return { action: 'deny' };
  });
});

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
