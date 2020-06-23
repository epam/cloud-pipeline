import {createClient} from 'webdav';
import electron from 'electron';
import FileSystem from './file-system';
import * as utilities from './utilities';

class WebdavFileSystem extends FileSystem {
  constructor() {
    const {config: webdavClientConfig} = electron.remote.getGlobal('webdavClient') || {};
    const {
      server,
      username,
      password,
    } = webdavClientConfig || {};
    super(server);
    this.username = username;
    this.password = password;
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
}

export default WebdavFileSystem;
