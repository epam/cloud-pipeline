// eslint-disable-next-line max-classes-per-file
class FileSystemAdapterError extends Error {
  // eslint-disable-next-line no-useless-constructor
  constructor(message) {
    super(message);
  }
}
class FileSystemAdapterInitializeError extends FileSystemAdapterError {
  constructor(message) {
    const header = 'Cannot initialize file system adapter';
    super(message ? `${header}: ${message}` : header);
  }
}

module.exports = {
  FileSystemAdapterError,
  FileSystemAdapterInitializeError,
};
