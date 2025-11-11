const { select, confirm, isCancel } = require('@clack/prompts');
const getFlags = require('./get-flag');

module.exports = class CliDialog {
  constructor(args = []) {
    this.skip = getFlags(args, '--skip');
    this.force = getFlags(args, '--force', '-f', '-r');
  }
  /**
   *
   * @param {DialogOptions} options
   * @returns {Promise<boolean>}
   */
  async openDialog(options) {
    return false;
  }

  /**
   *
   * @param {Operation} operation
   */
  reportOperation(operation) {
    // noop
  }

  reloadFileSystems() {
    // noop
  }

  /**
   * @param {InputOptions} options
   * @returns {Promise<string>}
   */
  async inputDialog(options) {
    return '';
  }

  /**
   * @param {ConfirmOptions} options
   * @returns {Promise<{result: *, checked: boolean}>}
   */
  async confirm(options) {
    if (this.skip) {
      return {
        result: false,
        checked: true,
      };
    }
    if (this.force) {
      return {
        result: true,
        checked: true,
      };
    }
    if (options.checkboxText) {
      const result = await select({
        message: options.message,
        options: [
          {value: 'yes', label: options.ok || 'Yes' },
          {value: 'no', label: options.cancel || 'No' },
          {value: 'yes-checked', label: `${options.ok || 'Yes'} (${options.checkboxText})` },
          {value: 'no-checked', label: `${options.cancel || 'No'} (${options.checkboxText})` }
        ]
      });
      if (isCancel(result)) {
        process.exit(1);
      }
      return {
        result: /^yes($|-checked)/i.test(result),
        checked: /-checked$/i.test(result),
      };
    }
    const result = await confirm({
      message: options.message,
    });
    return {result, checked: false};
  }
};
