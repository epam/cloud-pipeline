import {createClient} from 'webdav';
import electron from 'electron';
import FileSystem from './file-system';
import * as utilities from './utilities';
import path from "path";
import fs from "fs";

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
    } = webdavClientConfig || {};
    super(server);
    this.username = username;
    this.password = password;
    this.rootName = '/';
  }
  initialize() {
    if (!this.root) {
      return Promise.reject('Webdav server url not specified');
    }
    return new Promise((resolve, reject) => {
      const options = {
        username: this.username,
        password: this.password
      };
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
        reject('WebDav client was not initialized');
      }
      this.webdavClient.getDirectoryContents(directoryCorrected)
        .then(contents => {
          resolve(
            (
              directoryCorrected === ''
                ? []
                : [{
                  name: '..',
                  path: undefined,
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
  parsePath (directory) {
    return (directory || '').split('/').filter(Boolean);
  }
  joinPath (...parts) {
    return (parts || []).join('/');
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
        name: this.joinPath(itemParts),
      }
    };
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject('WebDav client was not initialized');
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
        reject('WebDav client was not initialized');
      }
      const pathCorrected = path || '';
      try {
        resolve(this.webdavClient.createReadStream(pathCorrected));
      } catch (e) {
        reject({message: e.message});
      }
    });
  }
  copy(stream, destinationPath, callback) {
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject('WebDav client was not initialized');
      }
      // const parentDirectory = path.dirname(destinationPath);// todo:
      // todo: make directory
      const writeStream = stream.pipe(this.webdavClient.createWriteStream(destinationPath));
      writeStream.on('finish', resolve);
      writeStream.on('error', ({message}) => reject(message));
    });
  }
  remove(path) {
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject('WebDav client was not initialized');
      }
      this.webdavClient.deleteFile(path)
        .then(resolve)
        .catch(({message}) => reject(message))
    });
  }
  createDirectory(name) {
    return new Promise((resolve, reject) => {
      if (!this.webdavClient) {
        reject('WebDav client was not initialized');
      }
      this.webdavClient.createDirectory(name)
        .then(() => resolve())
        .catch(e => reject(e.message));
    });
  }
}

export default WebdavFileSystem;
