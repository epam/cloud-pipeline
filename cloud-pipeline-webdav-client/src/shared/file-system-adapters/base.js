/* eslint-disable class-methods-use-this,no-unused-vars,max-classes-per-file */
const types = require('./types');
const { FileSystemAdapterInitializeError } = require('./errors');
const logger = require('../shared-logger');
const correctDirectoryPath = require('./utils/correct-path');

/**
 * A helper class that manages FS adapter session.
 * An FS adapter can create a session that initiates `interface` (i.e. client instance)
 * for FS requests; that can be useful for some cases - for instance, if we want
 * to create interface and reuse it for some requests without destroying after each of
 * them.
 * `LastRequestOnlySession` exposes `doRequest` method that will create or
 * reuse interface; `doRequest` method uses short queue of requests - only last one will be
 * performed (e.g., we can call `doRequest(<list directory contents method>)` multiple
 * times, but only last request will be performed)
 */
class LastRequestOnlySession {
  /**
   * @param {FileSystemAdapter} adapter
   * @param {string} sessionName
   */
  constructor(adapter, sessionName) {
    if (!adapter) {
      throw new Error('Cannot initialize last-request-only session: adapter not specified');
    }
    if (!sessionName) {
      throw new Error('Cannot initialize last-request-only session: key not specified');
    }
    this.adapter = adapter;
    this.sessionName = sessionName;
    this.currentRequest = undefined;
    this.request = undefined;
    /**
     * @type {FileSystemInterface}
     */
    this.fsInterface = undefined;
  }

  async destroy() {
    if (this.fsInterface && this.adapter) {
      await this.adapter.destroyInterface(this.fsInterface);
    }
    this.adapter = undefined;
    this.cancelOngoingRequest();
    this.currentRequest = undefined;
  }

  cancelOngoingRequest() {
    if (this.request && typeof this.request.reject === 'function') {
      this.request.reject(new Error('Listing skipped'));
    }
  }

  registerRequest(request, resolve, reject) {
    this.cancelOngoingRequest();
    this.request = {
      request,
      resolve,
      reject,
    };
    return this.perform();
  }

  async perform() {
    if (this.currentRequest) {
      await this.currentRequest;
    }
    this.currentRequest = undefined;
    if (this.request) {
      const {
        request: promiseFn,
        resolve: resolveRequest,
        reject: rejectRequest,
      } = this.request;
      this.request = undefined;
      this.currentRequest = new Promise((resolve) => {
        promiseFn()
          .then((result) => resolveRequest(result))
          .catch(rejectRequest)
          .then(() => {
            this.currentRequest = undefined;
            resolve();
            return Promise.resolve();
          })
          .then(() => {
            (this.perform)();
          });
      });
    }
  }

  async acquireInterface() {
    if (this.fsInterface) {
      return this.fsInterface;
    }
    this.fsInterface = await this.adapter.createInterface();
    return this.fsInterface;
  }

  async doRequest(fn) {
    return new Promise((resolve, reject) => {
      this.registerRequest(
        async () => {
          const i = await this.acquireInterface();
          return fn(i);
        },
        resolve,
        reject,
      );
    });
  }
}

/**
 * @typedef {Object} FSItem
 * @property {string} name
 * @property {string} path
 * @property {boolean} isDirectory
 * @property {boolean} isFile
 * @property {boolean} [isSymbolicLink]
 * @property {boolean} [isBackLink]
 * @property {number} [size]
 * @property {moment.Moment} [changed]
 * @property {boolean} [isObjectStorage]
 * @property {string} [owner]
 * @property {string} [group]
 * @property {boolean} [removable]
 */

/**
 * @typedef {Object} FileSystemAdapterOptions
 * @property {string} [type]
 * @property {string} [identifier]
 * @property {string} [name]
 * @property {string} [user]
 * @property {string} [password]
 * @property {string} [url]
 * @property {boolean} [ignoreCertificateErrors]
 */

class FileSystemAdapter {
  static Types = types;

  /**
     * @param {FileSystemAdapterOptions} options
     */
  constructor(options) {
    if (!options) {
      throw new FileSystemAdapterInitializeError('options not specified');
    }
    if (!options.type) {
      throw new FileSystemAdapterInitializeError('type not specified');
    }
    if (!options.identifier) {
      throw new FileSystemAdapterInitializeError('identifier not specified');
    }
    /**
     * File System Adapter type
     * @type {string}
     */
    this.type = options.type;
    /**
     * FIle System Adapter identifier
     * @type {string}
     */
    this.identifier = options.identifier;
    /**
     * Root
     * @type {string}
     */
    this.root = '';
    this.name = options.name;
    this.rootName = 'Root';
    /**
     * @type {LastRequestOnlySession[]}
     */
    this.lastRequestOnlySessions = [];
  }

  toString() {
    return `Adapter ${this.type}`;
  }

  /**
   * @returns {string}
   */
  getInitialDirectory() {
    return '/';
  }

  // eslint-disable-next-line class-methods-use-this
  getPathSeparator() {
    return '/';
  }

  // eslint-disable-next-line class-methods-use-this, no-unused-vars
  getPathComponents(element = '') {
    return [];
  }

  /**
   * @param {string} root
   * @param {string} pathToJoin
   * @param {boolean} [isDirectory=false]
   * @returns {string}
   */
  joinPath(root, pathToJoin, isDirectory = false) {
    const separator = this.getPathSeparator();
    let rootCorrected = (root || '');
    if (rootCorrected.endsWith(separator)) {
      rootCorrected = rootCorrected.slice(0, -1);
    }
    const pathToJoinCorrected = correctDirectoryPath(
      pathToJoin,
      { leadingSlash: false, trailingSlash: isDirectory, separator },
    );
    return `${rootCorrected}${separator}${pathToJoinCorrected}`;
  }

  /**
   * @param {string} element
   * @param {string} relativeTo
   * @returns {string}
   */
  getRelativePath(element, relativeTo) {
    if (!element) {
      return undefined;
    }
    const separator = this.getPathSeparator();
    let relativeCorrected = (relativeTo || separator);
    if (!relativeCorrected.endsWith(separator)) {
      relativeCorrected = relativeCorrected.concat(separator);
    }
    if (element.startsWith(relativeCorrected)) {
      let result = element.slice(relativeCorrected.length);
      if (result.endsWith(separator)) {
        result = result.slice(0, -1);
      }
      return result;
    }
    return undefined;
  }

  /**
   * @param {string} element
   * @param {string} relativeTo
   * @returns {string[]}
   */
  getRelativePathComponents(element, relativeTo) {
    const relative = this.getRelativePath(element, relativeTo);
    if (relative) {
      return relative.split(this.getPathSeparator());
    }
    return [];
  }

  /**
   * @param {FileSystemAdapterOptions} options
   * @returns {boolean}
   */
  equals(options) {
    return this.identifier === options?.identifier && this.type === options?.type;
  }

  /**
   * @param {function(fsInterface: FileSystemInterface, *):*} fn
   * @param {*} options
   * @returns {Promise<*>}
   */
  async useSession(fn, ...options) {
    let fsInterface;
    try {
      fsInterface = await this.createInterface();
      const result = await fn(fsInterface, ...options);
      return result;
    } catch (error) {
      logger.error(error.message);
      throw error;
    } finally {
      await this.destroyInterface(fsInterface);
    }
  }

  /**
   * @param {string} sessionName
   * @param {function(fsInterface: FileSystemInterface, *):*} fn
   * @returns {Promise<*>}
   */
  async useLastRequestOnlySession(sessionName, fn) {
    try {
      if (!this.lastRequestOnlySessions.find((aSession) => aSession.sessionName === sessionName)) {
        this.lastRequestOnlySessions.push(
          new LastRequestOnlySession(this, sessionName),
        );
      }
      const session = this.lastRequestOnlySessions.find(
        (aSession) => aSession.sessionName === sessionName,
      );
      return session.doRequest(fn);
    } catch (error) {
      logger.error(error.message);
      throw error;
    }
  }

  /**
   * @returns {Promise<FileSystemInterface>}
   */
  async createInterface() {
    return undefined;
  }

  /**
   * @param {FileSystemInterface} fsInterface
   * @returns {Promise<void>}
   */
  async destroyInterface(fsInterface) {
    if (fsInterface) {
      await fsInterface.destroy();
    }
  }

  async diagnose() {
    return this.useSession(async (fsInterface) => {
      await fsInterface.list();
      return true;
    });
  }

  async destroy() {
    await Promise.all(this.lastRequestOnlySessions.map((aSession) => aSession.destroy()));
  }
}

module.exports = FileSystemAdapter;
