const logger = require('../common/logger');

/**
 * @typedef {Object} OperationOptions
 * @property {FileSystemAdapters} adapters
 * @property {function(string?):Promise<boolean>} [abortConfirmation]
 * @property {function:void} [callback]
 */

class Operation {
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
    aborted: 'aborted',
  };

  static counter = 0;

  static createOperationId() {
    Operation.counter += 1;
    return Operation.counter;
  }

  /**
   * @param {OperationOptions} options
   */
  constructor(options = {}) {
    const {
      // eslint-disable-next-line no-unused-vars
      abortConfirmation = ((o) => Promise.resolve(true)),
      // eslint-disable-next-line no-unused-vars
      callback = ((o) => {}),
      adapters,
    } = options || {};
    this.adapters = adapters;
    if (!this.adapters) {
      throw new Error('Operation should be initialized with file system adapters');
    }
    this.abortConfirmation = abortConfirmation;
    this.waitForAborted = new Promise((resolve) => {
      this.waitForAbortedResolve = resolve;
    });
    this.stageDuration = { ...this.constructor.StageDuration };
    this.report = callback;
    /**
     * Operation identifier
     * @type {number}
     */
    this.id = Operation.createOperationId();
    this.operationStatus = undefined;
    this.fatalError = undefined;
    this.statusValue = Operation.Status.waiting;
    this.abortRequest = undefined;
    this.progressValue = 0;
    this.operationErrors = [];
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

  get progress() {
    return this.progressValue;
  }

  set progress(value) {
    if (this.progressValue !== value && !Number.isNaN(Number(value))) {
      this.progressValue = value;
      this.report(this);
    }
  }

  get status() {
    return this.statusValue;
  }

  set status(value) {
    if (this.statusValue !== value) {
      this.statusValue = value;
      this.report(this);
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
    this.report(this);
  };

  info = (...message) => {
    logger.info(`[${this.name}]`, ...message);
    this.operationStatus = message.join(' ');
    this.report(this);
  };

  warn = (...message) => {
    logger.warn(`[${this.name}]`, ...message);
    this.operationStatus = message.join(' ');
    this.report(this);
  };

  error = (...message) => {
    logger.error(`[${this.name}]`, ...message);
    this.operationStatus = message.join(' ');
    this.report(this);
  };

  fatal = (...message) => {
    logger.error(`[${this.name}]`, ...message);
    this.fatalError = message.join(' ');
    this.operationStatus = message.join(' ');
    this.report(this);
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
            if (!confirmed) {
              this.abortRequest = undefined;
            } else if (typeof this.waitForAbortedResolve === 'function') {
              this.waitForAbortedResolve();
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
    return Promise.resolve();
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
}

module.exports = Operation;
