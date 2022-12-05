// eslint-disable-next-line no-unused-vars
const FileSystemAdapter = require('../../base');
const { FileSystemAdapterInitializeError } = require('../../errors');
const correctDirectoryPath = require('../../utils/correct-path');

/**
 * @typedef {Object} AdditionalOptions
 * @property {string} [rootName]
 */

/**
 * @typedef {AdditionalOptions & FileSystemAdapterOptions} WebBasedFileSystemAdapterOptions
 */

class WebBasedAdapter extends FileSystemAdapter {
  /**
   * @param {WebBasedFileSystemAdapterOptions} options
   */
  constructor(options) {
    super({
      identifier: options?.url,
      ...(options || {}),
    });
    if (!options?.url) {
      throw new FileSystemAdapterInitializeError(`${this.type || ''} url not specified`);
    }
    this.url = options?.url;
    this.user = options?.user;
    this.password = options.password;
    this.ignoreCertificateErrors = options?.ignoreCertificateErrors;
    this.root = '';
    this.rootName = 'Root';
  }

  toString() {
    return [
      `Adapter ${this.type}:`,
      `  url:                ${this.url}`,
      `  user:               ${this.user}`,
      `  password:           ${this.password ? '***' : '<empty>'}`,
      `  ignore cert errors: ${!!this.ignoreCertificateErrors}`,
    ].join('\n');
  }

  // eslint-disable-next-line class-methods-use-this
  getPathComponents(element = '') {
    const components = correctDirectoryPath(element, { leadingSlash: true, trailingSlash: true })
      .split('/')
      .slice(0, -1);
    return components.map((aComponent, idx, array) => ({
      name: idx === 0 ? this.rootName : aComponent,
      path: correctDirectoryPath(array.slice(0, idx + 1).join('/')),
      isCurrent: idx === array.length,
    }));
  }

  /**
   * @param {FileSystemAdapterOptions} options
   * @returns {boolean}
   */
  equals(options) {
    return this.url === options?.url && super.equals(options);
  }
}

module.exports = WebBasedAdapter;
