const Operation = require('./base');
const displaySize = require('../common/display-size');

/**
 * @typedef {Object} CopyOperationOptions
 * @property {string|FileSystemInterface} source
 * @property {string[]} elements
 * @property {string|FileSystemInterface} destination
 * @property {string} destinationPath
 * @property {function} [overwriteExistingCallback]
 */

/**
 * @typedef {Object} CopyMoveElementInfo
 * @property {string} name
 * @property {string} from
 * @property {string} to
 * @property {boolean} isDirectory
 * @property {boolean} isFile
 */

class CopyOperation extends Operation {
  static StageDuration = {
    [Operation.Stages.before]: 1,
    [Operation.Stages.invoke]: 97,
    [Operation.Stages.after]: 1,
    [Operation.Stages.cleanUp]: 1,
  };

  /**
   * @param {CopyOperationOptions & OperationOptions} options
   */
  constructor(options = {}) {
    super(options);
    const {
      source,
      elements,
      destination,
      destinationPath,
      // eslint-disable-next-line no-unused-vars
      overwriteExistingCallback = ((o) => Promise.resolve(true)),
    } = options;
    /**
     * @type {string|FileSystemInterface}
     */
    this.source = source;
    this.elements = elements;
    /**
     * @type {string|FileSystemInterface}
     */
    this.destination = destination;
    this.destinationPath = destinationPath;
    this.overwriteExistingCallback = overwriteExistingCallback;
    this.stageDuration[Operation.Stages.before] = (elements || []).length;
    this.stageDuration[Operation.Stages.invoke] = 100 - (elements || []).length;
  }

  get affectedFileSystems() {
    return [
      this.getFileSystemIdentifier(this.source),
      this.getFileSystemIdentifier(this.destination),
    ];
  }

  get name() {
    return `Copy operation: ${this.getFileSystemIdentifier(this.source)} -> ${this.getFileSystemIdentifier(this.destination)}`;
  }

  // eslint-disable-next-line class-methods-use-this
  get operationName() {
    return 'copy operation';
  }

  /**
   * @param {string} root
   * @returns {Promise<FSItem[]>}
   */
  async getRelativeList(root) {
    if (!this.sourceAdapterInterface) {
      return [];
    }
    const isAborted = () => this.isAborted();
    if (await isAborted()) {
      return [];
    }
    const adapterInterface = this.sourceAdapterInterface;
    if (await isAborted()) {
      return [];
    }
    const isDirectory = await adapterInterface.isDirectory(root);
    if (await isAborted()) {
      return [];
    }
    const parentDirectory = await adapterInterface.getParentDirectory(root);
    if (await isAborted()) {
      return [];
    }
    const info = this.info.bind(this);
    const warn = this.warn.bind(this);
    info(`Processing "${root}"...`);
    async function getList(directory) {
      if (await isAborted()) {
        return [];
      }
      info(`Reading "${directory}"...`);
      /**
       * @param {FSItem} anItem
       * @returns {Promise<unknown>|Promise<{path, name: *}>}
       */
      async function mapItem(anItem) {
        if (await isAborted()) {
          return [];
        }
        if (anItem.isBackLink) {
          return [];
        }
        if (anItem.isFile) {
          return [{
            path: anItem.path,
            name: adapterInterface.getRelativePath(anItem.path, parentDirectory),
            isFile: true,
            isDirectory: false,
          }];
        }
        if (anItem.isDirectory) {
          const directoryContents = await getList(anItem.path);
          return [
            {
              path: anItem.path,
              name: adapterInterface.getRelativePath(anItem.path, parentDirectory),
              isFile: false,
              isDirectory: true,
            },
            ...directoryContents,
          ];
        }
        return [];
      }
      try {
        const items = await adapterInterface.list(directory);
        const files = items.filter((anItem) => !anItem.isBackLink && anItem.isFile);
        const folders = items.filter((anItem) => !anItem.isBackLink && anItem.isDirectory);
        const results = await Promise.all([...files, ...folders].map(mapItem));
        return results.reduce((r, c) => ([...r, ...c]), []).filter(Boolean);
      } catch (_) {
        warn(`Error reading directory "${directory}": ${_.message}`);
        return [];
      }
    }
    const result = [{
      path: root,
      name: adapterInterface.getRelativePath(root, parentDirectory),
      isFile: !isDirectory,
      isDirectory,
    }];
    if (isDirectory) {
      const items = await getList(root);
      result.push(...items);
    }
    return result;
  }

  async createInterfaces() {
    const {
      adapter: sourceAdapter,
      interface: sourceAdapterInterface,
    } = await this.createInterface(this.source);
    const {
      adapter: destinationAdapter,
      interface: destinationAdapterInterface,
    } = await this.createInterface(this.destination);
    this.sourceAdapter = sourceAdapter;
    this.sourceAdapterInterface = sourceAdapterInterface;
    this.destinationAdapter = destinationAdapter;
    this.destinationAdapterInterface = destinationAdapterInterface;
  }

  async clearInterfaces() {
    await this.clearInterfaceAndAdapter(this.sourceAdapterInterface, this.sourceAdapter);
    this.sourceAdapterInterface = undefined;
    this.sourceAdapter = undefined;
    await this.clearInterfaceAndAdapter(this.destinationAdapterInterface, this.destinationAdapter);
    this.destinationAdapterInterface = undefined;
    this.destinationAdapter = undefined;
  }

  async cleanUp() {
    await this.clearInterfaces();
    return super.cleanUp();
  }

  async before() {
    await this.createInterfaces();
    const list = await this.iterate(
      this.retry.bind(this, this.getRelativeList.bind(this)),
      this.elements,
      {
        stage: Operation.Stages.before,
      },
    );
    /**
     * @type {CopyMoveElementInfo[]}
     */
    this.list = list.map((item) => ({
      name: item.name,
      from: item.path,
      to: this.destinationAdapter.joinPath(this.destinationPath, item.name, item.isDirectory),
      isDirectory: item.isDirectory,
      isFile: item.isFile,
    }));
    this.info(`${this.list.length} item${this.list.length > 1 ? 's' : ''} read.`);
  }

  /**
   * @param {CopyMoveElementInfo} element
   * @param {function} progressCallback
   * @returns {Promise<void>}
   */
  async invokeElement(element, progressCallback) {
    if (await this.isAborted()) {
      return;
    }
    if (!this.sourceAdapterInterface
      || !this.destinationAdapterInterface
    ) {
      return;
    }
    const {
      name,
      isDirectory,
      to,
      from,
    } = element;
    if (!this.parentDirectories) {
      this.parentDirectories = [];
    }
    if (isDirectory) {
      this.info(`Creating directory "${to}"`);
      await this.destinationAdapterInterface.createDirectory(to);
      this.parentDirectories.push(to);
      return;
    }
    this.info(`Copying file "${name}"`);
    const cancel = await this.doOnce(to, async () => {
      const exists = await this.destinationAdapterInterface.exists(to);
      if (exists && typeof this.overwriteExistingCallback === 'function') {
        const overwrite = await this.overwriteExistingCallback(to);
        if (!overwrite) {
          this.info(`"${to}" already exists. Wouldn't be overridden`);
          return true;
        }
        this.info(`"${to}" already exists. Will be overridden`);
      }
      return false;
    });
    if (cancel) {
      return;
    }
    /**
     * @param {number} transferred
     * @param {number} size
     */
    const callback = (transferred, size) => {
      if (size > 0 && typeof progressCallback === 'function') {
        progressCallback(Math.min(1, transferred / size));
      }
      this.log(`Copying file "${name}": ${displaySize(transferred)} / ${displaySize(size)}`);
    };
    const aFile = await this.sourceAdapterInterface.readFile(from);
    if (!aFile || !aFile.data) {
      throw new Error(`Error creating read stream for ${this.getFileSystemIdentifier(this.source)}`);
    }
    const parent = await this.destinationAdapterInterface.getParentDirectory(to);
    if (!this.parentDirectories.find((p) => p === parent)) {
      const parentDirectoryExists = await this.destinationAdapterInterface.exists(parent);
      if (!parentDirectoryExists) {
        this.info(`Creating "${parent}" directory`);
        await this.destinationAdapterInterface.createDirectory(parent);
      }
      this.parentDirectories.push(parent);
    } else {
      this.info(`"${parent}" directory already exists`);
    }
    await this.destinationAdapterInterface.writeFile(
      to,
      aFile.data,
      {
        size: aFile.size,
        progressCallback: callback,
        abortSignal: this,
      },
    );
  }

  async cancelCurrentAdapterTasks() {
    await Promise.all([
      this.sourceAdapterInterface
        ? this.sourceAdapterInterface.cancelCurrentTask()
        : Promise.resolve(),
      this.destinationAdapterInterface
        ? this.destinationAdapterInterface.cancelCurrentTask()
        : Promise.resolve(),
    ]);
  }

  async invoke() {
    return this.iterate(
      this.retry.bind(this, this.invokeElement.bind(this)),
      (this.list || []).slice(),
      {
        stage: Operation.Stages.invoke,
      },
    );
  }
}

module.exports = CopyOperation;
