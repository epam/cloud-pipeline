const apiBaseRequest = require('./api-base-request');

class CloudPipelineApi {
  /**
   * @typedef {object} APIOptions
   * @property {string} api
   * @property {string} password
   * @property {boolean} [ignoreCertificateErrors]
   */

  /**
   * @param {APIOptions|Configuration} options
   */
  constructor(options) {
    if (!options) {
      throw new Error('API should be initialized with configuration');
    }
    /**
     * @type {APIOptions|Configuration}
     */
    this.options = options;
  }

  /**
   * @param {string} endpoint
   * @param {ApiRequestOptions} [options]
   * @returns {Promise<unknown>}
   */
  async apiRequest(endpoint, options = {}) {
    const {
      api,
      password,
      ignoreCertificateErrors = false,
    } = this.options || {};
    const rejectUnauthorized = !ignoreCertificateErrors;
    return apiBaseRequest(
      api,
      endpoint,
      {
        token: password,
        rejectUnauthorized,
        ...options,
      },
    );
  }

  async getDataStorageAvailable() {
    return this.apiRequest('datastorage/available');
  }

  async getPreference(name) {
    const preference = await this.apiRequest(`preferences/${encodeURIComponent(name)}`);
    return preference?.value;
  }

  async getHiddenStorages() {
    const uiHiddenObjects = await this.getPreference('ui.hidden.objects');
    const { data_storage: hiddenStorages = [] } = JSON.parse(uiHiddenObjects || '{}');
    return hiddenStorages
      .map((o) => Number(o))
      .filter((o) => !Number.isNaN(o));
  }

  async getStorages() {
    const [
      storages,
      hiddenStorages,
    ] = await Promise.all([
      this.getDataStorageAvailable(),
      this.getHiddenStorages(),
    ]);
    return storages.map((aStorage) => ({
      ...aStorage,
      hidden: hiddenStorages.includes(Number(aStorage.id)),
    }));
  }

  getMetadata(entities) {
    return this.apiRequest('metadata/load', { body: entities });
  }

  async getStoragesWithMetadata() {
    const storages = await this.getStorages();
    const entities = (storages || []).map((storage) => ({
      entityId: storage.id,
      entityClass: 'DATA_STORAGE',
    }));
    const metadata = await this.getMetadata(entities);
    return (storages || []).map((storage) => {
      const metadataEntry = (metadata || [])
        .find((o) => o.entity && o.entity.entityId === storage.id);
      const {
        data = {},
      } = metadataEntry || {};
      return {
        ...storage,
        metadata: Object.entries(data || {})
          .filter(([, value]) => !!value && value.value !== undefined)
          .map(([key, value]) => ({ [key]: value.value }))
          .reduce((r, c) => ({ ...r, ...c }), {}),
      };
    });
  }

  async getStorageAccessDurationPreference() {
    const preference = await this.getPreference('storage.webdav.access.duration.seconds');
    if (preference && !Number.isNaN(Number(preference))) {
      return Number(preference);
    }
    return 0;
  }

  async requestDavAccess(identifier) {
    if (!this.getStorageAccessDurationPreferencePromise) {
      this.getStorageAccessDurationPreferencePromise = this.getStorageAccessDurationPreference();
    }
    const duration = await this.getStorageAccessDurationPreferencePromise;
    return this.apiRequest(
      'datastorage/davmount',
      {
        body: { id: identifier, time: duration },
      },
    );
  }

  async getAppDistributionUrl() {
    const value = await this.getPreference('base.cloud.data.distribution.url');
    return JSON.parse(value);
  }

  async getAppInfo() {
    const info = await this.apiRequest('app/info');
    if (info && info.components) {
      return info.components['cloud-pipeline-webdav-client'];
    }
    return undefined;
  }

  async diagnose() {
    await this.getDataStorageAvailable();
    return true;
  }
}

module.exports = CloudPipelineApi;
