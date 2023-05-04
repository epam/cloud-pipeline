/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import {
  action,
  autorun,
  computed,
  observable
} from 'mobx';
import {createObjectStorageWrapper} from '../object-storage';
import whoAmI from '../../models/user/WhoAmI';
import UserAnnotations from './user-annotations';
import HCSBaseState from '../../components/special/hcs-image/utilities/base-state';

/**
 * @typedef {Object} ImagesAnnotationsOptions
 * @property {number|string} storageIdentifier
 * @property {string} root - annotations folder path
 */

class ImagesAnnotations extends HCSBaseState {
  @observable pending = true;
  @observable error = undefined;
  @observable initialized = false;
  /**
   * @type {ObjectStorage}
   */
  @observable storage = undefined;
  /**
   * @type {UserAnnotations[]}
   */
  @observable usersAnnotations = [];
  /**
   * @param {ImagesAnnotationsOptions} options
   */
  constructor (options) {
    const {
      root,
      storageIdentifier
    } = options || {};
    super(undefined, 'projectionChanged');
    this.root = root;
    this.storageIdentifier = storageIdentifier;
    (this.fetch)();
    this.dispose = autorun(() => this.updateViewer(this.annotations));
  }

  destroy () {
    if (typeof this.dispose === 'function') {
      this.dispose();
    }
    (this.usersAnnotations || []).forEach((annotation) => annotation.destroy());
  }

  /**
   * @type {UserAnnotations}
   */
  @computed
  get myAnnotations () {
    if (whoAmI.loaded) {
      const {userName} = whoAmI.value || {};
      return this.usersAnnotations.find((annotations) => annotations.userName === userName);
    }
    return undefined;
  }

  @computed
  get annotations () {
    return (this.usersAnnotations || [])
      .reduce((r, c) => ([...r, ...(c.annotations || []).map((annotation) => ({
        ...annotation,
        visible: c.visible
      }))]), []);
  }

  @action
  initialize () {
    if (!this._initializePromise) {
      this._initializePromise = new Promise(async (resolve) => {
        try {
          this.pending = true;
          this.storage = await createObjectStorageWrapper(
            undefined,
            this.storageIdentifier,
            {read: true, write: true}
          );
          if (!this.storage) {
            throw new Error(`Unknown storage: #${this.storageIdentifier}`);
          }
          await whoAmI.fetchIfNeededOrWait();
          this.initialized = true;
        } catch (error) {
          this.error = error.message;
          this.initialized = false;
          console.warn(error.message);
        } finally {
          this.pending = false;
          resolve();
        }
      });
    }
    return this._initializePromise;
  }

  @action
  async fetch (force = false) {
    if (this._fetchPromise && !force) {
      return this._fetchPromise;
    }
    this._token = (this._token || 0) + 1;
    const token = this._token;
    const commit = (data) => {
      if (token === this._token) {
        this.pending = false;
        const {
          error,
          usersAnnotations = []
        } = data || {};
        this.error = error;
        (this.usersAnnotations || []).forEach((annotation) => annotation.destroy());
        this.usersAnnotations = usersAnnotations;
        this._fetchPromise = undefined;
      }
    };
    this._fetchPromise = new Promise(async (resolve) => {
      await this.initialize();
      if (!this.initialized) {
        resolve();
        return;
      }
      try {
        this.pending = true;
        const contents = await this.storage.getFolderContents(this.root);
        const annotationFiles = contents
          .filter((item) => /\.json$/i.test(item.name) && /^file$/i.test(item.type))
          .map((item) => item.path);
        /**
         * @type {UserAnnotations[]}
         */
        const usersAnnotations = annotationFiles.map((path) => new UserAnnotations({
          storage: this.storage,
          path,
          onUpdate: () => this.updateViewer()
        }));
        const {
          id,
          userName
        } = whoAmI.value || {};
        if (!usersAnnotations.find((annotations) => annotations.userName === userName)) {
          usersAnnotations.push(new UserAnnotations({
            storage: this.storage,
            path: `${this.root}/${id}.json`,
            userName,
            autoFetch: false,
            onUpdate: () => this.updateViewer()
          }));
        }
        commit({usersAnnotations});
      } catch (error) {
        commit({error: error.message});
        console.warn(error.message);
      } finally {
        resolve();
      }
    });
    return this._fetchPromise;
  }

  updateViewer (annotations = this.annotations) {
    if (this.viewer) {
      this.viewer.setAnnotations(annotations);
    }
  }

  attachToViewer (viewer) {
    super.attachToViewer(viewer);
    this.updateViewer();
    this.updateProjection(viewer ? viewer.projection : undefined);
  }

  detachFromViewer () {
    if (this.viewer) {
      this.viewer.setAnnotations([]);
    }
    super.detachFromViewer();
  }

  updateProjection (projection) {
    this.projection = projection;
  }

  @action
  onStateChanged (viewer, newState) {
    this.updateProjection(newState);
  }

  /**
   * @param {{x: number, y: number}} canvasCoordinate
   * @returns {{x: number, y: number}}
   */
  getImageCoordinates (canvasCoordinate) {
    if (
      canvasCoordinate &&
      typeof this.projection === 'function'
    ) {
      const [x, y] = this.projection([canvasCoordinate.x, canvasCoordinate.y]);
      return {x, y};
    }
    return undefined;
  }
}

/**
 * @param {number|string} storageId
 * @param {string} imageFilePath
 * @returns {ImagesAnnotations}
 */
export function getImagesAnnotationsForFilePath (storageId, imageFilePath) {
  const path = (imageFilePath || '')
    .split('/')
    .filter((part) => part && part.length > 0)
    .slice(0, -1)
    .concat('annotations')
    .join('/');
  return new ImagesAnnotations({
    storageIdentifier: storageId,
    root: path
  });
}

export default ImagesAnnotations;
