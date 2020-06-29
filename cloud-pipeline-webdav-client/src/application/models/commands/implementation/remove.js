import Operation from './operation';
import {initializeFileSystemAdapter} from "./utilities";
import showConfirmationDialog from "../../../show-confirmation-dialog";

class RemoveOperation extends Operation {
  constructor(mainWindow, sourceFS, sources) {
    super(mainWindow);
    this.sources = sources;
    this.fileSystem = sourceFS;
  }
  preprocess() {
    return new Promise((resolve, reject) => {
      const description = (this.sources || []).length === 1
        ? this.sources[0]
        : `${(this.sources || []).length} items`;
      super.preprocess()
        .then(() => {
          initializeFileSystemAdapter(this.fileSystem)
            .then(fs => {
              showConfirmationDialog(
                `Are you sure you want to remove ${description}?`
              )
                .then(confirmed => {
                  resolve({confirmed, fs});
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
      const {confirmed, fs} = preprocessResult;
      if (!confirmed) {
        this.reportProgress(100, `Remove cancelled`);
        resolve();
      } else {
        const invokeForElement = async (index, array, callback) => {
          if (index >= array.length) {
            return Promise.resolve();
          }
          try {
            await fs.remove(array[index])
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
      }
    });
  }
}

export default RemoveOperation;
