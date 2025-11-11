const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('cloudDataEvents', {
  registerDialogPropertiesCallback: (callback) => ipcRenderer.on(
    'dialog-properties',
    (event, data) => (typeof callback === 'function' ? callback(data) : undefined),
  ),
  dialogResponse: (response) => ipcRenderer.send('dialog-response', response),
  registerDialogResponseValidationCallback: (callback) => ipcRenderer.on(
    'dialog-response-validation',
    (event, data) => (typeof callback === 'function' ? callback(data) : undefined),
  ),
});
