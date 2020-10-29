const electron = require('electron');
const path = require('path');
const fs = require('fs');

function getRoot() {
  const app = electron.app;
  if (process.platform === 'darwin' ) {
    return path.dirname(path.join(app.getPath('exe'), '/../../../' ));
  }  else {
    return path.dirname(app.getPath('exe'));
  }
}

const DEFAULT_APP_NAME = 'Cloud Data';

module.exports = function readCustomConfiguration () {
  const config = path.join(getRoot(), 'settings.json');
  if (fs.existsSync(config)) {
    try {
      const buffer = fs.readFileSync(config);
      return Object.assign(
        {name: DEFAULT_APP_NAME},
        JSON.parse(buffer.toString())
      );
    } catch (e) {}
  }
  return {
    name: DEFAULT_APP_NAME
  };
}
