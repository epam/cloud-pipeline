import Operation from './operation';
import createDirectoryNameDialog from '../../../create-directory-name-dialog';
import {initializeFileSystemAdapter} from "./utilities";

class CreateDirectoryOperation extends Operation {
  constructor(mainWindow, fileSystem, parentDirectory) {
    super(mainWindow);
    this.parent = parentDirectory;
    this.fileSystem = fileSystem;
  }
  preprocess() {
    return new Promise((resolve, reject) => {
      super.preprocess()
        .then(() => {
          this.reportProgress('Creating directory...');
          initializeFileSystemAdapter(this.fileSystem)
            .then(fs => {
              createDirectoryNameDialog()
                .then(directoryName => {
                  resolve({name: directoryName, fs});
                })
                .catch(reject);
            })
            .catch(message => reject({message}));
        })
        .catch(reject);
    });
  }
  invoke(preprocessResult) {
    return new Promise((resolve, reject) => {
      const {name, fs} = preprocessResult;
      if (!name) {
        this.reportProgress(100, `Directory creation cancelled`);
        resolve();
      } else {
        const path = fs.joinPath(this.parent, name);
        this.reportProgress(0, `Creating directory ${name}...`);
        fs.createDirectory(path)
          .then(() => {
            this.reportProgress(100, `Creating directory ${name}...`);
            resolve();
          })
          .catch(message => reject({message}));
      }
    });
  }
}

export default CreateDirectoryOperation;
