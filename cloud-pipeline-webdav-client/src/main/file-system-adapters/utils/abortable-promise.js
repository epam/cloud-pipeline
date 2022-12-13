/**
 * @param {EventEmitter} abortSignal
 * @param {function} onAbort
 * @param {function(resolve:function, reject:function)} fn
 * @returns {Promise<unknown>}
 */
module.exports = function AbortablePromise(abortSignal, onAbort, fn) {
  return new Promise((resolve, reject) => {
    let aborted = false;
    const onAbortHandle = () => {
      aborted = true;
      if (typeof onAbort === 'function') {
        onAbort();
      }
      reject(new Error('Aborted'));
    };
    const resolveWrapper = (result) => {
      if (abortSignal) {
        abortSignal.removeListener('abort', onAbortHandle);
      }
      if (!aborted) {
        resolve(result);
      }
    };
    const rejectWrapper = (error) => {
      if (abortSignal) {
        abortSignal.removeListener('abort', onAbortHandle);
      }
      if (!aborted) {
        reject(error);
      }
    };
    if (abortSignal) {
      abortSignal.on('abort', onAbortHandle);
    }
    fn(resolveWrapper, rejectWrapper);
  });
};
