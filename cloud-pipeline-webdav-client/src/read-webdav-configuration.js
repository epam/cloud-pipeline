const fs = require('fs');
const path = require('path');

function readCertificates () {
  const certificates = []
  const certificatesDir = path.resolve(__dirname, 'certs');
  if (fs.existsSync(certificatesDir)) {
    const contents = fs.readdirSync(certificatesDir);
    for (let i = 0; i < contents.length; i++) {
      const certificatePath = path.resolve(certificatesDir, contents[i]);
      certificates.push(fs.readFileSync(certificatePath).toString('base64'));
    }
  }
  return certificates;
}

module.exports = function () {
  const certificates = readCertificates();
  try {
    const buffer = fs.readFileSync(path.resolve(__dirname, 'webdav.config'));
    return Object.assign({certificates}, JSON.parse(buffer.toString()));
  } catch (e) {
    return {certificates};
  }
}
