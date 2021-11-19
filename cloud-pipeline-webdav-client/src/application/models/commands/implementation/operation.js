let identifier = 0;

class Operation {
  constructor(progressCallback) {
    identifier += 1;
    this.identifier = identifier;
    this.progressCallback = progressCallback;
    this.finished = false;
    this.aborted = false;
    this.progress = 0;
    this.info = undefined;
    this.error = undefined;
  }
  preprocess() {
    if (!this.aborted) {
      this.reportProgress(this.progress, 'Preparing...');
    }
    return Promise.resolve();
  }
  invoke(preprocessResult) {
    return Promise.resolve();
  }
  postprocess(preprocessResult, invokeResult) {
    if (!this.aborted) {
      this.reportProgress(this.progress, 'Finishing...');
    }
    return Promise.resolve();
  }
  reportProgress(progress, info) {
    if (!this.aborted) {
      this.progress = progress;
      this.info = info;
    }
    if (this.progressCallback) {
      this.progressCallback(this);
    }
  }
  reportError(error) {
    this.info = undefined;
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
              this.invocationDone = true;
              if (
                invokeResult &&
                Array.isArray(invokeResult) &&
                invokeResult.length > 0
              ) {
                const invocationErrors = invokeResult
                  .filter(r => !!r.error)
                  .map(r => ({
                    ...r,
                    error: r.error.message || r.error
                  }));
                if (invocationErrors.length > 1) {
                  const errors = invocationErrors
                    .map(ie => (ie.error || '').toString())
                    .join('; ');
                  throw new Error(`Multiple errors occurred: ${errors}`);
                } else if (invocationErrors.length === 1) {
                  const [invocationError] = invocationErrors;
                  const {
                    name,
                    error
                  } = invocationError;
                  throw new Error(`${name}: ${error}`);
                }
              }
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
  abort() {
    if (this.invocationDone) {
      this.clear();
    } else {
      this.reportProgress(0, 'Aborting...');
      this.aborted = true;
      if (this.progressCallback) {
        this.progressCallback(this);
      }
    }
  }
}

export default Operation;
