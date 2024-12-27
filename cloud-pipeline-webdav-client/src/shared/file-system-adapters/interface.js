/* eslint-disable class-methods-use-this, no-unused-vars, no-empty-function */
const stream = require('stream');
const EventEmitter = require('events');
const {
  waitForStreamClosed,
  releaseStream,
  getReadableStream,
} = require('./utils/streams');
const AbortablePromise = require('./utils/abortable-promise');

class FileSystemInterface extends EventEmitter {
  /**
   * @param {string} type
   * @param {FileSystemAdapter} adapter
   */
  constructor(type, adapter) {
    super();
    this.type = type;
    this.adapter = adapter;
    this.streams = [];
  }

  /**
   * @param {Readable|Writable|Duplex|PassThrough|Transform} aStream
   * @returns {Readable|Writable|Duplex|PassThrough|Transform}
   */
  registerStream(aStream) {
    if (aStream) {
      this.streams.push(aStream);
      waitForStreamClosed(aStream)
        .then(() => this.unregisterStream(aStream));
    }
    return aStream;
  }

  /**
   * @param {Readable|Writable|Duplex|PassThrough|Transform} aStream
   */
  unregisterStream(aStream) {
    const idx = this.streams.indexOf(aStream);
    if (idx >= 0) {
      this.streams.splice(idx, 1);
    }
  }

  get separator() {
    return this.getPathSeparator();
  }

  async initialize() {
    if (!this.adapter) {
      throw new Error(`Adapter not specified for "${this.type || 'unknown'}" interface`);
    }
  }

  async cancelCurrentTask() {
  }

  async destroy() {
    const streams = this.streams.slice();
    await Promise.all(streams.map(releaseStream));
    this.streams = [];
  }

  /**
   * @param {string} element
   * @returns {Promise<boolean>}
   */
  async isDirectory(element = '') {
    return false;
  }

  /**
   * @param {string} element
   * @returns {Promise<{size: number, changed: moment.Moment}>}
   */
  async getFileInfo(element = '') {
    return undefined;
  }

  /**
   * @param {string} element
   * @returns {Promise<boolean>}
   */
  async exists(element = '') {
    return false;
  }

  /**
   * @param {string} element
   * @returns {Promise<string>}
   */
  async getParentDirectory(element = '') {
    return '';
  }

  /**
   * @param {string} [directory]
   * @returns {Promise<FSItem[]>}
   */
  async list(directory = '') {
    return [];
  }

  /**
   * @param {string} directory
   * @returns {Promise<void>}
   */
  async createDirectory(directory = '') {
    throw new Error(`Create directory operation is not implemented for "${this.type}" file system`);
  }

  onDirectoryCreated(directory = '') {
    // noop
  }

  /**
   * @param {string} element
   * @returns {Promise<void>}
   */
  async remove(element = '') {
    throw new Error(`Remove item operation is not implemented for "${this.type}" file system`);
  }

  /**
   * @param {string} element
   * @param {{}} [options]
   * @returns {*}
   */
  async createReadStream(element, options = {}) {
    return undefined;
  }

  /**
   * @param {string} element
   * @param {{}} [options]
   * @returns {Promise<{data: Readable|Buffer|string, size: number?}>}
   */
  async readFile(element, options = {}) {
    const info = await this.getFileInfo(element);
    const {
      size = 0,
    } = info || {};
    const data = this.registerStream(await this.createReadStream(element, options));
    return {
      data,
      size,
    };
  }

  async getFilesChecksums(files) {
    return files.map(() => undefined);
  }

  /**
   * @param {string} element
   * @param {{size: number?, overwrite: boolean?}} [options]
   * @returns {*}
   */
  async createWriteStream(element, options = {}) {
    return undefined;
  }

  /**
   * @typedef {Object} WriteFileOptions
   * @property {number} [size]
   * @property {boolean} [overwrite=true]
   * @property {function} [progressCallback]
   * @property {EventEmitter} [abortSignal]
   */

  /**
   * @param {string} element
   * @param {stream.Readable|Buffer|string} data
   * @param {WriteFileOptions} [options]
   */
  async writeFile(element, data, options = {}) {
    const writeStream = this.registerStream(await this.createWriteStream(element, options));
    const readableStream = getReadableStream(data);
    if (!readableStream) {
      throw new Error(`Error writing to ${element}: source data is empty`);
    }
    const {
      size,
      progressCallback,
      abortSignal,
    } = options;
    if (typeof progressCallback === 'function' && size) {
      let transferred = 0;
      readableStream
        .on('data', (chunk) => {
          transferred += chunk.length;
          progressCallback(transferred, size);
        });
    }
    return AbortablePromise(
      abortSignal,
      () => {
        readableStream.destroy();
        writeStream.end();
        writeStream.destroy();
      },
      (resolve, reject) => {
        readableStream
          .on('error', reject);
        writeStream
          .on('error', reject);
        readableStream
          .pipe(writeStream)
          .on('error', reject)
          .on('finish', resolve);
      },
    );
  }

  /**
   * @returns {string}
   */
  getInitialDirectory() {
    if (!this.adapter) {
      throw new Error(`Adapter not specified for "${this.type || 'unknown'}" interface`);
    }
    return this.adapter.getInitialDirectory();
  }

  // eslint-disable-next-line class-methods-use-this
  getPathSeparator() {
    if (!this.adapter) {
      throw new Error(`Adapter not specified for "${this.type || 'unknown'}" interface`);
    }
    return this.adapter.getPathSeparator();
  }

  getPathComponents(element = '') {
    if (!this.adapter) {
      throw new Error(`Adapter not specified for "${this.type || 'unknown'}" interface`);
    }
    return this.adapter.getPathComponents(element);
  }

  /**
   * @param {string} root
   * @param {string} pathToJoin
   * @param {boolean} [isDirectory=false]
   * @returns {string}
   */
  joinPath(root, pathToJoin, isDirectory = false) {
    if (!this.adapter) {
      throw new Error(`Adapter not specified for "${this.type || 'unknown'}" interface`);
    }
    return this.adapter.joinPath(root, pathToJoin, isDirectory);
  }

  /**
   * @param {string} element
   * @param {string} relativeTo
   * @returns {string}
   */
  getRelativePath(element, relativeTo) {
    if (!this.adapter) {
      throw new Error(`Adapter not specified for "${this.type || 'unknown'}" interface`);
    }
    return this.adapter.getRelativePath(element, relativeTo);
  }

  /**
   * @param {string} element
   * @param {string} relativeTo
   * @returns {string[]}
   */
  getRelativePathComponents(element, relativeTo) {
    if (!this.adapter) {
      throw new Error(`Adapter not specified for "${this.type || 'unknown'}" interface`);
    }
    return this.adapter.getRelativePathComponents(element, relativeTo);
  }
}

module.exports = FileSystemInterface;
