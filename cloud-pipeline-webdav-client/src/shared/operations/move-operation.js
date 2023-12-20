const Operation = require('./base');
const CopyOperation = require('./copy-operation');

class MoveOperation extends CopyOperation {
  /**
   * @param {CopyOperationOptions & OperationOptions} options
   */
  constructor(options) {
    super(options);
    const {
      elements = [],
    } = options;
    this.stageDuration[Operation.Stages.after] = (elements || []).length;
  }

  get name() {
    return `Move operation: ${this.getFileSystemIdentifier(this.source)} -> ${this.getFileSystemIdentifier(this.destination)}`;
  }

  // eslint-disable-next-line class-methods-use-this
  get operationName() {
    return 'move operation';
  }

  // eslint-disable-next-line class-methods-use-this
  get operationType() {
    return 'move';
  }

  async removeItem(element) {
    if (await this.isAborted()) {
      return;
    }
    if (!this.sourceAdapterInterface) {
      return;
    }
    this.info(`Removing "${element}"`);
    await this.sourceAdapterInterface.remove(element);
  }

  async after() {
    await super.after();
    await this.reInitializeInterfaces();
    return this.iterate(
      this.retry.bind(this, this.removeItem.bind(this)),
      this.elements,
      { stage: Operation.Stages.after },
    );
  }
}

module.exports = MoveOperation;
