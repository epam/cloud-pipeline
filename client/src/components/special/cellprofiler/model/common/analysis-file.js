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

class AnalysisFile {
  path;
  name;
  /**
   * @type {AnalysisModule}
   */
  cpModule;
  constructor (cpModule, path, name = (path || 'file').split(/[/\\]/).pop()) {
    this.cpModule = cpModule;
    this.path = path;
    this.name = name;
  }
}

/**
 * @typedef {Object} HCSSourceFileOptions
 * @property {string} sourceDirectory
 * @property {{x: number, y: number}} well
 * @property {string} image
 * @property {number} z
 * @property {number} time
 * @property {string[]} [channels]
 */

/**
 * @param {HCSSourceFileOptions} optionsA
 * @param {HCSSourceFileOptions} optionsB
 * @returns {boolean}
 */
function sourceFileOptionsEqual (optionsA, optionsB) {
  const {
    sourceDirectory: aSourceDirectory,
    well: aWell,
    image: aImage,
    z: aZ,
    time: aTime
  } = optionsA || {};
  const {
    x: aX,
    y: aY
  } = aWell || {};
  const {
    sourceDirectory: bSourceDirectory,
    well: bWell,
    image: bImage,
    z: bZ,
    time: bTime
  } = optionsB || {};
  const {
    x: bX,
    y: bY
  } = bWell || {};
  return aSourceDirectory === bSourceDirectory &&
    aX === bX &&
    aY === bY &&
    aZ === bZ &&
    aTime === bTime &&
    aImage === bImage;
}

class HCSSourceFile extends AnalysisFile {
  /**
   * @param {HCSSourceFileOptions} options
   * @returns {boolean}
   */
  static check (options) {
    const {
      sourceDirectory,
      well,
      image
    } = options || {};
    return !!sourceDirectory && !!well && well.x !== undefined && well.y !== undefined && !!image;
  }
  /**
   *
   * @param {AnalysisModule} cpModule
   * @param {HCSSourceFileOptions} options
   */
  constructor (cpModule, options) {
    const {
      sourceDirectory,
      well,
      image,
      z = 0,
      time = 0
    } = options;
    super(cpModule, sourceDirectory, image);
    this.sourceDirectory = sourceDirectory;
    this.x = well ? well.x : undefined;
    this.y = well ? well.y : undefined;
    this.image = image;
    this.z = z;
    this.time = time;
  }
}

export {
  AnalysisFile,
  HCSSourceFile,
  sourceFileOptionsEqual
};
