import fs from 'fs';
import path from 'path';
import moment from 'moment-timezone';
import FileSystem from './file-system';
import {log, error} from '../log';
import * as utilities from './utilities';

class LocalFileSystem extends FileSystem {
  constructor(root) {
    super(root || path.parse(__dirname).root);
  }

  reInitialize() {
    return super.reInitialize(this.root);
  }

  getDirectoryContents(directory) {
    return new Promise((resolve, reject) => {
      const directoryCorrected = directory || '';
      const absolutePath = path.isAbsolute(directoryCorrected)
        ? directoryCorrected
        : path.resolve(this.root, directoryCorrected || '');
      const parentAbsolutePath = path.dirname(absolutePath);
      fs.promises.readdir(absolutePath, {withFileTypes: true})
        .then((results) => {
          resolve(
            (
              parentAbsolutePath === absolutePath
                ? []
                : [{
                  name: '..',
                  path: parentAbsolutePath,
                  isDirectory: true,
                  isFile: false,
                  isSymbolicLink: false,
                  isBackLink: true,
                }]
            )
              .concat(
                results
                  .map(item => {
                    let size;
                    let changed;
                    const isDirectory = item.isDirectory();
                    const fullPath = path.resolve(absolutePath, item.name);
                    if (!isDirectory && fs.existsSync(fullPath)) {
                      const stat = fs.statSync(fullPath);
                      size = +(stat.size);
                      changed = moment(stat.ctime)
                    }
                    return {
                      name: item.name,
                      path: fullPath,
                      isDirectory,
                      isFile: item.isFile(),
                      isSymbolicLink: item.isSymbolicLink(),
                      size,
                      changed
                    };
                  })
              )
          );
        })
        .catch(utilities.rejectError(reject));
    });
  }
  parsePath(directory, relativeToRoot = false) {
    if (!relativeToRoot) {
      return super.parsePath(directory, relativeToRoot);
    }
    return super.parsePath(path.relative(this.root, directory || this.root), false);
  }

  joinPath(...parts) {
    return path.join(...parts);
  }
  buildSources (item) {
    const parentDirectory = path.dirname(item);
    const mapper = (child) => ({
      path: child,
      name: path.relative(parentDirectory, child),
    });
    return Promise.resolve(utilities.getDirectoryFiles(item).map(mapper));
  }
  async buildDestination(directory) {
    if (fs.existsSync(directory)) {
      if (fs.lstatSync(directory).isDirectory()) {
        return super.buildDestination(directory);
      } else {
        return super.buildDestination(path.dirname(directory));
      }
    }
  }
  getContentsStream(path) {
    return new Promise((resolve, reject) => {
      try {
        const stat = fs.statSync(path);
        resolve({
          stream: fs.createReadStream(path),
          size: +stat.size,
        });
      } catch (e) {
        reject(e);
      }
    });
  }
  copy(stream, destinationPath, callback, size) {
    return new Promise((resolve, reject) => {
      const parentDirectory = path.dirname(destinationPath);
      if (!fs.existsSync(parentDirectory)) {
        log(`Creating directory ${parentDirectory}...`);
        try {
          fs.mkdirSync(parentDirectory, {recursive: true});
        } catch (e) {
          error(e);
          reject(e);
          return;
        }
        log(`Creating directory ${parentDirectory}: done.`);
      }
      log(`Copying ${size} bytes to ${destinationPath}...`);
      this.watchCopyProgress(stream, callback, size);
      const writeStream = stream.pipe(fs.createWriteStream(destinationPath));
      writeStream.on('finish', (e) => {
        log(`Copying ${size} bytes to ${destinationPath}: done`);
        resolve(e);
      });
      writeStream.on('error', ({message}) => {
        error(message);
        reject(message);
      });
    });
  }
  remove(path) {
    return new Promise((resolve, reject) => {
      try {
        if (fs.existsSync(path)) {
          if (fs.lstatSync(path).isDirectory()) {
            log(`Removing directory ${path}`);
            fs.rmdirSync(path, {recursive: true});
            resolve();
          } else {
            log(`Removing file ${path}`);
            fs.unlinkSync(path);
            resolve();
          }
        }
      } catch (e) {
        reject(e.message);
      }
    });
  }
  createDirectory(name) {
    return new Promise((resolve, reject) => {
      try {
        fs.mkdirSync(name);
        resolve();
      } catch (e) {
        reject(e.message);
      }
    });
  }
  pathExists(path) {
    return new Promise((resolve) => {
      try {
        const exists = fs.existsSync(path);
        resolve(exists);
      } catch (e) {
        resolve(false);
      }
    });
  }
}

export default LocalFileSystem;
