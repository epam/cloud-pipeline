const fs = require('fs');
const path = require('path');
const os = require('os');

module.exports = function getNetworkLogFileMask() {
  if (!fs.existsSync(path.join(os.homedir(), '.pipe-webdav-client'))) {
    fs.mkdirSync(path.join(os.homedir(), '.pipe-webdav-client'));
  }
  const fileName = '[DATE]-network-log.json';
  return path.join(os.homedir(), '.pipe-webdav-client', fileName);
}
