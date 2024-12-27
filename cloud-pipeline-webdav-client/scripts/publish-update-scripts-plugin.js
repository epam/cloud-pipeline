const fs = require('fs');
const path = require('path');
const { sources, Compilation } = require('webpack');

function publishScript(compilation, fileName) {
  try {
    const script = fs.readFileSync(path.resolve(__dirname, fileName));
    const scriptData = Buffer.from(script);
    if (scriptData.length > 0) {
      console.log(`Publishing update script "${fileName}": ${scriptData.length} bytes`);
      compilation.emitAsset(
        fileName,
        new sources.RawSource(scriptData),
      );
    } else {
      console.log(`Skipping update script "${fileName}": empty script`);
    }
  } catch (e) {
    console.log(`Error publishing update script "${fileName}":`, e.message);
  }
}

class PublishUpdateScriptsPlugin {
  // eslint-disable-next-line class-methods-use-this
  apply(compiler) {
    compiler.hooks.compilation.tap('PublishUpdateScriptsPlugin', (compilation) => {
      compilation.hooks.processAssets.tapPromise(
        {
          name: 'PublishUpdateScriptsPlugin',
          // https://github.com/webpack/webpack/blob/master/lib/Compilation.js#L3280
          stage: Compilation.PROCESS_ASSETS_STAGE_ADDITIONAL,
        },
        async () => {
          publishScript(compilation, 'update-win.ps1');
          publishScript(compilation, 'update-darwin.sh');
          publishScript(compilation, 'update-linux.sh');
        },
      );
    });
  }
}

module.exports = PublishUpdateScriptsPlugin;
