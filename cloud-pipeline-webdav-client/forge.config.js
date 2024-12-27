const path = require('path');

const iconPath = path.resolve(__dirname, 'assets/favicon.png');

module.exports = {
  packagerConfig: {
    icon: iconPath,
  },
  makers: [
    {
      name: '@electron-forge/maker-squirrel',
      config: {
        name: 'cloud_data',
      },
    },
    {
      name: '@electron-forge/maker-zip',
      platforms: [
        'darwin',
        'linux',
      ],
    },
    {
      name: '@electron-forge/maker-deb',
      config: {
        options: {
          maintainer: 'EPAM Systems',
        },
      },
    },
    {
      name: '@electron-forge/maker-dmg',
      config: {
        name: 'Cloud Data',
        icon: iconPath,
      },
    },
    {
      name: '@electron-forge/maker-rpm',
      config: {},
    },
  ],
  plugins: [
    {
      name: '@electron-forge/plugin-webpack',
      config: {
        name: '@electron-forge/plugin-webpack',
        mainConfig: './src/main/webpack.config.js',
        renderer: {
          config: './src/renderer/webpack.config.js',
          // eslint-disable-next-line global-require
          entryPoints: require('./src/renderer/forge.entry.points'),
        },
      },
    },
  ],
};
