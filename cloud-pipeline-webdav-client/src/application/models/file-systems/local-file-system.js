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
}

export default LocalFileSystem;
