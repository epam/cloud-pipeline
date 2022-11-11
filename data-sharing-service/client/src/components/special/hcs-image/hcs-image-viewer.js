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

const HcsImageViewer = window.HcsImageViewer;
const {
  Viewer,
  constants = {},
  fetchSourceInfo,
  defaultChannelsColors
} = HcsImageViewer || {};

const DEFAULT_CHANNELS_COLORS_KEY = 'HCS-default-colors';
const DEFAULT_CHANNELS_COLORS = {
  'DAPI': [9, 113, 255],
  'Alexa 405': [9, 2, 196],
  'Alexa 488': [8, 202, 8],
  'Alexa 555': [180, 240, 15],
  'Alexa 568': [255, 147, 9],
  'Alexa 594': [214, 0, 36],
  'Alexa 647': [196, 3, 3],
  'Alexa 680': [118, 10, 3],
  'Alexa 750': [0, 228, 228],
  'Alexa 790': [161, 1, 209],
  'GFP': [54, 255, 0]
};

function readChannelsColorsFromStore () {
  try {
    return JSON.parse(localStorage.getItem(DEFAULT_CHANNELS_COLORS_KEY)) || DEFAULT_CHANNELS_COLORS;
  } catch (_) {
    return DEFAULT_CHANNELS_COLORS;
  }
}

function writeChannelsColorsToStore (config, colors) {
  localStorage.setItem(DEFAULT_CHANNELS_COLORS_KEY, JSON.stringify(colors));
}

function initialize () {
  const colors = {
    ...DEFAULT_CHANNELS_COLORS,
    ...(readChannelsColorsFromStore())
  };
  try {
    defaultChannelsColors.update(colors);
    defaultChannelsColors.addEventListener(
      defaultChannelsColors.Events.defaultColorsUpdated,
      writeChannelsColorsToStore
    );
  } catch (e) {
    console.warn(`[HCS] Error setting default channels colors: ${e.message}`);
  }
}

initialize();

export {Viewer, constants, fetchSourceInfo, defaultChannelsColors};
export default Viewer;
