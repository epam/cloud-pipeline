class PublishVersionPlugin {
  apply(compiler) {
    compiler.hooks.emit.tapPromise('PublishVersionPlugin', async compilation => {
      const version = process.env.CLOUD_DATA_APP_VERSION || '';
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
