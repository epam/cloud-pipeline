import fs from 'fs';
import path from 'path';
import FileSystem from './file-system';
import * as utilities from './utilities';

class LocalFileSystem extends FileSystem {
  constructor(root) {
    super(root || path.parse(__dirname).root);
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
                  .map(item => ({
                    name: item.name,
                    path: path.resolve(absolutePath, item.name),
                    isDirectory: item.isDirectory(),
                    isFile: item.isFile(),
                    isSymbolicLink: item.isSymbolicLink()
                  }))
                  .sort(utilities.sorters.nameSorter)
                  .sort(utilities.sorters.elementTypeSorter)
              )
          );
        })
        .catch(utilities.rejectError(reject));
    });
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
    return Promise.resolve(fs.createReadStream(path))
  }
  copy(stream, destinationPath, callback) {
    return new Promise((resolve, reject) => {
      const parentDirectory = path.dirname(destinationPath);
      if (!fs.existsSync(parentDirectory)) {
        fs.mkdirSync(parentDirectory, {recursive: true});
      }
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
}

export default LocalFileSystem;
