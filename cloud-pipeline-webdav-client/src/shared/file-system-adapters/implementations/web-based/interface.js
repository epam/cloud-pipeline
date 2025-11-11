const FileSystemInterface = require('../../interface');
const { FileSystemAdapterInitializeError } = require('../../errors');
const correctDirectoryPath = require('../../utils/correct-path');

class WebBasedInterface extends FileSystemInterface {
  /**
   * @param {FileSystemAdapterOptions} options
   * @returns {Promise<void>}
   */
  async initialize(options) {
    await super.initialize();
    if (!options?.url) {
      throw new FileSystemAdapterInitializeError(`${this.type || ''} url not specified`);
    }
    this.url = options?.url;
    this.user = options?.user;
    this.password = options.password;
    this.root = '';
    this.rootName = 'Root';
    /**
     * @type {WebBasedClient}
     */
    this.client = undefined;
  }

  async cancelCurrentTask() {
    if (this.client) {
      await this.client.cancelCurrentTask();
    }
  }

  async isDirectory(element = '/') {
    if (!this.client) {
      throw new FileSystemAdapterInitializeError(`"${this.type}" interface not initialized`);
    }
    return this.client.isDirectory(element);
  }

  async getFileInfo(element = '') {
    try {
      if (!this.client) {
        throw new FileSystemAdapterInitializeError(`"${this.type}" interface not initialized`);
      }
      return this.client.statistics(element);
    } catch (_) {
      if (_.status === 401) {
        throw new Error(`${this.type}: unauthorized`);
      }
      return undefined;
    }
  }

  async exists(element = '') {
    if (!this.client) {
      throw new FileSystemAdapterInitializeError(`"${this.type}" interface not initialized`);
    }
    return this.client.exists(element);
  }

  // eslint-disable-next-line class-methods-use-this
  async getParentDirectory(element = '') {
    return correctDirectoryPath(
      correctDirectoryPath(element).split('/').slice(0, -2).join('/'),
    );
  }

  async list(directory = '/') {
    if (!this.client) {
      throw new FileSystemAdapterInitializeError(`"${this.type}" interface not initialized`);
    }
    const directoryCorrected = correctDirectoryPath(directory);
    const isRoot = /^(|\/)$/.test(directoryCorrected);
    const contents = await this.client.getDirectoryContents(directoryCorrected);
    const parentDirectory = await this.getParentDirectory(directoryCorrected);
    /**
     * @type {FSItem}
     */
    const parentItem = {
      name: '..',
      path: parentDirectory,
      isDirectory: true,
      isFile: false,
      isSymbolicLink: false,
      isBackLink: true,
    };
    return [
      isRoot ? undefined : parentItem,
      ...contents,
    ].filter(Boolean);
  }

  async createDirectory(directory = '') {
    const exists = await this.exists(directory);
    if (exists) {
      const isDirectory = await this.isDirectory(directory);
      if (!isDirectory) {
        throw new Error(`Cannot create directory at path "${directory}": a file with the same name already exists`);
      }
    }
    if (!this.client) {
      throw new FileSystemAdapterInitializeError(`"${this.type}" interface not initialized`);
    }
    const corrected = correctDirectoryPath(directory);
    const result = await this.client.createDirectory(corrected, { recursive: true });
    this.onDirectoryCreated(corrected);
    return result;
  }

  async createReadStream(element, options = {}) {
    if (!this.client) {
      throw new FileSystemAdapterInitializeError(`"${this.type}" interface not initialized`);
    }
    return this.client.createReadStream(element, options);
  }

  async createWriteStream(element, options = {}) {
    if (!this.client) {
      throw new FileSystemAdapterInitializeError(`"${this.type}" interface not initialized`);
    }
    return this.client.createWriteStream(element, options);
  }

  async remove(element = '') {
    if (!this.client) {
      throw new FileSystemAdapterInitializeError(`"${this.type}" interface not initialized`);
    }
    const isDirectory = await this.isDirectory(element);
    if (isDirectory) {
      return this.client.removeDirectory(element);
    }
    return this.client.removeFile(element);
  }

  async destroy() {
    await super.destroy();
    if (this.client) {
      await this.client.destroy();
    }
  }
}

module.exports = WebBasedInterface;
