import path from 'path';

let identifier = 0;

class FileSystem {
  constructor(root) {
    identifier += 1;
    this.identifier = identifier;
    this.root = root;
    this.separator = path.sep;
    this.rootName = root === this.separator ? 'Root' : root;
  }

  reInitialize(root) {
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
  buildDestination (directory) {
    return directory || this.root || '';
  }
  getContentsStream(path) {
    return Promise.resolve({stream: null, size: 0});
  }
  watchCopyProgress(stream, callback, size) {
    if (size > 0 && callback) {
      let transferred = 0;
      stream.on('data', (buffer) => {
        transferred += buffer.length;
        const percentage = Math.round(
          Math.min(
            100,
            Math.max(
              0,
              transferred / size * 100.0
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
}

export default FileSystem;
