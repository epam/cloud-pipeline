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
 * @property {number} x
 * @property {number} y
 * @property {number} z
 * @property {number} t
 * @property {number} c
 * @property {string} channel
 * @property {string} fieldID
 */

/**
 * @param {HCSSourceFileOptions} optionsA
 * @param {HCSSourceFileOptions} optionsB
 * @returns {boolean}
 */
function sourceFileOptionsEqual (optionsA, optionsB) {
  const {
    sourceDirectory: aSourceDirectory,
    x: ax,
    y: ay,
    z: az,
    fieldID: aF,
    channel: aChannel,
    c: ac,
    t: at
  } = optionsA || {};
  const {
    sourceDirectory: bSourceDirectory,
    x: bx,
    y: by,
    z: bz,
    fieldID: bF,
    channel: bChannel,
    c: bc,
    t: bt
  } = optionsB || {};
  return aSourceDirectory === bSourceDirectory &&
    ax === bx &&
    ay === by &&
    az === bz &&
    at === bt &&
    ac === bc &&
    aChannel === bChannel &&
    aF === bF;
}

/**
 * @param {HCSSourceFileOptions} a
 * @param {HCSSourceFileOptions} b
 * @returns {number}
 */
function sortSourceFiles (a, b) {
  const {
    x: ax,
    y: ay,
    z: az,
    fieldID: aF,
    c: ac,
    t: at
  } = a || {};
  const {
    x: bx,
    y: by,
    z: bz,
    fieldID: bF,
    c: bc,
    t: bt
  } = b || {};
  return (ax - bx) || (ay - by) || (az - bz) || (at - bt) || (aF - bF) || (ac - bc);
}

/**
 * @param {HCSSourceFileOptions[]} a
 * @param {HCSSourceFileOptions[]} b
 * @returns {boolean}
 */
function sourceFileOptionsSetsEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b || a.length !== b.length) {
    return false;
  }
  const aSorted = a.slice().sort(sortSourceFiles);
  const bSorted = b.slice().sort(sortSourceFiles);
  for (let i = 0; i < aSorted.length; i++) {
    if (!sourceFileOptionsEqual(aSorted[i], bSorted[i])) {
      return false;
    }
  }
  return true;
}

class HCSSourceFile extends AnalysisFile {
  /**
   * @param {HCSSourceFileOptions} aFile
   * @returns {boolean}
   */
  static check (...aFile) {
    return !aFile.some((fileInfo) => {
      const {
        sourceDirectory,
        fieldID,
        channel
      } = fileInfo || {};
      return !sourceDirectory ||
        !fieldID ||
        !channel;
    });
  }
  /**
   *
   * @param {AnalysisModule} cpModule
   * @param {HCSSourceFileOptions} options
   */
  constructor (cpModule, options) {
    const {
      sourceDirectory,
      c,
      channel,
      fieldID,
      t,
      y,
      x,
      z
    } = options;
    super(
      cpModule,
      sourceDirectory,
      `Field ${fieldID}, Well (${x},${y}), z=${z}, t=${t}, channel ${channel} (#${c})`
    );
    this.sourceDirectory = sourceDirectory;
    this.x = x;
    this.y = y;
    this.z = z;
    this.t = t;
    this.c = c;
    this.channel = channel;
    this.fieldID = fieldID;
  }
}

export {
  AnalysisFile,
  HCSSourceFile,
  sourceFileOptionsSetsEqual
};
