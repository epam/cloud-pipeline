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
import { defaultChannelsColors } from './default-color-palette';

export function changeChannelProperties(state, channelIndex, properties) {
  const propertiesArray = Object
    .entries(properties || {})
    .filter(([property]) => state && state[property] && Array.isArray(state[property]));
  if (propertiesArray.length > 0) {
    const newState = { ...(state || {}) };
    const {
      channels = [],
    } = state;
    const channelName = channels[channelIndex];
    propertiesArray.forEach(([property, value]) => {
      const propertyValue = newState[property];
      newState[property] = [...propertyValue];
      newState[property][channelIndex] = value;
      if (property === 'colors' && channelName) {
        defaultChannelsColors.updateChannelColor(channelName, value);
      }
    });
    return newState;
  }
  return state;
}

export function setDefaultChannelsColors(state, defaultColors = {}) {
  const {
    channels = [],
    colors = [],
  } = state || {};
  const newColors = colors.slice();
  Object.entries(defaultColors)
    .forEach(([channel, color]) => {
      const index = channels.findIndex((o) => o === channel);
      if (index >= 0) {
        newColors[index] = color;
      }
    });
  defaultChannelsColors.update(defaultColors);
  return {
    ...state,
    colors: newColors,
  };
}
