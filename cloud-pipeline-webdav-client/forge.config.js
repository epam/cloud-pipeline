const path = require('path');

const iconPath = path.resolve(__dirname, 'assets/favicon.png');

module.exports = {
  "packagerConfig": {
    "icon": iconPath
  },
  "makers": [
    {
      "name": "@electron-forge/maker-squirrel",
      "config": {
        "name": "cloud_pipeline_webdav_client"
      }
    },
    {
      "name": "@electron-forge/maker-zip",
      "platforms": [
        "darwin",
        "linux"
      ],
    },
    {
      "name": "@electron-forge/maker-deb",
      "config": {
        "options": {
          "maintainer": "EPAM Systems"
        }
      }
    },
    {
      "name": "@electron-forge/maker-dmg",
      "config": {
        "name": "Cloud Pipeline Webdav Client",
        "icon": iconPath
      }
    },
    {
      "name": "@electron-forge/maker-rpm",
      "config": {}
    }
  ],
  "plugins": [
    [
      "@electron-forge/plugin-webpack",
      {
        "mainConfig": "./webpack.main.config.js",
        "renderer": {
          "config": "./webpack.renderer.config.js",
          "entryPoints": [
            {
              "html": "./src/index.html",
              "js": "./src/renderer.js",
              "name": "main_window"
            },
            {
              "html": "./src/operations.html",
              "js": "./src/operations-renderer.js",
              "name": "operations_window"
            },
            {
              "html": "./src/directory-name-dialog.html",
              "js": "./src/directory-name-renderer.js",
              "name": "directory_name_dialog"
            },
            {
              "html": "./src/confirmation-dialog.html",
              "js": "./src/confirmation-dialog-renderer.js",
              "name": "confirmation_dialog"
            }
          ]
        }
      }
    ]
  ]
};
