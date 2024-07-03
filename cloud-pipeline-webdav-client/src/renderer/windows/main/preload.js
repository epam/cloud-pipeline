const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('cloudData', {
  getConfiguration: () => ipcRenderer.invoke('getConfiguration'),
  getAvailableAdapters: () => ipcRenderer.invoke('getAvailableAdapters'),
  getAdapter: (options) => ipcRenderer.invoke('getAdapter', options),
  setAdapter: (index, adapter) => ipcRenderer.invoke('setAdapter', index, adapter),
  getAdapterContentsOnPath: (identifier, path) => ipcRenderer.invoke('getAdapterContentsOnPath', identifier, path),
  getAdapterPathComponents: (identifier, path) => ipcRenderer.invoke('getAdapterPathComponents', identifier, path),
  getCurrentPlatform: () => ipcRenderer.invoke('getCurrentPlatform'),

  submitCreateDirectoryOperation: (options) => ipcRenderer.invoke('submitCreateDirectoryOperation', options),
  submitCopyOperation: (options) => ipcRenderer.invoke('submitCopyOperation', options),
  submitMoveOperation: (options) => ipcRenderer.invoke('submitMoveOperation', options),
  submitRemoveOperation: (options) => ipcRenderer.invoke('submitRemoveOperation', options),
  abortOperation: (options) => ipcRenderer.invoke('abortOperation', options),

  getAutoUpdateVersion: (reCheck) => ipcRenderer.invoke('getAutoUpdateVersion', reCheck),
  autoUpdate: () => ipcRenderer.invoke('autoUpdate'),
});

contextBridge.exposeInMainWorld('cloudDataActions', {
  openConfigurationWindow: () => ipcRenderer.send('open-configuration-window'),
  openStorageAccessWindow: () => ipcRenderer.send('open-storage-access-window'),
});

contextBridge.exposeInMainWorld('cloudDataEvents', {
  reloadFileSystemsCallback: (callback) => ipcRenderer.on('reload-file-systems', callback),
  reportOperationCallback: (callback) => ipcRenderer.on('report-operation', callback),
  autoUpdateAvailableCallback: (callback) => ipcRenderer.on('auto-update-info', callback),
});
