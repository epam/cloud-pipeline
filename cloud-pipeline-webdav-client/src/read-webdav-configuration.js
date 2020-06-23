const fs = require('fs');
const path = require('path');

module.exports = function () {
  try {
    const buffer = fs.readFileSync(path.resolve(__dirname, 'webdav.config'));
    return JSON.parse(buffer.toString());
  } catch (e) {
    return {};
  }
}
