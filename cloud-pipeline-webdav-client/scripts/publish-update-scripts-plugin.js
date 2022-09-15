const fs = require('fs');
const path = require('path');

class PublishUpdateScriptsPlugin {
  apply(compiler) {
    compiler.hooks.emit.tapPromise('PublishUpdateScriptsPlugin', async compilation => {
      try {
        const updateWinScript = fs.readFileSync(path.resolve(__dirname, './update-win.ps1'));
        compilation.assets['update-win.ps1'] = {
          source: () => Buffer.from(updateWinScript),
          size: () => Buffer.from(updateWinScript).length
        };
      } catch (e) {
        console.log('Error publishing update script:', e.message);
      }
    });
  }
}

module.exports = PublishUpdateScriptsPlugin;
