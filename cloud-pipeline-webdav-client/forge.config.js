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
        "name": "Webdav Client",
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
            }
          ]
        }
      }
    ]
  ]
};
