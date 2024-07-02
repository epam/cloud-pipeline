const os = require('os');
const fs = require('fs');
const path = require('path');
const apiBaseRequest = require('../api/api-base-request');
const logger = require('../shared-logger');
const { wrapSecure, wrapValue } = require('../utilities/helpers');

module.exports = async function readGlobalConfiguration() {
  const pipeCliConfig = path.join(os.homedir(), '.pipe/config.json');
  if (fs.existsSync(pipeCliConfig)) {
    try {
      const buffer = fs.readFileSync(pipeCliConfig);
      const config = JSON.parse(buffer.toString());
      if (config.api && config.access_key) {
        logger.log(`pipe cli configuration API=${wrapValue(config.api)} TOKEN=${wrapSecure(config.access_key)}`);
        const whoAmI = await apiBaseRequest(config.api, 'whoami', { token: config.access_key });
        if (whoAmI) {
          const { userName } = whoAmI;
          logger.log(`pipe cli configuration USER=${wrapValue(userName)}`);
          logger.log('pipe cli configuration: fetching base.dav.auth.url preference');
          const davAuthUrl = await apiBaseRequest(
            config.api,
            'preferences/base.dav.auth.url',
            { token: config.access_key },
          );
          if (davAuthUrl) {
            let { value: webdavAuthSSO } = davAuthUrl;
            logger.log(`pipe cli configuration: base.dav.auth.url=${wrapValue(webdavAuthSSO)}`);
            const reg = /\/webdav\/(.*)$/i;
            const result = reg.exec(webdavAuthSSO);
            if (result) {
              webdavAuthSSO = webdavAuthSSO.substr(0, result.index);
              webdavAuthSSO = `${webdavAuthSSO}/webdav/${userName}`;
              logger.log(`pipe cli configuration: SERVER=${wrapValue(webdavAuthSSO)}`);
            }
            return {
              ignoreCertificateErrors: true,
              updatePermissions: false,
              username: userName,
              password: config.access_key,
              server: webdavAuthSSO,
              api: config.api,
            };
          }
          logger.log('pipe-cli configuration: NO base.dav.auth.url preference');
        }
      }
      return {
        password: config.access_key,
        username: 'pipe cli user',
        api: config ? config.api : undefined,
      };
    } catch (e) {
      logger.error(`Error reading pipe-cli configuration at path ${pipeCliConfig}: ${e.message}`);
      return undefined;
    }
  } else {
    logger.log(`No pipe-cli configuration fount at path ${pipeCliConfig}`);
  }
  return undefined;
};
