const logger = require('../shared-logger');
const { wrapSecure, wrapValue } = require('../utilities/helpers');

module.exports = function printConfiguration(configuration, title, printRest = true) {
  if (!configuration) {
    if (title) {
      logger.log(title, 'empty');
    }
    return;
  }
  const {
    api,
    extra,
    server,
    username,
    password,
    ignoreCertificateErrors,
    updatePermissions,
    logsEnabled,
    displayBucketSelection,
    displaySettings,
    webDavRestricted,
    webDavErrorDisclaimer,
    ftp = [],
    name,
    ...rest
  } = configuration;
  logger.log('--------------------------------------------------------');
  if (title) {
    logger.log(title);
  }
  logger.log('  name:                     ', wrapValue(name));
  logger.log('  api:                      ', wrapValue(api));
  logger.log('  extra api:                ', wrapValue(extra));
  logger.log('  server:                   ', wrapValue(server));
  logger.log('  username:                 ', wrapValue(username));
  logger.log('  password:                 ', wrapValue(wrapSecure(password)));
  logger.log('  webdav restricted mode:   ', !!webDavRestricted);
  logger.log('  ignore certificate errors:', !!ignoreCertificateErrors);
  logger.log('  update permissions:       ', !!updatePermissions);
  logger.log('  logging enabled:          ', !!logsEnabled);
  logger.log('  display bucket selection: ', displayBucketSelection === undefined ? '<default>' : displayBucketSelection);
  logger.log('  display settings:         ', displaySettings === undefined ? '<default>' : displaySettings);
  logger.log('  logging enabled:          ', !!logsEnabled);
  logger.log('');
  logger.log(`  ${ftp.length} ftp/sftp server${ftp.length === 1 ? '' : 's'}:`);
  for (let f = 0; f < ftp.length; f += 1) {
    const ftpServer = ftp[f];
    const {
      url,
      user: ftpUser,
      useDefaultUser = !ftpUser,
      password: ftpPassword,
      protocol,
      enableLogs: extendedLogs,
    } = ftpServer;
    logger.log(`  * ${url}`);
    logger.log('    protocol:       ', wrapValue(protocol));
    if (useDefaultUser) {
      logger.log('    user:           ', '<use default user>');
      logger.log('    password:       ', '<use default password>');
    } else {
      logger.log('    user:           ', wrapValue(ftpUser));
      logger.log('    password:       ', wrapValue(wrapSecure(ftpPassword)));
    }
    logger.log('    logging enabled:', !!extendedLogs);
  }
  const restValues = Object.entries(rest);
  if (restValues.length > 0) {
    logger.log('');
    logger.log('  other configuration:      ');
  }
  if (printRest) {
    restValues.forEach(([key, value]) => {
      try {
        const secured = /(password|token)/i.test(key);
        const valueString = secured ? wrapSecure(value) : value;
        logger.log(`    ${key}: ${wrapValue(valueString)}`);
      } catch {
      }
    });
  }
  logger.log('--------------------------------------------------------');
};
