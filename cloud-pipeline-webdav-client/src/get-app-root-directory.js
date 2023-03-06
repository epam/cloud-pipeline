const electron = require('electron');
const path = require('path');

module.exports = function getRoot(forceExePath = false) {
  const app = electron.app || electron.remote.app;
  if (process.platform === 'darwin' && !forceExePath) {
    return path.dirname(path.join(app.getPath('exe'), '/../../../' ));
  }  else {
    return path.dirname(app.getPath('exe'));
  }
}
