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

const red = [255, 0, 0];
const green = [0, 255, 0];
const blue = [0, 0, 255];
const white = [255, 255, 255];
const pink = [255, 0, 255];
const violet = [154, 0, 255];
const yellow = [255, 255, 0];
const orange = [255, 60, 0];

function correctWellKnownChannelName(channelName) {
  if (/^dapi$/i.test(channelName)) {
    return 'DAPI';
  }
  if (/^alexa[\s_-]+/i.test(channelName)) {
    const e = /^alexa[\s_-]+([\d]+)$/i.exec(channelName);
    if (e && e[1]) {
      return `Alexa ${e[1]}`;
    }
  }
  if (/^gfp$/i.test(channelName)) {
    return 'GFP';
  }
  return channelName;
}

class DefaultChannelsColors {
  Events = {
    defaultColorsUpdated: 'defaultColorsUpdated',
  };

  constructor(defaultColors = {}) {
    this.listeners = [];
    this.update(defaultColors);
  }

  update(defaultColors = {}) {
    this.defaultColors = { ...defaultColors };
  }

  getColorForChannel(channel) {
    const channelName = correctWellKnownChannelName(channel);
    if (this.defaultColors[channelName]) {
      return this.defaultColors[channelName];
    }
    return undefined;
  }

  updateChannelColor(channel, color) {
    const channelName = correctWellKnownChannelName(channel);
    this.defaultColors[channelName] = color;
    this.emit(
      this.Events.defaultColorsUpdated,
      this.defaultColors,
    );
  }

  addEventListener(event, listener) {
    this.removeEventListener(event, listener);
    this.listeners.push({ event, listener });
  }

  removeEventListener(event, listener) {
    this.listeners = this.listeners.filter((o) => o.event !== event || o.listener !== listener);
  }

  emit(event, payload) {
    this.listeners
      .filter((o) => o.event === event && typeof o.listener === 'function')
      .map((o) => o.listener)
      .forEach((listener) => listener(this, payload));
  }
}

const defaultChannelsColors = new DefaultChannelsColors();

export {
  red,
  green,
  blue,
  white,
  defaultChannelsColors,
};
export default [
  blue,
  green,
  pink,
  yellow,
  orange,
  violet,
  white,
  red,
];
