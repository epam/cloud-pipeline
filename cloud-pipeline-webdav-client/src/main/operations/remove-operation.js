const Operation = require('./base');

/**
 * @typedef {Object} RemoveOperationOptions
 * @property {string|FileSystemInterface} [source]
 * @property {string[]} [elements]
 */

class RemoveOperation extends Operation {
  /**
   * @param {RemoveOperationOptions & OperationOptions} options
   */
  constructor(options) {
    super(options);
    const {
      source,
      elements,
    } = options;
    this.source = source;
    this.elements = elements || [];
  }

  get affectedFileSystems() {
    return [this.getFileSystemIdentifier(this.source)];
  }

  get name() {
    return `Remove operation: ${this.getFileSystemIdentifier(this.source)}`;
  }

  // eslint-disable-next-line class-methods-use-this
  get operationName() {
    return 'remove operation';
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

  /**
   * @param {string} element
   * @returns {Promise<void>}
   */
  async removeElement(element) {
    if (await this.isAborted()) {
      this.info(`Skipping "${element}": aborted`);
      return;
    }
    this.info(`Removing "${element}"...`);
    await this.adapterInterface.remove(element);
    this.info(`Removing "${element}": done`);
  }

  async invoke() {
    await this.iterate(
      this.retry.bind(this, this.removeElement.bind(this)),
      this.elements.slice(),
      { stage: Operation.Stages.invoke },
    );
  }

  async cancelCurrentAdapterTasks() {
    await Promise.all([
      this.adapterInterface
        ? this.adapterInterface.cancelCurrentTask()
        : Promise.resolve(),
    ]);
  }
}

module.exports = RemoveOperation;
