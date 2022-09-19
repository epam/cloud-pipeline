const fs = require('fs');
const path = require('path');

function publishScript(compilation, fileName) {
  try {
    const script = fs.readFileSync(path.resolve(__dirname, fileName));
    compilation.assets[fileName] = {
      source: () => Buffer.from(script),
      size: () => Buffer.from(script).length
    };
  } catch (e) {
    console.log(`Error publishing update script "${fileName}":`, e.message);
  }
}

class PublishUpdateScriptsPlugin {
  apply(compiler) {
    compiler.hooks.emit.tapPromise('PublishUpdateScriptsPlugin', async compilation => {
      publishScript(compilation, 'update-win.ps1');
      publishScript(compilation, 'update-darwin.sh');
      publishScript(compilation, 'update-linux.sh');
    });
  }
}

module.exports = PublishUpdateScriptsPlugin;
