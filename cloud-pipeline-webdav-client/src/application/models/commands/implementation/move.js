const fs = require('fs');
import CopyOperation from './copy';

class MoveOperation extends CopyOperation {
  getInfoTitle(element) {
    return `Moving ${element}...`;
  }
  postprocess(preprocessResult, invokeResult) {
    const {
      sourceFileSystem
    } = preprocessResult;
    return new Promise((resolve, reject) => {
      super.postprocess(preprocessResult, invokeResult)
        .then(() => {
          Promise.all((this.sources || []).map(source => sourceFileSystem.remove(source)))
            .then(() => resolve())
            .catch(e => reject({message: e.message}));
        })
        .catch(reject);
    });
  }
}

export default MoveOperation;
