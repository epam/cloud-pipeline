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
          if (data && data.length > 0) {
            const json = JSON.parse(data);
            if (json && /^ok$/i.test(json.status)) {
              resolve(json.payload);
            } else {
              error(`${method} ${url}: ${json.message}`);
              reject(new Error(json.message || 'Request error'));
            }
          } else {
            resolve(undefined);
          }
        } catch (e) {
          error(`${method} ${url}: ${e.message}`);
          reject(e);
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

class CloudPipelineApi {
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
    return new Promise((resolve, reject) => {
      Promise.all([
        this.apiGetRequest('datastorage/available'),
        this.getHiddenStorages()
      ])
        .then(([storages, hiddenIds]) => {
          storages.forEach(storage => {
            storage.hidden = hiddenIds.includes(storage.id);
          })
          resolve(storages);
        })
        .catch(reject);
    });
  }

  getMetadata(entities) {
    return this.apiPostRequest('metadata/load', entities);
  }

  getStoragesWithMetadata() {
    return new Promise( (resolve, reject) => {
      this.getStorages()
        .then((storages) => {
          const entities = (storages || []).map(o => ({
            entityId: o.id,
            entityClass: 'DATA_STORAGE'
          }));
          this.getMetadata(entities)
            .then((metadata) => {
              (storages || []).forEach(storage => {
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
              resolve(storages.filter(o => !o.hidden));
            })
            .catch(reject);
        })
        .catch(reject);
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

  getHiddenStorages() {
    return new Promise((resolve) => {
      this.getPreference('ui.hidden.objects')
        .then((preference) => {
          if (preference) {
            try {
              const {data_storage: hiddenStorages = []} = JSON.parse(preference) || {};
              resolve(hiddenStorages.map(o => Number(o)).filter(n => !Number.isNaN(n)))
            } catch (e) {
              resolve([]);
            }
          } else {
            resolve([]);
          }
        })
        .catch((e) => {
          console.log(e.message);
          resolve([]);
        });
    });
  }

  requestDavAccess(identifier, duration) {
    return this.apiPostRequest('datastorage/davmount', {id: identifier, time: duration});
  }

  getAppInfo() {
    return new Promise((resolve, reject) => {
      this.apiGetRequest('app/info')
        .then(info => {
          if (info && info.components) {
            return Promise.resolve(info.components['cloud-pipeline-webdav-client']);
          }
          return Promise.resolve(undefined);
        })
        .then(resolve)
        .catch(reject);
    });
  }

  getAppDistributionUrl() {
    return new Promise((resolve, reject) => {
      this.getPreference('base.cloud.data.distribution.url')
        .then(value => Promise.resolve(JSON.parse(value)))
        .then(resolve)
        .catch(reject);
    });
  }
}

export default new CloudPipelineApi();
