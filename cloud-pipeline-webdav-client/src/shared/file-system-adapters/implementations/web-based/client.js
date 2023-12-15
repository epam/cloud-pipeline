/* eslint-disable  class-methods-use-this,no-empty-function,no-unused-vars */
const EventEmitter = require('events');
const defaultLogger = require('../../../shared-logger');

class WebBasedClient extends EventEmitter {
  async initialize(url) {
    this.url = url;
    this.logTitle = url;
  }

  async cancelCurrentTask() {
  }

  /**
   * @param {string} directory
   * @returns {Promise<FSItem[]>}
   */
  async getDirectoryContents(directory) {
    return [];
  }

  async createDirectory(directory) {
    return Promise.resolve(undefined);
  }

  async removeDirectory(directory) {
    return Promise.resolve(undefined);
  }

  async removeFile(file) {
    return Promise.resolve(undefined);
  }

  /**
   * @param {string} file
   * @param {{}} [options]
   * @returns {Promise<unknown>}
   */
  async createReadStream(file, options = {}) {
    return Promise.resolve(undefined);
  }

  /**
   * @param {string} file
   * @param {{size: number?, overwrite: boolean?}} [options]
   * @returns {Promise<unknown>}
   */
  async createWriteStream(file, options = {}) {
    return Promise.resolve(undefined);
  }

  /**
   * @param {string} element
   * @returns {Promise<{size: number, changed: moment.Moment, type: string}>}
   */
  async statistics(element) {
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
   * @param {string} directory
   * @returns {Promise<boolean>}
   */
  async isDirectory(directory = '') {
    return false;
  }

  async destroy() {
  }

  log(...message) {
    defaultLogger.log(`[${this.logTitle}]`, ...message);
  }

  info(...message) {
    defaultLogger.info(`[${this.logTitle}]`, ...message);
  }

  warn(...message) {
    defaultLogger.warn(`[${this.logTitle}]`, ...message);
  }

  error(...message) {
    defaultLogger.error(`[${this.logTitle}]`, ...message);
  }

  /**
   * @param {FSItem[]} contents
   */
  logDirectoryContents(contents = []) {
    /**
     * @param {FSItem} anItem
     * @returns {string}
     */
    const mapType = (anItem) => {
      if (anItem.isDirectory) {
        return 'DIR ';
      }
      if (anItem.isFile) {
        return 'FILE';
      }
      if (anItem.isSymbolicLink) {
        return 'SYMB';
      }
      return '----';
    };
    contents.forEach((anItem) => this.log(`  ${mapType(anItem)} ${anItem.name}`));
  }
}

module.exports = WebBasedClient;
