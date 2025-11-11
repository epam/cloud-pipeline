const { BrowserWindow, ipcMain, dialog } = require('electron');
const logger = require('../shared/shared-logger');

class Application {
  /**
   * @type {Electron.CrossProcessExports.BrowserWindow}
   */
  mainWindow;

  /**
   * @param {ClientConfiguration} configuration
   */
  constructor(configuration) {
    if (!configuration) {
      throw new Error('Application should be initialized with configuration');
    }
    this.configuration = configuration;
    this.dialogResolvers = {};
    this.dialogValidators = {};
    /**
     * @param {{id: number, resolver: function?, callback: function?, validator: function?}} options
     * @returns {Promise<unknown>}
     */
    this.registerDialogResolver = (options = {}) => {
      const {
        id,
        resolver,
        callback,
        validator = (() => false),
      } = options;
      return new Promise((resolve) => {
        this.dialogValidators[id] = validator;
        this.dialogResolvers[id] = (response) => {
          if (this.dialogResolvers[id]) {
            delete this.dialogResolvers[id];
          }
          if (this.dialogValidators[id]) {
            delete this.dialogValidators[id];
          }
          const result = resolver(response);
          resolve(result);
          if (typeof callback === 'function') {
            callback(result);
          }
        };
      });
    };
    async function dialogResponseHandler(event, response) {
      if (this.dialogValidators[event.sender.id]) {
        const error = await this.dialogValidators[event.sender.id](response);
        if (error) {
          event.sender.send('dialog-response-validation', { error });
          return;
        }
      }
      event.sender.send('dialog-response-validation', { });
      if (this.dialogResolvers[event.sender.id]) {
        const resolve = this.dialogResolvers[event.sender.id];
        if (typeof resolve === 'function') {
          resolve(response);
        }
      }
    }
    const handler = dialogResponseHandler.bind(this);
    ipcMain.on('dialog-response', handler);
    ipcMain.on('open-configuration-window', this.openConfigurationsWindow.bind(this));
    ipcMain.on('close-configuration-window', this.closeConfigurationWindow.bind(this));
    ipcMain.on('open-storage-access-window', this.openStorageAccessWindow.bind(this));
    ipcMain.on('close-storage-access-window', this.closeStorageAccessWindow.bind(this));
  }

  async openMainWindow() {
    if (this.mainWindow) {
      this.mainWindow.show();
      return this.mainWindow;
    }
    this.mainWindow = new BrowserWindow({
      title: this.configuration.name,
      width: 800,
      height: 600,
      roundedCorners: false,
      menuBarVisible: false,
      webPreferences: {
        // eslint-disable-next-line no-undef
        preload: MAIN_WINDOW_PRELOAD_WEBPACK_ENTRY,
      },
    });
    this.mainWindow.removeMenu();
    this.mainWindow.maximize();
    this.mainWindow.on('close', () => {
      this.mainWindow = undefined;
    });
    try {
      // eslint-disable-next-line no-undef
      await this.mainWindow.loadURL(MAIN_WINDOW_WEBPACK_ENTRY);
    } catch (error) {
      logger.error('Error loading main window:');
      logger.error(error);
    }
    return this.mainWindow;
  }

  async closeConfigurationWindow() {
    if (this.configurationWindow) {
      this.configurationWindow.close();
      this.configurationWindow = undefined;
    }
  }

  async openConfigurationsWindow() {
    if (this.configuration.displaySettings === false) {
      return;
    }
    const mainWindow = await this.openMainWindow();
    if (this.configurationWindow) {
      this.configurationWindow.show();
      return this.configurationWindow;
    }
    this.configurationWindow = new BrowserWindow({
      title: `${this.configuration.name} configuration`,
      parent: mainWindow,
      width: 600,
      height: 400,
      roundedCorners: false,
      maximizable: false,
      menuBarVisible: false,
      webPreferences: {
        // eslint-disable-next-line no-undef
        preload: CONFIGURATION_WINDOW_PRELOAD_WEBPACK_ENTRY,
      },
    });
    this.configurationWindow.removeMenu();
    this.configurationWindow.on('close', () => {
      this.configurationWindow = undefined;
    });
    try {
      // eslint-disable-next-line no-undef
      await this.configurationWindow.loadURL(CONFIGURATION_WINDOW_WEBPACK_ENTRY);
    } catch (error) {
      logger.error('Error loading configuration window:');
      logger.error(error);
    }
    return this.configurationWindow;
  }

  async closeStorageAccessWindow() {
    if (this.storageAccessWindow) {
      this.storageAccessWindow.close();
      this.storageAccessWindow = undefined;
    }
  }

  async openStorageAccessWindow() {
    if (this.configuration.displayBucketSelection === false) {
      return;
    }
    const mainWindow = await this.openMainWindow();
    if (this.storageAccessWindow) {
      this.storageAccessWindow.show();
      return this.storageAccessWindow;
    }
    this.storageAccessWindow = new BrowserWindow({
      title: 'Request storage access',
      parent: mainWindow,
      width: 600,
      height: 400,
      roundedCorners: false,
      maximizable: false,
      menuBarVisible: false,
      webPreferences: {
        // eslint-disable-next-line no-undef
        preload: STORAGE_ACCESS_WINDOW_PRELOAD_WEBPACK_ENTRY,
      },
    });
    this.storageAccessWindow.removeMenu();
    this.storageAccessWindow.on('close', () => {
      this.storageAccessWindow = undefined;
    });
    try {
      // eslint-disable-next-line no-undef
      await this.storageAccessWindow.loadURL(STORAGE_ACCESS_WINDOW_WEBPACK_ENTRY);
    } catch (error) {
      logger.error('Error loading storage window:');
      logger.error(error);
    }
    return this.storageAccessWindow;
  }

  /**
   * @param {DialogOptions & {resolver: function?, validator: function?}} options
   * @returns {Promise<unknown>}
   */
  async openDialog(options = {}) {
    const mainWindow = await this.openMainWindow();
    const {
      title,
      resolver = ((o) => o),
      validator = (() => false),
      ...dialogConfig
    } = options;
    const win = new BrowserWindow({
      title,
      modal: true,
      parent: mainWindow,
      width: 400,
      height: 200,
      useContentSize: true,
      roundedCorners: false,
      maximizable: false,
      minimizable: false,
      menuBarVisible: false,
      webPreferences: {
        // eslint-disable-next-line no-undef
        preload: INPUT_WINDOW_PRELOAD_WEBPACK_ENTRY,
      },
    });
    win.removeMenu();
    win.webContents.on('did-finish-load', () => {
      win.webContents.send('dialog-properties', { title, ...dialogConfig });
    });
    try {
      // eslint-disable-next-line no-undef
      await win.loadURL(INPUT_WINDOW_WEBPACK_ENTRY);
    } catch (error) {
      logger.error('Error loading modal window:');
      logger.error(error);
    }
    return this.registerDialogResolver({
      id: win.webContents.id,
      resolver,
      validator,
      callback: () => win.close(),
    });
  }

  /**
   * @typedef {Object} ConfirmOptions
   * @property {string} [title]
   * @property {string} [message]
   * @property {string} [ok=Yes]
   * @property {string} [cancel=No]
   * @property {boolean} [danger=false]
   * @property {string} [details]
   * @property {string} [checkboxText]
   */

  /**
   * @param {ConfirmOptions} options
   * @returns {Promise<{result: boolean, checked: boolean}>}
   */
  async confirm(options) {
    const {
      title = this.configuration.name,
      message,
      ok = 'Yes',
      cancel = 'No',
      details,
      checkboxText,
    } = options;
    const mainWindow = await this.openMainWindow();
    const {
      response,
      checkboxChecked,
    } = await dialog.showMessageBox(
      mainWindow,
      {
        title,
        type: 'question',
        message,
        detail: details,
        buttons: [cancel, ok],
        defaultId: 0,
        cancelId: 0,
        checkboxLabel: checkboxText,
      },
    );
    return {
      result: response === 1,
      checked: checkboxChecked,
    };
  }

  /**
   * @param {ConfirmOptions} options
   */
  async confirm2(options) {
    const {
      title = this.configuration.name,
      message,
      ok = 'Yes',
      cancel = 'No',
      details,
    } = options;
    const mainWindow = await this.openMainWindow();
    const { response } = await dialog.showMessageBox(
      mainWindow,
      {
        title,
        type: 'question',
        message,
        detail: details,
        buttons: [cancel, ok],
        defaultId: 0,
        cancelId: 0,
      },
    );
    return response === 1;
  }

  /**
   * @typedef {Object} InputOptions
   * @property {string} [title]
   * @property {string} [message]
   * @property {string} [ok=Yes]
   * @property {string} [cancel=No]
   * @property {string} [details]
   * @property {string} [placeholder]
   * @property {boolean} [required=true]
   * @property {string} [requiredMessage=Value is required]
   * @property {function(string):Promise<string>} [validate] -
   * should return error message if validation failed
   */

  /**
   * @param {InputOptions} options
   * @returns {Promise<*>}
   */
  async inputDialog(options = {}) {
    const {
      title,
      message,
      details,
      ok,
      cancel,
      required = true,
      requiredMessage = 'Value is required',
      placeholder,
      // eslint-disable-next-line
      validate = ((o) => false),
    } = options;
    return this.openDialog({
      title,
      message,
      details,
      buttons: [cancel, { name: ok, type: 'primary' }],
      validator: (result) => {
        const {
          id,
          input,
        } = result || {};
        if (id === 0) {
          return false;
        }
        if ((!input || !input.length) && required) {
          return requiredMessage;
        }
        return validate(input);
      },
      resolver: (result) => {
        const {
          id,
          input,
        } = result || {};
        if (id === 0) {
          return undefined;
        }
        return input;
      },
      input: true,
      inputPlaceholder: placeholder,
    });
  }

  // eslint-disable-next-line class-methods-use-this
  tokenAlert(info) {
    const {
      subject,
      expired,
      issuedAt,
      expiresAt,
      expireInDays = 0,
    } = info;
    const detail = [
      `Issued to ${subject}`,
      issuedAt ? `Issued at ${issuedAt}` : false,
      expiresAt ? `Expires at ${expiresAt}` : false,
    ].filter(Boolean).join('\n');
    if (expired) {
      return dialog.showMessageBox({
        type: 'warning',
        message: 'Access token is expired',
        detail,
      });
    }
    if (expireInDays < 1) {
      return dialog.showMessageBox({
        type: 'warning',
        message: 'Access token will expire soon',
        detail,
      });
    }
    return dialog.showMessageBox({
      type: 'info',
      message: `Access token will expire in ${expireInDays} day${expireInDays === 1 ? '' : 's'}`,
      detail,
    });
  }

  /**
   * @param {(string|{identifier: string, path: string?})[]} fileSystemPath
   */
  reloadFileSystems(fileSystemPath = []) {
    if (this.mainWindow) {
      const mapFileSystemPath = (o) => {
        if (typeof o === 'string') {
          return {
            identifier: o,
          };
        }
        return o;
      };
      this.mainWindow.webContents.send('reload-file-systems', (fileSystemPath || []).map(mapFileSystemPath));
    }
  }

  /**
   * @param {Operation} operation
   */
  reportOperation(operation) {
    if (this.mainWindow) {
      this.mainWindow.webContents.send('report-operation', {
        id: operation.id,
        status: operation.status,
        progress: operation.progress,
        description: operation.description,
      });
    }
  }

  autoUpdateAvailable(info) {
    if (this.mainWindow) {
      this.mainWindow.webContents.send('auto-update-info', info);
    }
  }
}

module.exports = Application;
