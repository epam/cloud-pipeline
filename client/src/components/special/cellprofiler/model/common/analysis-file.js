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

function asc (a, b) {
  return a - b;
}

function numberArraysAreEqual (array1, array2) {
  const a = [...(new Set(array1 || []))].sort(asc);
  const b = [...(new Set(array2 || []))].sort(asc);
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

function stringArraysAreEqual (array1, array2) {
  const a = [...(new Set(array1 || []))].sort();
  const b = [...(new Set(array2 || []))].sort();
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

function wellsAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return true;
  }
  const {
    x: ax,
    y: ay
  } = a;
  const {
    x: bx,
    y: by
  } = b;
  return ax === bx && ay === by;
}

function wellsArraysAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return true;
  }
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    const bWell = b.find(bb => wellsAreEqual(bb, a[i]));
    if (!bWell) {
      return false;
    }
  }
  for (let i = 0; i < b.length; i++) {
    const aWell = a.find(aa => wellsAreEqual(aa, b[i]));
    if (!aWell) {
      return false;
    }
  }
  return true;
}

/**
 * @typedef {Object} HCSSourceFileOptions
 * @property {string} sourceDirectory
 * @property {HCSImageWell[]} [wells]
 * @property {number[]} [images]
 * @property {number[]} [zCoordinates]
 * @property {number[]} [timePoints]
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
    images: aImages = [],
    wells: aWells = [],
    zCoordinates: aZCoordinates = [],
    timePoints: aTimePoints = [],
    channels: aChannels = []
  } = optionsA || {};
  const {
    sourceDirectory: bSourceDirectory,
    images: bImages = [],
    wells: bWells = [],
    zCoordinates: bZCoordinates = [],
    timePoints: bTimePoints = [],
    channels: bChannels = []
  } = optionsB || {};
  return aSourceDirectory === bSourceDirectory &&
    wellsArraysAreEqual(aWells, bWells) &&
    numberArraysAreEqual(aImages, bImages) &&
    numberArraysAreEqual(aZCoordinates, bZCoordinates) &&
    numberArraysAreEqual(aTimePoints, bTimePoints) &&
    stringArraysAreEqual(aChannels, bChannels);
}

class HCSSourceFile extends AnalysisFile {
  /**
   * @param {HCSSourceFileOptions} options
   * @returns {boolean}
   */
  static check (options) {
    const {
      sourceDirectory,
      wells = [],
      images = [],
      zCoordinates = [],
      timePoints = []
    } = options || {};
    return !!sourceDirectory &&
      wells.length > 0 &&
      images.length > 0 &&
      zCoordinates.length > 0 &&
      timePoints.length > 0;
  }
  /**
   *
   * @param {AnalysisModule} cpModule
   * @param {HCSSourceFileOptions} options
   */
  constructor (cpModule, options) {
    const {
      sourceDirectory,
      wells = [],
      images = [],
      zCoordinates = [1],
      timePoints = [1],
      channels = []
    } = options;
    super(cpModule, sourceDirectory, `${images[0] || 'image'}`);
    this.sourceDirectory = sourceDirectory;
    this.wells = wells;
    this.timePoints = timePoints;
    this.images = images;
    this.zCoordinates = zCoordinates;
    this.channels = channels;
  }
}

export {
  AnalysisFile,
  HCSSourceFile,
  sourceFileOptionsEqual
};
