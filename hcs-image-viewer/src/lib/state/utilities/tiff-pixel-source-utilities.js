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

// source: https://github.com/hms-dbmi/viv/blob/master/avivator/src/utils.js

import { getChannelStats } from '@hms-dbmi/viv';
import { Matrix4 } from '@math.gl/core';
import isInterleaved from './is-interleaved';
import { GlobalDimensionFields } from '../constants';

/**
 * @typedef {Object} TiffPixelSource
 * @property {String[]} labels
 * @property {number[]} shape
 */

/**
 * Return the initial point of the global dimensions.
 * @param {TiffPixelSource} tiffPixelSource
 * @param {String[]} [dimensions]
 * @returns {*}
 */
function getShapeInitialPoints(tiffPixelSource, dimensions = GlobalDimensionFields) {
  const { labels } = tiffPixelSource;
  return labels
    .filter((label) => dimensions.includes(label))
    .map((name) => ({ [name]: 0 }))
    .reduce((r, c) => ({ ...r, ...c }), {});
}

// source: https://github.com/hms-dbmi/viv/blob/master/avivator/src/utils.js
/**
 * Create a default selection using the initial point of the available global dimensions,
 * and then the first four available selections from the first selectable channel.
 * @param {TiffPixelSource} pixelSource
 * @param {{ t: number, z: number}} globalPosition
 * @returns {[{[p: string]: *}]|*[]}
 */
export function buildDefaultSelection(pixelSource, globalPosition) {
  let selection = [];
  const globalSelection = {
    ...getShapeInitialPoints(pixelSource),
    ...(globalPosition || {}),
  };
  // First non-global dimension with some sort of selectable values.
  const firstNonGlobalDimension = pixelSource.labels
    .map((name, i) => ({ name, size: pixelSource.shape[i] }))
    .find((d) => !GlobalDimensionFields.includes(d.name) && d.size);

  // todo: why maximum 4 channels?
  for (let i = 0; i < Math.min(4, firstNonGlobalDimension.size); i += 1) {
    selection.push({
      [firstNonGlobalDimension.name]: i,
      ...globalSelection,
    });
  }
  selection = isInterleaved(pixelSource.shape)
    ? [{ ...selection[0], c: 0 }]
    : selection;
  return selection;
}

export async function getSingleSelectionStats2D({ loader, selection }) {
  const data = Array.isArray(loader) ? loader[loader.length - 1] : loader;
  const raster = await data.getRaster({ selection });
  const selectionStats = getChannelStats(raster.data);
  const { domain, contrastLimits } = selectionStats;
  return { domain, contrastLimits };
}

export async function getSingleSelectionStats3D({ loader, selection }) {
  const lowResSource = loader[loader.length - 1];
  const { shape, labels } = lowResSource;
  // eslint-disable-next-line no-bitwise
  const sizeZ = shape[labels.indexOf('z')] >> (loader.length - 1);
  const raster0 = await lowResSource.getRaster({
    selection: { ...selection, z: 0 },
  });
  const rasterMid = await lowResSource.getRaster({
    selection: { ...selection, z: Math.floor(sizeZ / 2) },
  });
  const rasterTop = await lowResSource.getRaster({
    selection: { ...selection, z: Math.max(0, sizeZ - 1) },
  });
  const stats0 = getChannelStats(raster0.data);
  const statsMid = getChannelStats(rasterMid.data);
  const statsTop = getChannelStats(rasterTop.data);
  return {
    domain: [
      Math.min(stats0.domain[0], statsMid.domain[0], statsTop.domain[0]),
      Math.max(stats0.domain[1], statsMid.domain[1], statsTop.domain[1]),
    ],
    contrastLimits: [
      Math.min(
        stats0.contrastLimits[0],
        statsMid.contrastLimits[0],
        statsTop.contrastLimits[0],
      ),
      Math.max(
        stats0.contrastLimits[1],
        statsMid.contrastLimits[1],
        statsTop.contrastLimits[1],
      ),
    ],
  };
}

export async function getSingleSelectionStats({ loader, selection, use3d }) {
  const getStats = use3d
    ? getSingleSelectionStats3D
    : getSingleSelectionStats2D;
  return getStats({ loader, selection });
}

export async function getMultiSelectionStats({ loader, selections, use3d }) {
  const stats = await Promise.all(
    selections.map((selection) => getSingleSelectionStats({ loader, selection, use3d })),
  );
  const domains = stats.map((stat) => stat.domain);
  const contrastLimits = stats.map((stat) => stat.contrastLimits);
  return { domains, contrastLimits };
}

/**
 * Get physical size scaling Matrix4
 * @property {TiffPixelSource} loader
 */
export function getPhysicalSizeScalingMatrix(loader) {
  const { x, y, z } = loader?.meta?.physicalSizes ?? {};
  if (x?.size && y?.size && z?.size) {
    const min = Math.min(z.size, x.size, y.size);
    const ratio = [x.size / min, y.size / min, z.size / min];
    return new Matrix4().scale(ratio);
  }
  return new Matrix4().identity();
}

/**
 *
 * @property {TiffPixelSource} loader
 * @returns {number[][]}
 */
export function getBoundingCube(loader) {
  const source = Array.isArray(loader) ? loader[0] : loader;
  const { shape, labels } = source;
  const physicalSizeScalingMatrix = getPhysicalSizeScalingMatrix(source);
  const xSlice = [0, physicalSizeScalingMatrix[0] * shape[labels.indexOf('x')]];
  const ySlice = [0, physicalSizeScalingMatrix[5] * shape[labels.indexOf('y')]];
  const zSlice = [
    0,
    physicalSizeScalingMatrix[10] * shape[labels.indexOf('z')],
  ];
  return [xSlice, ySlice, zSlice];
}
