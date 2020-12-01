import {createClient} from 'webdav';
import electron from 'electron';
import https from 'https';
import moment from 'moment-timezone';
import FileSystem from './file-system';
import {log, error} from '../log';
import * as utilities from './utilities';

class WebdavFileSystem extends FileSystem {
  constructor() {
    let cfg;
    let settings;
    if (electron.remote === undefined) {
      cfg = global.webdavClient;
      settings = global.settings;
    } else {
      cfg = electron.remote.getGlobal('webdavClient');
      settings = electron.remote.getGlobal('settings');
    }
    const {config: webdavClientConfig} = cfg || {};
    const {name: appName} = settings || {};
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
    this.rootName = `${appName} Root`;
    this.appName = appName;
    this.separator = '/';
    log(`Initializing webdav client: URL ${server}; IGNORE CERTIFICATE ERRORS: ${ignoreCertificateErrors}; USER: ${username}`);
  }
  reInitialize() {
    return new Promise((resolve, reject) => {
      let cfg;
      let settings;
      if (electron.remote === undefined) {
        cfg = global.webdavClient;
        settings = global.settings;
      } else {
        cfg = electron.remote.getGlobal('webdavClient');
        settings = electron.remote.getGlobal('settings');
      }
      const {config: webdavClientConfig} = cfg || {};
      const {name: appName} = settings || {};
      this.appName = appName;
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
          this.rootName = `${appName} Root`;
          this.separator = '/';
          this.appName = appName;
          log(`Initializing webdav client: URL ${server}; IGNORE CERTIFICATE ERRORS: ${ignoreCertificateErrors}; USER: ${username}`);
          this.initialize()
            .then(resolve)
            .catch(reject);
        });
    });
  }
  initialize() {
    if (!this.root) {
      log(`Initializing webdav client ERROR: ${this.appName || 'Cloud Data'} server url not specified`);
      return Promise.reject(`${this.appName || 'Cloud Data'} server url not specified`);
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
        log('webdav client initialized');
      } catch (e) {
        error(e);
        utilities.rejectError(reject)(e);
      }
      resolve();
    });
  }

  getDirectoryContents(directory) {
    return new Promise((resolve, reject) => {
      const directoryCorrected = directory || '';
      if (!this.webdavClient) {
        error(`${this.appName || 'Cloud Data'} client was not initialized`);
        reject(`${this.appName || 'Cloud Data'} client was not initialized`);
      }
      const parentDirectory = this.joinPath(...this.parsePath(directoryCorrected).slice(0, -1));
      log(`webdav: fetching directory "${directoryCorrected}" contents...`);
      this.webdavClient.getDirectoryContents(directoryCorrected)
        .then(contents => {
          log(`webdav: fetching directory "${directoryCorrected}" contents: ${contents.length} results:`);
          contents.map(c => log(c.filename));
          log('');
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
                  .map(item => {
                    const isDirectory = /^directory$/i.test(item.type);
                    return {
                      name: item.basename,
                      path: item.filename,
                      isDirectory,
                      isFile: /^file/i.test(item.type),
                      isSymbolicLink: false,
                      size: isDirectory ? undefined : +(item.size),
                      changed: moment(item.lastmod)
                    };
                  })
              )
          );
        })
        .catch(
          utilities.rejectError(
            reject,
            directoryCorrected
              ? undefined
              : 'Typically, this means that you don\'t have any data storages available for remote access. Please contact the platform support to create them for you'
          )
        );
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
      return Promise.reject(`${this.appName || 'Cloud Data'} client was not initialized`);
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
        reject(`${this.appName || 'Cloud Data'} client was not initialized`);
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
        reject(`${this.appName || 'Cloud Data'} client was not initialized`);
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
        reject(`${this.appName || 'Cloud Data'} client was not initialized`);
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
            log(`Copying ${size} bytes to ${destinationPath}...`);
            this.watchCopyProgress(stream, callback, size);
            const writeStream = stream.pipe(this.webdavClient.createWriteStream(destinationPath));
            writeStream.on('finish', (e) => {
              log(`Copying ${size} bytes to ${destinationPath}: done`);
              setTimeout(resolve, 500, e);
            });
            writeStream.on('error', ({message}) => {
              error(message);
              reject(message);
            });
          })
          .catch(({message}) => {
            error(message);
            reject(message);
          });
      }
    });
  }
  remove(path) {
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject(`${this.appName || 'Cloud Data'} client was not initialized`);
      }
      log(`Removing ${path}...`);
      this.isDirectory(path)
        .then((isDirectory) => this.webdavClient.deleteFile(isDirectory ? path.concat('/') : path))
        .then(e => {
          log(`Removing ${path}: done`);
          resolve(e);
        })
        .catch(({message}) => {
          error(`Removing ${path} error: ${message}`);
          reject(message);
        });
    });
  }
  createDirectory(name) {
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject(`${this.appName || 'Cloud Data'} client was not initialized`);
      }
      log(`Creating directory ${name}...`);
      this.webdavClient.createDirectory(name)
        .then(() => {
          log(`Creating directory ${name}: done`);
          resolve();
        })
        .catch(e => {
          error(`Creating directory ${name} error: ${e.message || e}`);
          reject(e.message);
        });
    });
  }
}

export default WebdavFileSystem;
