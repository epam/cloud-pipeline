const { ipcMain } = require('electron');
const ipcMessage = require('../common/ipc-message');

/**
 * @param {{configuration: Configuration}} bridgeOptions
 */
module.exports = function build(bridgeOptions) {
  const {
    configuration,
  } = bridgeOptions;
  if (!configuration) {
    throw new Error('Configuration is required to build bridge for it');
  }
  async function getConfiguration() {
    await configuration.initialize();
    return Promise.resolve({
      api: configuration.api,
      server: configuration.server,
      user: configuration.username,
      password: configuration.password,
      version: configuration.version,
      componentVersion: configuration.componentVersion,
      ignoreCertificateErrors: configuration.ignoreCertificateErrors,
      updatePermissions: configuration.updatePermissions,
      ftp: configuration.ftpServers,
      appName: configuration.name,
      logsEnabled: configuration.logsEnabled,
    });
  }

  async function saveConfiguration(newConfiguration) {
    const {
      user,
      username = user,
    } = newConfiguration || {};
    configuration.save({
      username,
      ...newConfiguration,
    });
    return Promise.resolve(true);
  }

  ipcMain.handle('getConfiguration', ipcMessage(getConfiguration));
  ipcMain.handle('saveConfiguration', ipcMessage(saveConfiguration));
};
