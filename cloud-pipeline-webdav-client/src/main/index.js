const { app, BrowserWindow } = require('electron');
const logger = require('../shared/shared-logger');
const startup = require('./startup');

process.on('uncaughtException', (error) => {
  logger.error('Unhandled Error:', error.message);
  logger.error('Stack trace', error.stack);
});

process.on('unhandledRejection', (error) => {
  logger.error('Unhandled Promise Rejection:', error.message);
  logger.error('Stack trace', error.stack);
});

// Handle creating/removing shortcuts on Windows when installing/uninstalling.
// eslint-disable-next-line global-require
if (require('electron-squirrel-startup')) {
  logger.log('Quit');
  app.quit();
}

app.whenReady()
  .then(startup)
  .catch((error) => {
    console.error(error.message);
    app.quit();
  });

// Quit when all windows are closed.
app.on('window-all-closed', () => {
  // On OS X it is common for applications and their menu bar
  // to stay active until the user quits explicitly with Cmd + Q
  if (process.platform !== 'darwin') {
    logger.log('Quit');
    app.quit();
  }
});

app.on('activate', () => {
  // On OS X it's common to re-create a window in the app when the
  // dock icon is clicked and there are no other windows open.
  if (BrowserWindow.getAllWindows().length === 0) {
    (startup)();
  }
});
