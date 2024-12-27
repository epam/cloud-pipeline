const FileSystemAdapter = require('./base');
const LocalFileSystemAdapter = require('./implementations/local');
const WebdavFileSystemAdapter = require('./implementations/webdav');
const FTPFileSystemAdapter = require('./implementations/ftp');
const { FileSystemAdapterInitializeError } = require('./errors');

/**
 * @param {FileSystemAdapterOptions} options
 * @returns {LocalFileSystemAdapter|WebDAVAdapter|FTPAdapter}
 */
module.exports = function initializeAdapter(options) {
  let Constructor;
  switch (options?.type) {
    case FileSystemAdapter.Types.local:
      Constructor = LocalFileSystemAdapter;
      break;
    case FileSystemAdapter.Types.webdav:
      Constructor = WebdavFileSystemAdapter;
      break;
    case FileSystemAdapter.Types.ftp:
      Constructor = FTPFileSystemAdapter;
      break;
    default:
      break;
  }
  if (!Constructor) {
    throw new FileSystemAdapterInitializeError(`type is not specified or unknown: ${options?.type || '<empty>'}`);
  }
  /**
   * @type {FileSystemAdapter}
   */
  return new Constructor(options);
};
