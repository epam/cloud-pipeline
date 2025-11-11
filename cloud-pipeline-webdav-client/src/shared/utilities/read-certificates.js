const path = require('path');
const fs = require('fs');
const logger = require('../shared-logger');

function safeReadCertificate(certificatePath) {
  try {
    return fs.readFileSync(certificatePath).toString('base64');
  } catch (error) {
    logger.warn(`Error reading certificate at path "${certificatePath}": ${error.message}`);
  }
  return undefined;
}

module.exports = function readCertificates(root) {
  const certificates = [];
  try {
    const certificatesDir = path.resolve(root, 'certs');
    if (fs.existsSync(certificatesDir)) {
      const contents = fs.readdirSync(certificatesDir);
      for (let i = 0; i < contents.length; i += 1) {
        const certificatePath = path.resolve(certificatesDir, contents[i]);
        certificates.push(safeReadCertificate(certificatePath));
      }
    }
  } catch (error) {
    logger.warn(`Error reading certificates at path "${root}": ${error.message}`);
  }
  return certificates.filter(Boolean);
};
