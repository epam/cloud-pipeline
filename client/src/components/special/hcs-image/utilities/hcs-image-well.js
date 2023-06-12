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

import * as HCSConstants from './constants';

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
 * @property {string|number} width
 * @property {string|number} height
 * @property {number} fieldWidth
 * @property {number} fieldHeight
 * @property {string|number} depth
 * @property {string|number} id
 * @property {number} fieldID
 * @property {number} wellID
 * @property {string[]} channels
 * @property {number} physicalSize
 * @property {string} unit
 * @property {number} physicalDepthSize
 * @property {string} depthUnit
 */

/**
 * @typedef {Object} WellOptions
 * @property {string|number} x
 * @property {string|number} y
 * @property {string|number} width
 * @property {string|number} height
 * @property {string|number} [round_radius]
 * @property {string} [path]
 * @property {string} [offsets_path]
 * @property {string} [overview_path]
 * @property {string} [overview_offsets_path]
 * @property {Object} [to_ome_wells_mapping]
 */

function generateOffsetsPath (dataPath) {
  const r = /(.+)\.ome\.tiff$/i.exec(dataPath);
  if (r && r.length > 1) {
    return `${r[1]}.offsets.json`;
  }
  return undefined;
}

function buildDefaultCoordinates (images = {}) {
  return Object.keys(images || {})
    .map(key => ({key: images[key], parsed: parseCoordinates(key)}))
    .filter(({parsed}) => parsed)
    .map(({key, parsed}) => ({
      [key]: [parsed.x, parsed.y - 1]
    }))
    .reduce((r, c) => ({...r, ...c}), {});
}

function buildImagesWithoutCoordinates (options = {}) {
  const {
    to_ome_wells_mapping: images,
    width,
    height
  } = options;
  const rawImages = Object
    .entries(buildDefaultCoordinates(images))
    .map(([imageId, coords]) => ({
      id: imageId,
      x: coords[0] - 1,
      y: height - coords[1] - 1,
      width: 1,
      height: 1,
      fieldWidth: 1,
      fieldHeight: 1,
      unit: 'px',
      physicalSize: 1
    }));
  const minX = Math.min(...rawImages.map(o => o.x));
  const maxX = Math.max(...rawImages.map(o => o.x + o.fieldWidth));
  const minY = Math.min(...rawImages.map(o => o.y));
  const maxY = Math.max(...rawImages.map(o => o.y + o.fieldHeight));
  const size = Math.ceil(Math.max(maxX - minX, maxY - minY));
  const getRealX = x => x - minX;
  const getRealY = y => y - minY;
  return {
    images: rawImages.map(image => ({
      ...image,
      realX: getRealX(image.x),
      realY: getRealY(image.y)
    })),
    width: width,
    height: height,
    meshSize: size
  };
}

function buildImages (options = {}) {
  const {
    coordinates,
    field_size: fieldSize = 1,
    width,
    height
  } = options;
  if (coordinates) {
    const convert = o => o / fieldSize;
    const rawImages = Object
      .entries(coordinates)
      .map(([imageId, coords]) => ({
        id: imageId,
        x1: convert(coords[0]),
        y1: convert(coords[1]),
        x2: convert(coords[0] + fieldSize),
        y2: convert(coords[1] + fieldSize)
      }));
    if (rawImages.length === 0) {
      return {
        images: [],
        width,
        height,
        meshSize: Math.max(width, height)
      };
    }
    const minX = Math.min(...rawImages.map(o => o.x1));
    const maxX = Math.max(...rawImages.map(o => o.x2));
    const minY = Math.min(...rawImages.map(o => o.y1));
    const maxY = Math.max(...rawImages.map(o => o.y2));
    const correctedSize = Math.ceil(Math.max((maxX - minX), (maxY - minY)));
    const translateX = x => x - minX;
    const translateY = y => correctedSize - (y - minY);
    const displayX = x => width
      ? width / 2.0 + x
      : x;
    const displayY = y => height
      ? height / 2.0 - y
      : y;
    return {
      images: rawImages.map(o => {
        const realX = translateX((o.x1 + o.x2) / 2.0) - Math.abs(o.x2 - o.x1) / 2.0;
        const realY = translateY((o.y1 + o.y2) / 2.0) - Math.abs(o.y2 - o.y1) / 2.0;
        return {
          id: o.id,
          x: displayX(o.x1),
          y: displayY(o.y1),
          realX,
          realY,
          width: 1,
          height: 1,
          fieldWidth: 1,
          fieldHeight: 1,
          unit: 'px',
          physicalSize: 1
        };
      }),
      width: width || correctedSize,
      height: height || correctedSize,
      meshSize: correctedSize
    };
  }
  return buildImagesWithoutCoordinates(options);
}

class HCSImageWell {
  /**
   * @param {WellOptions} options
   * @param {HCSImageSequence} sequence
   */
  constructor (options = {}, sequence) {
    const {
      directory,
      plateWidth = 10,
      plateHeight = 10,
      objectStorage
    } = sequence || {};
    const {
      x,
      y,
      round_radius: roundRadius,
      well_overview: wellImageId,
      tags,
      path = HCSConstants.OME_TIFF_FILE_NAME,
      offsets_path: offsetsPath = generateOffsetsPath(
        path
      ),
      overview_path: overviewPath = HCSConstants.OVERVIEW_OME_TIFF_FILE_NAME,
      overview_offsets_path: overviewOffsetsPath = generateOffsetsPath(
        overviewPath
      )
    } = options;
    this.omeTiffFileName = [directory, path]
      .filter(Boolean)
      .join(objectStorage.delimiter || '/');
    this.offsetsJsonFileName = [directory, offsetsPath]
      .filter(Boolean)
      .join(objectStorage.delimiter || '/');
    this.overviewOmeTiffFileName = [directory, overviewPath]
      .filter(Boolean)
      .join(objectStorage.delimiter || '/');
    this.overviewOffsetsJsonFileName = [directory, overviewOffsetsPath]
      .filter(Boolean)
      .join(objectStorage.delimiter || '/');
    const formatter = Math.max(plateWidth, plateHeight);
    const idx = getWellCoordinateString(x, formatter);
    const idy = getWellCoordinateString(y, formatter);
    this.id = `Well ${idx}_${idy}`;
    /**
     * Well x coordinate
     * @type {number}
     */
    this.x = Number(x) - 1;
    /**
     * Well y coordinate
     * @type {number}
     */
    this.y = Number(y) - 1;
    /**
     * Well radius
     * @type {number|undefined}
     */
    this.radius = roundRadius ? Number(roundRadius) : undefined;
    const {
      images,
      width,
      height,
      meshSize
    } = buildImages(options);
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
     * Well mesh size
     * @type {number}
     */
    this.meshSize = Number(meshSize);
    /**
     * Well fields (images)
     * @type {WellField[]}
     */
    this.images = images;
    /**
     * Well overview image id
     * @type {string}
     */
    this.wellImageId = wellImageId;
    this.tags = tags || {};
    /**
     * @type {HCSImageSequence}
     */
    this.sequence = sequence;
  }

  destroy () {
    this.images = undefined;
    this.sequence = undefined;
  }

  fetchMetadata = () => {
    if (!this.metadataPromise) {
      this.metadataPromise = new Promise((resolve) => {
        Promise.resolve()
          .then(() => this.sequence.hcsImageMetadataCache.getMetadata(this))
          .then(metadataArray => {
            const {images = []} = this;
            images.forEach(image => {
              const {id} = image;
              const metadata = metadataArray.find(o => o.ID === id);
              if (metadata && metadata.Name) {
                const {
                  field: fieldID,
                  well: wellID
                } = getImageInfoFromName(metadata.Name);
                image.wellID = wellID;
                image.fieldID = fieldID;
              }
              if (metadata && metadata.Pixels) {
                image.width = metadata.Pixels.SizeX;
                image.height = metadata.Pixels.SizeY;
                image.depth = metadata.Pixels.SizeZ || 1;
                image.physicalSize = metadata.Pixels.PhysicalSizeX || 1;
                image.unit = metadata.Pixels.PhysicalSizeXUnit || 'px';
                image.physicalDepthSize = metadata.Pixels.PhysicalSizeZ || 1;
                image.depthUnit = metadata.Pixels.PhysicalSizeZUnit || '';
              }
              if (metadata && metadata.Pixels && metadata.Pixels.Channels) {
                image.channels = metadata.Pixels.Channels.map(o => o.Name);
              }
            });
            resolve();
          })
          .catch(() => {
            resolve();
          });
      });
    }
    return this.metadataPromise;
  };

  /**
   * Parses wells_map.json content
   * @param {Object} wellsFileContentsJSON
   * @param {HCSImageSequence} sequence
   * @return {HCSImageWell[]}
   */
  static parseWellsInfo (wellsFileContentsJSON, sequence) {
    return Object.keys(wellsFileContentsJSON || {})
      .map(key => ({
        key,
        parsed: parseCoordinates(key)
      }))
      .filter(o => o.parsed)
      .map(({key, parsed}) => new HCSImageWell(
        {...parsed, ...wellsFileContentsJSON[key]},
        sequence
      ));
  }
}

export function getImageInfoFromName (imageName) {
  if (!imageName) {
    return {};
  }
  const getID = (key) => {
    const regExp = new RegExp(`${key} ([\\d]+)\\s*(\\s|,|$)`, 'i');
    if (imageName && regExp.test(imageName)) {
      const e = regExp.exec(imageName);
      if (e && e.length) {
        return Number(e[1]);
      }
    }
    return undefined;
  };
  return {
    well: getID('well'),
    field: getID('field')
  };
}

export default HCSImageWell;
