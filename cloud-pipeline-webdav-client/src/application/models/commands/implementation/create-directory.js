import Operation from './operation';

class CreateDirectoryOperation extends Operation {
  constructor(fileSystem, directory, progressCallback) {
    super(progressCallback);
    this.directory = directory;
    this.fileSystem = fileSystem;
  }
  preprocess() {
    return new Promise((resolve, reject) => {
      super.preprocess()
        .then(() => {
          this.reportProgress('Creating directory...');
          if (!this.fileSystem) {
            throw new Error('Internal error: file system is not initialized');
          }
          resolve(this.fileSystem);
        })
        .catch(reject);
    });
  }
  invoke(preprocessResult) {
    return new Promise((resolve, reject) => {
      const fs = preprocessResult;
      if (!this.directory) {
        this.reportProgress(100, `Directory creation cancelled`);
        resolve();
      } else {
        const path = this.directory;
        this.reportProgress(0, `Creating directory ${path}...`);
        fs.createDirectory(path)
          .then(() => {
            this.reportProgress(100, `Creating directory ${name}...`);
            resolve();
          })
          .catch(message => reject({message: `Error: ${message}`}));
      }
    });
  }
}

export default CreateDirectoryOperation;
