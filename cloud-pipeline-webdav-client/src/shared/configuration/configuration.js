const path = require('path');
const fs = require('fs');
const https = require('https');
const readConfiguration = require('./read-configuration');
const readGlobalConfiguration = require('./export-from-pipe-cli');
const readOSUserName = require('../utilities/read-os-user-name');
const logger = require('../shared-logger');
const TokenExpirationChecker = require('../utilities/token-expiration-checker');
const printConfiguration = require('./print-configuration');

const DEFAULT_APP_NAME = 'Cloud Data';

/**
 * @typedef {Object} ClientConfiguration
 * @property {string} [api]
 * @property {string} [server]
 * @property {string} [extra]
 * @property {string} [username]
 * @property {string} [password]
 * @property {string} [name]
 * @property {string[]} [adapters]
 * @property {{url: string, user: string?, password: string?}[]} [ftp]
 * @property {boolean} [ignoreCertificateErrors]
 * @property {boolean} [updatePermissions]
 * @property {string} [componentVersion]
 * @property {string} [version]
 * @property {boolean} [logsEnabled]
 * @property {boolean} [displaySettings]
 * @property {boolean} [displayBucketSelection]
 * @property {boolean} [webDavRestricted]
 * @property {string} [webDavErrorDisclaimer]
 */

class Configuration extends TokenExpirationChecker {
  constructor() {
    super();
    this.configurationFiles = [];
    this.predefinedConfig = {};
    this.config = undefined;
    this.userConfigPath = undefined;
  }

  /**
   * Set configuration files paths respecting priority (first file will have highest priority)
   * @param {string} configurationFile
   */
  setConfigurationSources(...configurationFile) {
    this.configurationFiles = configurationFile;
    this.initializePromise = undefined;
  }

  /**
   * @param {ClientConfiguration} predefinedConfig
   */
  setPredefinedConfiguration(predefinedConfig) {
    this.predefinedConfig = predefinedConfig;
    this.initializePromise = undefined;
  }

  /**
   * Sets user configuration path (configuration modified by user will be saved here)
   * @param {string} userConfiguration
   */
  setUserConfigurationPath(userConfiguration) {
    this.userConfigPath = userConfiguration;
  }

  /**
   * API endpoint
   * @returns {string}
   */
  get api() {
    return this.config?.api;
  }

  get extra() {
    return this.config?.extra;
  }

  get server() {
    return this.config?.server;
  }

  get username() {
    return this.config?.username;
  }

  get password() {
    return this.config?.password;
  }

  get name() {
    return this.config?.name || DEFAULT_APP_NAME;
  }

  get version() {
    return this.config?.version;
  }

  get componentVersion() {
    return this.config?.componentVersion;
  }

  get ignoreCertificateErrors() {
    return this.config?.ignoreCertificateErrors;
  }

  get updatePermissions() {
    return this.config?.updatePermissions;
  }

  get ftpServers() {
    return (this.config?.ftp || []);
  }

  get logsEnabled() {
    return this.config?.logsEnabled;
  }

  get webDavRestricted() {
    return this.config?.webDavRestricted;
  }

  get webDavErrorDisclaimer() {
    return this.config?.webDavErrorDisclaimer;
  }

  get displayBucketSelection() {
    return this.config?.displayBucketSelection;
  }

  get displaySettings() {
    return this.config?.displaySettings;
  }

  /**
   * @returns {string[]}
   */
  get adapters() {
    return this.config?.adapters || [];
  }

  get initialized() {
    return !!this.config;
  }

  get empty() {
    return !this.server
      || !this.api
      || !this.username
      || !this.password
      || this.expired;
  }

  // eslint-disable-next-line class-methods-use-this
  readCompilationAsset = (assetName) => {
    try {
      const assetFile = path.join(__dirname, assetName);
      if (fs.existsSync(assetFile)) {
        return fs.readFileSync(assetFile).toString();
      }
      // eslint-disable-next-line no-empty
    } catch (_) {}
    return undefined;
  };

  getAppVersion = () => this.readCompilationAsset('VERSION');

  getComponentVersion = () => this.readCompilationAsset('COMPONENT_VERSION');

  checkToken() {
    this.token = this.password;
    this.checkTokenExpiration();
  }

  enableOrDisableLogs() {
    logger.enabled = this.logsEnabled;
  }

  initialize() {
    if (!this.initializePromise) {
      const initializationLogic = async () => {
        try {
          logger.log('Initializing...');
          logger.log('Configuration priority (from lowest to highest):');
          logger.log('\t↓', 'pipe-cli config');
          const paths = this.configurationFiles.slice().reverse();
          paths.forEach((configFile) => logger.log('\t↓', configFile));
          if (this.predefinedConfig) {
            logger.log('\t↓ predefined config');
          }
          let config = paths
            .map((configFile) => readConfiguration(configFile))
            .concat(this.predefinedConfig || {})
            .reduce((result, c) => ({ ...result, ...(c || {}) }), {});
          if (
            !config.api
            || !config.server
            || !config.username
            || !config.password
          ) {
            // read pipe cli
            const pipeCLI = await readGlobalConfiguration();
            config = {
              ...pipeCLI,
              ...config,
            };
          } else {
            logger.log('Skipping reading pipe-cli config (`api`, `server`, `username` and `password` already defined)');
          }
          const osUserName = await readOSUserName();
          const serverCorrected = osUserName && config.server
            ? (config.server || '').replace(/<USER_ID>/ig, osUserName)
            : config.server;
          const userNameCorrected = osUserName && (!config.username || /^<USER_ID>$/i.test(config.username))
            ? osUserName
            : config.username;
          if (serverCorrected !== config.server) {
            logger.log(`WEBDAV Server corrected: ${serverCorrected}`);
          }
          if (userNameCorrected !== config.username) {
            logger.log(`WEBDAV username corrected: ${userNameCorrected}`);
          }
          config.server = serverCorrected;
          config.username = userNameCorrected;
          config.name = config.name || DEFAULT_APP_NAME;
          config.version = this.getAppVersion();
          config.componentVersion = this.getComponentVersion();
          printConfiguration(config, 'CLOUD-DATA CONFIGURATION');
          this.config = config;
          if (this.ignoreCertificateErrors) {
            logger.log('"ignore-certificate-errors": true');
            https.globalAgent.options.rejectUnauthorized = false;
          } else {
            logger.log('"ignore-certificate-errors": false');
            https.globalAgent.options.rejectUnauthorized = true;
          }
          this.emit('initialized');
        } catch (error) {
          logger.error(`Error initializing configuration: ${error.message}`);
        } finally {
          this.checkToken();
          this.enableOrDisableLogs();
        }
      };
      this.initializePromise = initializationLogic();
    }
    return this.initializePromise;
  }

  /**
   * @param {ClientConfiguration} options
   * @param {boolean} [emitReload=true]
   */
  save(options = {}, emitReload = true) {
    let {
      server = this.server,
      username = this.username,
    } = options;
    const {
      api = this.api,
      extra = this.extra,
      password = this.password,
      name = this.name,
      ignoreCertificateErrors = this.ignoreCertificateErrors,
      updatePermissions = this.updatePermissions,
      ftp = this.ftpServers,
      adapters = this.adapters,
      logsEnabled = this.logsEnabled,
      displayBucketSelection = this.displayBucketSelection,
      webDavRestricted = this.webDavRestricted,
      webDavErrorDisclaimer = this.webDavErrorDisclaimer,
      displaySettings = this.displaySettings,
    } = options;
    if (ftp.some((o) => !o.url)) {
      throw new Error('FTP/SFTP server url is required');
    }
    const uniqueFtpAdapters = [
      ...new Set(ftp.map((anFtpServer) => (anFtpServer.url || '').toLowerCase())),
    ];
    if (uniqueFtpAdapters.length > 0 && uniqueFtpAdapters.length < ftp.length) {
      // there are two or more adapters with the same url
      const nonUnique = uniqueFtpAdapters.filter((adapter) => ftp
        .filter((anFtpServer) => (anFtpServer.url || '').toLowerCase() === adapter).length > 1);
      throw new Error(`There are several FTP servers with the same URL (${nonUnique.join(', ')})`);
    }
    if (server) {
      const parts = server.split('/');
      /*
      scheme:
      empty
      server
      webdav
      username
      ...
       */
      if (parts.length > 4) {
        parts[4] = parts[4].toUpperCase();
      }
      server = parts.join('/');
    }
    if (username) {
      username = username.toUpperCase();
    }
    this.config = {
      ...(this.config || {}),
      api,
      extra,
      server,
      username,
      password,
      ignoreCertificateErrors,
      updatePermissions,
      name,
      ftp: (ftp || []).map((anFtpServer) => {
        const {
          url,
          protocol,
          user,
          password: ftpPassword,
          useDefaultUser,
          enableLogs,
        } = anFtpServer;
        if (useDefaultUser) {
          return {
            url,
            protocol,
            useDefaultUser,
            enableLogs,
          };
        }
        return {
          url,
          protocol,
          user,
          password: ftpPassword,
          enableLogs,
          useDefaultUser: false,
        };
      }),
      adapters,
      logsEnabled,
      webDavRestricted,
      webDavErrorDisclaimer,
      displayBucketSelection,
      displaySettings,
    };
    printConfiguration(this.config, 'Configuration saved:');
    if (this.userConfigPath) {
      fs.writeFileSync(this.userConfigPath, Buffer.from(JSON.stringify(this.config, undefined, ' ')));
    }
    if (emitReload) {
      this.emit('reload');
    }
    this.checkToken();
    this.enableOrDisableLogs();
  }
}

module.exports = Configuration;
