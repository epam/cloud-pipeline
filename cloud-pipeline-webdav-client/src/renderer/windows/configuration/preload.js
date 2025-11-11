const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('cloudData', {
  getConfiguration: () => ipcRenderer.invoke('getConfiguration'),
  getAvailableAdapters: () => ipcRenderer.invoke('getAvailableAdapters'),
  saveConfiguration: (configuration) => ipcRenderer.invoke('saveConfiguration', configuration),

  diagnoseApi: (payload) => ipcRenderer.invoke('diagnoseApi', payload),
  diagnoseFtp: (payload) => ipcRenderer.invoke('diagnoseFtp', payload),
  diagnoseWebdav: (payload) => ipcRenderer.invoke('diagnoseWebdav', payload),

  getAutoUpdateVersion: (reCheck) => ipcRenderer.invoke('getAutoUpdateVersion', reCheck),
  autoUpdate: () => ipcRenderer.invoke('autoUpdate'),
});

contextBridge.exposeInMainWorld('cloudDataActions', {
  closeConfigurationWindow: () => ipcRenderer.send('close-configuration-window'),
});

contextBridge.exposeInMainWorld('cloudDataEvents', {
  autoUpdateAvailableCallback: (callback) => ipcRenderer.on('auto-update-info', callback),
});
