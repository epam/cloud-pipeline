const fs = require('fs');
const path = require('path');
const https = require('https');
const URL = require('url');
const {log, error} = require('./application/models/log');
const localSettingsPath = require('./local-settings-path');
const getUserName = require('./read-os-user-name');

const DEFAULT_APP_NAME = 'Cloud Data';

function readCustomConfiguration () {
  const config = localSettingsPath();
  if (fs.existsSync(config)) {
    try {
      const buffer = fs.readFileSync(config);
      return Object.assign(
        {name: DEFAULT_APP_NAME},
        JSON.parse(buffer.toString())
      );
    } catch (e) {}
  }
  return undefined;
}

function readCompilationAsset(assetName) {
  try {
    const assetFile = path.join(__dirname, assetName);
    if (fs.existsSync(assetFile)) {
      return fs.readFileSync(assetFile).toString();
    }
  } catch (_) {}
  return undefined;
}

function getAppVersion() {
  return readCompilationAsset('VERSION')
}

function getComponentVersion() {
  return readCompilationAsset('COMPONENT_VERSION')
}

function readCertificates (root) {
  const certificates = []
  const certificatesDir = path.resolve(root, 'certs');
  if (fs.existsSync(certificatesDir)) {
    const contents = fs.readdirSync(certificatesDir);
    for (let i = 0; i < contents.length; i++) {
      const certificatePath = path.resolve(certificatesDir, contents[i]);
      certificates.push(fs.readFileSync(certificatePath).toString('base64'));
    }
  }
  return certificates;
}

function readLocalConfiguration(root) {
  const certificates = readCertificates(root);
  try {
    if (fs.existsSync(path.resolve(root, 'webdav.config'))) {
      log(`Reading local configuration ${path.resolve(root, 'webdav.config')}...`);
      const buffer = fs.readFileSync(path.resolve(root, 'webdav.config'));
      const json = JSON.parse(buffer.toString());
      log(`Reading local configuration ${path.resolve(root, 'webdav.config')}:\n${JSON.stringify(json, undefined, ' ')}`);
      if (Object.keys(json).length > 0) {
        return Object.assign({certificates, ignoreCertificateErrors: true}, json);
      }
    } else {
      log(`No local configuration at path ${path.resolve(root, 'webdav.config')}`);
    }
    return undefined;
  } catch (e) {
    log(`Error reading local configuration ${path.resolve(root, 'webdav.config')}`);
    error(e);
    return undefined;
  }
}

const REQUEST_TIMEOUT_SECONDS = 10;

function apiGetRequest(api, accessKey, endpoint) {
  return new Promise((resolve, reject) => {
    log(`Performing API request ${api} with token ${accessKey} to endpoint "${endpoint}"...`);
    const url = URL.resolve(api, endpoint);
    https.get(url, {
      method: 'GET',
      timeout: REQUEST_TIMEOUT_SECONDS * 1000,
      rejectUnauthorized: false,
      headers: {
        "Authorization": `Bearer ${accessKey}`,
        "Content-type": "application/json",
        "Accept": "application/json",
        "Accept-Charset": "utf-8"
      }
    }, (response) => {
      let data = '';
      response.on('data', (chunk) => {
        data += chunk;
      });
      response.on('end', () => {
        try {
          log(`API request ${url}: ${data}`);
          const json = JSON.parse(data);
          if (json && /^ok$/i.test(json.status)) {
            resolve(json.payload);
          } else {
            reject(json.message || `Error fetching ${url}`);
            log(`Error performing API request ${url}: ${json.message}`);
          }
        } catch (e) {
          log(`Error performing API request ${url}`);
          error(e);
          console.log('Error fetching', url);
          console.log(e);
          resolve(null);
        }
      });
    })
      .on('error', (e) => {
        log(`Error performing API request ${url}: ${e.toString()}`);
        error(e);
        reject(new Error(`Request error: ${e.toString()}`));
      })
      .on('timeout', () => {
        log(`API request ${url} TIMEOUT`);
        reject(new Error(`Request ${url} timeout`));
      });
  });
}

async function readGlobalConfiguration() {
  const pipeCliConfig = path.join(require('os').homedir(), '.pipe', 'config.json');
  if (fs.existsSync(pipeCliConfig)) {
    log(`Reading global configuration ${pipeCliConfig}`);
    try {
      const buffer = fs.readFileSync(pipeCliConfig);
      const config = JSON.parse(buffer.toString());
      if (config.api && config.access_key) {
        log(`Global configuration API=${config.api} TOKEN=${config.access_key}`);
        const whoAmI = await apiGetRequest(config.api, config.access_key, 'whoami');
        if (whoAmI) {
          const {userName} = whoAmI;
          log(`Global configuration USER=${userName}`);
          log(`Global configuration: fetching base.dav.auth.url preference`);
          const davAuthUrl = await apiGetRequest(config.api, config.access_key, 'preferences/base.dav.auth.url');
          if (davAuthUrl) {
            let {value: webdavAuthSSO} = davAuthUrl;
            log(`Global configuration: base.dav.auth.url=${webdavAuthSSO}`);
            const reg = /\/webdav\/(.*)$/i;
            const result = reg.exec(webdavAuthSSO);
            if (result) {
              webdavAuthSSO = webdavAuthSSO.substr(0, result.index);
              webdavAuthSSO = `${webdavAuthSSO}/webdav/${userName}`;
              log(`Global configuration: SERVER=${webdavAuthSSO}`);
            }
            return {
              ignoreCertificateErrors: true,
              username: userName,
              password: config.access_key,
              server: webdavAuthSSO,
              api: config.api
            };
          } else {
            log(`Global configuration: NO base.dav.auth.url preference`);
          }
        }
      }
      return {
        password: config.access_key,
        username: 'pipe cli user',
        api: config ? config.api : undefined
      };
    } catch (e) {
      log(`Error reading global configuration at path ${pipeCliConfig}`);
      error(e);
      console.log(e);
      return undefined;
    }
  } else {
    log(`No global configuration at path ${pipeCliConfig}`);
  }
  return undefined;
}

module.exports = async function () {
  const localConfiguration = readLocalConfiguration(path.join(require('os').homedir(), '.pipe-webdav-client'));
  const predefinedConfiguration = readLocalConfiguration(__dirname);
  const globalConfig = await readGlobalConfiguration();
  const custom = readCustomConfiguration();
  log(`Global configuration:\n${JSON.stringify(globalConfig, undefined, ' ')}`);
  log(`webdav.config configuration (legacy):\n${JSON.stringify(globalConfig, undefined, ' ')}`);
  log(`Settings.json configuration:\n${JSON.stringify(custom, undefined, ' ')}`);
  const config = {
    ...(predefinedConfiguration || {}),
    ...(globalConfig || {}),
    ...(localConfiguration || {}),
    ...(custom || {}),
    version: getAppVersion()
  };
  const osUserName = await getUserName();
  const serverCorrected = osUserName && config.server
    ? (config.server || '').replace(/<USER_ID>/ig, osUserName)
    : config.server;
  const userNameCorrected = osUserName && (!config.username || /^<USER_ID>$/i.test(config.username))
    ? osUserName
    : config.username;
  if (serverCorrected !== config.server) {
    log(`WEBDAV Server corrected: ${serverCorrected}`);
  }
  if (userNameCorrected !== config.username) {
    log(`WEBDAV username corrected: ${userNameCorrected}`);
  }
  config.server = serverCorrected;
  config.username = userNameCorrected;
  config.name = (custom ? custom.name : undefined) || DEFAULT_APP_NAME;
  config.version = getAppVersion();
  log(`Parsed configuration:\n${JSON.stringify(config, undefined, ' ')}`);
  return config;
}
