const fs = require('fs');
const {log} = require('./application/models/log');
const localSettingsPath = require('./local-settings-path');

module.exports = function writeLocalConfiguration(configuration) {
  const localConfigPath = localSettingsPath();
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
  fs.writeFileSync(
    localConfigPath,
    Buffer.from(JSON.stringify(configuration, null, ' ')),
  );
}
