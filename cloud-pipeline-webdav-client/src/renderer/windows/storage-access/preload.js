const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('cloudData', {
  getStoragesWithDavAccessInfo: () => ipcRenderer.invoke('getStoragesWithDavAccessInfo'),
  requestDavAccess: (identifier) => ipcRenderer.invoke('requestDavAccess', identifier),
});

contextBridge.exposeInMainWorld('cloudDataActions', {
  closeStorageAccessWindow: () => ipcRenderer.send('close-storage-access-window'),
});

contextBridge.exposeInMainWorld('cloudDataEvents', {
});
