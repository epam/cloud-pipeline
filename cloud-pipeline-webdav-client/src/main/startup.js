const { app } = require('electron');
const path = require('path');
const AutoUpdateChecker = require('./auto-update');
const FileSystemAdapters = require('../shared/file-system-adapters');
const Application = require('./application');
const Operations = require('../shared/operations');
const logger = require('../shared/shared-logger');
const currentPlatform = require('../shared/platform');
const buildBridges = require('./bridges');
const initializeConfiguration = require('../shared/configuration/initialize');

async function initialize() {
  const configuration = await initializeConfiguration(path.dirname(app.getPath('exe')));
  if (configuration.ignoreCertificateErrors) {
    app.commandLine.appendSwitch('ignore-certificate-errors');
  } else {
    app.commandLine.removeSwitch('ignore-certificate-errors');
  }
  const fileSystemAdapters = new FileSystemAdapters(configuration);
  const autoUpdateChecker = new AutoUpdateChecker(configuration, currentPlatform);
  await fileSystemAdapters.defaultInitialize();
  const application = new Application(configuration);
  const operations = new Operations(fileSystemAdapters, application);
  buildBridges({
    adapters: fileSystemAdapters,
    autoUpdateChecker,
    configuration,
    operations,
    currentPlatform,
  });
  return {
    adapters: fileSystemAdapters,
    application,
    autoUpdateChecker,
    configuration,
    operations,
    platform: currentPlatform,
  };
}

let initializationPromise;
async function initializeOnce() {
  if (!initializationPromise) {
    initializationPromise = initialize();
  }
  return initializationPromise;
}

module.exports = async function startup() {
  try {
    // todo: show loading indicator page
    const {
      adapters,
      application,
      autoUpdateChecker,
      configuration,
    } = await initializeOnce();
    configuration.once('token-will-expire', (info) => application.tokenAlert(info));
    configuration.once('token-expired', (info) => application.tokenAlert(info));
    configuration.checkToken();
    // todo: close loading indicator page
    await application.openMainWindow();
    if (configuration.empty) {
      await application.openConfigurationsWindow();
    }
    adapters.on('reload-adapter', (adapter) => application.reloadFileSystems([adapter]));
    autoUpdateChecker.on('auto-update-info', application.autoUpdateAvailable.bind(application));
    await autoUpdateChecker.checkForUpdates(true);
  } catch (error) {
    logger.error(`General error occurred: ${error.message}`);
    logger.log('Quitting because of the error');
    app.quit();
  }
};
