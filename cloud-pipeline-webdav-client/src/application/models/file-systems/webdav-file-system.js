import {axios, createClient} from 'webdav';
import electron from 'electron';
import https from 'https';
import moment from 'moment-timezone';
import FileSystem from './file-system';
import {log, error} from '../log';
import * as utilities from './utilities';
import copyPingConfiguration from './copy-ping-configuration';
import cloudPipelineAPI from '../cloud-pipeline-api';

axios.defaults.adapter = require('axios/lib/adapters/http');

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
      name: appName,
      server,
      username,
      password,
      certificates,
      ignoreCertificateErrors,
      maxWaitSeconds = copyPingConfiguration.maxWaitSeconds,
      pingTimeoutSeconds = copyPingConfiguration.pingTimeoutSeconds,
      api
    } = webdavClientConfig || {};
    super(server, {maxWait: maxWaitSeconds, ping: pingTimeoutSeconds});
    this.username = username;
    this.password = password;
    this.certificates = certificates;
    this.ignoreCertificateErrors = ignoreCertificateErrors;
    this.rootName = `${appName} Root`;
    this.appName = appName;
    this.separator = '/';
    this.api = api;
    log(`Initializing webdav client: URL ${server}; IGNORE CERTIFICATE ERRORS: ${ignoreCertificateErrors}; USER: ${username}`);
    if (this.pingAfterCopy) {
      log(`Webdav client ping after copy operation config: ping ${pingTimeoutSeconds}sec. for ${maxWaitSeconds}sec.`);
    } else {
      log(`Webdav client ping after copy operation config: no ping (parameters: ${JSON.stringify({maxWaitSeconds, pingTimeoutSeconds})})`);
    }
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
        name: appName,
        server,
        username,
        password,
        certificates,
        ignoreCertificateErrors,
        maxWaitSeconds = copyPingConfiguration.maxWaitSeconds,
        pingTimeoutSeconds = copyPingConfiguration.pingTimeoutSeconds
      } = webdavClientConfig || {};
      this.appName = appName;
      super.reInitialize(server, {maxWait: maxWaitSeconds, ping: pingTimeoutSeconds})
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
      this.updateStorages()
        .then(resolve);
    });
  }

  updateStorages(skip = false) {
    if (skip) {
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      cloudPipelineAPI
        .initialize()
        .getStorages()
        .then((storages) => {
          this.storages = storages;
          resolve();
        });
    });
  }

  getDirectoryContents(directory) {
    return new Promise((resolve, reject) => {
      this.updateStorages(directory && directory.length)
        .then(() => {
          const directoryCorrected = directory || '';
          if (!this.webdavClient) {
            error(`${this.appName || 'Cloud Data'} client was not initialized`);
            reject(`${this.appName || 'Cloud Data'} client was not initialized`);
          }
          const parentDirectory = this.joinPath(...this.parsePath(directoryCorrected).slice(0, -1));
          log(`webdav: fetching directory "${directoryCorrected}" contents...`);
          const formatStorageName = storage => (storage.name || '').replace(/-/g, '_');
          const goBackItem = {
            name: '..',
            path: parentDirectory,
            isDirectory: true,
            isFile: false,
            isSymbolicLink: false,
            isBackLink: true,
          };
          const checkStorageType = (contents) => {
            const result = (contents || []).slice();
            const allStorages = (this.storages || []);
            const isHiddenStorage = name => {
              const currentStorage = allStorages.find(o => name && formatStorageName(o).toLowerCase() === name.toLowerCase());
              return currentStorage && currentStorage.hidden;
            }
            let [p1, p2] = (directory || '').split('/');
            const rootDir = p1 || p2;
            if (isHiddenStorage(rootDir)) {
              throw new Error('Access denied');
            }
            const objectStorageNames = (allStorages || [])
              .filter(storage => !/^nfs$/i.test(storage.type))
              .map(formatStorageName);
            result.forEach(content => {
              if (
                (!directory || !directory.length) &&
                content.isDirectory &&
                objectStorageNames.includes(content.name)
              ) {
                content.isObjectStorage = true;
              }
            });
            return result.filter(o => !isHiddenStorage(o.name));
          };
          this.webdavClient.getDirectoryContents(directoryCorrected)
            .then(contents => {
              log(`webdav: fetching directory "${directoryCorrected}" contents: ${contents.length} results:`);
              contents.map(c => log(c.filename));
              log('');
              if (!directoryCorrected || !directoryCorrected.length) {
              }
              resolve(
                checkStorageType(
                  (
                    directoryCorrected === ''
                      ? []
                      : [goBackItem]
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
    const getContents = (root) => new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject(`${this.appName || 'Cloud Data'} client was not initialized`);
      }
      this.isDirectory(root)
        .then(isDirectory => {
          if (!isDirectory) {
            return Promise.resolve([{filename: root, type: 'file'}]);
          }
          return this.webdavClient.getDirectoryContents(root || '');
        })
        .then(contents => {
          const mapped = contents
            .map(o => ({
              path: o.filename,
              isDirectory: /^directory$/i.test(o.type)
            }));
          const files = mapped.filter(o => !o.isDirectory).map(o => mapper(o.path));
          const directories = mapped.filter(o => o.isDirectory);
          return Promise.all([
            ...files.map(file => Promise.resolve([file])),
            ...directories.map(directory => getContents(directory.path))
          ]);
        })
        .then(contents => resolve(contents.reduce((r, c) => ([...r, ...c]), [])))
        .catch((e) => {
          log(`error fetching directory contents "${root}": ${e.message}`);
          resolve([]);
        });
    });
    return getContents(item);
  }
  async buildDestination(directory) {
    const isDirectory = await this.isDirectory(directory);
    if (!isDirectory) {
      const parentDirectory = this.joinPath(...this.parsePath(directory).slice(0, -1));
      return super.buildDestination(parentDirectory);
    }
    return super.buildDestination(directory);
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
        const createDirectorySafe = async (aDir) => {
          if (!aDir || !aDir.length) {
            return;
          }
          await createDirectorySafe(this.joinPath(...this.parsePath(aDir).slice(0, -1)));
          try {
            if (await this.webdavClient.exists(aDir) === false) {
              log('creating directory', aDir);
              await this.webdavClient.createDirectory(aDir);
            }
          } catch (_) {
            log(`error creating directory: ${_.message}`);
          }
        };
        createDirectorySafe(parentDirectory)
          .then(() => {
            log(`Copying ${size} bytes to ${destinationPath}...`);
            this.watchCopyProgress(stream, callback, size, 99);
            const writeStream = stream.pipe(
              this.webdavClient.createWriteStream(
                destinationPath,
                {
                  maxContentLength: Infinity
                }
              )
            );
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
  pathExists(path) {
    return new Promise(resolve => {
      this.getItemType(path)
        .then((type) => resolve(!!type))
        .catch(() => resolve(false));
    });
  }

  async safelyStopLogging () {
    try {
      const result = await electron.remote.netLog.stopLogging();
      return result;
    } catch (e) {
      error(e);
      return undefined;
    }
  }

  diagnose(options, callback) {
    const logCallback = o => {
      log(o);
      if (callback) {
        callback(o);
      }
    };
    return new Promise(async (resolve) => {
      const diagnoseResult = {};
      try {
        axios.defaults.adapter = require('axios/lib/adapters/xhr');
        const {
          ignoreCertificateErrors,
          testWebdav = false,
          testApi = false
        } = options || {};
        if (ignoreCertificateErrors) {
          https.globalAgent.options.rejectUnauthorized = false;
        }
        let parts = [
          testWebdav ? 'webdav' : undefined,
          testApi ? 'api' : undefined
        ].filter(Boolean).join('-');
        if (parts) {
          parts = '-'.concat(parts);
        }
        const path = electron.remote.getGlobal('networkLogFile')
          .replace('[DATE]', moment().format('YYYY-MM-DD-HH-mm-ss').concat(parts))
        await electron.remote.netLog.startLogging(path);
        if (testWebdav) {
          diagnoseResult.webdavError = await this.diagnoseWebdav(options, logCallback);
        }
        if (testApi) {
          diagnoseResult.apiError = await this.diagnoseAPI(options, logCallback);
        }
      } catch (e) {
        diagnoseResult.error = e.message;
        logCallback(`Error: ${e.message}`);
      } finally {
        diagnoseResult.filePath = await this.safelyStopLogging();
        if (this.ignoreCertificateErrors) {
          https.globalAgent.options.rejectUnauthorized = false;
        }
        diagnoseResult.error = diagnoseResult.error || diagnoseResult.webdavError || diagnoseResult.apiError;
        axios.defaults.adapter = require('axios/lib/adapters/http');
        resolve(diagnoseResult);
      }
    });
  }

  async apiGetRequest (endpoint, api) {
    api = api || this.api || '';
    if (api.endsWith('/')) {
      api = api.slice(0, -1);
    }
    await fetch(`${api}/${endpoint}`, {
      headers: {
        "Authorization": `Bearer ${this.password}`,
        "Content-type": "application/json",
        "Accept": "application/json",
        "Accept-Charset": "utf-8"
      }
    });
  }

  diagnoseAPI(options, callback) {
    const logCallback = callback || (() => {});
    return new Promise(async (resolve) => {
      console.log(this.api);
      try {
        const {api} = options;
        if (api) {
          logCallback('Testing API...');
          await this.apiGetRequest('whoami', api);
        } else {
          logCallback('No API endpoint');
        }
        resolve();
      } catch (e) {
        resolve(e.message);
      }
    });
  }

  diagnoseWebdav(options, callback) {
    const logCallback = callback || (() => {});
    return new Promise(async (resolve) => {
      try {
        const {
          server,
          password,
          username,
          ignoreCertificateErrors
        } = options || {};
        log(`Testing webdav connection: ${server}; user ${username}`);
        const client = createClient(server, {username, password});
        logCallback('Fetching webdav directory contents...');
        log('Testing webdav connection: fetching directory contents...');
        await client.getDirectoryContents('');
        log('Testing webdav connection: directory contents received');
        resolve();
      } catch (e) {
        error(`Testing webdav connection: ${e.message}`);
        resolve(e.message);
      }
    });
  }
}

export default WebdavFileSystem;
