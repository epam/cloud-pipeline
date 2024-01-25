/**
 * @typedef {Object} EventCallback
 * @property {string} event
 * @property {function(string, ...*)} callback
 */
import ipcEvent from './ipc-event';

const Events = {
  reload: 'reload',
  operation: 'operation',
};

class FileSystemEvents {
  constructor() {
    /**
     * @type {EventCallback[]}
     */
    this.listeners = [];
    const reloadFileSystemsCallback = (e, fileSystemIdentifiers) => this.emit(
      Events.reload,
      fileSystemIdentifiers,
    );
    const reportOperationCallback = (e, operation) => this.emit(
      Events.operation,
      operation,
    );
    ipcEvent('reloadFileSystemsCallback', reloadFileSystemsCallback);
    ipcEvent('reportOperationCallback', reportOperationCallback);
  }

  addEventListener(event, callback) {
    this.removeEventListener(event, callback);
    if (typeof callback === 'function' && event) {
      this.listeners.push({ event, callback });
    }
  }

  removeEventListener(event, callback) {
    this.listeners = this.listeners.filter(
      (aListener) => aListener.event !== event || aListener.callback !== callback,
    );
  }

  emit(event, ...options) {
    this.listeners
      .filter((aListener) => aListener.event === event)
      .forEach((aListener) => {
        aListener.callback(event, ...options);
      });
  }
}

const fileSystemEvents = new FileSystemEvents();
export { Events };
export default fileSystemEvents;
