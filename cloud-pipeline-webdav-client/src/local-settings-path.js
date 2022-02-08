const electron = require('electron');
const path = require('path');

function getRoot() {
  const app = electron.app || electron.remote.app;
  if (process.platform === 'darwin' ) {
    return path.dirname(path.join(app.getPath('exe'), '/../../../' ));
  }  else {
    return path.dirname(app.getPath('exe'));
  }
}

module.exports = function localSettingsPath() {
  return path.join(getRoot(), 'settings.json');
};
