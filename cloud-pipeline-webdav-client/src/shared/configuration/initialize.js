const path = require("path");
const Configuration = require("./configuration");
const clientConfigDirectory = require("../utilities/client-config-directory");
const currentPlatform = require("../platform");

/**
 * @param {string} cwd
 * @returns {Promise<Configuration>}
 */
module.exports = async function initialize(cwd) {
  const configuration = new Configuration();
  const localConfigurationFile = path.join(cwd, 'settings.json');
  const webdavClientConfigFile = path.join(clientConfigDirectory, 'webdav.config');
  if (currentPlatform.isMacOS) {
    configuration.setConfigurationSources(
      webdavClientConfigFile,
      localConfigurationFile,
    );
    configuration.setUserConfigurationPath(webdavClientConfigFile);
  } else {
    configuration.setConfigurationSources(
      localConfigurationFile,
      webdavClientConfigFile,
    );
    configuration.setUserConfigurationPath(localConfigurationFile);
  }
  await configuration.initialize();
  return configuration;
};
