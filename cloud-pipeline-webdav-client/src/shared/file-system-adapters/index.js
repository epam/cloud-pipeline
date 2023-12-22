const EventEmitter = require('events');
const logger = require('../shared-logger');
const { FileSystemAdapterInitializeError } = require('./errors');
const initializeAdapter = require('./initialize-adapter');
const Types = require('./types');

class FileSystemAdapters extends EventEmitter {
  static LOCAL_IDENTIFIER = 'Local file system';

  static WEBDAV_IDENTIFIER = 'Webdav';

  /**
   * @param {Configuration} configuration
   */
  constructor(configuration) {
    if (!configuration) {
      throw new Error('File system adapters should be initialized with configuration');
    }
    super();
    /**
     * @type {Configuration}
     */
    this.configuration = configuration;
    configuration.on('reload', () => this.defaultInitialize(true));
    /**
     * @type {FileSystemAdapter[]}
     */
    this.adapters = [];
  }

  /**
   * @param identifier
   * @returns {FileSystemAdapter}
   */
  getByIdentifier(identifier) {
    return this.adapters
      .find((adapter) => adapter.identifier === identifier);
  }

  getLocalAdapter() {
    return this.getByIdentifier(FileSystemAdapters.LOCAL_IDENTIFIER);
  }

  getWebdavAdapter() {
    return this.getByIdentifier(FileSystemAdapters.WEBDAV_IDENTIFIER);
  }

  /**
   * @param {number} index
   * @returns {FileSystemAdapter}
   */
  getByIndex(index = 0) {
    if (this.adapters.length === 0) {
      throw new Error('File system adapters not initialized');
    }
    const correctedIndex = Math.max(0, Math.min(index, this.adapters.length - 1));
    return this.adapters[correctedIndex];
  }

  async defaultInitialize(force = false) {
    if (!this.defaultInitializePromise || force) {
      try {
        await Promise.all(this.adapters.map((adapter) => adapter.destroy()));
      } catch (_) { /* empty */ }
      this.defaultInitializePromise = new Promise((resolve) => {
        Promise
          .resolve()
          .then(() => this.configuration.initialize())
          .then(() => this.initialize(
            {
              type: Types.local,
              identifier: FileSystemAdapters.LOCAL_IDENTIFIER,
              name: 'Local',
            },
            {
              type: Types.webdav,
              identifier: FileSystemAdapters.WEBDAV_IDENTIFIER,
              url: this.configuration.server,
              user: this.configuration.username,
              password: this.configuration.password,
              apiURL: this.configuration.api,
              extraApiURL: this.configuration.extra,
              rootName: this.configuration.name,
              ignoreCertificateErrors: this.configuration.ignoreCertificateErrors,
              updatePermissions: this.configuration.updatePermissions,
              name: this.configuration.name || 'WebDAV',
              disclaimer: this.configuration.webDavErrorDisclaimer,
              restricted: this.configuration.webDavRestricted,
            },
            ...this.configuration.ftpServers.map((anFtpServer) => ({
              type: Types.ftp,
              identifier: anFtpServer.url,
              url: anFtpServer.url,
              protocol: anFtpServer.protocol,
              user: anFtpServer.useDefaultUser
                ? this.configuration.username
                : anFtpServer.user,
              password: anFtpServer.useDefaultUser
                ? this.configuration.password
                : anFtpServer.password,
              enableLogs: anFtpServer.enableLogs,
              ignoreCertificateErrors: this.configuration.ignoreCertificateErrors,
            })),
          ))
          .then(resolve);
      });
    }
    return this.defaultInitializePromise;
  }

  /**
     * @param {FileSystemAdapterOptions} adapter
     * @returns {Promise<void>}
     */
  async initialize(...adapter) {
    this.adapters = [];
    logger.log(`Initializing ${adapter.length} adapters`);
    adapter.forEach((a) => {
      try {
        this.registerAdapter(a);
      } catch (error) {
        logger.log(`Error initializing adapter: ${error.message}`);
      }
    });
    logger.log(`Initializing ${adapter.length} adapters done:`);
    this.adapters.forEach((anAdapter) => {
      logger.log();
      logger.log(anAdapter.toString());
    });
  }

  /**
   * @param {FileSystemAdapterOptions} options
   */
  registerAdapter(options) {
    if (this.adapters.find((existingAdapter) => existingAdapter.equals(options))) {
      throw new FileSystemAdapterInitializeError('current file system adapter already registered');
    }
    /**
     * @type {FileSystemAdapter}
     */
    const adapter = initializeAdapter(options);
    this.adapters.push(adapter);
    this.emit('reload-adapter', adapter.identifier);
    return adapter;
  }
}

module.exports = FileSystemAdapters;
