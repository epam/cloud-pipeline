const { ipcMain } = require('electron');
const ipcMessage = require('../common/ipc-message');

/**
 * @param {{autoUpdateChecker: AutoUpdatesChecker}} bridgeOptions
 */
module.exports = function build(bridgeOptions) {
  const {
    autoUpdateChecker,
  } = bridgeOptions || {};
  if (!autoUpdateChecker) {
    throw new Error('Auto-update checker instance is required to build bridge for it');
  }
  async function getAutoUpdateVersion(reCheck = false) {
    if (reCheck) {
      await autoUpdateChecker.checkForUpdates();
    }
    return Promise.resolve({
      available: autoUpdateChecker.available,
      supported: autoUpdateChecker.supported,
    });
  }

  async function autoUpdate() {
    await autoUpdateChecker.update();
    return Promise.resolve(true);
  }

  ipcMain.handle('getAutoUpdateVersion', ipcMessage(getAutoUpdateVersion));
  ipcMain.handle('autoUpdate', ipcMessage(autoUpdate));
};
