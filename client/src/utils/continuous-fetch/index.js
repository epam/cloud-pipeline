/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import moment from 'moment-timezone';
import attachDocumentVisibilityHandlers from './document-visibility-handler';

const SECOND = 1000;
const DEFAULT_INTERVAL = 5 * SECOND;
const MAX_INTERVAL_FN = defaultInterval => defaultInterval * 12;
const INCREASE_FACTOR = 1.5;
const NOISE = range => Math.random() * range;
const NOOP = () => {};

function log (...messages) {
  const time = moment().format('HH:mm:ss.SSS');
  console.log(`[${time}]`, ...messages);
}

function formatInterval (ms) {
  return `${Math.round(ms * 10) / 10000} sec`;
}

function makePromise (fn) {
  if (!fn) {
    return Promise.resolve();
  }
  try {
    const promise = fn();
    if (promise && promise.then) {
      return promise;
    }
    return Promise.resolve(promise);
  } catch (e) {
    return Promise.reject(e);
  }
}

const currentRequests = new Map();

function unregister (identifier, callStop = true) {
  if (!identifier) {
    return;
  }
  if (currentRequests.has(identifier)) {
    if (callStop) {
      const stop = currentRequests.get(identifier);
      if (stop && typeof stop === 'function') {
        if (stop.verbose) {
          log(`Unregistering request "${identifier}"`);
        }
        stop();
      }
    }
    currentRequests.delete(identifier);
  }
}

function register (identifier, stop) {
  if (!identifier) {
    return;
  }
  unregister(identifier);
  currentRequests.set(identifier, stop);
}

function wrapRequestFetch (request) {
  return new Promise((resolve, reject) => {
    request
      .fetch()
      .then(() => {
        if (request.networkError) {
          throw new Error(request.networkError);
        }
        resolve();
      })
      .catch(reject);
  });
}

/**
 * @typedef {Object} ContinuousFetchOptions
 * @property {number} [intervalMS]
 * @property {number} [maxIntervalMS]
 * @property {Remote|RemotePost} [request]
 * @property {function} [call]
 * @property {boolean} [fetchImmediate=true]
 * @property {function} [onError]
 * @property {function} [beforeInvoke]
 * @property {function} [afterInvoke]
 * @property {boolean} [verbose=false]
 * @property {boolean} [continuous=true]
 * @property {string} [identifier]
 */

/**
 * @param {ContinuousFetchOptions} options
 * @returns {{resume: function, stop: function, reset: function, pause: function, fetch: function}}
 */
export default function continuousFetch (options = {}) {
  const {
    intervalMS = DEFAULT_INTERVAL,
    maxIntervalMS = MAX_INTERVAL_FN(intervalMS),
    request,
    call = request
      ? () => wrapRequestFetch(request)
      : undefined,
    fetchImmediate = true,
    onError = NOOP,
    beforeInvoke = NOOP,
    afterInvoke = NOOP,
    verbose = false,
    identifier,
    continuous = true
  } = options;
  if (!call) {
    return {
      stop: NOOP,
      reset: NOOP,
      pause: NOOP,
      resume: NOOP,
      fetch: NOOP
    };
  }
  const verboseName = identifier || 'request';
  let cancelToken;
  let stopRequested = false;
  const clearCurrentToken = () => {
    if (cancelToken) {
      clearTimeout(cancelToken);
      cancelToken = undefined;
    }
  };
  const handleError = (error) => {
    if (verbose) {
      // eslint-disable-next-line
      log(`Continuous request "${verboseName}": error "${error.message}"`);
    }
    onError(error);
    return Promise.resolve({failed: true});
  };
  let interval = intervalMS;
  const resetInterval = () => {
    interval = intervalMS;
  };
  const increaseInterval = () => {
    interval = Math.ceil(
      Math.max(
        intervalMS,
        Math.min(
          maxIntervalMS,
          interval * INCREASE_FACTOR + NOISE(intervalMS / 2.0)
        )
      )
    );
    if (verbose) {
      // eslint-disable-next-line
      log(`Continuous request "${verboseName}": increasing interval to ${formatInterval(interval)}`);
    }
  };
  const scheduleNext = (fn) => {
    clearCurrentToken();
    if (stopRequested || !continuous) {
      return;
    }
    if (verbose) {
      // eslint-disable-next-line
      log(`Continuous request "${verboseName}": scheduling next request after ${formatInterval(interval)}`);
    }
    cancelToken = setTimeout(() => fn(), interval);
  };
  const doSingleFetch = () => {
    clearCurrentToken();
    if (stopRequested) {
      return;
    }
    if (verbose) {
      // eslint-disable-next-line
      log(`Continuous request "${verboseName}": fetching...`);
    }
    beforeInvoke();
    const callResult = makePromise(call);
    callResult
      .catch(handleError)
      .then(result => {
        afterInvoke();
        if (verbose) {
          // eslint-disable-next-line
          log(`Continuous request "${verboseName}": fetching done`);
        }
        if (result && result.failed) {
          increaseInterval();
        } else {
          resetInterval();
        }
      })
      .then(() => scheduleNext(doSingleFetch));
  };
  if (fetchImmediate || !continuous) {
    doSingleFetch();
  } else {
    scheduleNext(doSingleFetch);
  }
  const pause = () => {
    if (verbose) {
      // eslint-disable-next-line
      log(`Continuous request "${verboseName}": paused`);
    }
    resetInterval();
    clearCurrentToken();
  };
  const resume = () => {
    if (verbose) {
      // eslint-disable-next-line
      log(`Continuous request "${verboseName}": resumed`);
    }
    clearCurrentToken();
    doSingleFetch();
  };
  const reset = () => {
    if (verbose) {
      // eslint-disable-next-line
      log(`Continuous request "${verboseName}": reset`);
    }
    scheduleNext(doSingleFetch);
  };
  const detach = attachDocumentVisibilityHandlers(pause, resume);
  const stop = () => {
    detach();
    clearCurrentToken();
    stopRequested = true;
    if (verbose) {
      // eslint-disable-next-line
      log(`Continuous request "${verboseName}": stopped`);
    }
    unregister(identifier, false);
  };
  stop.verbose = verbose;
  register(identifier, stop);
  return {
    stop,
    reset,
    pause,
    resume,
    fetch: doSingleFetch
  };
}
