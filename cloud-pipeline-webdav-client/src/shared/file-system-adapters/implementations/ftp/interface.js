const SFTPClient = require('./sftp-client');
const FTPClient = require('./ftp-client');
const WebBasedInterface = require('../web-based/interface');
const Types = require('../../types');
const Protocols = require('./protocols');
const { FileSystemAdapterInitializeError } = require('../../errors');

class FTPInterface extends WebBasedInterface {
  /**
   * @param {FileSystemAdapter} adapter
   */
  constructor(adapter) {
    super(Types.ftp, adapter);
  }

  /**
   * @param {FileSystemAdapterOptions & FTPAdapterOptions} options
   * @returns {Promise<void>}
   */
  async initialize(options) {
    await super.initialize(options);
    if (!options?.port) {
      throw new FileSystemAdapterInitializeError('ftp port not specified');
    }
    this.port = options?.port;
    if (options?.protocol === Protocols.sftp) {
      /**
       * @type {WebBasedClient}
       */
      this.client = new SFTPClient();
    } else {
      /**
       * @type {WebBasedClient}
       */
      this.client = new FTPClient();
    }
    this.client.once('close', () => this.emit('close'));
    await this.client.initialize({
      url: options?.url,
      port: options?.port,
      user: options?.user,
      password: options?.password,
      enableLogs: options?.enableLogs,
      protocol: options?.protocol,
      ignoreCertificateErrors: options?.ignoreCertificateErrors,
    });
  }

  async isDirectory(element = '/') {
    if (this.client && typeof this.client.isDirectory === 'function') {
      return this.client.isDirectory(element);
    }
    return super.isDirectory(element);
  }

  async writeFile(element, data, options = {}) {
    if (this.client && typeof this.client.writeFile === 'function') {
      return this.client.writeFile(element, data, options);
    }
    return super.writeFile(element, data, options);
  }
}

module.exports = FTPInterface;
