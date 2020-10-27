const fs = require('fs');
const path = require('path');
const {log} = require('./application/models/log');

module.exports = function writeLocalConfiguration(configuration) {
  if (!fs.existsSync(path.join(require('os').homedir(), '.pipe-webdav-client'))) {
    fs.mkdirSync(path.join(require('os').homedir(), '.pipe-webdav-client'));
  }
  const localConfigPath = path.resolve(
    path.join(require('os').homedir(), '.pipe-webdav-client'),
    'webdav.config'
  );
  console.log(localConfigPath);
  console.log(configuration);
  log(`Writing configuration ${localConfigPath}:\n${JSON.stringify(configuration, null, ' ')}`);
  fs.writeFileSync(
    localConfigPath,
    Buffer.from(JSON.stringify(configuration, null, ' ')),
  );
}
