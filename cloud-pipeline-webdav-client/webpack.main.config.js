const PublishVersionPlugin = require('./scripts/PublishVersionPlugin');
const PublishWebDavConfigurationPlugin = require('./scripts/PublishWebDavConfigurationPlugin');

module.exports = {
  /**
   * This is the main entry point for your application, it's the first file
   * that runs in the main process.
   */
  entry: './src/main.js',
  // Put your normal webpack config below here
  plugins: [new PublishWebDavConfigurationPlugin(), new PublishVersionPlugin()],
  module: {
    rules: require('./webpack.rules'),
  },
};
