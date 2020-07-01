let identifier = 0;

class Operation {
  constructor(progressCallback) {
    identifier += 1;
    this.identifier = identifier;
    this.progressCallback = progressCallback;
    this.finished = false;
    this.progress = 0;
    this.info = undefined;
    this.error = undefined;
  }
  preprocess() {
    this.reportProgress(this.progress, 'Preparing...');
    return Promise.resolve();
  }
  invoke(preprocessResult) {
    return Promise.resolve();
  }
  postprocess(preprocessResult, invokeResult) {
    this.reportProgress(this.progress, 'Finishing...');
    return Promise.resolve();
  }
  reportProgress(progress, info) {
    this.progress = progress;
    this.info = info;
    if (this.progressCallback) {
      this.progressCallback(this);
    }
  }
  reportError(error) {
    this.error = error;
    if (this.progressCallback) {
      this.progressCallback(this);
    }
  }
  clear() {
    this.finished = true;
    if (this.progressCallback) {
      this.progressCallback(this);
    }
  }
  run() {
    return new Promise((resolve) => {
      this.preprocess()
        .then(preprocessResult => {
          this.reportProgress(0);
          this.invoke(preprocessResult)
            .then(invokeResult => {
              this.reportProgress(100);
              this.postprocess(preprocessResult, invokeResult)
                .then(() => {
                  this.clear();
                  resolve();
                })
                .catch(e => {
                  this.reportError(e.message);
                  resolve();
                });
            })
            .catch(e => {
              this.reportError(e.message);
              resolve();
            });
        })
        .catch(e => {
          this.reportError(e.message);
          resolve();
        })
    });
  }
}

export default Operation;
