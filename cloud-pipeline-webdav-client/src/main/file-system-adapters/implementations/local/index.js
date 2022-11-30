const os = require('os');
const FileSystemAdapter = require('../../base');
const Types = require('../../types');
const LocalFileSystemInterface = require('./interface');
const correctDirectoryPath = require('../../utils/correct-path');

class LocalFileSystemAdapter extends FileSystemAdapter {
  /**
   * @param {FileSystemAdapterOptions} options
   */
  constructor(options) {
    super({
      identifier: Types.local,
      ...(options || {}),
      type: Types.local,
    });
    this.isWindows = /^win/i.test(process.platform);
    this.root = os.homedir();
  }

  get parsingOptions() {
    const separator = this.getPathSeparator();
    return {
      leadingSlash: !this.isWindows,
      trailingSlash: true,
      separator,
    };
  }

  // eslint-disable-next-line class-methods-use-this
  getInitialDirectory() {
    return os.homedir();
  }

  // eslint-disable-next-line class-methods-use-this
  getPathSeparator() {
    return this.isWindows ? '\\' : '/';
  }

  // eslint-disable-next-line class-methods-use-this, no-unused-vars
  getPathComponents(element = '') {
    const separator = this.getPathSeparator();
    const rootName = (componentName) => (this.isWindows ? componentName : 'Root');
    const components = correctDirectoryPath(element, this.parsingOptions)
      .split(separator)
      .slice(0, -1);
    return components.map((aComponent, idx, array) => ({
      name: idx === 0 ? rootName(aComponent) : aComponent,
      path: correctDirectoryPath(
        array.slice(0, idx + 1).join(separator),
        this.parsingOptions,
      ),
      isCurrent: idx === array.length,
    }));
  }

  // eslint-disable-next-line class-methods-use-this
  async createInterface() {
    const localInterface = new LocalFileSystemInterface(this);
    await localInterface.initialize();
    return localInterface;
  }

  async useLastRequestOnlySession(sessionName, fn) {
    return this.useSession(fn);
  }
}

module.exports = LocalFileSystemAdapter;
