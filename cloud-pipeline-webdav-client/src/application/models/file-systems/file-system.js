import path from 'path';

class FileSystem {
  constructor(root) {
    this.root = root;
    this.separator = path.sep;
    this.rootName = root === this.separator ? 'Root' : root;
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
  parsePath (directory) {
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
    return Promise.resolve(null);
  }
  copy(stream, destinationPath, callback) {
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
