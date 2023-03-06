const path = require('path');
const getRoot = require('./get-app-root-directory');

module.exports = function localSettingsPath() {
  return path.join(getRoot(true), 'settings.json');
};
