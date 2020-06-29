const { BrowserWindow, ipcMain } = require('electron');

export default function createDirectoryNameDialog () {
  const directoryNameDialog = new BrowserWindow({
    width: 400,
    height: 120,
    show: false,
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
  directoryNameDialog.loadURL(DIRECTORY_NAME_DIALOG_WEBPACK_ENTRY);
  directoryNameDialog.setMenuBarVisibility(false);
  directoryNameDialog.removeMenu();
  directoryNameDialog.on('ready-to-show', () => {
    directoryNameDialog.show();
  });
  return new Promise((resolve) => {
    let handled = false;
    const handle = function (event, directoryName) {
      handled = true;
      directoryNameDialog.close();
      resolve(directoryName);
    }
    ipcMain.on('directory-name-dialog', handle);
    directoryNameDialog.on('closed', () => {
      ipcMain.removeListener('directory-name-dialog', handle);
      if (!handled) {
        resolve(undefined);
      }
    });
  });
}
