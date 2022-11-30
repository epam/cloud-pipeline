const WebDAVClient = require('./client');
const WebBasedInterface = require('../web-based/interface');
const Types = require('../../types');
const CloudPipelineAPI = require('../../../api/cloud-pipeline-api');
const logger = require('../../../common/logger');
const correctDirectoryPath = require('../../utils/correct-path');
const { FileSystemAdapterError, FileSystemAdapterInitializeError } = require('../../errors');

function formatStorageName(storage = {}) {
  return (storage.name || '').replace(/-/g, '_');
}

class WebDAVInterface extends WebBasedInterface {
  /**
   * @param {FileSystemAdapter} adapter
   */
  constructor(adapter) {
    super(Types.webdav, adapter);
  }

  /**
   * @typedef {Object} WebdavInterfaceAdditionalOptions
   * @property {*[]} [storages]
   * @property {string} [rootName]
   * @property {string} [apiURL]
   * @property {boolean} [ignoreCertificateErrors]
   */

  /**
   * @param {FileSystemAdapterOptions & WebdavInterfaceAdditionalOptions} options
   * @returns {Promise<void>}
   */
  async initialize(options) {
    await super.initialize(options);
    this.ignoreCertificateErrors = options?.ignoreCertificateErrors;
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
    this.client = new WebDAVClient(this.name);
    await this.client.initialize(this.url, this.user, this.password, this.ignoreCertificateErrors);
  }

  async updateStorages(force = false) {
    if (!this.api) {
      return [];
    }
    if (!force && this.storages.length) {
      return this.storages;
    }
    try {
      this.storages = await this.api.getStorages();
    } catch (error) {
      logger.warn(`Error updating storage list: ${error.message}`);
    }
    return this.storages;
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
    return this.client.writeFile(element, data, options);
  }

  async destroy() {
    await super.destroy();
    this.storages = [];
  }
}

module.exports = WebDAVInterface;
