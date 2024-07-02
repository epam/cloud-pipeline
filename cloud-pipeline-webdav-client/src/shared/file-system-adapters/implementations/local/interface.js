const path = require('path');
const fs = require('fs');
const moment = require('moment-timezone');
const xxh = require('xxhashjs');
const FileSystemInterface = require('../../interface');
const logger = require('../../../shared-logger');
const Types = require('../../types');
const correctDirectoryPath = require('../../utils/correct-path');

async function getFileXXHash(filePath) {
  const CHUNK_SIZE = 10 * 1024 * 1024; // 10MB
  const xxhash = xxh.h64(0);
  const readStream = fs.createReadStream(filePath, { highWaterMark: CHUNK_SIZE });
  return new Promise((resolve, reject) => {
    readStream
      .on('data', (chunk) => {
        xxhash.update(chunk);
      })
      .on('end', () => {
        resolve(xxhash.digest().toString(16).padStart(16, '0'));
      })
      .on('error', reject);
  });
}

class LocalFileSystemInterface extends FileSystemInterface {
  /**
   * @param {LocalFileSystemAdapter} adapter
   */
  constructor(adapter) {
    super(Types.local, adapter);
    this.isWindows = adapter.isWindows;
    this.streams = [];
    this.root = adapter.root;
  }

  get parsingOptions() {
    return {
      leadingSlash: !this.isWindows,
      trailingSlash: true,
      separator: this.separator,
    };
  }

  async initialize() {
    await super.initialize();
  }

  // eslint-disable-next-line no-unused-vars
  async list(directory = '') {
    const directoryCorrected = directory || '';
    const absolutePath = path.isAbsolute(directoryCorrected)
      ? directoryCorrected
      : path.resolve(this.root, directoryCorrected || '');
    const parentAbsolutePath = path.dirname(absolutePath);
    const results = await fs.promises.readdir(absolutePath, { withFileTypes: true });
    const parentItem = {
      name: '..',
      path: parentAbsolutePath,
      isDirectory: true,
      isFile: false,
      isSymbolicLink: false,
      isBackLink: true,
    };
    /**
     * @param item
     * @returns {FSItem}
     */
    const processItem = (item) => {
      let size;
      let changed;
      const isDirectory = item.isDirectory();
      const fullPath = path.resolve(absolutePath, item.name);
      if (!isDirectory && fs.existsSync(fullPath)) {
        const stat = fs.statSync(fullPath);
        size = +(stat.size);
        changed = moment(stat.ctime);
      }
      return {
        name: item.name,
        path: fullPath,
        isDirectory,
        isFile: item.isFile(),
        isSymbolicLink: item.isSymbolicLink(),
        size,
        changed,
      };
    };
    return [
      parentAbsolutePath !== absolutePath ? parentItem : false,
      ...results.map(processItem),
    ].filter(Boolean);
  }

  // eslint-disable-next-line no-unused-vars,class-methods-use-this
  async isDirectory(element = '') {
    try {
      return fs.lstatSync(element).isDirectory();
    } catch (error) {
      logger.warn(`Error checking if "${element}" is directory: ${error.message}`);
    }
    return false;
  }

  // eslint-disable-next-line class-methods-use-this, no-unused-vars
  async getFileInfo(element = '') {
    try {
      const {
        size,
        ctime,
      } = fs.lstatSync(element);
      return {
        size,
        changed: moment(ctime),
      };
    } catch (error) {
      return undefined;
    }
  }

  // eslint-disable-next-line class-methods-use-this,no-unused-vars
  async getFilesChecksums(files) {
    return Promise.all(files.map(getFileXXHash));
  }

  // eslint-disable-next-line class-methods-use-this,no-unused-vars
  async exists(element = '') {
    try {
      let corrected = correctDirectoryPath(
        element,
        { ...this.parsingOptions, trailingSlash: false },
      );
      if (corrected.endsWith(this.separator)) {
        corrected = corrected.slice(0, -1);
      }
      return Promise.resolve(fs.existsSync(corrected));
    } catch (error) {
      return false;
    }
  }

  // eslint-disable-next-line no-unused-vars
  async getParentDirectory(element = '') {
    try {
      return path.dirname(element);
    } catch (error) {
      logger.warn(`Error retrieving parent directory for "${element}": ${error.message}`);
    }
    return super.getParentDirectory(element);
  }

  // eslint-disable-next-line no-unused-vars
  async createDirectory(directory = '') {
    let corrected = correctDirectoryPath(
      directory,
      { ...this.parsingOptions, trailingSlash: false },
    );
    if (corrected.endsWith(this.separator)) {
      corrected = corrected.slice(0, -1);
    }
    logger.log(`local - creating directory "${corrected}"`);
    fs.mkdirSync(corrected, { recursive: true });
    this.onDirectoryCreated(corrected);
  }

  // eslint-disable-next-line class-methods-use-this,no-unused-vars
  async remove(element = '') {
    if (/^(\\|\/|.:\\|)$/.test(element)) {
      // prevent removing roots
      throw new Error(`Cannot remove items on path "${element}"`);
    }
    fs.rmSync(element, { recursive: true });
  }

  // eslint-disable-next-line class-methods-use-this, no-unused-vars
  async createReadStream(element, options = {}) {
    return fs.createReadStream(element);
  }

  // eslint-disable-next-line class-methods-use-this, no-unused-vars
  async createWriteStream(element, options = {}) {
    return fs.createWriteStream(element);
  }
}

module.exports = LocalFileSystemInterface;
