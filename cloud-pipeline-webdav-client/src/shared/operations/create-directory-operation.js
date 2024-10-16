const Operation = require('./base');

/**
 * @typedef {Object} CreateDirectoryOperationOptions
 * @property {string|FileSystemInterface} [source]
 * @property {string} [sourcePath]
 * @property {string} [directory]
 * @property {boolean} [isFullPath]
 */

class CreateDirectoryOperation extends Operation {
  /**
   * @param {CreateDirectoryOperationOptions & OperationOptions} options
   */
  constructor(options) {
    super(options);
    const {
      source,
      sourcePath,
      directory,
      isFullPath = false,
    } = options;
    this.source = source;
    this.sourcePath = sourcePath;
    this.directory = directory;
    this.isFullPath = isFullPath;
  }

  get affectedFileSystems() {
    return [this.getFileSystemIdentifier(this.source)];
  }

  get name() {
    return `Create directory operation: ${this.getFileSystemIdentifier(this.source)}`;
  }

  // eslint-disable-next-line class-methods-use-this
  get operationName() {
    return 'create directory operation';
  }

  // eslint-disable-next-line class-methods-use-this
  get operationType() {
    return 'directory';
  }

  async cleanUp() {
    await this.clearInterfaceAndAdapter(this.adapterInterface, this.adapter);
    this.adapter = undefined;
    this.adapterInterface = undefined;
    return super.cleanUp();
  }

  async before() {
    const {
      adapter,
      interface: adapterInterface,
    } = await this.createInterface(this.source);
    this.adapter = adapter;
    this.adapterInterface = adapterInterface;
  }

  async invoke() {
    if (!this.directory) {
      throw new Error('Directory name not specified');
    }
    await this.retry(async () => {
      this.info(`Creating directory "${this.isFullPath ? this.sourcePath : this.directory}"...`);
      const directoryPath = this.isFullPath
        ? this.sourcePath
        : this.adapterInterface.joinPath(this.sourcePath, this.directory, true);
      await this.adapterInterface.createDirectory(directoryPath);
      this.info(`Directory "${this.isFullPath ? this.sourcePath : this.directory}" created`);
    });
  }

  async cancelCurrentAdapterTasks() {
    await Promise.all([
      this.adapterInterface
        ? this.adapterInterface.cancelCurrentTask()
        : Promise.resolve(),
    ]);
  }
}

module.exports = CreateDirectoryOperation;
