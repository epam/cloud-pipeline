const { ipcMain } = require('electron');
const ipcMessage = require('../common/ipc-message');

/**
 * @param {{currentPlatform: Platform}} bridgeOptions
 */
module.exports = function build(bridgeOptions) {
  const {
    currentPlatform,
  } = bridgeOptions;
  if (!currentPlatform) {
    throw new Error('Current platform info is required to build bridge for it');
  }
  async function getCurrentPlatform() {
    return Promise.resolve({
      isWindows: currentPlatform.isWindows,
      isMacOS: currentPlatform.isMacOS,
      isLinux: currentPlatform.isLinux,
    });
  }

  ipcMain.handle('getCurrentPlatform', ipcMessage(getCurrentPlatform));
};
