const { app, BrowserWindow } = require('electron');
const readWebdavConfiguration = require('./read-webdav-configuration');

// IMPORTANT!!!
// You should remove "browser": ... config from package.json of axios module MANUALLY!
require('axios').defaults.adapter = require('axios/lib/adapters/http');

// Handle creating/removing shortcuts on Windows when installing/uninstalling.
if (require('electron-squirrel-startup')) { // eslint-disable-line global-require
  app.quit();
}

const createWindow = () => {
  // Create the browser window.
  const mainWindow = new BrowserWindow({
    width: 900,
    height: 600,
    webPreferences: {
      nodeIntegration: true,
      enableRemoteModule: true
    },
  });
  // and load the index.html of the app.
  mainWindow.loadURL(MAIN_WINDOW_WEBPACK_ENTRY);
  mainWindow.setMenuBarVisibility(false);
  mainWindow.removeMenu();
  // Open the DevTools.
  // mainWindow.webContents.openDevTools();
  return mainWindow;
};

// This method will be called when Electron has finished
// initialization and is ready to create browser windows.
// Some APIs can only be used after this event occurs.
app.on('ready', () => {
  readWebdavConfiguration()
    .then((webdavClientConfig) => {
      global.webdavClient = {
        config: webdavClientConfig,
      };
      if (webdavClientConfig.ignoreCertificateErrors) {
        console.log('"ignore-certificate-errors": true');
        app.commandLine.appendSwitch('ignore-certificate-errors');
      }
      createWindow();
    });
});

// Quit when all windows are closed.
app.on('window-all-closed', () => {
  // On OS X it is common for applications and their menu bar
  // to stay active until the user quits explicitly with Cmd + Q
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  // On OS X it's common to re-create a window in the app when the
  // dock icon is clicked and there are no other windows open.
  if (BrowserWindow.getAllWindows().length === 0) {
    readWebdavConfiguration()
      .then((webdavClientConfig) => {
        global.webdavClient = {
          config: webdavClientConfig,
        };
        if (webdavClientConfig.ignoreCertificateErrors) {
          console.log('"ignore-certificate-errors": true');
          app.commandLine.appendSwitch('ignore-certificate-errors');
        }
        createWindow();
      });
  }
});
