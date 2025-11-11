const fs = require('fs');
const path = require('path');
const sharedLogger = require('../../shared/shared-logger');
const adapterTypes = require('../../shared/file-system-adapters/types');

/**
 * @param {string} itemPath
 * @param {FileSystemAdapter[]} adapters
 * @returns {Promise<{path: string, adapter: FileSystemAdapter}>}
 */
module.exports = async function getPathInfo(itemPath, adapters = []) {
  const webdav = adapters.find((a) => a.type === adapterTypes.webdav);
  const local = adapters.find((a) => a.type === adapterTypes.local);
  if (/^dav:\/\//i.test(itemPath) && webdav) {
    const relative = itemPath.slice('dav:/'.length);
    return {
      adapter: webdav,
      path: relative === '/' ? '' : relative,
    };
  }
  if (/^ftp:\/\//i.test(itemPath)) {
    const [, root] = /^ftp:\/\/([^\/]+)\//.exec(itemPath) ?? [];
    const ftpAdapter = adapters.find((a) => root && a.type === adapterTypes.ftp && (a.url || '').toLowerCase() === root.toLowerCase());
    if (!ftpAdapter || !root) {
      sharedLogger.error(`Ftp adapter not found for path: ${itemPath}`);
      throw new Error(`Ftp adapter not found for path: ${itemPath}`);
    }
    const relative = itemPath.slice('ftp://'.length + root.length);
    return {
      adapter: ftpAdapter,
      path: relative,
    };
  }
  if (local) {
    return {
      adapter: local,
      path: path.resolve(process.cwd(), itemPath).concat(itemPath === '.' || itemPath === './' ? '/' : ''),
    };
  }
  sharedLogger.error(`Adapter not found for path: ${itemPath}`);
  throw new Error(`Adapter not found for path: ${itemPath}`);
}
