const path = require('path');

module.exports = [
  {
    html: path.resolve(__dirname, './windows/main/index.html'),
    js: path.resolve(__dirname, './windows/main/index.jsx'),
    name: 'MAIN_WINDOW',
    preload: {
      js: path.resolve(__dirname, './windows/main/preload.js'),
    },
  },
  {
    html: path.resolve(__dirname, './windows/configuration/index.html'),
    js: path.resolve(__dirname, './windows/configuration/index.jsx'),
    name: 'CONFIGURATION_WINDOW',
    preload: {
      js: path.resolve(__dirname, './windows/configuration/preload.js'),
    },
  },
  {
    html: path.resolve(__dirname, './windows/storage-access/index.html'),
    js: path.resolve(__dirname, './windows/storage-access/index.jsx'),
    name: 'STORAGE_ACCESS_WINDOW',
    preload: {
      js: path.resolve(__dirname, './windows/storage-access/preload.js'),
    },
  },
  {
    html: path.resolve(__dirname, './windows/dialogs/default-dialog/index.html'),
    js: path.resolve(__dirname, './windows/dialogs/default-dialog/index.jsx'),
    name: 'INPUT_WINDOW',
    preload: {
      js: path.resolve(__dirname, './windows/dialogs/preload.js'),
    },
  },
];
