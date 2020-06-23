const fs = require('fs');
const path = require('path');

const DEFAULT_CONFIG = 'webdav.config';
const DEVELOPMENT_CONFIG = 'webdav.dev.config';
const PUBLISH_FILE_NAME = 'webdav.config';

class PublishWebDavConfigurationPlugin {
  constructor() {
    this.developmentMode = process.env.DEV_MODE
      ? (process.env.DEV_MODE.trim() === "true")
      : false;
  }
  readConfig(name) {
    const configPath = path.resolve(__dirname, '..', name);
    if (fs.existsSync(configPath)) {
      return fs.readFileSync(configPath);
    }
    return undefined;
  }
  apply(compiler) {
    compiler.hooks.emit.tapPromise('PublishWebDavConfigurationPlugin', async compilation => {
      let config;
      if (this.developmentMode) {
        config = this.readConfig(DEVELOPMENT_CONFIG);
      }
      if (!config || !config.length) {
        config = this.readConfig(DEFAULT_CONFIG);
      }
      if (!config || !config.length) {
        config = Buffer.from('{}');
      }
      if (config) {
        compilation.assets[PUBLISH_FILE_NAME] = {
          source: () => config,
          size: () => config.length
        };
      }
    });
  }
}

module.exports = PublishWebDavConfigurationPlugin;
