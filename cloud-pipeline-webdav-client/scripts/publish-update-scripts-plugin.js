const fs = require('fs');
const path = require('path');

function publishScript(compilation, fileName) {
  try {
    const script = fs.readFileSync(path.resolve(__dirname, fileName));
    const scriptData = Buffer.from(script);
    if (scriptData.length > 0) {
      console.log(`Publishing update script "${fileName}": ${scriptData.length} bytes`);
      compilation.assets[fileName] = {
        source: () => scriptData,
        size: () => scriptData.length
      };
    } else {
      console.log(`Skipping update script "${fileName}": empty script`);
    }
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
