const fs = require('fs');
const path = require('path');

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
  fs.writeFileSync(
    localConfigPath,
    Buffer.from(JSON.stringify(configuration, null, ' ')),
  );
}
