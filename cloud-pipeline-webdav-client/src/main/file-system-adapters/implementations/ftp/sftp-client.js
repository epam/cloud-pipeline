const SSH2SFTPClient = require('ssh2-sftp-client');
const moment = require('moment-timezone');
const WebBasedClient = require('../web-based/client');
const Logger = require('../../../common/logger/logger');
const correctDirectoryPath = require('../../utils/correct-path');
const defaultLogger = require('../../../common/logger');

/**
 * @typedef {Object} FTPClientOptions
 * @property {string} url
 * @property {string} protocol
 * @property {number} port
 * @property {string} user
 * @property {string} password
 * @property {boolean} enableLogs
 * @property {boolean} ignoreCertificateErrors
 */

class SFTPClient extends WebBasedClient {
  /**
   * @param {FTPClientOptions} options
   * @returns {Promise<void>}
   */
  async initialize(options) {
    const {
      url,
      port,
      user,
      password,
      enableLogs,
    } = options || {};
    await super.initialize(url);
    this.client = new SSH2SFTPClient();
    let debug;
    if (enableLogs) {
      const ftpClientLogger = new Logger((url || '').replace(/[\s.,;!?^$]/g, '-'));
      ftpClientLogger.enabled = true;
      this.log = (...message) => {
        defaultLogger.log(`[${url}]`, ...message);
        ftpClientLogger.log(...message);
      };
      debug = ftpClientLogger.log.bind(ftpClientLogger);
    }
    await this.client.connect({
      host: url,
      port,
      username: user,
      password,
      debug,
    });
  }

  async getDirectoryContents(directory) {
    this.log(`Fetching "${directory}"...`);
    const contents = await this.client.list(directory);
    this.info(`Fetching "${directory}":`, contents.length, 'elements');
    const mapFTPItem = (anItem) => ({
      name: anItem.name,
      path: directory.concat(anItem.name),
      isDirectory: /^d$/i.test(anItem.type),
      isFile: !/^d$/i.test(anItem.type),
      isSymbolicLink: false,
      size: /^d$/i.test(anItem.type) ? undefined : Number(anItem.size),
      changed: moment(anItem.modifyTime),
      isObjectStorage: false,
    });
    const results = contents.map(mapFTPItem);
    this.logDirectoryContents(results);
    return results;
  }

  async statistics(element) {
    const {
      size,
      isDirectory,
      modifyTime,
    } = await this.client.stat(element);
    return {
      size,
      type: isDirectory ? 'directory' : 'file',
      changed: moment(modifyTime),
    };
  }

  async isDirectory(directory = '') {
    const info = await this.client.exists(directory);
    // info is "false" if "directory" does not exist
    // OR
    // "d" - is directory
    // "-" - is file
    // "l" - is link
    return /^d$/i.test(info);
  }

  async exists(element = '') {
    return this.client.exists(element);
  }

  async createDirectory(directory) {
    await this.client.mkdir(directory, true);
  }

  // eslint-disable-next-line no-unused-vars
  async createReadStream(file, options = {}) {
    return this.client.createReadStream(file, { autoClose: true });
  }

  // eslint-disable-next-line no-unused-vars
  async createWriteStream(file, options) {
    return this.client.createWriteStream(file, { autoClose: true });
  }

  async removeDirectory(directory) {
    if (!directory) {
      return Promise.resolve();
    }
    return this.client.rmdir(correctDirectoryPath(directory), true);
  }

  async removeFile(file) {
    if (!file) {
      return Promise.resolve();
    }
    let filePathCorrected = file;
    if (filePathCorrected.endsWith('/')) {
      filePathCorrected = filePathCorrected.slice(0, -1);
    }
    return this.client.delete(filePathCorrected, true);
  }

  async destroy() {
    if (this.client) {
      try {
        await this.client.end();
      } catch (error) {
        this.error(`Error closing SFTP client: ${error.message}`);
      }
    }
    return Promise.resolve();
  }
}

module.exports = SFTPClient;
