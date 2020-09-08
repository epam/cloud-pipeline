const fs = require('fs');
const path = require('path');
const https = require('https');
const URL = require('url');

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
      const buffer = fs.readFileSync(path.resolve(root, 'webdav.config'));
      const json = JSON.parse(buffer.toString());
      if (Object.keys(json).length > 0) {
        return Object.assign({certificates}, json);
      }
    }
    return undefined;
  } catch (e) {
    return undefined;
  }
}

function apiGetRequest(api, accessKey, endpoint) {
  return new Promise((resolve, reject) => {
    const url = URL.resolve(api, endpoint);
    https.get(url, {
      method: 'GET',
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
          const json = JSON.parse(data);
          if (json && /^ok$/i.test(json.status)) {
            resolve(json.payload);
          }
        } catch (e) {
          console.log('Error fetching', url);
          console.log(e);
          resolve(null);
        }
      });
    })
      .on('error', () => {
        resolve(null);
      });
  });
}

async function readGlobalConfiguration() {
  const pipeCliConfig = path.join(require('os').homedir(), '.pipe', 'config.json');
  if (fs.existsSync(pipeCliConfig)) {
    try {
      const buffer = fs.readFileSync(pipeCliConfig);
      const config = JSON.parse(buffer.toString());
      if (config.api && config.access_key) {
        const whoAmI = await apiGetRequest(config.api, config.access_key, 'whoami');
        if (whoAmI) {
          const {userName} = whoAmI;
          const davAuthUrl = await apiGetRequest(config.api, config.access_key, 'preferences/base.dav.auth.url');
          if (davAuthUrl) {
            let {value: webdavAuthSSO} = davAuthUrl;
            const reg = /\/webdav\/(.*)$/i;
            const result = reg.exec(webdavAuthSSO);
            if (result) {
              webdavAuthSSO = webdavAuthSSO.substr(0, result.index);
              webdavAuthSSO = `${webdavAuthSSO}/webdav/${userName}`;
            }
            return {
              ignoreCertificateErrors: true,
              username: userName,
              password: config.access_key,
              server: webdavAuthSSO
            };
          }
        }
      }
      return {
        password: config.access_key,
        username: 'pipe cli user'
      };
    } catch (e) {
      console.log(e);
      return undefined;
    }
  }
  return undefined;
}

module.exports = async function () {
  const localConfiguration = readLocalConfiguration(path.join(require('os').homedir(), '.pipe-webdav-client'));
  const predefinedConfiguration = readLocalConfiguration(__dirname);
  const globalConfig = await readGlobalConfiguration();
  return localConfiguration || predefinedConfiguration || globalConfig || {};
}
