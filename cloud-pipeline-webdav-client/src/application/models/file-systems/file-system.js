import path from 'path';

class FileSystem {
  constructor(root) {
    this.root = root;
    this.separator = path.sep;
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
}

export default FileSystem;
