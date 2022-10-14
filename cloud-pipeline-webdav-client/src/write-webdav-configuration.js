const fs = require('fs');
const path = require('path');
const {log} = require('./application/models/log');
const localSettingsPath = require('./local-settings-path');
const homeDirectorySettingsPath = require('./home-directory-settings-path');

module.exports = function writeLocalConfiguration(configuration) {
  /*
  For MacOS we store user-defined settings at the home directory (~/.pipe-webdav-client/webdav.config),
  because of the "sandbox" limitations.
  For other platforms we store user-defined settings at the app's directory.
   */
  const localConfigPath = process.platform === 'darwin'
    ? homeDirectorySettingsPath
    : localSettingsPath();
  if (configuration) {
    if (configuration.server) {
      const parts = configuration.server.split('/');
      /*
      scheme:
      empty
      server
      webdav
      username
      ...
       */
      if (parts.length > 4) {
        parts[4] = parts[4].toUpperCase();
      }
      configuration.server = parts.join('/');
    }
    if (configuration.username) {
      configuration.username = configuration.username.toUpperCase();
    }
  }
  console.log(localConfigPath);
  console.log(configuration);
  log(`Writing configuration ${localConfigPath}:\n${JSON.stringify(configuration, null, ' ')}`);
  if (!fs.existsSync(path.dirname(localConfigPath))) {
    fs.mkdirSync(path.dirname(localConfigPath));
  }
  fs.writeFileSync(
    localConfigPath,
    Buffer.from(JSON.stringify(configuration, null, ' ')),
  );
}
