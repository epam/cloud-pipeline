import path from 'path';
import {log, error} from '../log';

let identifier = 0;

class FileSystem {
  constructor(root, copyPingOptions = {}) {
    const {
      maxWait,
      ping
    } = copyPingOptions;
    this.maxWaitSeconds = maxWait;
    this.pingTimeoutSeconds = ping;
    this.pingAfterCopy = !Number.isNaN(Number(maxWait)) &&
      !Number.isNaN(Number(ping)) &&
      Number(maxWait) > 0;
    identifier += 1;
    this.identifier = identifier;
    this.root = root;
    this.separator = path.sep;
    this.rootName = root === this.separator ? 'Root' : root;
  }

  reInitialize(root, copyPingOptions = {}) {
    this.maxWaitSeconds = maxWait;
    this.pingTimeoutSeconds = ping;
    this.pingAfterCopy = !Number.isNaN(Number(maxWait)) &&
      !Number.isNaN(Number(ping)) &&
      Number(maxWait) > 0;
    this.root = root;
    this.separator = path.sep;
    this.rootName = root === this.separator ? 'Root' : root;
    return Promise.resolve();
  }
  initialize () {
    return Promise.resolve();
  }
  close () {
    return Promise.resolve();
  }
  getDirectoryContents (directory) {
    return new Promise((resolve) => resolve([]));
  }
  parsePath (directory, relativeToRoot = false) {
    return (directory || '').split(this.separator);
  }
  joinPath (...parts) {
    return (parts || []).join(this.separator);
  }
  buildSources (item) {
    return Promise.resolve([item]);
  }
  async buildDestination (directory) {
    return directory || this.root || '';
  }
  getContentsStream(path) {
    return Promise.resolve({stream: null, size: 0});
  }
  watchCopyProgress(stream, callback, size, percentMax = 100) {
    if (size > 0 && callback) {
      let transferred = 0;
      stream.on('data', (buffer) => {
        transferred += buffer.length;
        const percentage = Math.round(
          Math.min(
            100,
            Math.max(
              0,
              transferred / size * percentMax
            )
          )
        );
        callback && callback(percentage);
      });
    }
  }
  copy(stream, destinationPath, callback, size) {
    return Promise.resolve();
  }
  remove(path) {
    return Promise.resolve();
  }
  createDirectory(name) {
    return Promise.resolve();
  }
  pathExists(path) {
    return Promise.resolve(true);
  }
  updateRemotePermissions(...path) {
    // empty
  }
  ensurePathExists(destinationPath) {
    if (this.pingAfterCopy) {
      const max = Number(this.maxWaitSeconds);
      const ping = Number(this.pingTimeoutSeconds);
      if (!Number.isNaN(max) && max > 0) {
        const now = performance.now();
        return new Promise((resolve, reject) => {
          const check = () => {
            const duration = (performance.now() - now) / 1000.0;
            if (duration > max) {
              error(`${destinationPath}: check file existence exceeded timeout ${max.toFixed(2)}sec. Assuming ${destinationPath} not exists`);
              reject(new Error(`Copied file not found at ${destinationPath}`));
            } else {
              log(`Checking if ${destinationPath} exists...`);
              this.pathExists(destinationPath)
                .then((exists) => {
                  log(`${destinationPath}: ${exists ? 'exists' : 'not exists'}`);
                  if (exists) {
                    resolve();
                  } else {
                    log(`${destinationPath}: next check after ${(ping).toFixed(2)}sec.`);
                    setTimeout(check, ping * 1000);
                  }
                });
            }
          };
          check();
        });
      }
    }
    return Promise.resolve();
  }

  diagnose () {
    return Promise.resolve();
  }
}

export default FileSystem;
