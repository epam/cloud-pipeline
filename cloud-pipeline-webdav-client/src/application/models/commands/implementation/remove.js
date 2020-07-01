import Operation from './operation';
import {initializeFileSystemAdapter} from "./utilities";

class RemoveOperation extends Operation {
  constructor(sourceFS, sources, progressCallback) {
    super(progressCallback);
    this.sources = sources;
    this.fileSystem = sourceFS;
  }
  preprocess() {
    return new Promise((resolve, reject) => {
      super.preprocess()
        .then(() => {
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
      const invokeForElement = async (index, array, callback) => {
        if (index >= array.length) {
          return Promise.resolve();
        }
        callback(index, 1);
        try {
          await fs.remove(array[index])
          callback(index, 100);
        } catch (e) {
          array[index].error = e;
        }
        return invokeForElement(index + 1, array, callback);
      }
      invokeForElement(0, this.sources, (idx, progress) => {
        const element = this.sources[idx];
        const prevProgress = 100.0 / this.sources.length * idx;
        this.reportProgress(
          prevProgress + progress / this.sources.length,
          `Removing ${element}...`,
        );
      })
        .then(resolve)
        .catch(reject);
    });
  }
}

export default RemoveOperation;
