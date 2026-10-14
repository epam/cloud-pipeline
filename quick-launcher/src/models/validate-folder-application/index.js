import React, {useCallback, useContext, useEffect, useMemo, useState} from 'react';
import moment from 'moment-timezone';
import removeExtraSlash from '../utilities/remove-slashes';
import getRun from '../cloud-pipeline-api/run';
import startValidationJob from './start-validation-job';
import stopRun from '../cloud-pipeline-api/stop-run';
import parseRunServiceUrl from '../cloud-pipeline-api/utilities/parse-run-service-url';
import buildApplicationPath from './build-application-path';
import checkApplication from './check-application';
import getUserToken from "../cloud-pipeline-api/user-token";

const KEY = 'validations';
const VALIDATION_POLLING_INTERVAL_MS = 2500;

function getLocalDateTime (unix) {
  return moment(unix).format('D MMMM, YYYY, HH:mm');
}

function getExpirationDate (date, expiration) {
  const r = /([\d]+)(m|d|h)/g;
  const expirationInfo = {
    d: 0,
    m: 0,
    h: 0
  };
  let e = r.exec(expiration);
  while (e && e.length === 3) {
    expirationInfo[e[2]] += (+e[1]);
    e = r.exec(expiration);
  }
  const minute = 60;
  const hour = 60 * minute;
  const day = 24 * hour;
  const expirationSeconds = expirationInfo.m * minute +
    expirationInfo.h * hour +
    expirationInfo.d * day;
  return date + expirationSeconds * 1000;
}

function getApplicationPath (application) {
  const storage = application?.storage;
  const path = removeExtraSlash(application?.info?.source || application?.info?.path || '');
  const publishedPath = removeExtraSlash(application?.info?.path || '');
  return {storage, path, publishedPath};
}

const colorLog = (color, ...opts) => {
  if (opts.length > 0) {
    console.log(
      `%c${opts.join(' ')}`,
      `color: ${color}`
    )
  }
}

const log = (...opts) => colorLog('green', ...opts);
const logError = (...opts) => colorLog('red', ...opts);

function FAValidationSession (info, sessions, validatePublished) {
  const {
    storage,
    path,
    publishedPath,
    id,
    status,
    jobStatus = 'running',
    initialized,
    endpoint,
    timestamp,
    stopped,
    ...rest
  } = info;
  return {
    storage,
    path,
    publishedPath,
    id,
    status,
    initialized,
    endpoint,
    timestamp,
    jobStatus,
    stopped,
    ...rest,
    get pathToValidate() {
      return validatePublished ? publishedPath : path;
    },
    update: function () {
      this.timestamp = new Date().getTime();
      sessions.updateTimestamp();
    },
    validationLog (...opts) {
      log(...[`Validating application ${this.pathToValidate}:`, ...opts])
    },
    validationErrorLog (...opts) {
      logError(...[`Validating application ${this.pathToValidate} error:`, ...opts])
    },
    remove: function () {
      if (this.removed) {
        return Promise.resolve();
      }
      return new Promise((resolve) => {
        this.validationLog('removing local storage item');
        this.stop()
          .then((stopped) => {
            this.removed = stopped;
            resolve(stopped);
          });
      });
    },
    stop: function () {
      if (this.stopped) {
        return Promise.resolve(this.stopped);
      }
      this.update();
      if (!this.stopping) {
        this.validationLog('stopping job...');
        this.stopping = new Promise((resolve) => {
          stopRun(this.id, true)
            .then((result) => {
              const {status: requestStatus, message} = result;
              if (requestStatus === 'OK') {
                this.stopped = true;
                this.status = /^pending$/i.test(this.status) ? undefined : this.status;
                this.validationLog('job stopped');
              } else {
                throw new Error(message || requestStatus);
              }
            })
            .catch(e => {
              this.validationErrorLog(e.message);
            })
            .then(() => this.update())
            .then(() => {
              this.stopping = undefined;
              resolve(this.stopped);
            });
        });
      }
      return this.stopping;
    },
    startValidation (application) {
      this.status = 'pending';
      this.update();
      return new Promise((resolve) => {
        this.validationLog('started');
        const logCb = this.validationLog.bind(this);
        startValidationJob(
          application,
          {
            log: logCb
          }
        )
          .then((startResult) => {
            const {
              error,
              run
            } = startResult;
            if (error) {
              throw new Error(error);
            }
            if (run) {
              const {
                id,
                status: jobStatus,
                serviceUrl,
                initialized = false
              } = run;
              this.validationLog('job received:', `#${id}`);
              this.id = id;
              this.jobStatus = jobStatus;
              this.initialized = initialized;
              const endpoints = parseRunServiceUrl(serviceUrl) || [];
              const endpoint = endpoints.find(e => e.isDefault) || endpoints[0];
              this.endpoint = endpoint ? endpoint.url : undefined;
              return this.validate();
            } else {
              throw new Error('missing job identifier');
            }
          })
          .catch((e) => {
            this.error = e.message;
            this.status = 'error';
            this.validationErrorLog(e.message);
          })
          .then(() => this.update())
          .then(resolve)
      });
    },
    getJobInfo () {
      return new Promise((resolve, reject) => {
        if (this.initialized && /^running$/i.test(this.jobStatus) && this.endpoint) {
          resolve({
            initialized: this.initialized,
            endpoint: this.endpoint,
            jobStatus: this.jobStatus
          });
        } else {
          this.validationLog(`polling job #${this.id}...`);
          getRun(this.id)
            .then((run) => {
              const {status: requestStatus, message, payload: runInfo} = run;
              if (requestStatus === 'OK' && runInfo) {
                const {
                  status,
                  serviceUrl,
                  initialized
                } = runInfo;
                const endpoints = parseRunServiceUrl(serviceUrl) || [];
                const endpoint = endpoints.find(e => e.isDefault) || endpoints[0];
                const endpointUrl = endpoint ? endpoint.url : undefined;
                this.validationLog(`job #${this.id} status: ${status}; initialized: ${initialized}; service url received: ${endpointUrl ? endpointUrl : 'none'}`);
                resolve({
                  jobStatus: status,
                  endpoint: endpointUrl,
                  initialized
                });
              } else {
                throw new Error(message || requestStatus);
              }
            })
            .catch(reject);
        }
      });
    },
    checkApplication: function () {
      return new Promise((resolve, reject) => {
        this.validationLog(`calling job endpoint to check application...`);
        Promise.all([
          buildApplicationPath(this.storage, this.pathToValidate, sessions.settings),
          getUserToken()
        ])
          .then(([absolutePath, token]) => {
            this.validationLog(`target_folder=${absolutePath}`);
            return checkApplication(this.endpoint, absolutePath, token, sessions.settings);
          })
          .then((checkResult) => {
            this.validationLog(`check result: ${JSON.stringify(checkResult)}`);
            const {
              is_valid = false,
              message
            } = checkResult || {};
            this.status = is_valid ? 'valid' : 'error';
            this.error = is_valid ? undefined : (message || 'unknown');
            return this.stop();
          })
          .then(() => resolve(this.valid))
          .catch(reject);
      });
    },
    validate: function (force = false) {
      if (!force && !this.pending) {
        return Promise.resolve(this.status);
      }
      if (this.removing || this.removed || this.stopping || this.stopped || !this.id) {
        return Promise.resolve(this.status);
      }
      if (force) {
        this.validating = undefined;
      }
      if (!this.validating) {
        this.status = 'pending';
        this.update();
        this.validating = new Promise((resolve) => {
          this.getJobInfo()
            .then((info) => {
              const {
                jobStatus,
                initialized,
                endpoint
              } = info;
              this.jobStatus = jobStatus;
              this.initialized = initialized;
              this.endpoint = endpoint;
              this.error = undefined;
              if (/^(stopped|failure)$/i.test(jobStatus)) {
                throw new Error(`job stopped`);
              } else if (/^(paused)$/i.test(jobStatus)) {
                this.validationLog('job is paused. stopping it');
                this.status = 'invalid';
                return this.stop();
              } else if (/^running$/i.test(jobStatus) && !!endpoint && initialized) {
                this.validationLog('endpoint available');
                this.status = 'pending';
                return this.checkApplication();
              } else {
                this.status = 'pending';
                return Promise.resolve();
              }
            })
            .catch(e => {
              this.error = e.message;
              this.status = 'error';
              logError(`Validating application ${this.pathToValidate} error: ${e.message}`);
              return this.stop();
            })
            .then(() => this.update())
            .then(() => {
              this.validating = undefined;
              resolve();
            })
        });
      }
      return this.validating;
    },
    get valid () {
      return /^valid$/i.test(this.status);
    },
    get invalid () {
      return /^error$/i.test(this.status);
    },
    get pending () {
      return !this.stopped && !/^(valid|error)$/i.test(this.status);
    },
    get info () {
      return `Session #${this.id}: ${path}, validation status: ${status}${this.expired ? '. EXPIRED' : ''}`;
    },
    get expired () {
      if (
        sessions.settings &&
        sessions.settings.folderApplicationValidation &&
        sessions.settings.folderApplicationValidation.expiresAfter
      ) {
        return getExpirationDate(
          this.timestamp,
          sessions.settings.folderApplicationValidation.expiresAfter
        ) < (new Date()).getTime();
      }
      return false;
    },
    get data () {
      return {
        storage: this.storage,
        path: this.path,
        publishedPath: this.publishedPath,
        validatePublished,
        id: this.id,
        status: this.status,
        timestamp: this.timestamp,
        error: this.error,
        stopped: this.stopped
      }
    }
  };
}

function Sessions (settings) {
  this.settings = settings;
  try {
    this.sessions = JSON.parse(localStorage.getItem(KEY))
      .filter(o => !!o.id)
      .map(o => new FAValidationSession(o, this, o.validatePublished));
  } catch (_) {
    this.sessions = [];
  }
  this.printSessions = function () {
    log('================================================');
    log('Folder application validation sessions:', this.sessions.length);
    this.sessions.forEach(session => {
      log(session.info);
    });
    log('================================================');
  }
  this.save = function () {
    localStorage.setItem(
      KEY,
      JSON.stringify(
        this.sessions
          .filter(session => !session.removed && !session.expired)
          .map(session => session.data)
      )
    );
  }
  this.listeners = [];
  this.removeListener = function (listener) {
    this.listeners.splice(this.listeners.indexOf(listener), 1);
  }
  this.addListener = function (listener) {
    this.listeners.push(listener);
    return this.removeListener.bind(this, listener);
  }
  this.updateTimestamp = function () {
    this.timestamp = new Date().getTime();
    this.listeners.forEach(cb => cb(this.timestamp));
  }
  this.updateTimestamp();
  this.validate = function () {
    clearTimeout(this.active);
    Promise.all(this.sessions.map(session => session.validate()))
      .then(() => {
        this.active = setTimeout(
          this.validate.bind(this),
          settings?.folderApplicationValidation?.pollingIntervalMS || VALIDATION_POLLING_INTERVAL_MS
        );
        this.save();
      });
  }
  this.start = function () {
    if (this.active) {
      return;
    }
    this.settings = settings;
    log('Start watching folder application sessions');
    this.printSessions();
    this.validate();
  }
  this.finish = function () {
    log('Stop watching folder application sessions');
    this.printSessions();
    clearTimeout(this.active);
    this.active = undefined;
  }
  this.findSession = function (storage, path, publishedPath) {
    return this.sessions.find(session => !session.removed &&
      !session.expired &&
      `${session.storage}` === `${storage}` &&
      session.path === path &&
      (session.publishedPath || '') === (publishedPath || '')
    );
  }
  this.getSessionForApplication = function (application) {
    if (!application) {
      return undefined;
    }
    const {
      path,
      publishedPath,
      storage
    } = getApplicationPath(application);
    return this.findSession(storage, path, publishedPath);
  }
  this.validateApplication = function (application, validatePublished = false) {
    const {
      path,
      storage,
      publishedPath
    } = getApplicationPath(application);
    const currentSession = this.findSession(storage, path, publishedPath);
    const removeCurrentJob = () => new Promise((resolve) => {
      if (currentSession) {
        currentSession.remove()
          .then(resolve);
      } else {
        resolve();
      }
    })
    return new Promise((resolve) => {
      removeCurrentJob()
        .then(() => this.save())
        .then(() => {
          const session = new FAValidationSession({storage, path, publishedPath}, this, validatePublished);
          this.sessions.push(session);
          this.save();
          return session.startValidation(application);
        })
        .then(resolve);
    });
  }
}

const FAValidationSessionsContext = React.createContext();

export function useSessions (settings) {
  const [sessions, setSessions] = useState(undefined);
  useEffect(() => {
    if (settings) {
      const newSessions = new Sessions(settings);
      newSessions.start();
      setSessions(newSessions);
      return newSessions.finish.bind(sessions);
    }
  }, [settings]);
  return sessions;
}

export function useApplicationSession (application) {
  const sessions = useContext(FAValidationSessionsContext);
  const [timeStamp, setTimeStamp] = useState(0);
  const [session, setSession] = useState(undefined);
  const [pending, setPending] = useState(false);
  const [valid, setValid] = useState(false);
  const [error, setError] = useState(false);
  const [validationDate, setValidationDate] = useState(undefined);
  useEffect(() => {
    if (sessions) {
      return sessions.addListener(setTimeStamp);
    }
  }, [sessions, setTimeStamp]);
  useEffect(() => {
    const session = sessions?.getSessionForApplication(application)
    setValid(session?.valid);
    setPending(session?.pending);
    setError(session?.error);
    setValidationDate(session?.timestamp);
    setSession(session);
  }, [application, sessions, timeStamp, setValidationDate]);
  const validate = useCallback((validatePublished = false) => {
    if (sessions) {
      sessions
        .validateApplication(application, validatePublished)
        .then(o => {
          if (o) {
            setSession(o);
          }
        });
    }
  }, [application, sessions, setSession]);
  const stopValidation = useCallback(() => {
    if (session) {
      session.remove();
    }
  }, [session]);
  const sessionInfo = useMemo(() => ({
    valid,
    pending,
    error,
    validated: !!session,
    validatedAt: session && validationDate
      ? getLocalDateTime(validationDate)
      : undefined
  }), [valid, pending, error, session, validationDate]);
  return useMemo(() => ({
    session: sessionInfo,
    validate,
    stopValidation: session && session.id ? stopValidation : undefined
  }), [session, sessionInfo, validate, stopValidation]);
}

export {FAValidationSessionsContext};
