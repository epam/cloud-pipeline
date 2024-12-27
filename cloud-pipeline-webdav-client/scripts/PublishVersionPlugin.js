const { sources, Compilation } = require('webpack');

class PublishVersionPlugin {
  // eslint-disable-next-line class-methods-use-this
  apply(compiler) {
    compiler.hooks.compilation.tap('PublishVersionPlugin', (compilation) => {
      compilation.hooks.processAssets.tapPromise(
        {
          name: 'PublishVersionPlugin',
          // https://github.com/webpack/webpack/blob/master/lib/Compilation.js#L3280
          stage: Compilation.PROCESS_ASSETS_STAGE_ADDITIONAL,
        },
        async () => {
          const version = process.env.CLOUD_DATA_APP_VERSION || '';
          // eslint-disable-next-line camelcase
          const componentVersion = '1111111111111111111111111111111111111111';
          console.log();
          console.log(`Cloud Data App Version: "${version}"`);
          console.log(`Cloud Data App Component Version: "${componentVersion}"`);
          compilation.emitAsset(
            'VERSION',
            new sources.RawSource(version),
          );
          compilation.emitAsset(
            'COMPONENT_VERSION',
            new sources.RawSource(componentVersion),
          );
        },
      );
    });
  }
}

module.exports = PublishVersionPlugin;
