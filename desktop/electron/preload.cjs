const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('openatom', {
  platform: process.platform,
  auth: {
    login: () => ipcRenderer.invoke('auth:login'),
    logout: () => ipcRenderer.invoke('auth:logout'),
    getSession: () => ipcRenderer.invoke('auth:get-session'),
    getAccessToken: () => ipcRenderer.invoke('auth:get-access-token'),
    refresh: () => ipcRenderer.invoke('auth:refresh'),
  },
  preview: {
    open: (fileId) => ipcRenderer.invoke('preview:open', fileId),
    onNavigate: (listener) => {
      const handler = (_event, payload) => listener(payload)
      ipcRenderer.on('preview:navigate', handler)
      return () => ipcRenderer.removeListener('preview:navigate', handler)
    },
  },
  updater: {
    check: () => ipcRenderer.invoke('updater:check'),
    onStatus: (listener) => {
      const handler = (_event, payload) => listener(payload)
      ipcRenderer.on('updater:status', handler)
      return () => ipcRenderer.removeListener('updater:status', handler)
    },
  },
})
