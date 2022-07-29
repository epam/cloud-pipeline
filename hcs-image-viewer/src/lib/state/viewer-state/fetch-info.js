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

import { GlobalDimensionFields } from '../constants';
import { buildDefaultSelection, getBoundingCube, getMultiSelectionStats } from '../utilities/tiff-pixel-source-utilities';
import guessRgb from '../utilities/guess-rgb';
import isInterleaved from '../utilities/is-interleaved';
import COLOR_PALETTE, {
  defaultChannelsColors,
  blue,
  green,
  red,
  white,
} from './default-color-palette';

const BYTE_RANGE = [0, 255];

function mapChannel(channel, index) {
  return channel.Name || channel.name || channel.ID || `Channel ${index + 1}`;
}

export default async function fetchInfo(loader, metadata, selections, globalPosition) {
  const { shape, labels = [] } = loader[0] || {};
  const globalDimensions = labels
    .map((label, index) => ({ label, size: shape[index] || 0 }))
    .filter((dimension) => dimension.size > 1
      && GlobalDimensionFields.includes(dimension.label));
  const currentSelections = selections || buildDefaultSelection(loader[0], globalPosition);
  const { Pixels = {} } = metadata;
  const {
    Channels = [],
  } = Pixels;
  let contrastLimits = [];
  let colors = [];
  let domains = [];
  let useLens = false;
  let useColorMap = false;
  const isRGB = guessRgb(metadata);
  const shapeIsInterleaved = isRGB && isInterleaved(shape);
  const channels = Channels.map(mapChannel);
  if (isRGB) {
    if (isInterleaved(shape)) {
      contrastLimits = [BYTE_RANGE.slice()];
      domains = [BYTE_RANGE.slice()];
      colors = [red];
    } else {
      contrastLimits = [
        BYTE_RANGE.slice(),
        BYTE_RANGE.slice(),
        BYTE_RANGE.slice(),
      ];
      domains = [
        BYTE_RANGE.slice(),
        BYTE_RANGE.slice(),
        BYTE_RANGE.slice(),
      ];
      colors = [red, green, blue];
    }
    useLens = false;
    useColorMap = false;
  } else {
    const stats = await getMultiSelectionStats({
      loader,
      selections: currentSelections,
      use3d: false,
    });
    domains = stats.domains.slice();
    contrastLimits = stats.contrastLimits.slice();
    // If there is only one channel, use white.
    colors = [];
    for (let i = 0; i < channels.length; i += 1) {
      const defaultColor = defaultChannelsColors.getColorForChannel(channels[i]);
      if (defaultColor) {
        colors.push(defaultColor);
      } else if (channels.length === 1) {
        colors.push(white);
      } else {
        colors.push(COLOR_PALETTE[colors.length % COLOR_PALETTE.length]);
      }
    }
    useLens = Channels.length > 1;
    useColorMap = true;
  }
  const [xSlice, ySlice, zSlice] = getBoundingCube(loader);
  return {
    channels,
    selections: currentSelections,
    useLens,
    useColorMap,
    colors,
    domains,
    contrastLimits,
    xSlice,
    ySlice,
    zSlice,
    ready: true,
    isRGB,
    shapeIsInterleaved,
    globalDimensions,
    metadata,
    loader,
  };
}
