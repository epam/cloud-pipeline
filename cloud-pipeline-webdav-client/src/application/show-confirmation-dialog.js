const { BrowserWindow, ipcMain } = require('electron');

export default function showConfirmationDialog (title) {
  const confirmationDialog = new BrowserWindow({
    width: 400,
    height: 120,
    show: false,
    useContentSize: true,
    alwaysOnTop: true,
    minimizable: false,
    maximizable: false,
    resizable: false,
    fullscreenable: false,
    modal: true,
    webPreferences: {
      nodeIntegration: true,
      enableRemoteModule: true
    },
  })
  confirmationDialog.loadURL(CONFIRMATION_DIALOG_WEBPACK_ENTRY);
  confirmationDialog.setMenuBarVisibility(false);
  confirmationDialog.removeMenu();
  confirmationDialog.on('ready-to-show', () => {
    confirmationDialog.webContents.send('show-confirmation', title);
    confirmationDialog.show();
  });
  return new Promise((resolve) => {
    let handled = false;
    const handle = function (event, confirmResult) {
      handled = true;
      confirmationDialog.close();
      resolve(confirmResult);
    }
    ipcMain.on('confirm', handle);
    confirmationDialog.on('closed', () => {
      ipcMain.removeListener('confirm', handle);
      if (!handled) {
        resolve(false);
      }
    });
  });
}
