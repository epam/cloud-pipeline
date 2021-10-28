import electron from 'electron';
import https from 'https';
import {error, log} from './log';

const REQUEST_TIMEOUT_SECONDS = 30;

function wrapRequest (url, method, body, authToken, ignoreCertificateErrors = false) {
  return new Promise((resolve, reject) => {
    log(`${method} ${url}...`);
    const request = https.request(url, {
      method,
      timeout: REQUEST_TIMEOUT_SECONDS * 1000,
      rejectUnauthorized: !ignoreCertificateErrors,
      headers: {
        "Authorization": `Bearer ${authToken}`,
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
        log(`${method} ${url}: data received (${data.length} bytes)`);
        try {
          const json = JSON.parse(data);
          if (json && /^ok$/i.test(json.status)) {
            resolve(json.payload);
          } else {
            error(`${method} ${url}: ${json.message}`);
            reject(new Error(json.message || 'Request error'));
          }
        } catch (e) {
          error(`${method} ${url}: ${e.message}`);
          resolve(null);
        }
      });
    });
    request.on('error', (e) => {
      error(`${method} ${url}: ${e.toString()}`);
      reject(new Error(`Request error: ${e.toString()}`));
    });
    request.on('timeout', () => {
      error(`${method} ${url}: timeout`);
      reject(new Error('Request timeout'));
    });
    if (body) {
      log(`${method} ${url}: sending ${body.length} bytes`);
      request.write(body);
    }
    request.end();
  });
}

class RequestStorageAccessApi {
  initialize() {
    let cfg;
    if (electron.remote === undefined) {
      cfg = global.webdavClient;
    } else {
      cfg = electron.remote.getGlobal('webdavClient');
    }
    const {config: webdavClientConfig = {}} = cfg || {};
    this.api = webdavClientConfig.api;
    this.password = webdavClientConfig.password;
    this.ignoreCertificateErrors = webdavClientConfig.ignoreCertificateErrors;
    return this;
  }

  apiRequest (endpoint, options = {}) {
    const {
      body,
      method = body ? 'POST' : 'GET'
    } = options;
    let api = this.api || '';
    if (api.endsWith('/')) {
      api = api.slice(0, -1);
    }
    if (endpoint.startsWith('/')) {
      endpoint = endpoint.slice(1);
    }
    return wrapRequest(
      `${api}/${endpoint}`,
      method,
      body ? JSON.stringify(body) : undefined,
      this.password,
      this.ignoreCertificateErrors
    )
  }

  apiGetRequest(endpoint) {
    return this.apiRequest(endpoint, {});
  }

  apiPostRequest(endpoint, payload) {
    return this.apiRequest(endpoint, {body: payload, method: 'POST'});
  }

  apiDeleteRequest(endpoint, payload) {
    return this.apiRequest(endpoint, {body: payload, method: 'DELETE'});
  }

  getStorages() {
    return this.apiGetRequest('datastorage/available')
  }

  getMetadata(entities) {
    return this.apiPostRequest('metadata/load', entities);
  }

  getStoragesWithMetadata() {
    return new Promise(async (resolve, reject) => {
      try {
        const storages = await this.getStorages();
        const entities = (storages || []).map(o => ({
          entityId: o.id,
          entityClass: 'DATA_STORAGE'
        }));
        const metadata = await this.getMetadata(entities);
        storages.forEach(storage => {
          const metadataEntry = (metadata || [])
            .find(o => o.entity && o.entity.entityId === storage.id);
          const {
            data = {}
          } = metadataEntry || {};
          storage.metadata = Object.entries(data || {})
            .filter(([key, value]) => !!value && value.value !== undefined)
            .map(([key, value]) => ({[key]: value.value}))
            .reduce((r, c) => ({...r, ...c}), {});
        });
        resolve(storages);
      } catch (e) {
        reject(e);
      }
    });
  }

  getPreference(name) {
    return new Promise((resolve, reject) => {
      this.apiGetRequest(`preferences/${encodeURIComponent(name)}`)
        .then(payload => {
          if (payload && payload.value) {
            resolve(payload.value);
          } else {
            resolve(undefined);
          }
        })
        .catch(reject);
    });
  }

  requestDavAccess(identifier, duration) {
    return this.apiPostRequest('datastorage/webdav', {id: identifier, time: duration});
  }
}

export default new RequestStorageAccessApi();
