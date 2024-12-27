const EventEmitter = require('events');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const logger = require('../shared-logger');
const operationsInfoDirectory = require('../utilities/operations-directory');

/**
 * @typedef {Object} OperationOptions
 * @property {FileSystemAdapters} adapters
 * @property {function(string?):Promise<boolean>} [abortConfirmation]
 * @property {function(string?):Promise<boolean>} [retryConfirmation]
 * @property {boolean} [saveOperationInfo]
 * @property {function} [operationInfoCallback]
 */

class Operation extends EventEmitter {
  static LOG_LEVEL = {
    log: 0,
    info: 1,
    warn: 2,
    error: 3,
    fatal: 4,
  };

  static Stages = {
    before: 'before',
    invoke: 'invoke',
    after: 'after',
    cleanUp: 'clean-up',
  };

  static StageDuration = {
    [Operation.Stages.before]: 0.1,
    [Operation.Stages.invoke]: 8,
    [Operation.Stages.after]: 0,
    [Operation.Stages.cleanUp]: 0.1,
  };

  static Status = {
    waiting: 'waiting',
    pending: 'pending',
    error: 'error',
    done: 'done',
    aborting: 'aborting',
    aborted: 'aborted',
  };

  static counter = 0;

  static createOperationId() {
    Operation.counter += 1;
    return Operation.counter;
  }

  static async getOperationInfoIdentifiers() {
    const contents = await fs.promises.readdir(operationsInfoDirectory, { withFileTypes: true });
    return contents
      .filter((c) => c.isDirectory())
      .map((d) => d.name);
  }

  static async getOperationInfoByIdentifier(identifier) {
    const infoPath = path.resolve(operationsInfoDirectory, identifier, 'info.json');
    if (fs.existsSync(infoPath)) {
      return JSON.parse(fs.readFileSync(infoPath).toString());
    }
    throw new Error(`Operation with identifier "${identifier}" not found`);
  }

  static async getOperationTypeByIdentifier(identifier) {
    const { type } = await Operation.getOperationInfoByIdentifier(identifier);
    return type;
  }

  /**
   * @param {OperationOptions} options
   */
  constructor(options = {}) {
    super();
    const {
      // eslint-disable-next-line no-unused-vars
      abortConfirmation = ((o) => Promise.resolve(true)),
      // eslint-disable-next-line no-unused-vars
      retryConfirmation = ((o) => Promise.resolve(false)),
      adapters,
      saveOperationInfo = false,
      // eslint-disable-next-line no-unused-vars
      operationInfoCallback = (o) => {},
    } = options || {};
    this.trackOperationInfo = saveOperationInfo;
    this.adapters = adapters;
    if (!this.adapters) {
      throw new Error('Operation should be initialized with file system adapters');
    }
    this.abortConfirmation = abortConfirmation;
    this.retryConfirmation = retryConfirmation;
    this.stageDuration = { ...this.constructor.StageDuration };
    /**
     * Operation identifier
     * @type {number}
     */
    this.id = Operation.createOperationId();
    this.uuid = crypto.randomUUID();
    if (typeof operationInfoCallback === 'function') {
      operationInfoCallback(this.uuid);
    }
    this.operationDirectory = path.resolve(operationsInfoDirectory, this.uuid);
    this.operationStatus = undefined;
    this.fatalError = undefined;
    this.statusValue = Operation.Status.waiting;
    this.abortRequest = undefined;
    this.abortConfirmed = false;
    this.progressValue = 0;
    this.operationErrors = [];
  }

  recover(uuid) {
    this.uuid = uuid;
    this.operationDirectory = path.resolve(operationsInfoDirectory, this.uuid);
  }

  report() {
    this.emit('stateChanged', this);
  }

  // eslint-disable-next-line class-methods-use-this
  get affectedFileSystems() {
    return [];
  }

  get name() {
    return `Operation #${this.id}`;
  }

  // eslint-disable-next-line class-methods-use-this
  get operationName() {
    return 'Operation';
  }

  // eslint-disable-next-line class-methods-use-this
  get operationType() {
    return 'operation';
  }

  get progress() {
    return this.progressValue;
  }

  set progress(value) {
    if (this.progressValue !== value && !Number.isNaN(Number(value))) {
      this.progressValue = value;
      this.report();
    }
  }

  get status() {
    if (this.statusValue !== Operation.Status.aborted && this.abortConfirmed) {
      return Operation.Status.aborting;
    }
    return this.statusValue;
  }

  set status(value) {
    if (this.statusValue !== value) {
      this.statusValue = value;
      this.report();
    }
  }

  get description() {
    if (this.fatalError) {
      return this.fatalError;
    }
    return this.operationStatus;
  }

  log = (...message) => {
    this.operationStatus = message.join(' ');
    this.report();
  };

  info = (...message) => {
    logger.info(`[${this.name}]`, ...message);
    this.operationStatus = message.join(' ');
    this.report();
  };

  warn = (...message) => {
    logger.warn(`[${this.name}]`, ...message);
    this.operationStatus = message.join(' ');
    this.report();
  };

  error = (...message) => {
    logger.error(`[${this.name}]`, ...message);
    this.operationStatus = message.join(' ');
    this.report();
  };

  fatal = (...message) => {
    logger.error(`[${this.name}]`, ...message);
    this.fatalError = message.join(' ');
    this.operationStatus = message.join(' ');
    this.report();
  };

  /**
   * @returns {Promise<boolean>}
   */
  isAborted() {
    if (!this.abortRequest) {
      return Promise.resolve(false);
    }
    return this.abortRequest;
  }

  /**
   * @param {string} stage
   * @param {number} ratio
   */
  reportStagePercentage(stage, ratio = 0) {
    const stagesOrder = [
      Operation.Stages.before,
      Operation.Stages.invoke,
      Operation.Stages.after,
      Operation.Stages.cleanUp,
    ];
    const idx = stagesOrder.indexOf(stage);
    if (idx === -1) {
      return;
    }
    const getStageDuration = (stageName) => (this.stageDuration[stageName] || 0);
    const getStagesDuration = (stages) => stages
      .reduce(
        (duration, stageName) => duration + getStageDuration(stageName),
        0,
      );
    const totalStagesDuration = getStagesDuration(stagesOrder);
    const beforeStageDuration = getStagesDuration(stagesOrder.slice(0, idx));
    const currentStageDuration = getStageDuration(stage);
    const currentProgress = beforeStageDuration
      + currentStageDuration * Math.max(0, Math.min(1, ratio));
    this.progress = currentProgress / totalStagesDuration;
  }

  submit = async () => {
    const safelyCleanUp = async () => {
      try {
        await this.cleanUp();
      } catch (error) {
        this.error(`Clean-up error: ${error.message}`);
      }
    };
    let status;
    try {
      this.status = Operation.Status.pending;
      this.reportStagePercentage(Operation.Stages.before);
      await this.before();
      this.reportStagePercentage(Operation.Stages.invoke);
      await this.invoke();
      this.reportStagePercentage(Operation.Stages.after);
      await this.after();
      status = Operation.Status.done;
    } catch (error) {
      this.fatal(error.message);
      status = Operation.Status.error;
    } finally {
      this.reportStagePercentage(Operation.Stages.cleanUp);
      await safelyCleanUp();
      const isAborted = await this.isAborted();
      if (isAborted) {
        this.status = Operation.Status.aborted;
        this.info('Operation aborted');
      } else if (status) {
        this.status = status;
      }
      this.progress = 0;
    }
  };

  // eslint-disable-next-line class-methods-use-this,no-empty-function
  async cancelCurrentAdapterTasks() {
  }

  /**
   * Perform operation once (do not repeat it on operation retry)
   * @param {string} identifier
   * @param {function:Promise<*>} fn
   * @returns {Promise<*>}
   */
  async doOnce(identifier, fn) {
    if (!this.onceOperations) {
      this.onceOperations = new Map();
    }
    if (!this.onceOperations.has(identifier)) {
      const fnCall = fn();
      this.onceOperations.set(identifier, fnCall);
      fnCall.catch(() => {
        this.onceOperations.delete(identifier);
      });
      return fnCall;
    }
    return this.onceOperations.get(identifier);
  }

  /**
   * @param {function(*):Promise<any>} fn
   * @param {*} options
   * @returns {Promise<*>}
   */
  async retry(fn, ...options) {
    let retry = false;
    try {
      return await fn(...options);
    } catch (error) {
      if (await this.isAborted()) {
        throw error;
      }
      logger.log('Error occurred:', error.message, '. Asking user for retry...');
      retry = await this.retryConfirmation(error.message);
      logger.log(`Asking user for retry; decision: ${retry ? 'retry' : 'abort'}`);
      if (!retry) {
        throw error;
      }
    }
    if (retry) {
      await this.cancelCurrentAdapterTasks();
      logger.log('Retrying...');
      return this.retry(fn, ...options);
    }
    return undefined;
  }

  async tryPerform(fn, onComplete, onFailed, onItemIterationFailed, ...options) {
    const maxIterations = 5;
    const tryIteration = async (iteration = 0) => {
      if (iteration >= maxIterations) {
        await onFailed(...options);
        return undefined;
      }
      try {
        const result = await fn(...options);
        await onComplete(...options);
        return result;
      } catch (error) {
        if (await this.isAborted()) {
          throw error;
        }
        logger.log('Error occurred:', error.message);
        await onItemIterationFailed(...options);
      }
      return tryIteration(iteration + 1);
    };
    return tryIteration();
  }

  /**
   * @param {function(*, function):Promise<*>} iteratorFn
   * @param {*[]} elements
   * @param {{stage: string, from: number?, to: number?}} [progressOptions]
   * @returns {Promise<*>}
   */
  async iterate(iteratorFn, elements = [], progressOptions = {}) {
    const {
      stage,
      from = 0,
      to = 1,
    } = progressOptions;
    const total = elements.length;
    const iterator = async (array = []) => {
      if (await this.isAborted()) {
        return [];
      }
      if (!array || !array.length) {
        return [];
      }
      const [current, ...rest] = array;
      this.reportStagePercentage(
        stage,
        from + ((total - array.length) / total) * (to - from),
      );
      const localProgress = (percentage = 0) => {
        this.reportStagePercentage(
          stage,
          from + ((total - array.length + percentage) / total) * (to - from),
        );
      };
      const items = await iteratorFn(current, localProgress);
      this.reportStagePercentage(
        stage,
        from + ((total - rest.length) / total) * (to - from),
      );
      const other = await iterator(rest);
      return [
        ...(items || []),
        ...other,
      ];
    };
    return iterator(elements);
  }

  async abort() {
    if (!this.abortRequest) {
      this.abortRequest = new Promise((resolve) => {
        this.abortConfirmation(this.operationName)
          .then((confirmed) => {
            resolve(confirmed);
            this.abortConfirmed = confirmed;
            this.report();
            if (!confirmed) {
              this.abortRequest = undefined;
            } else {
              this.emit('abort');
            }
          });
      });
    }
    return this.abortRequest;
  }

  /**
   * Invoked before `invoke` method
   * @returns {Promise<void>}
   */
  // eslint-disable-next-line class-methods-use-this
  async before() {
    return Promise.resolve();
  }

  /**
   * Main operation logic; invoked after `before` method
   * @returns {Promise<void>}
   */
  // eslint-disable-next-line class-methods-use-this
  async invoke() {
    return Promise.resolve();
  }

  /**
   * After-invocation logic; invoked after `invoke` method if it was successful
   * @returns {Promise<void>}
   */
  // eslint-disable-next-line class-methods-use-this
  async after() {
    return Promise.resolve();
  }

  /**
   * Clean-up logic; invoked after operation (successful, failed or aborted)
   * @returns {Promise<void>}
   */
  // eslint-disable-next-line class-methods-use-this
  async cleanUp() {
    if (this.onceOperations) {
      this.onceOperations.clear();
      this.onceOperations = undefined;
    }
    return Promise.resolve();
  }

  // eslint-disable-next-line class-methods-use-this
  getOperationInfo() {
    return {
      type: this.operationType,
    };
  }

  saveOperationInfo() {
    if (this.trackOperationInfo) {
      this.ensureOperationDirectory();
      const info = this.getOperationInfo();
      fs.writeFileSync(this.getOperationInfoPath('info.json'), JSON.stringify(info));
    }
  }

  readOperationInfo() {
    const infoPath = this.getOperationInfoPath('info.json');
    if (fs.existsSync(infoPath)) {
      return JSON.parse(fs.readFileSync(infoPath).toString());
    }
    throw new Error(`"${infoPath}" does not exist`);
  }

  clearOperationInfo() {
    if (fs.existsSync(this.operationDirectory)) {
      fs.rmSync(this.operationDirectory, { recursive: true, force: true });
    }
  }

  getOperationInfoPath(relative) {
    return path.resolve(this.operationDirectory, relative);
  }

  ensureOperationDirectory() {
    if (this.trackOperationInfo) {
      fs.mkdirSync(this.operationDirectory, { recursive: true });
    }
  }

  /**
   * @param {string|FileSystemInterface} type
   * @returns {string}
   */
  // eslint-disable-next-line class-methods-use-this
  getFileSystemIdentifier(type) {
    if (typeof type === 'string') {
      return type;
    }
    if (type && type.adapter) {
      return type.adapter.identifier;
    }
    return undefined;
  }

  /**
   * @param {string|FileSystemInterface} type
   * @returns {Promise<{adapter: FileSystemAdapter, interface: FileSystemInterface}>}
   */
  // eslint-disable-next-line class-methods-use-this
  async createInterface(type) {
    if (!type) {
      throw new Error('Unknown interface');
    }
    if (typeof type === 'string') {
      this.info(`Initializing ${type} interface...`);
      const adapter = this.adapters.getByIdentifier(type);
      if (!adapter) {
        throw new Error(`Unknown file system: "${type}"`);
      }
      const adapterInterface = await adapter.createInterface();
      if (!adapterInterface) {
        throw new Error(`Error creating interface for file system "${type}"`);
      }
      this.info(`Initializing ${type} interface: done`);
      return {
        adapter,
        interface: adapterInterface,
      };
    }
    return {
      interface: type,
      adapter: type.adapter,
    };
  }

  /**
   * @param {FileSystemInterface} adapterInterface
   * @param {FileSystemAdapter} adapter
   * @returns {Promise<void>}
   */
  // eslint-disable-next-line class-methods-use-this
  async clearInterfaceAndAdapter(adapterInterface, adapter) {
    if (adapterInterface && adapter) {
      await adapter.destroyInterface(adapterInterface);
    } else if (adapterInterface) {
      await adapterInterface.destroy();
    }
  }

  /**
   * @param {FileSystemInterface} adapterInterface
   * @param {FileSystemAdapter} adapter
   * @returns {Promise<void>}
   */
  // eslint-disable-next-line class-methods-use-this
  async clearInterfaceOnly(adapterInterface, adapter) {
    if (adapterInterface && adapter) {
      await adapter.destroyInterface(adapterInterface);
    }
  }
}

module.exports = Operation;
