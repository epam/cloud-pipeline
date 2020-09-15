import fs from 'fs';
import path from 'path';
import moment from 'moment-timezone';
import FileSystem from './file-system';
import * as utilities from './utilities';

class LocalFileSystem extends FileSystem {
  constructor(root) {
    super(root || require('os').homedir());
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
                    if (!isDirectory) {
                      const stat = fs.statSync(path.resolve(absolutePath, item.name));
                      size = +(stat.size);
                      changed = moment(stat.ctime)
                    }
                    return {
                      name: item.name,
                      path: path.resolve(absolutePath, item.name),
                      isDirectory,
                      isFile: item.isFile(),
                      isSymbolicLink: item.isSymbolicLink(),
                      size,
                      changed
                    };
                  })
                  .sort(utilities.sorters.nameSorter)
                  .sort(utilities.sorters.elementTypeSorter)
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
  getContentsStream(path) {
    return new Promise((resolve, reject) => {
      try {
        const stat = fs.statSync(path);
        resolve({
          stream: fs.createReadStream(path),
          size: +stat.size,
        });
      } catch (e) {
        reject(e.message);
      }
    });
  }
  copy(stream, destinationPath, callback, size) {
    return new Promise((resolve, reject) => {
      const parentDirectory = path.dirname(destinationPath);
      if (!fs.existsSync(parentDirectory)) {
        fs.mkdirSync(parentDirectory, {recursive: true});
      }
      this.watchCopyProgress(stream, callback, size);
      const writeStream = stream.pipe(fs.createWriteStream(destinationPath));
      writeStream.on('finish', resolve);
      writeStream.on('error', ({message}) => reject(message));
    });
  }
  remove(path) {
    return new Promise((resolve, reject) => {
      try {
        if (fs.existsSync(path)) {
          if (fs.lstatSync(path).isDirectory()) {
            console.log('removing directory', path);
            fs.rmdirSync(path, {recursive: true});
            resolve();
          } else {
            console.log('removing file', path);
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
}

export default LocalFileSystem;
