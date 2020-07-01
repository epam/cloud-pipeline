import Operation from './operation';
import {buildSources} from './utilities';

class CopyOperation extends Operation {
  constructor(sourceFS, sources, destinationFS, destinationPath, progressCallback) {
    super(progressCallback);
    this.sourceFS = sourceFS;
    this.sources = sources;
    this.destinationFS = destinationFS;
    this.destinationPath = destinationPath;
  }
  preprocess() {
    return new Promise(async (resolve, reject) => {
      try {
        await super.preprocess();
        if (!this.sourceFS) {
          throw new Error('Internal error: source file system is not initialized');
        }
        if (!this.destinationFS) {
          throw new Error('Internal error: destination file system is not initialized');
        }
        const sfs = this.sourceFS;
        const dfs = this.destinationFS;
        const sources = await buildSources(sfs, this.sources);
        const destination = await dfs.buildDestination(this.destinationPath);
        const transfers = sources.map(({path, name}) => ({
          from: path,
          to: dfs.joinPath(destination, ...sfs.parsePath(name)),
          name,
        }));
        resolve({
          sourceFileSystem: sfs,
          destinationFileSystem: dfs,
          transfers,
        });
      } catch (e) {
        reject(e);
      }
    });
  }

  getInfoTitle (element) {
    return `Copying ${element}...`;
  }

  processElement (sourceAdapter, destinationAdapter, element, progressCallback) {
    return new Promise((resolve, reject) => {
      progressCallback(0);
      sourceAdapter
        .getContentsStream(element.from)
        .then((stream) => {
          if (!stream) {
            reject(`Cannot read ${element.from}`);
          } else {
            destinationAdapter.copy(
              stream,
              element.to,
              progressCallback
            )
              .then(() => {
                progressCallback(100);
                resolve();
              })
              .catch(reject);
          }
        })
        .catch(reject);
    });
  }

  invoke(preprocessResult) {
    const {
      sourceFileSystem,
      destinationFileSystem,
      transfers = []
    } = preprocessResult || {};
    const invokeForElement = async (index, array, callback) => {
      if (index >= array.length) {
        return Promise.resolve();
      }
      try {
        await this.processElement(
          sourceFileSystem,
          destinationFileSystem,
          array[index],
          p => callback(index, p),
        );
      } catch (e) {
        array[index].error = e;
      }
      return invokeForElement(index + 1, array, callback);
    }
    return new Promise(async (resolve) => {
      await invokeForElement(0, transfers, (idx, progress) => {
        const element = transfers[idx];
        const prevProgress = 100.0 / transfers.length * idx;
        this.reportProgress(
          prevProgress + progress / transfers.length,
          this.getInfoTitle(element.name),
        );
      })
      resolve();
    });
  }
}

export default CopyOperation;
