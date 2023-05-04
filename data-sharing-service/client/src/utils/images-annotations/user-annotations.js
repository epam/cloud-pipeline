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

import {action, autorun, observable} from 'mobx';

/**
 * @typedef {Object} UserAnnotationsOptions
 * @property {ObjectStorage} storage
 * @property {string} path
 * @property {string} [userName]
 * @property {boolean} [autoFetch=true]
 * @property {function} [onUpdate]
 */

/**
 * @typedef {[number, number, number]} AnnotationPoint
 */

/**
 * @typedef {Object} AnnotationLabel
 * @property {string} value
 * @property {number} fontSize
 * @property {string} color
 */

/**
 * @typedef {Object} ArrowAnnotation
 * @property {string} identifier
 * @property {string} type
 * @property {number} [lineWidth]
 * @property {string} [lineColor]
 * @property {[AnnotationPoint, AnnotationPoint]} points
 * @property {AnnotationLabel} [label]
 */

/**
 * @typedef {Object} PolylineAnnotation
 * @property {string} identifier
 * @property {string} type
 * @property {boolean} closed
 * @property {string} [lineColor]
 * @property {number} [lineWidth]
 * @property {AnnotationPoint[]} points
 */

/**
 * @typedef {Object} RectangleAnnotation
 * @property {string} identifier
 * @property {AnnotationPoint} center
 * @property {number} width
 * @property {number} height
 * @property {number} [rotation]
 * @property {string} [lineColor]
 * @property {number} [lineWidth]
 */

/**
 * @typedef {Object} CircleAnnotation
 * @property {string} identifier
 * @property {AnnotationPoint} center
 * @property {number} radius
 * @property {string} [lineColor]
 * @property {number} [lineWidth]
 */

/**
 * @typedef {Object} TextAnnotation
 * @property {string} type
 * @property {AnnotationPoint} center
 * @property {AnnotationLabel} label
 */

/**
 * @typedef {
 * ArrowAnnotation|
 * PolylineAnnotation|
 * RectangleAnnotation|
 * CircleAnnotation|
 * TextAnnotation
 * } UserAnnotation
 */

/**
 * @param {string} userName
 * @param {UserAnnotation[]} annotations
 * @returns {string}
 */
function getNextIdentifier (userName, annotations = []) {
  /**
   * @param {string} identifier
   * @returns {number}
   */
  function parseIdentifier (identifier) {
    return Number((identifier || '').split('/').pop());
  }
  const identifiers = annotations
    .map((annotation) => parseIdentifier(annotation.identifier))
    .filter((n) => !Number.isNaN(n));
  const next = Math.max(0, ...identifiers) + 1;
  return `${userName}/${next}`;
}

class UserAnnotations {
  @observable pending = false;
  @observable error = undefined;
  /**
   * @type {UserAnnotation[]}
   */
  @observable annotations = [];
  /**
   * @type {string}
   */
  @observable userName;
  @observable visible = true;
  /**
   * @param {UserAnnotationsOptions} options
   */
  constructor (options) {
    const {
      storage,
      path,
      autoFetch = true,
      userName,
      onUpdate
    } = options;
    this.path = path;
    this.storage = storage;
    this.userName = userName;
    this.onUpdate = onUpdate;
    if (autoFetch) {
      (this.fetch)();
    }
    this.dispose = autorun(() => {
      if (typeof onUpdate === 'function') {
        onUpdate({
          changed: this.annotations,
          visible: this.visible
        });
      }
    });
  }

  destroy () {
    if (typeof this.dispose === 'function') {
      this.dispose();
    }
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
          annotations = [],
          userName = this.userName
        } = data || {};
        const corrected = [];
        annotations.forEach((annotation) => {
          corrected.push({
            ...annotation,
            identifier: annotation.identifier || getNextIdentifier(userName, corrected)
          });
        });
        this.annotations = corrected;
        this.userName = userName;
        this.error = error;
        this._fetchPromise = undefined;
      }
      return Promise.resolve();
    };
    this._fetchPromise = new Promise((resolve) => {
      if (!this.storage) {
        commit({
          error: 'Unknown storage'
        }).then(resolve);
        return;
      }
      if (!this.path) {
        commit({
          error: 'Annotations file path is not defined'
        }).then(resolve);
        return;
      }
      this.pending = true;
      this.storage.getFileContent(this.path, {json: true})
        .then((data) => {
          const {
            elements: annotations = [],
            name: userName
          } = data || {};
          return commit({annotations, userName});
        })
        .catch((error) => commit({error: error.message}))
        .then(resolve);
    });
    return this._fetchPromise;
  }

  /**
   * @param {string} identifier
   * @returns {UserAnnotation}
   */
  getAnnotationByIdentifier (identifier) {
    return this.annotations.find((annotation) => annotation.identifier === identifier);
  }

  /**
   * @param {string} identifier
   * @returns {number}
   */
  getAnnotationIndexByIdentifier (identifier) {
    return this.annotations.findIndex((annotation) => annotation.identifier === identifier);
  }

  @action
  createOrUpdateAnnotation (annotation, save = false) {
    if (!annotation) {
      return;
    }
    if (!annotation.identifier) {
      annotation.identifier = getNextIdentifier(this.userName, this.annotations);
    }
    const currentIndex = this.getAnnotationIndexByIdentifier(annotation.identifier);
    if (currentIndex >= 0) {
      this.annotations.splice(
        currentIndex,
        1,
        annotation
      );
    } else {
      this.annotations.push(annotation);
    }
    if (save) {
      (this.save)();
    }
    return annotation.identifier;
  }

  @action
  removeAnnotation (annotation, save = false) {
    if (!annotation || !annotation.identifier) {
      return;
    }
    console.log('removing annotation', annotation.identifier, annotation.type);
    this.annotations = this.annotations.filter((a) => a.identifier !== annotation.identifier);
    if (save) {
      (this.save)();
    }
  }

  @action
  async save (reFetch = true) {
    if (!this.storage) {
      throw new Error('Unknown storage');
    }
    if (!this.path) {
      throw new Error('Annotations file path is not defined');
    }
    const payload = JSON.stringify({
      name: this.userName,
      elements: this.annotations
    });
    this.pending = true;
    await this.storage.writeFile(this.path, payload);
    if (reFetch) {
      await this.fetch(true);
    } else {
      this.pending = false;
    }
  }
}

export default UserAnnotations;
