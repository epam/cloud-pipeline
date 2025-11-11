const BasicFTP = require('basic-ftp');
const moment = require('moment-timezone');
const { PassThrough } = require('stream');
const WebBasedClient = require('../web-based/client');
const Logger = require('../../../logger');
const defaultLogger = require('../../../shared-logger');
const correctDirectoryPath = require('../../utils/correct-path');
const Protocols = require('./protocols');
const { getReadableStream } = require('../../utils/streams');
const displayDate = require('../../../utilities/display-date');
const makeDelayedLog = require('../../utils/delayed-log');
const AbortablePromise = require('../../utils/abortable-promise');

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

class FTPClient extends WebBasedClient {
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
      protocol = Protocols.ftp,
      ignoreCertificateErrors = false,
    } = options || {};
    await super.initialize(url);
    this.initializeOptions = options || {};
    this.url = url;
    this.client = new BasicFTP.Client();
    if (enableLogs) {
      const ftpClientLogger = new Logger((url || '').replace(/[\s.,;!?^$]/g, '-'));
      ftpClientLogger.enabled = true;
      this.log = (...message) => {
        defaultLogger.log(`[${url}]`, ...message);
        ftpClientLogger.log(...message);
      };
      this.client.ftp.log = this.log.bind(this);
    }
    let secure = true;
    const secureOptions = {
      rejectUnauthorized: !ignoreCertificateErrors,
    };
    switch (Protocols.parse(protocol)) {
      case Protocols.ftp:
        secure = false;
        break;
      case Protocols.ftps:
        secure = 'implicit';
        break;
      case Protocols.ftpes:
        secure = true;
        break;
      default:
        break;
    }
    this.log('Connecting...');
    await this.client.access({
      host: url,
      port,
      user,
      password,
      secure,
      secureOptions,
    });
    this.log('Connected');
  }

  async reconnectIfRequired() {
    if (this.client && this.client.closed && this.initializeOptions) {
      this.log('Socket closed. Reconnecting...');
      await this.initialize(this.initializeOptions);
    }
  }

  // eslint-disable-next-line class-methods-use-this
  async cancelCurrentTask() {
    if (this.client) {
      try {
        this.client.close();
      } catch (_) { /* empty */ }
    }
  }

  async getDirectoryContents(directory) {
    await this.reconnectIfRequired();
    this.log(`Fetching "${directory}"...`);
    const contents = await this.client.list(directory);
    this.info(`Fetching "${directory}":`, contents.length, 'elements');
    const mapFTPItem = (anItem) => ({
      name: anItem.name,
      path: directory.concat(anItem.name),
      isDirectory: anItem.type === BasicFTP.FileType.Directory,
      isFile: anItem.type === BasicFTP.FileType.File,
      isSymbolicLink: anItem.type === BasicFTP.FileType.SymbolicLink,
      size: anItem.type === BasicFTP.FileType.File ? Number(anItem.size) : undefined,
      changed: anItem.modifiedAt ? moment(anItem.modifiedAt) : undefined,
      isObjectStorage: false,
    });
    const results = contents.map(mapFTPItem);
    this.logDirectoryContents(results);
    return results;
  }

  async statistics(element) {
    await this.reconnectIfRequired();
    this.log(`Fetching ${element} statistics:`);
    const size = await this.client.size(element);
    this.log(`  size        : ${size}`);
    let modifyTime;
    try {
      modifyTime = await this.client.lastMod(element);
    } catch (_) { /* empty */ }
    this.log(`  modify time : ${modifyTime ? displayDate(moment(modifyTime)) : '<empty>'}`);
    const isDirectory = await this.isDirectory(element);
    this.log(`  type        : ${isDirectory ? 'directory' : 'file'}`);
    return {
      size,
      type: isDirectory ? 'directory' : 'file',
      changed: modifyTime ? moment(modifyTime) : undefined,
    };
  }

  async createDirectory(directory) {
    await this.reconnectIfRequired();
    this.log(`Creating directory "${directory}"...`);
    await this.client.ensureDir(directory);
    this.info(`Creating directory "${directory}" done`);
  }

  createStreamHelpers(progressTitle, bytesMsg = 'received') {
    const stream = new PassThrough();
    const passThrough = new PassThrough();
    const { flush, log } = makeDelayedLog(this.log.bind(this));
    let total = 0;
    passThrough
      .on('data', (bytes) => {
        total += bytes.length;
        log(`${progressTitle}:`, total, `bytes ${bytesMsg}`);
      });
    const onFulfilled = () => {
      stream.end();
      flush();
    };
    const onRejected = (error) => {
      flush();
      let emitError = error;
      if (/forgot to use 'await' or '\.then\(\)'/i.test(error.message)) {
        emitError = new Error('Cannot perform operation while previous is in progress');
      }
      this.error(`${progressTitle}: rejected with error:`, emitError.message);
      if (emitError !== error) {
        this.error(' * original error:', error.message);
      }
      stream.emit('error', emitError);
    };
    passThrough.pipe(stream, { end: false });
    return {
      stream,
      passThrough,
      onFulfilled,
      onRejected,
    };
  }

  // eslint-disable-next-line no-unused-vars
  async createReadStream(file, options = {}) {
    await this.reconnectIfRequired();
    const {
      stream,
      passThrough,
      onFulfilled,
      onRejected,
    } = this.createStreamHelpers(`downloading ${file}`);
    this.client.downloadTo(passThrough, file)
      .then(
        onFulfilled,
        onRejected,
      )
      .catch((error) => this.error(`download ${file} error:`, error.message));
    return stream;
  }

  // eslint-disable-next-line no-unused-vars
  async createWriteStream(file, options) {
    await this.reconnectIfRequired();
    const {
      stream,
      passThrough,
      onFulfilled,
      onRejected,
    } = this.createStreamHelpers(`uploading ${file}`, 'sent');
    this.client.uploadFrom(passThrough, file)
      .then(
        onFulfilled,
        onRejected,
      )
      .catch((error) => this.error(`upload ${file} error:`, error.message));
    return stream;
  }

  /**
   * @param {string} element
   * @param {stream.Readable|Buffer|string} data
   * @param {WriteFileOptions} [options]
   */
  async writeFile(element, data, options) {
    const {
      size,
      progressCallback,
      abortSignal,
    } = options;
    const readableStream = getReadableStream(data);
    if (!readableStream) {
      throw new Error(`Error writing to ${element}: source data is empty`);
    }
    await this.reconnectIfRequired();
    this.client.trackProgress((info) => {
      if (
        typeof progressCallback === 'function'
        && info.name === element
      ) {
        progressCallback(info.bytesOverall, size);
      }
    });
    return AbortablePromise(
      abortSignal,
      () => {
        this.client.ftp.closeWithError(new Error('Aborted'));
        this.client.trackProgress();
        readableStream.destroy();
      },
      (resolve, reject) => {
        readableStream
          .on('error', (error) => {
            this.error('source stream error:', error.message);
            reject(error);
          });
        this.client.uploadFrom(readableStream, element)
          .then(() => {
            this.client.trackProgress();
          })
          .then(resolve)
          .catch(reject);
      },
    );
  }

  async removeDirectory(directory) {
    if (!directory) {
      return Promise.resolve();
    }
    await this.reconnectIfRequired();
    this.info(`Removing directory "${directory}"`);
    return this.client.removeDir(correctDirectoryPath(directory));
  }

  async removeFile(file) {
    if (!file) {
      return Promise.resolve();
    }
    await this.reconnectIfRequired();
    let filePathCorrected = file;
    if (filePathCorrected.endsWith('/')) {
      filePathCorrected = filePathCorrected.slice(0, -1);
    }
    this.info(`Removing file "${filePathCorrected}"`);
    return this.client.remove(filePathCorrected, true);
  }

  async exists(element = '') {
    await this.reconnectIfRequired();
    if (await this.isFile(element)) {
      return true;
    }
    return this.isDirectory(element, true);
  }

  async isFile(element = '/') {
    if (element === '/') {
      return false;
    }
    try {
      await this.reconnectIfRequired();
      const corrected = element.startsWith('/') ? element : '/'.concat(element);
      // trying to cd
      await this.client.send(`MDTM ${corrected}`);
      return true;
    } catch (_) { /* empty */ }
    return false;
  }

  async isDirectory(element = '/') {
    if (element === '/') {
      return true;
    }
    try {
      await this.reconnectIfRequired();
      // trying to cd
      await this.client.cd(element);
      await this.client.cd('/');
      return true;
    } catch (_) { /* empty */ }
    return false;
  }

  async destroy() {
    if (this.client) {
      try {
        this.client.close();
      } catch (error) {
        this.error(`Error closing FTP(s) client: ${error.message}`);
      }
    }
    return Promise.resolve();
  }
}

module.exports = FTPClient;
