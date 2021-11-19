import LocalFileSystem from './local-file-system';
import WebdavFileSystem from './webdav-file-system';

const FileSystems = {
  local: 'local',
  webdav: 'webdav'
}

function initializeFileSystem (type) {
  switch (type) {
    case FileSystems.local: return new LocalFileSystem();
    case FileSystems.webdav: return new WebdavFileSystem();
  }
  return undefined;
}

export {FileSystems, initializeFileSystem};
