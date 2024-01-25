const fs = require('fs');
const path = require('path');
const { sources, Compilation } = require('webpack');

const DEFAULT_CONFIG = 'webdav.config';
const DEVELOPMENT_CONFIG = 'webdav.dev.config';
const PUBLISH_FILE_NAME = 'webdav.config';

class PublishWebDavConfigurationPlugin {
  constructor() {
    this.developmentMode = process.env.DEV_MODE
      ? (process.env.DEV_MODE.trim() === 'true')
      : false;
  }

  // eslint-disable-next-line class-methods-use-this
  readConfig(name) {
    const configPath = path.resolve(__dirname, '..', name);
    if (fs.existsSync(configPath)) {
      return fs.readFileSync(configPath);
    }
    return undefined;
  }

  apply(compiler) {
    compiler.hooks.compilation.tap('PublishWebDavConfigurationPlugin', (compilation) => {
      compilation.hooks.processAssets.tapPromise(
        {
          name: 'PublishWebDavConfigurationPlugin',
          // https://github.com/webpack/webpack/blob/master/lib/Compilation.js#L3280
          stage: Compilation.PROCESS_ASSETS_STAGE_ADDITIONAL,
        },
        async () => {
          const certificatesDirectory = path.resolve(__dirname, '../certs');
          const certificates = [];
          if (fs.existsSync(certificatesDirectory)) {
            const contents = fs.readdirSync(certificatesDirectory);
            for (let i = 0; i < contents.length; i += 1) {
              const certificate = contents[i];
              const certificateData = fs.readFileSync(
                path.resolve(certificatesDirectory, certificate),
              );
              certificates.push({ data: certificateData, name: certificate });
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
            compilation.emitAsset(
              PUBLISH_FILE_NAME,
              new sources.RawSource(config),
            );
          }
          if (certificates && certificates.length) {
            certificates.forEach(({ data, name }) => {
              const certPath = path.join('certs', name);
              compilation.emitAsset(
                certPath,
                new sources.RawSource(data),
              );
            });
          }
        },
      );
    });
  }
}

module.exports = PublishWebDavConfigurationPlugin;
