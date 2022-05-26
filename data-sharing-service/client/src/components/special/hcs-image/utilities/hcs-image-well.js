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

function parseCoordinates (key) {
  const e = /^([\d]+)_([\d]+)$/.exec(key);
  if (e && e.length >= 3) {
    return {
      x: Number(e[1]),
      y: Number(e[2])
    };
  }
  return undefined;
}

function stringFormatter (string, size = 2) {
  if (string) {
    const str = `${string}`;
    const append = Math.max(0, size - str.length);
    return (new Array(append)).fill('0').join().concat(str);
  }
  return (new Array(size)).fill('_').join();
}

function getWellCoordinateString (coordinate, dimension) {
  return stringFormatter(coordinate, Math.ceil(Math.log10(dimension)));
}

/**
 * @typedef {Object} WellField
 * @property {string|number} x
 * @property {string|number} y
 * @property {string|number} id
 */

/**
 * @typedef {Object} WellOptions
 * @property {string|number} x
 * @property {string|number} y
 * @property {string|number} width
 * @property {string|number} height
 * @property {string|number} [round_radius]
 * @property {Object} [to_ome_wells_mapping]
 */

class HCSImageWell {
  /**
   * @param {WellOptions} options
   * @param {HCSInfo} hcs
   */
  constructor (options = {}, hcs) {
    const {
      x,
      y,
      width,
      height,
      round_radius: roundRadius,
      to_ome_wells_mapping: images = {},
      well_overview: wellImageId
    } = options;
    const {
      width: plateWidth = 10,
      height: plateHeight = 10
    } = hcs || {};
    const size = Math.max(plateWidth, plateHeight);
    const idx = getWellCoordinateString(x, size);
    const idy = getWellCoordinateString(y, size);
    this.id = `Well ${idx}_${idy}`;
    /**
     * Well x coordinate
     * @type {number}
     */
    this.x = Number(x);
    /**
     * Well y coordinate
     * @type {number}
     */
    this.y = Number(y);
    /**
     * Well width
     * @type {number}
     */
    this.width = Number(width);
    /**
     * Well height
     * @type {number}
     */
    this.height = Number(height);
    /**
     * Well radius
     * @type {number|undefined}
     */
    this.radius = roundRadius ? Number(roundRadius) : undefined;
    /**
     * Well fields (images)
     * @type {WellField[]}
     */
    this.images = Object.keys(images || {})
      .map(key => ({key, parsed: parseCoordinates(key)}))
      .filter(({parsed}) => parsed)
      .map(({key, parsed}) => ({
        ...parsed,
        id: (images || {})[key],
        x: parsed.x,
        y: parsed.y
      }));
    /**
     * Well overview image id
     * @type {string}
     */
    this.wellImageId = wellImageId;
  }

  /**
   * Parses wells_map.json content
   * @param {Object} wellsFileContentsJSON
   * @param {HCSInfo} [hcs]
   * @return {HCSImageWell[]}
   */
  static parseWellsInfo (wellsFileContentsJSON, hcs) {
    return Object.keys(wellsFileContentsJSON || {})
      .map(key => ({
        key,
        parsed: parseCoordinates(key)
      }))
      .filter(o => o.parsed)
      .map(({key, parsed}) => new HCSImageWell(
        {...parsed, ...wellsFileContentsJSON[key]},
        hcs
      ));
  }
}

export default HCSImageWell;
