const { app, BrowserWindow, ipcMain } = require('electron');
const readWebdavConfiguration = require('./read-webdav-configuration');
const {submit} = require('./application/models/commands');

import axios from 'axios';
axios.defaults.adapter = require('axios/lib/adapters/http');

const OPERATION_WIDTH = 400;
const OPERATION_HEIGHT = 45;

const webdavClientConfig = readWebdavConfiguration();
global.webdavClient = {
  config: webdavClientConfig,
};

if (webdavClientConfig.ignoreCertificateErrors) {
  console.log('"ignore-certificate-errors": true');
  app.commandLine.appendSwitch('ignore-certificate-errors');
}

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

let operationsWindowId;

const createOperationsWindow = (parent) => {
  const operationsWindow = new BrowserWindow({
    width: OPERATION_WIDTH,
    height: OPERATION_HEIGHT,
    alwaysOnTop: true,
    show: false,
    minimizable: false,
    maximizable: false,
    resizable: false,
    fullscreenable: false,
    parent,
    webPreferences: {
      nodeIntegration: true,
      enableRemoteModule: true
    },
  })
  operationsWindow.loadURL(OPERATIONS_WINDOW_WEBPACK_ENTRY);
  operationsWindow.setMenuBarVisibility(false);
  operationsWindow.removeMenu();
  operationsWindow.on('closed', () => {
    operationsWindowId = undefined;
  });
  return operationsWindow;
}

const resizeOperationsWindow = () => {
  if (operationsWindowId) {
    const window = BrowserWindow.fromId(operationsWindowId);
    window.setBounds({
      width: OPERATION_WIDTH,
      height: Math.min(
        600,
        Math.max(1, (global.operations || []).filter(o => !o.finished).length) * OPERATION_HEIGHT + 30
      )
    });
  }
}

const showOperationsWindow = (parent) => {
  if (!operationsWindowId) {
    operationsWindowId = createOperationsWindow(parent).id;
  }
  const window = BrowserWindow.fromId(operationsWindowId);
  resizeOperationsWindow();
  window.show();
}

const hideOperationsWindow = () => {
  if (operationsWindowId) {
    const window = BrowserWindow.fromId(operationsWindowId);
    window.hide();
  }
}

// This method will be called when Electron has finished
// initialization and is ready to create browser windows.
// Some APIs can only be used after this event occurs.
app.on('ready', () => {
  const mainWindow = createWindow();
  global.operations = [];
  ipcMain.on('operation-start', function (event, ...args) {
    const {operation, promise} = submit(mainWindow, ...args);
    if (operation) {
      global.operations.push(operation);
      showOperationsWindow(mainWindow);
      if (promise) {
        promise.then(() => {
          mainWindow.webContents.send('operation-end');
          const operations = global.operations;
          if (operations.filter(o => !o.finished).length === 0) {
            hideOperationsWindow();
          } else {
            resizeOperationsWindow();
          }
        })
      }
    }
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
    createWindow();
  }
});

// In this file you can include the rest of your app's specific main process
// code. You can also put them in separate files and import them here.
