class PublishVersionPlugin {
  apply(compiler) {
    compiler.hooks.emit.tapPromise('PublishVersionPlugin', async compilation => {
      const version = process.env.CLOUD_DATA_APP_VERSION || '';
      const component_version = '1111111111111111111111111111111111111111';
      console.log();
      console.log(`Cloud Data App Version: "${version}"`);
      console.log(`Cloud Data App Component Version: "${component_version}"`);
      compilation.assets['VERSION'] = {
        source: () => Buffer.from(version),
        size: () => Buffer.from(version).length
      };
      compilation.assets['COMPONENT_VERSION'] = {
        source: () => Buffer.from(component_version),
        size: () => Buffer.from(component_version).length
      };
    });
  }
}

module.exports = PublishVersionPlugin;
