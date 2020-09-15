const packageJson = require('../package.json');

class PublishVersionPlugin {
  apply(compiler) {
    compiler.hooks.emit.tapPromise('PublishVersionPlugin', async compilation => {
      let version = process.env.CLOUD_DATA_APP_VERSION || '';
      if (version) {
        version = `${packageJson.version}.${version}`;
      } else {
        version = packageJson.version;
      }
      console.log();
      console.log(`Cloud Data App Version: "${version}"`);
      compilation.assets['VERSION'] = {
        source: () => Buffer.from(version),
        size: () => Buffer.from(version).length
      };
    });
  }
}

module.exports = PublishVersionPlugin;
