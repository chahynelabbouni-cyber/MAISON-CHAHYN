const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('orion', {
  setProxy: (u) => ipcRenderer.invoke('set-proxy', u),
  checkIp: () => ipcRenderer.invoke('check-ip'),
  askClaude: (p) => ipcRenderer.invoke('ask-claude', p)
});
