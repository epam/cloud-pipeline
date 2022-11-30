const path = require('path');
const PublishVersionPlugin = require('../../scripts/PublishVersionPlugin');
const PublishWebDavConfigurationPlugin = require('../../scripts/PublishWebDavConfigurationPlugin');
const PublishUpdateScriptsPlugin = require('../../scripts/publish-update-scripts-plugin');

module.exports = {
  /**
     * This is the main entry point for your application, it's the first file
     * that runs in the main process.
     */
  entry: path.resolve(__dirname, './index.js'),
  // Put your normal webpack config below here
  plugins: [
    new PublishWebDavConfigurationPlugin(),
    new PublishVersionPlugin(),
    new PublishUpdateScriptsPlugin(),
  ],
  module: {
    rules: [
      {
        // We're specifying native_modules in the test because the asset
        // relocator loader generates a "fake" .node file which is really
        // a cjs file.
        test: /native_modules\/.+\.node$/,
        use: 'node-loader',
      },
      {
        test: /\.(m?js|node)$/,
        parser: { amd: false },
        use: {
          loader: '@vercel/webpack-asset-relocator-loader',
          options: {
            outputAssetBase: 'native_modules',
          },
        },
      },
    ],
  },
};
