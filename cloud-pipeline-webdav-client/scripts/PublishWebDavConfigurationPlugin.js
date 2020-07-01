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
      const certificatesDirectory = path.resolve(__dirname, '../certs');
      const certificates = [];
      if (fs.existsSync(certificatesDirectory)) {
        const contents = fs.readdirSync(certificatesDirectory);
        for (let i = 0; i < contents.length; i++) {
          const certificate = contents[i];
          const certificateData = fs.readFileSync(path.resolve(certificatesDirectory, certificate)).toString('base64');
          certificates.push(certificateData);
        }
      }
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
        const data = JSON.stringify(Object.assign({certificates}, JSON.parse(config)));
        compilation.assets[PUBLISH_FILE_NAME] = {
          source: () => data,
          size: () => data.length
        };
      }
    });
  }
}

module.exports = PublishWebDavConfigurationPlugin;
