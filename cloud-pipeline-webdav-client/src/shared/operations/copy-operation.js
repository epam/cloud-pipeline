const fs = require('fs');
const Operation = require('./base');
const displaySize = require('../utilities/display-size');

/**
 * @typedef {Object} CopyOperationOptions
 * @property {string|FileSystemInterface} source
 * @property {string[]|string} elements
 * @property {string|FileSystemInterface} destination
 * @property {string} destinationPath
 * @property {function} [overwriteExistingCallback]
 * @property {function} [operationRetryConfirmation]
 * @property {number} [iterationsCount]
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
      operationRetryConfirmation = (() => Promise.resolve(true)),
      iterationsCount,
    } = options;
    /**
     * @type {string|FileSystemInterface}
     */
    this.source = source;
    this.elements = typeof elements === 'string' ? [elements] : elements;
    this.directDestination = typeof elements === 'string';
    this.iterationsCount = iterationsCount;
    /**
     * @type {string|FileSystemInterface}
     */
    this.destination = destination;
    this.destinationPath = destinationPath;
    this.overwriteExistingCallback = overwriteExistingCallback;
    this.operationRetryConfirmation = operationRetryConfirmation;
    this.stageDuration[Operation.Stages.before] = (elements || []).length;
    this.stageDuration[Operation.Stages.invoke] = 100 - (elements || []).length;
    this.completed = [];
    this.list = [];
    this.recovered = false;
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

  // eslint-disable-next-line class-methods-use-this
  get operationType() {
    return 'copy';
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

  recover(uuid) {
    super.recover(uuid);
    const info = this.readOperationInfo();
    // const filePath = path
    const {
      source,
      elements  = [],
      destination,
      destinationPath,
      list,
      direct,
    } = info;
    this.source = source;
    this.destination = destination;
    this.elements = elements;
    this.destinationPath = destinationPath;
    this.list = list;
    this.directDestination = direct;
    this.recovered = true;
    const completedPath = this.getOperationInfoPath('completed');
    this.completed = [];
    if (fs.existsSync(completedPath)) {
      this.completed = fs.readFileSync(completedPath).toString().split('\n').filter((c) => c.length > 0);
    }
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
    if (typeof this.source !== 'string' && this.source) {
      this.source = this.source.adapter.identifier;
    }
    if (typeof this.destination !== 'string' && this.destination) {
      this.destination = this.destination.adapter.identifier;
    }
    await this.clearInterfaceAndAdapter(this.sourceAdapterInterface, this.sourceAdapter);
    this.sourceAdapterInterface = undefined;
    this.sourceAdapter = undefined;
    await this.clearInterfaceAndAdapter(this.destinationAdapterInterface, this.destinationAdapter);
    this.destinationAdapterInterface = undefined;
    this.destinationAdapter = undefined;
  }

  async reInitializeInterfaces() {
    if (this.sourceAdapter) {
      await this.clearInterfaceOnly(this.sourceAdapterInterface, this.sourceAdapter);
      this.sourceAdapterInterface = await this.sourceAdapter.createInterface();
    } else {
      const {
        adapter: sourceAdapter,
        interface: sourceAdapterInterface,
      } = await this.createInterface(this.source);
      this.sourceAdapter = sourceAdapter;
      this.sourceAdapterInterface = sourceAdapterInterface;
    }
    if (this.destinationAdapter) {
      await this.clearInterfaceOnly(this.destinationAdapterInterface, this.destinationAdapter);
      this.destinationAdapterInterface = await this.destinationAdapter.createInterface();
    } else {
      const {
        adapter: destinationAdapter,
        interface: destinationAdapterInterface,
      } = await this.createInterface(this.destination);
      this.destinationAdapter = destinationAdapter;
      this.destinationAdapterInterface = destinationAdapterInterface;
    }
  }

  async cleanUp() {
    await this.clearInterfaces();
    return super.cleanUp();
  }

  async before() {
    await this.createInterfaces();
    if (!this.recovered) {
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
        to: this.directDestination
          ? this.destinationPath
          : this.destinationAdapter.joinPath(this.destinationPath, item.name, item.isDirectory),
        isDirectory: item.isDirectory,
        isFile: item.isFile,
      }));
    }
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
    this.info(`Validating file "${name}" checksum...`);
    const [sourceChecksums, destinationChecksums] = await Promise.all([
      this.sourceAdapterInterface.getFilesChecksums([from]),
      this.destinationAdapterInterface.getFilesChecksums([to]),
    ]);
    const [sourceChecksum] = sourceChecksums;
    const [destinationChecksum] = destinationChecksums;
    if (
      sourceChecksum !== undefined
      && destinationChecksum !== undefined
      && sourceChecksum !== destinationChecksum
    ) {
      this.error(`Validating file "${name}" failed. Source checksum: ${sourceChecksum}, destination checksum: ${destinationChecksum}`);
      throw new Error(`"${name}" validation failed: checksums don't match`);
    }
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
    this.saveOperationInfo();
    const operationLogic = async (iteration = 0) => {
      const itemsToProcess = (this.list || [])
        .filter((item) => !this.completed.includes(item.from));
      if (itemsToProcess.length === 0) {
        this.info('Files copied');
        return;
      }
      const isAborted = await this.isAborted();
      if (isAborted) {
        return;
      }
      if (iteration > 0) {
        this.info(itemsToProcess.length > 1 ? `${itemsToProcess.length} items failed to copy` : `"${itemsToProcess[0].name}" item failed to copy`);
        let retry = this.iterationsCount === undefined || this.iterationsCount > iteration;
        if (retry && typeof this.operationRetryConfirmation === 'function') {
          retry = await this.operationRetryConfirmation(
            itemsToProcess.length > 1 ? `${itemsToProcess.length} items failed to copy. Retry?` : `"${itemsToProcess[0].name}" item failed to copy.Retry?`,
          );
        }
        if (!retry) {
          throw new Error(
            itemsToProcess.length > 1 ? `${itemsToProcess.length} items failed to copy` : `"${itemsToProcess[0].name}" item failed to copy`,
          );
        }
      }
      await this.reInitializeInterfaces();
      this.failed = [];
      await this.iterate(
        this.tryPerform.bind(
          this,
          this.invokeElement.bind(this),
          this.markItemCompleted.bind(this),
          this.onItemFailed.bind(this),
          this.onItemIterationFailed.bind(this),
        ),
        itemsToProcess.slice(),
        {
          stage: Operation.Stages.invoke,
        },
      );
      await this.reInitializeInterfaces();
      await operationLogic(iteration + 1);
    };
    await operationLogic();
    this.clearOperationInfo();
  }

  getOperationInfo() {
    return {
      ...super.getOperationInfo(),
      source: typeof this.source === 'string' ? this.source : this.source.adapter.identifier,
      elements: this.elements,
      destination: typeof this.destination === 'string' ? this.destination : this.destination.adapter.identifier,
      destinationPath: this.destinationPath,
      list: this.list,
      direct: this.directDestination,
    };
  }

  /**
   * @param {CopyMoveElementInfo} item
   */
  markItemCompleted(item) {
    this.completed.push(item.from);
    if (this.trackOperationInfo) {
      const filePath = this.getOperationInfoPath('completed');
      this.ensureOperationDirectory();
      fs.appendFileSync(filePath, `${item.from}\n`);
    }
  }

  /**
   * @param {CopyMoveElementInfo} item
   */
  async onItemFailed(item) {
    this.error(`Failed to copy item: "${item.name}"`);
    try {
      await this.reInitializeInterfaces();
    } catch {
      // noop
    }
  }

  async onItemIterationFailed() {
    try {
      await this.reInitializeInterfaces();
    } catch {
      // noop
    }
  }
}

module.exports = CopyOperation;
