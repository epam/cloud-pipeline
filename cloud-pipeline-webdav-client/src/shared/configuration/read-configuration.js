const fs = require('fs');
const path = require('path');
const readCertificates = require('../utilities/read-certificates');
const logger = require('../shared-logger');
const printConfiguration = require('./print-configuration');

/**
 * @param {string} configPath
 * @param {{readCertificates: boolean?}} [options]
 * @returns {undefined|any}
 */
module.exports = function readConfiguration(configPath, options = {}) {
  const {
    readCertificates: _readCertificates = true,
  } = options;
  try {
    if (fs.existsSync(configPath)) {
      const buffer = fs.readFileSync(configPath);
      const json = JSON.parse(buffer.toString());
      printConfiguration(json, `Configuration ${configPath}`);
      if (json && Object.keys(json).length > 0) {
        const certificates = _readCertificates
          ? readCertificates(path.dirname(configPath))
          : [];
        return Object.assign(
          _readCertificates ? { certificates, ignoreCertificateErrors: true } : {},
          json,
        );
      }
    } else {
      logger.log(`No configuration found at path ${configPath}`);
    }
    return undefined;
  } catch (e) {
    logger.error(`Error reading configuration ${configPath}: ${e.message}`);
    return undefined;
  }
};
