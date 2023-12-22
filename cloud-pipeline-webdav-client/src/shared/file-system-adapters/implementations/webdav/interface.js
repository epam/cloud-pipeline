const WebDAVClient = require('./client');
const WebBasedInterface = require('../web-based/interface');
const Types = require('../../types');
const CloudPipelineAPI = require('../../../api/cloud-pipeline-api');
const WebDAVApi = require('../../../api/webdav-api');
const ApiError = require('../../../api/api-error');
const logger = require('../../../shared-logger');
const correctDirectoryPath = require('../../utils/correct-path');
const { FileSystemAdapterError, FileSystemAdapterInitializeError } = require('../../errors');

function formatStorageName(storage = {}) {
  return (storage.name || '').replace(/-/g, '_');
}

/**
 * Count of paths to send immediate permissions request
 * @type {number}
 */
const PERMISSIONS_REQUESTS_CHUNK_SIZE = 20;
const PERMISSIONS_REQUESTS_DEBOUNCE_MS = 1; // 1 sec
const PERMISSIONS_REQUESTS_RETRY_TIMEOUT_MS = 10000; // 10 sec

class WebDAVInterface extends WebBasedInterface {
  /**
   * @param {FileSystemAdapter} adapter
   */
  constructor(adapter) {
    super(Types.webdav, adapter);
    this.permissionsRequests = [];
  }

  /**
   * @typedef {Object} WebdavInterfaceAdditionalOptions
   * @property {*[]} [storages]
   * @property {string} [rootName]
   * @property {string} [apiURL]
   * @property {string} [extraApiURL]
   * @property {boolean} [ignoreCertificateErrors]
   * @property {boolean} [updatePermissions]
   * @property {string} [disclaimer]
   */

  /**
   * @param {FileSystemAdapterOptions & WebdavInterfaceAdditionalOptions} options
   * @returns {Promise<void>}
   */
  async initialize(options) {
    await super.initialize(options);
    this.ignoreCertificateErrors = options?.ignoreCertificateErrors;
    this.disclaimer = options?.disclaimer;
    this.updatePermissions = options?.updatePermissions;
    this.storages = options?.storages || [];
    this.name = options.name || options.rootName || 'Cloud Data';
    this.rootName = options.rootName ? `${options.rootName} Root` : 'Root';
    if (options.apiURL) {
      this.api = new CloudPipelineAPI({
        api: options.apiURL,
        password: options.password,
        ignoreCertificateErrors: this.ignoreCertificateErrors,
      });
    }
    this.webdavApi = new WebDAVApi({
      url: options.extraApiURL || options.url,
      password: options.password,
      ignoreCertificateErrors: options.ignoreCertificateErrors,
      correctUrl: !options.extraApiURL,
    });
    this.client = new WebDAVClient(this.name);
    await this.client.initialize(
      this.url,
      this.user,
      this.password,
      this.ignoreCertificateErrors,
      this.disclaimer,
    );
    if (!this.updatePermissions) {
      this.clearPermissionsRequestsTimeout();
      this.permissionsRequests = [];
    }
    if (this.updatePermissions) {
      (this.sendPermissionsRequest)(0);
    }
  }

  async updateStorages(force = false) {
    if (!this.api) {
      return [];
    }
    if (!force && this.storages.length) {
      return this.storages;
    }
    if (!this.updateStoragesRequest || force) {
      const logic = async () => {
        if (!this.api) {
          return [];
        }
        try {
          this.storages = await this.api.getStorages();
        } catch (error) {
          logger.warn(`Error updating storage list: ${error.message}`);
          this.updateStoragesRequest = undefined;
        }
        return this.storages;
      };
      this.updateStoragesRequest = logic();
    }
    return this.updateStoragesRequest;
  }

  async updateRemotePermissions(...request) {
    if (!this.updatePermissions) {
      return;
    }
    await this.updateStorages(false);
    const objectStorageNames = (this.storages || [])
      .filter((storage) => !/^nfs$/i.test(storage.type))
      .map(formatStorageName);
    const filtered = request.filter((path) => {
      const storageName = path.split('/').filter((o) => o.length)[0];
      return !objectStorageNames.includes(storageName);
    });
    if (filtered.length > 0) {
      const list = '\n\n'.concat(filtered.join('\n')).concat('\n');
      logger.log(`Permissions requests added (${this.permissionsRequests.length} in queue, ${filtered.length} added):${list}`);
    }
    this.permissionsRequests.push(...filtered);
    (this.sendPermissionsRequest)(
      this.permissionsRequests.length > PERMISSIONS_REQUESTS_CHUNK_SIZE
        ? 0
        : PERMISSIONS_REQUESTS_DEBOUNCE_MS,
    );
  }

  clearPermissionsRequestsTimeout() {
    clearTimeout(this.permissionsRequestTimeout);
    this.permissionsRequestTimeout = undefined;
  }

  async sendPermissionsRequest(debounce = PERMISSIONS_REQUESTS_DEBOUNCE_MS) {
    this.clearPermissionsRequestsTimeout();
    if (this.permissionsRequests.length > 0 && this.updatePermissions) {
      const send = async () => {
        const path = this.permissionsRequests.slice();
        const list = '\n\n'.concat(path.join('\n')).concat('\n');
        logger.log(`Sending ${path.length} permission${path.length === 1 ? '' : 's'} requests:${list}`);
        this.permissionsRequests = [];
        try {
          const result = await this.webdavApi.sendPermissionsRequest(path);
          logger.log(`${path.length} permission${path.length === 1 ? '' : 's'} requests sent: \n\n${result || ''}\n\n`);
          this.sendPermissionsRequest();
        } catch (error) {
          if (error instanceof ApiError) {
            logger.error(`API error sending permissions request: ${error.message}`);
          } else {
            logger.error(`Network error sending permissions request: ${error.message}`);
            logger.log(`Retrying in ${Math.floor(PERMISSIONS_REQUESTS_RETRY_TIMEOUT_MS / 10000) * 10} seconds.`);
            this.permissionsRequests.push(...path);
            this.sendPermissionsRequest(PERMISSIONS_REQUESTS_RETRY_TIMEOUT_MS);
          }
        }
      };
      if (debounce === 0) {
        return send();
      }
      return new Promise((resolve) => {
        this.permissionsRequestTimeout = setTimeout(() => send().then(resolve), debounce);
      });
    }
    return undefined;
  }

  getFilesChecksums(files = []) {
    return this.webdavApi.getChecksum(files);
  }

  async list(directory = '/') {
    const directoryCorrected = correctDirectoryPath(directory);
    const isRoot = /^(|\/)$/.test(directoryCorrected);
    await this.updateStorages(isRoot);
    const firstDirectory = isRoot
      ? '/'
      : directoryCorrected.split('/').filter(Boolean)[0];
    const isHiddenStorage = (storageName) => {
      if (!storageName) {
        return false;
      }
      const currentStorage = (this.storages || [])
        .find((aStorage) => storageName
          && formatStorageName(aStorage).toLowerCase() === storageName.toLowerCase());
      return !!currentStorage && !!currentStorage.hidden;
    };
    if (isHiddenStorage(firstDirectory)) {
      throw new FileSystemAdapterError(`Access to "${firstDirectory}" is denied`);
    }
    const objectStorageNames = new Set(
      (this.storages || [])
        .filter((aStorage) => !/^nfs$/i.test(aStorage.type))
        .map(formatStorageName)
        .map((o) => o.toLowerCase()),
    );
    const contents = await super.list(directoryCorrected);
    const filteredContents = contents.filter((anItem) => isRoot || !isHiddenStorage(anItem.name));
    /**
     * @param {FSItem} anItem
     * @returns {FSItem}
     */
    const mapFilteredItem = (anItem) => ({
      ...anItem,
      isObjectStorage: isRoot
        && anItem.isDirectory
        && objectStorageNames.has(anItem.name.toLowerCase()),
    });
    return filteredContents.map(mapFilteredItem);
  }

  /**
   * @param {string} element
   * @param {stream.Readable|Buffer|string} data
   * @param {WriteFileOptions} [options]
   */
  async writeFile(element, data, options = {}) {
    if (!this.client) {
      throw new FileSystemAdapterInitializeError(`"${this.type}" interface not initialized`);
    }
    await this.client.writeFile(element, data, options);
    (this.updateRemotePermissions)(element);
  }

  onDirectoryCreated(directory = '') {
    (this.updateRemotePermissions)(directory);
  }

  async destroy() {
    await this.sendPermissionsRequest(0);
    await super.destroy();
    this.storages = [];
  }
}

module.exports = WebDAVInterface;
