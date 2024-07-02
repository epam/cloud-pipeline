const CopyOperation = require('./copy-operation');
const CreateDirectoryOperation = require('./create-directory-operation');
const MoveOperation = require('./move-operation');
const RemoveOperation = require('./remove-operation');
const Operation = require("./base");

/**
 * @typedef {Object} Dialog
 * @property {function(DialogOptions): Promise<boolean>} openDialog
 * @property {function(Operation)} reportOperation
 * @property {function([])} reloadFileSystems
 * @property {function(InputOptions)} inputDialog
 * @property {function(ConfirmOptions)} confirm
 */

/**
 *
 */
const OperationTypes = {
  createDirectory: 'create directory',
  copy: 'copy',
  move: 'move',
  remove: 'remove',
};

/**
 * @param {Dialog} dialog
 * @returns {(function(string): Promise<boolean>)}
 */
function createOverrideConfirmation(dialog) {
  let rememberChoice;
  return async (fileName) => {
    if (rememberChoice !== undefined) {
      return Promise.resolve(rememberChoice);
    }
    if (!dialog || typeof dialog.confirm !== 'function') {
      return Promise.resolve(true);
    }
    const {
      result,
      checked,
    } = await dialog.confirm({
      message: `"${fileName}" already exists. Overwrite?`,
      ok: 'Yes',
      cancel: 'No',
      checkboxText: 'Remember choice for all files',
    });
    if (checked) {
      rememberChoice = result;
    }
    return result;
  };
}

/**
 * @param {Dialog} dialog
 * @returns {(function(string): Promise<boolean>)}
 */
function createGeneralRetryConfirmation(dialog) {
  return async (message) => {
    if (!dialog || typeof dialog.confirm !== 'function') {
      return Promise.resolve(true);
    }
    const {
      result,
    } = await dialog.confirm({
      message,
      ok: 'Yes',
      cancel: 'No',
    });
    return result;
  };
}

/**
 * @param {Dialog} dialog
 * @returns {function(): Promise<boolean>}
 */
function createAbortConfirmation(dialog) {
  if (!dialog || typeof dialog.confirm !== 'function') {
    return () => Promise.resolve(true);
  }
  return async (operationName) => {
    const { result } = await dialog.confirm({
      message: `Abort ${(operationName || 'operation').toLowerCase()}?`,
      ok: 'Abort',
      cancel: 'Continue',
    });
    return result;
  };
}

/**
 * @param {Dialog} dialog
 * @returns {function(): Promise<boolean>}
 */
function createRetryConfirmation(dialog) {
  if (!dialog || typeof dialog.confirm !== 'function') {
    return () => Promise.resolve(false);
  }
  return async (error) => {
    const { result } = await dialog.confirm({
      message: `Error occurred: ${error}. Retry?`,
      ok: 'Retry',
      cancel: 'Abort',
    });
    return result;
  };
}

class Operations {
  /**
   * @param {FileSystemAdapters} adapters
   * @param {Dialog} dialog
   */
  constructor(adapters, dialog) {
    if (!adapters) {
      throw new Error('Operations should be initialized with file system adapters');
    }
    /**
     * @type {FileSystemAdapters}
     */
    this.adapters = adapters;
    this.dialog = dialog;
    /**
     * @type {Operation[]}
     */
    this.operations = [];
  }

  // eslint-disable-next-line class-methods-use-this
  report = (operation) => {
    if (this.dialog && typeof this.dialog.reportOperation === 'function') {
      this.dialog.reportOperation(operation);
    }
  };

  // eslint-disable-next-line class-methods-use-this
  async submit(type, options, resolve) {
    if (!type) {
      throw new Error('Operation not supported (unknown type)');
    }
    switch (type) {
      case OperationTypes.createDirectory:
        return this.createDirectory(options, resolve);
      case OperationTypes.remove:
        return this.remove(options, resolve);
      case OperationTypes.copy:
        return this.copy(options, resolve);
      case OperationTypes.move:
        return this.move(options, resolve);
      default:
        throw new Error(`Unknown operation "${type}"`);
    }
  }

  async recover(uuid, options = undefined) {
    const { type, ...rest } = await Operation.getOperationInfoByIdentifier(uuid);
    switch (type) {
      case 'copy': {
        const copyOperation = new CopyOperation({
          adapters: this.adapters,
          source: rest.source,
          elements: [],
          destination: rest.destination,
          destinationPath: '',
          abortConfirmation: createAbortConfirmation(this.dialog),
          retryConfirmation: createRetryConfirmation(this.dialog),
          overwriteExistingCallback: createOverrideConfirmation(this.dialog),
          operationRetryConfirmation: createGeneralRetryConfirmation(this.dialog),
          saveOperationInfo: true,
          ...(options || {}),
        });
        copyOperation.recover(uuid);
        return copyOperation;
      }
      case 'move': {
        const moveOperation = new MoveOperation({
          adapters: this.adapters,
          source: rest.source,
          elements: [],
          destination: rest.destination,
          destinationPath: '',
          abortConfirmation: createAbortConfirmation(this.dialog),
          retryConfirmation: createRetryConfirmation(this.dialog),
          overwriteExistingCallback: createOverrideConfirmation(this.dialog),
          operationRetryConfirmation: createGeneralRetryConfirmation(this.dialog),
          saveOperationInfo: true,
          ...(options || {}),
        });
        moveOperation.recover(uuid);
        return moveOperation;
      }
      default:
        throw new Error('Unsupported operation type');
    }
  }

  async recoverAndSubmit(uuid, options = undefined) {
    const operation = await this.recover(uuid, options);
    return new Promise((resolve) => {
      this.submitOperation(operation, resolve);
    });
  }

  /**
   * @param {Operation} operation
   * @param {function} [resolve]
   * @returns {number}
   */
  submitOperation(operation, resolve) {
    if (operation) {
      this.operations.push(operation);
      operation
        .on('stateChanged', this.report.bind(this, operation));
      operation
        .submit()
        .then(() => {
          if (this.dialog && typeof this.dialog.reloadFileSystems === 'function') {
            this.dialog.reloadFileSystems(
              operation.affectedFileSystems
                .filter(Boolean)
                .map((fsIdentifier) => ({ identifier: fsIdentifier })),
            );
          }
          if (typeof resolve === 'function') {
            resolve(operation);
          }
        });
      return operation.id;
    }
    return undefined;
  }

  // eslint-disable-next-line class-methods-use-this
  async createDirectory(options = {}, resolve = undefined) {
    const {
      source,
      sourcePath,
      isNewDirectoryPath = false,
      ...rest
    } = options;
    if (!source) {
      throw new Error('File system not specified');
    }
    if (!sourcePath) {
      throw new Error('Parent directory not specified');
    }
    const sourceAdapter = typeof source === 'string' ? this.adapters.getByIdentifier(source) : source;
    if (!sourceAdapter) {
      throw new Error(`Unknown source file system: "${source}"`);
    }
    const sourceAdapterInterfacePromise = sourceAdapter.createInterface();
    const validator = (folderName) => new Promise((resolve) => {
      if (/[#@!$%^&~\\/{}[\]]/.test(folderName)) {
        resolve('Invalid folder name');
        return;
      }
      const fullPath = sourceAdapter.joinPath(sourcePath, folderName, true);
      sourceAdapterInterfacePromise
        .then((adapterInterface) => adapterInterface.exists(fullPath))
        .then((exists) => resolve((exists ? 'Folder already exists' : false)))
        .catch((error) => resolve(error.message));
    });
    let folder = 'New folder';
    if (!isNewDirectoryPath && this.dialog && typeof this.dialog.inputDialog === 'function') {
      folder = await this.dialog.inputDialog({
        ok: 'CREATE',
        cancel: 'CANCEL',
        required: true,
        requiredMessage: 'Folder name is required',
        placeholder: 'Folder name',
        validate: validator,
      });
    }
    if (!folder) {
      return Promise.resolve();
    }
    const sourceInterface = await sourceAdapterInterfacePromise;
    return this.submitOperation(new CreateDirectoryOperation({
      ...rest,
      adapters: this.adapters,
      source: sourceInterface,
      sourcePath,
      directory: folder,
      isFullPath: isNewDirectoryPath,
      abortConfirmation: createAbortConfirmation(this.dialog),
      retryConfirmation: createRetryConfirmation(this.dialog),
    }), resolve);
  }

  async remove(options = {}, resolve = undefined) {
    const {
      source,
      sourceElements = [],
      ...rest
    } = options;
    if (!source) {
      throw new Error('File system not specified');
    }
    if (sourceElements.length === 0) {
      throw new Error('Nothing to delete');
    }
    const message = sourceElements.length > 1
      ? `${sourceElements.length} items`
      : sourceElements[0];
    let confirmed = true;
    if (this.dialog && typeof this.dialog.confirm === 'function') {
      const { result } = await this.dialog.confirm({
        message: `Are you sure you want to remove ${message}?`,
        details: 'This operation cannot be undone',
        danger: true,
      });
      confirmed = result;
    }
    if (confirmed) {
      return this.submitOperation(new RemoveOperation({
        ...rest,
        adapters: this.adapters,
        source,
        elements: sourceElements,
        abortConfirmation: createAbortConfirmation(this.dialog),
        retryConfirmation: createRetryConfirmation(this.dialog),
      }), resolve);
    }
    return undefined;
  }

  async copy(options = {}, resolve = undefined) {
    const {
      source,
      sourceElements = [],
      destination,
      destinationPath,
      ...rest
    } = options;
    if (!source) {
      throw new Error('File system not specified');
    }
    if (sourceElements.length === 0) {
      throw new Error('Nothing to copy');
    }
    return this.submitOperation(new CopyOperation({
      adapters: this.adapters,
      source,
      elements: sourceElements,
      destination,
      destinationPath,
      abortConfirmation: createAbortConfirmation(this.dialog),
      retryConfirmation: createRetryConfirmation(this.dialog),
      overwriteExistingCallback: createOverrideConfirmation(this.dialog),
      operationRetryConfirmation: createGeneralRetryConfirmation(this.dialog),
      ...rest,
    }), resolve);
  }

  async move(options = {}, resolve = undefined) {
    const {
      source,
      sourceElements = [],
      destination,
      destinationPath,
      ...rest
    } = options;
    if (!source) {
      throw new Error('File system not specified');
    }
    if (sourceElements.length === 0) {
      throw new Error('Nothing to move');
    }
    return this.submitOperation(new MoveOperation({
      adapters: this.adapters,
      source,
      elements: sourceElements,
      destination,
      destinationPath,
      abortConfirmation: createAbortConfirmation(this.dialog),
      retryConfirmation: createRetryConfirmation(this.dialog),
      overwriteExistingCallback: createOverrideConfirmation(this.dialog),
      operationRetryConfirmation: createGeneralRetryConfirmation(this.dialog),
      ...rest,
    }), resolve);
  }

  async abort(id) {
    const operation = this.operations.find((o) => o.id === id);
    if (operation) {
      (operation.abort)();
      return Promise.resolve(true);
    }
    return Promise.resolve(false);
  }
}

module.exports = Operations;
