/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import createSnapshot from './utilities/create-snapshot';

const LOG_MESSAGE = 'LOG_MESSAGE';
const LOG_ERROR = 'LOG_ERROR';
const CALLBACKS = 'CALLBACKS';

/**
 * @typedef {Object} OverviewOptions
 */

/**
 * @typedef {Object} HcsImageViewerOptions
 * @property {Element} container
 * @property {string} [className]
 * @property {Object} [style]
 * @property {number} [minZoomBackOff=0]
 * @property {number} [maxZoomBackOff]
 * @property {number} [defaultZoomBackOff=0]
 * @property {OverviewOptions} [overview]
 * @property {boolean|number} [verbose=1]
 */

const Verbosity = {
  none: 0,
  error: 1,
  all: 2,
};

class Viewer {
  Events = {
    stateChanged: 'state-changed',
    viewerStateChanged: 'viewer-state-changed',
    onCellClick: 'on-cell-click',
  };

  initializationPromise;

  verbose = Verbosity.error;

  [LOG_MESSAGE] = (...message) => (this.verbose === Verbosity.all
    ? console.log('[HCS Image Viewer]', ...message)
    : undefined);

  [LOG_ERROR] = (...error) => (this.verbose !== Verbosity.none
    ? console.error('[HCS Image Viewer]', ...error)
    : undefined);

  /**
     *
     * @param {HcsImageViewerOptions} [options]
     */
  constructor(options = {}) {
    const {
      container,
      className,
      style,
      verbose = Verbosity.error,
      minZoomBackOff,
      maxZoomBackOff,
      defaultZoomBackOff,
      overview,
    } = options;
    if (typeof verbose === 'boolean') {
      this.verbose = verbose ? Verbosity.all : Verbosity.none;
    } else {
      this.verbose = verbose;
    }
    this.listeners = [];
    this.container = container;
    this.initializationPromise = new Promise((resolve, reject) => {
      import(/* webpackChunkName: "hcs-image-inject" */ './hcs-image-wrapper')
        .then(({ default: wrapper }) => {
          const onStateChanged = (state) => {
            this.state = state;
            this.emit(this.Events.stateChanged, state);
          };
          const onViewerStateChanged = (state) => {
            this.viewerState = state;
            this.emit(this.Events.viewerStateChanged, state);
          };
          const registerActions = (actions) => {
            this[CALLBACKS] = actions;
            resolve(this);
          };
          const onCellClick = (cell) => {
            this.emit(this.Events.onCellClick, cell);
          };
          wrapper(
            container,
            {
              className,
              style,
              minZoomBackOff,
              maxZoomBackOff,
              defaultZoomBackOff,
              overview,
            },
            {
              onStateChange: onStateChanged,
              onRegisterStateActions: registerActions,
              onViewerStateChanged,
              onCellClick,
            },
          );
        })
        .catch(reject);
    });
    this.initializationPromise
      .then(() => this[LOG_MESSAGE]('initialized'))
      .catch((e) => this[LOG_ERROR](`initialization error: ${e.message}`));
  }

  addEventListener(event, listener) {
    this.listeners.push({ event, listener });
  }

  removeEventListener(event, listener) {
    const index = this.listeners.findIndex((o) => o.event === event && o.listener === listener);
    if (index >= 0) {
      this.listeners.splice(index, 1);
    }
  }

  emit(event, payload) {
    this.listeners
      .filter((o) => o.event === event && typeof o.listener === 'function')
      .map((o) => o.listener)
      .forEach((listener) => listener(this, payload));
  }

  onInitialized(callback) {
    this.initializationPromise
      .then(callback)
      .catch(() => {});
  }

  waitForInitialization() {
    return new Promise((resolve, reject) => {
      this.initializationPromise
        .then(() => resolve())
        .catch(reject);
    });
  }

  getCallback(name) {
    if (this[CALLBACKS] && typeof this[CALLBACKS][name] === 'function') {
      return this[CALLBACKS][name];
    }
    return () => {};
  }

  /**
     * Set OME-TIFF url
     * @param {string} url
     * @param {string} [offsetsUrl]
     * @returns {Promise<unknown>}
     */
  setData(url, offsetsUrl) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          if (this.state && this.state.url === url && this.state.offsetsUrl === offsetsUrl) {
            resolve(this);
          } else {
            this.getCallback('setData')(url, offsetsUrl, (result) => {
              if (result && result.error) {
                reject(new Error(result.error));
              } else {
                resolve(this);
              }
            });
          }
        })
        .catch(reject);
    });
  }

  setImage(options) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setImage')(options, (result) => {
            if (result && result.error) {
              reject(new Error(result.error));
            } else {
              resolve(this);
            }
          });
        })
        .catch(reject);
    });
  }

  setDefaultChannelsColors(defaultColors = {}) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setDefaultChannelsColors')(defaultColors);
          resolve();
        })
        .catch(reject);
    });
  }

  setMesh(mesh) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setMesh')(mesh);
          resolve();
        })
        .catch(reject);
    });
  }

  hideMesh() {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setMesh')(undefined);
          resolve();
        })
        .catch(reject);
    });
  }

  setOverlayImages(overlayImages = []) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setOverlayImages')(overlayImages);
          resolve();
        })
        .catch(reject);
    });
  }

  setChannelProperties(channel, properties) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setChannelProperties')(channel, properties);
          resolve();
        })
        .catch(reject);
    });
  }

  setColorMap(colorMap) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setColorMap')(colorMap);
          resolve();
        })
        .catch(reject);
    });
  }

  setLensChannel(channel) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setLensChannel')(channel);
          resolve();
        })
        .catch(reject);
    });
  }

  setLensEnabled(enabled) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setLensEnabled')(enabled);
          resolve();
        })
        .catch(reject);
    });
  }

  setGlobalPosition(position) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setGlobalPosition')(position);
          resolve();
        })
        .catch(reject);
    });
  }

  setGlobalZPosition(z) {
    return this.setGlobalPosition({ z });
  }

  setGlobalTimePosition(time) {
    return this.setGlobalPosition({ t: time });
  }

  setLockChannels(lock) {
    return new Promise((resolve, reject) => {
      this.waitForInitialization()
        .then(() => {
          this.getCallback('setLockChannels')(lock);
          resolve();
        })
        .catch(reject);
    });
  }

  makeSnapshot(name) {
    if (this.container) {
      const canvas = this.container.getElementsByTagName('canvas')[0];
      let imageName = name;
      if (!imageName && this.viewerState && this.viewerState.metadata) {
        const { metadata } = this.viewerState;
        const {
          ID,
          Name,
        } = metadata;
        const parts = [ID, Name].filter(Boolean);
        if (parts.length > 0) {
          imageName = parts.join(' ');
        }
      }
      createSnapshot(canvas, imageName);
    }
  }
}

export default Viewer;
