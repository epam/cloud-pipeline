import {axios, createClient} from 'webdav';
import electron from 'electron';
import https from 'https';
import moment from 'moment-timezone';
import FileSystem from './file-system';
import {log, error} from '../log';
import * as utilities from './utilities';
import copyPingConfiguration from './copy-ping-configuration';
import requestStorageAccessApi from '../request-storage-access-api';

axios.defaults.adapter = require('axios/lib/adapters/http');

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
        maxWaitSeconds = copyPingConfiguration.maxWaitSeconds,
        pingTimeoutSeconds = copyPingConfiguration.pingTimeoutSeconds
      } = webdavClientConfig || {};
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
      resolve();
    });
  }

  getDirectoryContents(directory) {
    const checkStorageType = (contents, skip) => {
      if (skip) {
        return Promise.resolve(contents);
      }
      return new Promise((resolve) => {
        const result = (contents || []).slice();
        requestStorageAccessApi
          .initialize()
          .getStorages()
          .then((allStorages) => {
            const objectStorageNames = (allStorages || [])
              .filter(storage => !/^nfs$/i.test(storage.type))
              .map(storage => (storage.name || '').replace(/-/g, '_'));
            result.forEach(content => {
              if (content.isDirectory && objectStorageNames.includes(content.name)) {
                content.isObjectStorage = true;
              }
            });
          })
          .catch(() => {})
          .then(() => resolve(result));
      });
    };
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
          if (!directoryCorrected || !directoryCorrected.length) {
          }
          return checkStorageType(
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
              ),
            directoryCorrected && directoryCorrected.length > 0
          );
        })
        .then(resolve)
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
