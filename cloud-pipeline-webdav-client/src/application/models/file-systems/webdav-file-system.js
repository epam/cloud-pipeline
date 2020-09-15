import {createClient} from 'webdav';
import electron from 'electron';
import https from 'https';
import FileSystem from './file-system';
import * as utilities from './utilities';

class WebdavFileSystem extends FileSystem {
  constructor() {
    let cfg;
    if (electron.remote === undefined) {
      cfg = global.webdavClient;
    } else {
      cfg = electron.remote.getGlobal('webdavClient');
    }
    const {config: webdavClientConfig} = cfg || {};
    const {
      server,
      username,
      password,
      certificates,
      ignoreCertificateErrors,
    } = webdavClientConfig || {};
    super(server);
    this.username = username;
    this.password = password;
    this.certificates = certificates;
    this.ignoreCertificateErrors = ignoreCertificateErrors;
    this.rootName = 'Cloud Data Root';
    this.separator = '/';
  }
  reInitialize() {
    return new Promise((resolve, reject) => {
      let cfg;
      if (electron.remote === undefined) {
        cfg = global.webdavClient;
      } else {
        cfg = electron.remote.getGlobal('webdavClient');
      }
      const {config: webdavClientConfig} = cfg || {};
      const {
        server,
        username,
        password,
        certificates,
        ignoreCertificateErrors,
      } = webdavClientConfig || {};
      super.reInitialize(server)
        .then(() => {
          this.username = username;
          this.password = password;
          this.certificates = certificates;
          this.ignoreCertificateErrors = ignoreCertificateErrors;
          this.rootName = 'Cloud Data Root';
          this.separator = '/';
          this.initialize()
            .then(resolve)
            .catch(reject);
        });
    });
  }
  initialize() {
    if (!this.root) {
      return Promise.reject('Cloud Data server url not specified');
    }
    return new Promise((resolve, reject) => {
      const options = {
        username: this.username,
        password: this.password,
      };
      if (this.certificates && this.certificates.length > 0) {
        https.globalAgent.options.ca = this.certificates.map(data => Buffer.from(data, 'base64'));
      }
      if (this.ignoreCertificateErrors) {
        https.globalAgent.options.rejectUnauthorized = false;
      }
      try {
        this.webdavClient = createClient(this.root, options);
      } catch (e) {
        utilities.rejectError(reject)(e);
      }
      resolve();
    });
  }

  getDirectoryContents(directory) {
    return new Promise((resolve, reject) => {
      const directoryCorrected = directory || '';
      if (!this.webdavClient) {
        reject('Cloud Data client was not initialized');
      }
      const parentDirectory = this.joinPath(...this.parsePath(directoryCorrected).slice(0, -1));
      this.webdavClient.getDirectoryContents(directoryCorrected)
        .then(contents => {
          resolve(
            (
              directoryCorrected === ''
                ? []
                : [{
                  name: '..',
                  path: parentDirectory,
                  isDirectory: true,
                  isFile: false,
                  isSymbolicLink: false,
                  isBackLink: true,
                }]
            )
              .concat(
                contents
                  .map(item => ({
                    name: item.basename,
                    path: item.filename,
                    isDirectory: /^directory$/i.test(item.type),
                    isFile: /^file/i.test(item.type),
                    isSymbolicLink: false
                  }))
                  .sort(utilities.sorters.nameSorter)
                  .sort(utilities.sorters.elementTypeSorter)
              )
          );
        })
        .catch(utilities.rejectError(reject));
    });
  }
  parsePath (directory, relativeToRoot = false) {
    const parts = (directory || '').split('/').filter(Boolean);
    if (relativeToRoot) {
      const rootParts = (this.root || '').split('/').filter(Boolean);
      let idx = -1;
      for (let i = 0; i < Math.min(rootParts.length, parts.length); i++) {
        if (rootParts[i] !== parts[i]) {
          break;
        }
        idx = i;
      }
      if (idx >= 0) {
        return parts.slice(idx + 1);
      }
    }
    return parts;
  }
  joinPath (...parts) {
    const path = (parts || []).join('/');
    if (path.toLowerCase().indexOf((this.root || '').toLowerCase()) === 0) {
      return path.substr((this.root || '').length);
    }
    return path;
  }
  getItemType (path) {
    if (!path) {
      return Promise.reject('Path not specified');
    }
    if (!this.webdavClient) {
      return Promise.reject('Cloud Data client was not initialized');
    }
    return new Promise((resolve, reject) => {
      const pathCorrected = path.endsWith('/') ? path.substr(0, path.length - 1) : path;
      const filePath = pathCorrected;
      const dirPath = pathCorrected.concat('/');
      const getTypeSafe = (o) => new Promise((r) => {
        this.webdavClient.stat(o)
          .then(({type}) => r(type))
          .catch(() => r(undefined));
      });
      Promise.all([getTypeSafe(filePath), getTypeSafe(dirPath)])
        .then((results) => {
          const [fileType, dirType] = results;
          const type = fileType || dirType;
          resolve(type);
        })
        .catch(({message}) => reject(message));
    });
  }
  isDirectory (path) {
    return new Promise((resolve, reject) => {
      this.getItemType(path)
        .then(type => resolve(/^directory$/i.test(type)))
        .catch(reject);
    });
  }
  buildSources(item) {
    const parts = this.parsePath(item);
    parts.pop();
    const mapper = (child) => {
      const itemParts = this.parsePath(child);
      let idx = 0;
      while (itemParts.length > 0 && idx < parts.length && itemParts[0] === parts[idx]) {
        idx += 1;
        itemParts.shift();
      }
      return {
        path: child,
        name: this.joinPath(...itemParts),
      }
    };
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject('Cloud Data client was not initialized');
      }
      this.webdavClient.getDirectoryContents(item || '')
        .then(contents => {
          resolve(
            contents
              .map(item => ({
                path: item.filename,
                isDirectory: /^directory$/i.test(item.type)
              }))
              .filter(o => !o.isDirectory)
              .map(o => mapper(o.path))
          )
        })
        .catch(() => resolve([item].map(mapper)));
    });
  }
  getContentsStream(path) {
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject('Cloud Data client was not initialized');
      }
      const pathCorrected = path || '';
      this.webdavClient.stat(pathCorrected)
        .then(({size}) => {
          const stream = this.webdavClient.createReadStream(pathCorrected);
          resolve({stream, size});
        })
        .catch(({message}) => reject(message));
    });
  }
  copy(stream, destinationPath, callback, size) {
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject('Cloud Data client was not initialized');
      } else {
        const parentDirectory = this.joinPath(...this.parsePath(destinationPath).slice(0, -1));
        const createDirectorySafe = async () => {
          try {
            if (await this.webdavClient.exists(parentDirectory) === false) {
              await this.webdavClient.createDirectory(parentDirectory);
            }
          } catch (_) {}
        };
        createDirectorySafe()
          .then(() => {
            this.watchCopyProgress(stream, callback, size);
            const writeStream = stream.pipe(this.webdavClient.createWriteStream(destinationPath));
            writeStream.on('finish', resolve);
            writeStream.on('error', ({message}) => reject(message));
          })
          .catch(({message}) => reject(message));
      }
    });
  }
  remove(path) {
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject('Cloud Data client was not initialized');
      }
      this.isDirectory(path)
        .then((isDirectory) => this.webdavClient.deleteFile(isDirectory ? path.concat('/') : path))
        .then(resolve)
        .catch(({message}) => reject(message));
    });
  }
  createDirectory(name) {
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject('Cloud Data client was not initialized');
      }
      this.webdavClient.createDirectory(name)
        .then(() => resolve())
        .catch(e => reject(e.message));
    });
  }
}

export default WebdavFileSystem;
