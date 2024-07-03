const { ipcMain, netLog } = require('electron');
const moment = require('moment-timezone');
const path = require('path');
const fs = require('fs');
const ipcMessage = require('../common/ipc-message');
const displaySize = require('../../shared/utilities/display-size');
const displayDate = require('../../shared/utilities/display-date');
const initializeAdapter = require('../../shared/file-system-adapters/initialize-adapter');
const CloudPipelineApi = require('../../shared/api/cloud-pipeline-api');
const logger = require('../../shared/shared-logger');
const types = require('../../shared/file-system-adapters/types');
const clientConfigDirectory = require('../../shared/utilities/client-config-directory');

/**
 * @typedef {Object} AdaptersBridgeOptions
 * @property {FileSystemAdapters} adapters
 * @property {Configuration} configuration
 */

/**
 * @param {AdaptersBridgeOptions} bridgeOptions
 */
module.exports = function build(bridgeOptions) {
  const {
    adapters,
    configuration,
  } = bridgeOptions || {};
  if (!configuration) {
    throw new Error('Configuration is required to build bridges for adapters');
  }
  if (!adapters) {
    throw new Error('File system adapters are required to build bridges for adapters');
  }
  async function getAvailableAdapters() {
    await adapters.defaultInitialize();
    return Promise.resolve(
      adapters.adapters.map((anAdapter) => ({
        identifier: anAdapter.identifier,
        name: anAdapter.name,
        restricted: !!anAdapter.restricted,
      })),
    );
  }

  /**
   * @param {{index: number?, identifier: string?}} options
   * @returns {Promise<{identifier: string, directory: string}>}
   */
  async function getAdapter(options = {}) {
    const {
      index = 0,
      identifier,
    } = options;
    await adapters.defaultInitialize();
    if (adapters.adapters.length === 0) {
      throw new Error('Error initializing file system adapters');
    }
    /**
     * @type {FileSystemAdapter}
     */
    let adapter;
    if (identifier) {
      adapter = adapters.getByIdentifier(identifier);
    }
    if (!adapter) {
      const adapterIdentifierFromSession = configuration.adapters[index];
      if (adapterIdentifierFromSession) {
        adapter = adapters.getByIdentifier(adapterIdentifierFromSession);
      }
    }
    if (!adapter) {
      adapter = adapters.getByIndex(index);
    }
    if (!adapter) {
      // returning first adapter
      adapter = adapters.getByIndex(0);
    }
    if (!adapter) {
      throw new Error('File system adapters not initialized');
    }
    const directory = adapter.getInitialDirectory();
    const delimiter = adapter.getPathSeparator();
    return Promise.resolve({
      identifier: adapter.identifier,
      directory,
      delimiter,
      restricted: adapter.restricted,
    });
  }

  async function setAdapter(index, identifier) {
    const session = configuration.adapters.slice();
    while (session.length < index + 1) {
      session.push(undefined);
    }
    session.splice(index, 1, identifier);
    await configuration.save({ adapters: session }, false);
    return getAdapter({ index, identifier });
  }

  async function getAdapterContentsOnPath(identifier, directory) {
    await adapters.defaultInitialize();
    if (adapters.adapters.length === 0) {
      throw new Error('Error initializing file system adapters');
    }
    const adapter = adapters.getByIdentifier(identifier);
    if (!adapter) {
      throw new Error(`"${identifier}" adapter not found`);
    }
    return adapter.useLastRequestOnlySession('listing', async (adapterInterface) => {
      const result = await adapterInterface.list(directory);
      return result.map((item) => ({
        ...item,
        displaySize: item.size ? displaySize(item.size) : undefined,
        displayChanged: displayDate(item.changed),
        changed: item.changed ? item.changed.unix() : undefined,
      }));
    });
  }

  async function getAdapterPathComponents(identifier, directory) {
    await adapters.defaultInitialize();
    if (adapters.adapters.length === 0) {
      throw new Error('Error initializing file system adapters');
    }
    const adapter = adapters.getByIdentifier(identifier);
    if (!adapter) {
      throw new Error(`"${identifier}" adapter not found`);
    }
    const components = adapter.getPathComponents(directory);
    return Promise.resolve({
      components,
      separator: adapter.getPathSeparator(),
    });
  }

  /**
   * @typedef {Object} DiagnoseOptions
   * @property {string} [url]
   * @property {string} [user]
   * @property {string} [password]
   * @property {string} [type]
   * @property {boolean} [ignoreCertificateErrors]
   * @property {string} type - one of "API", "WEBDAV", "FTP"
   */

  /**
   * @param {DiagnoseOptions} options
   * @returns {{diagnose: function:Promise<*>}}
   */
  function initializeDiagnosable(options) {
    const {
      type,
      url,
      password,
      ignoreCertificateErrors,
    } = options;
    if (/^api$/i.test(type)) {
      return new CloudPipelineApi({
        api: url,
        password,
        ignoreCertificateErrors,
      });
    }
    return initializeAdapter(options);
  }

  /**
   * @param {DiagnoseOptions} options
   * @returns {Promise<{error}|{result: *}>}
   */
  async function diagnose(options) {
    const {
      type = '',
    } = options;
    const fileName = moment().format('YYYY-MM-DD-HH-mm-ss').concat(`-${type.toLowerCase()}-network-log.json`);
    const logsFolder = clientConfigDirectory;
    const logsFile = path.join(logsFolder, fileName);
    if (!fs.existsSync(logsFolder)) {
      fs.mkdirSync(logsFolder, { recursive: true });
    }
    logger.log('[Diagnostics] Diagnosing', type, '; file:', logsFile);
    logger.log('[Diagnostics] Diagnose options:', JSON.stringify(options));
    const stopSafely = async () => {
      try {
        await netLog.stopLogging();
      } catch (_) { /* empty */ }
    };
    try {
      const adapter = initializeDiagnosable(options);
      await netLog.startLogging(logsFile);
      const diagnoseResult = await adapter.diagnose();
      logger.error('[Diagnostics] Diagnosing', type, ' result:', JSON.stringify(diagnoseResult));
      if (!diagnoseResult) {
        throw new Error('Diagnostics failed');
      }
      return Promise.resolve({
        result: diagnoseResult,
        logs: logsFile,
      });
    } catch (error) {
      logger.error('[Diagnostics] Diagnosing', type, ' error:', error.message);
      return Promise.resolve({
        error: error.message,
        logs: logsFile,
      });
    } finally {
      await stopSafely();
      logger.log('[Diagnostics] Diagnosing', type, ' done');
    }
  }

  async function diagnoseAPI(payload) {
    return diagnose({
      ...payload,
      identifier: 'API-diagnose',
      type: 'API',
    });
  }

  async function diagnoseWebdav(payload) {
    return diagnose({
      ...payload,
      identifier: 'WEBDAV-diagnose',
      type: types.webdav,
    });
  }

  async function diagnoseFTP(payload) {
    return diagnose({
      ...payload,
      identifier: 'FTP-diagnose',
      type: types.ftp,
    });
  }

  ipcMain.handle('getAvailableAdapters', ipcMessage(getAvailableAdapters));
  ipcMain.handle('getAdapter', ipcMessage(getAdapter));
  ipcMain.handle('setAdapter', ipcMessage(setAdapter));
  ipcMain.handle('getAdapterContentsOnPath', ipcMessage(getAdapterContentsOnPath));
  ipcMain.handle('getAdapterPathComponents', ipcMessage(getAdapterPathComponents));

  ipcMain.handle('diagnoseApi', ipcMessage(diagnoseAPI));
  ipcMain.handle('diagnoseFtp', ipcMessage(diagnoseFTP));
  ipcMain.handle('diagnoseWebdav', ipcMessage(diagnoseWebdav));
};
