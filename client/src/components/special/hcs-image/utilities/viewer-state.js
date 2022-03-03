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

import {action, observable} from 'mobx';

function shallowCompareArrays (array1, array2) {
  if (array1 && array2 && array1.length === array2.length) {
    for (let i = 0; i < array1.length; i++) {
      if (array1[i] !== array2[i]) {
        return false;
      }
    }
    return true;
  }
  return false;
}

class ChannelState {
  @observable index = 0;
  @observable identifier = 'Channel';
  @observable name = 'Channel';
  @observable visible = true;
  @observable domain = [];
  @observable contrastLimits = [];
  @observable color = [];
  @observable pixels;

  /**
   * @typedef {Object} ChannelOptions
   * @property {number} index
   * @property {string} [identifier]
   * @property {string} [name]
   * @property {boolean} [visible=true]
   * @property {number[]} [domain=[0, 1]]
   * @property {number[]} [contrastLimits=[0, 1]]
   * @property {number[]} [color=[255, 255, 255]]
   * @property {undefined|string|number[]} [pixels]
   */
  /**
   * Creates channel state
   * @param {ChannelOptions} options
   */
  constructor (options) {
    this.update(options);
  }

  /**
   * Updates channel state
   * @param {ChannelOptions} options
   */
  @action
  update (options) {
    const {
      index = 0,
      identifier = 'Channel',
      name = 'Channel',
      visible = true,
      domain = [0, 1],
      contrastLimits = [0, 1],
      color = [255, 255, 255],
      pixels
    } = options;
    this.index = index;
    this.identifier = identifier;
    this.name = name;
    this.visible = visible;
    this.domain = domain;
    this.contrastLimits = contrastLimits;
    this.color = color;
    this.pixels = pixels;
  }
}

function buildZPositionsArray (max, zSize, zUnit) {
  const basePower = Math.floor(Math.log10(zSize || 1));
  const base = 10 ** basePower;
  const decimalDigits = 2;
  const format = o => {
    const rounded = Math.round(o / base * (10 ** decimalDigits)) / (10 ** decimalDigits);
    const postfix = basePower !== 0 ? `e${basePower}` : '';
    return [
      `${rounded}${postfix}`,
      zUnit
    ].filter(Boolean).join('');
  };
  return (new Array(max))
    .fill('')
    .map((o, z) => ({
      z,
      title: format((z + 1) * zSize)
    }));
}

class ViewerState {
  @observable use3D = false;
  @observable useLens = false;
  @observable useColorMap = false;
  @observable colorMap = '';
  @observable lensEnabled = false;
  @observable lensChannel = 0;
  @observable pending = false;
  @observable isRGB = false;
  @observable xSlice = [];
  @observable ySlice = [];
  @observable zSlice = [];
  @observable selection = [];
  @observable dimensions = [];
  /**
   * Channels state
   * @type {ChannelState[]}
   */
  @observable channels = [];
  @observable channelsLocked = false;
  @observable imageZPosition = 0;
  @observable availableZPositions = [];

  constructor (viewer) {
    this.attachToViewer(viewer);
  }

  attachToViewer (viewer) {
    this.detachFromViewer();
    if (viewer) {
      this.viewer = viewer;
      this.viewer.addEventListener(
        this.viewer.Events.viewerStateChanged,
        this.onViewerStateChange
      );
    }
  }

  detachFromViewer () {
    if (this.viewer) {
      this.viewer.removeEventListener(
        this.viewer.Events.viewerStateChanged,
        this.onViewerStateChange
      );
    }
  }

  @action
  onViewerStateChange = (viewer, newState) => {
    const {
      identifiers = [],
      channels = [],
      channelsVisibility = [],
      channelsLocked,
      globalSelection = {},
      globalDimensions = [],
      pixelValues = [],
      colors = [],
      domains = [],
      contrastLimits = [],
      useLens = false,
      lensEnabled = false,
      lensChannel,
      useColorMap = true,
      colorMap = '',
      xSlice = [],
      ySlice = [],
      zSlice = [],
      use3D = false,
      isRGB,
      pending = false,
      metadata
    } = newState || {};
    this.pending = pending;
    if (pending) {
      return;
    }
    this.use3D = use3D;
    this.useLens = useLens;
    this.lensEnabled = lensEnabled;
    this.useColorMap = useColorMap;
    this.lensChannel = lensChannel;
    this.colorMap = colorMap;
    this.isRGB = isRGB;
    this.xSlice = xSlice;
    this.ySlice = ySlice;
    this.zSlice = zSlice;
    this.selection = globalSelection;
    this.dimensions = globalDimensions;
    this.imageZPosition = globalSelection && globalSelection.z
      ? globalSelection.z
      : 0;
    const zDimension = globalDimensions.find(o => /^z$/i.test(o.label));
    const zSize = zDimension ? Math.max(zDimension.size || 0, 1) : 1;
    let zPhysicalSize = 1;
    let zPhysicalSizeUnit;
    if (metadata && metadata.Pixels) {
      const {
        PhysicalSizeZ = 1,
        PhysicalSizeZUnit
      } = metadata.Pixels;
      zPhysicalSize = PhysicalSizeZ;
      zPhysicalSizeUnit = PhysicalSizeZUnit;
    }
    this.availableZPositions = buildZPositionsArray(zSize, zPhysicalSize, zPhysicalSizeUnit);
    /**
     * updated channels options
     * @type {ChannelOptions[]}
     */
    if (channelsLocked !== undefined) {
      this.channelsLocked = channelsLocked;
    }
    const updatedChannels = [];
    for (let c = 0; c < identifiers.length; c++) {
      updatedChannels.push({
        identifier: identifiers[c] || `Channel #${c + 1}`,
        name: channels[c] || `Channel #${c + 1}`,
        visible: channelsVisibility[c],
        domain: domains[c],
        contrastLimits: contrastLimits[c],
        color: colors[c],
        pixels: pixelValues[c],
        index: c
      });
    }
    const existing = Math.min(this.channels.length, updatedChannels.length);
    for (let i = 0; i < existing; i++) {
      this.channels[i].update(updatedChannels[i]);
    }
    if (updatedChannels.length < this.channels.length) {
      this.channels.splice(
        updatedChannels.length,
        this.channels.length - updatedChannels.length
      );
    } else if (updatedChannels.length > this.channels.length) {
      for (let i = this.channels.length; i < updatedChannels.length; i++) {
        this.channels.push(new ChannelState(updatedChannels[i]));
      }
    }
  };

  @action
  changeChannelVisibility = (channel, visible) => {
    if (this.viewer && typeof this.viewer.setChannelProperties === 'function') {
      const channelIndex = typeof channel === 'number' ? channel : this.channels.indexOf(channel);
      if (channelIndex >= 0 && channelIndex < this.channels.length) {
        const channelObj = this.channels[channelIndex];
        if (channelObj) {
          if (visible === channelObj.visible) {
            return;
          }
          channelObj.visible = visible;
        }
        this.viewer.setChannelProperties(channelIndex, {channelsVisibility: visible});
      }
    }
  };

  @action
  changeChannelContrastLimits = (channel, contrastLimits) => {
    if (this.viewer && typeof this.viewer.setChannelProperties === 'function') {
      const channelIndex = typeof channel === 'number' ? channel : this.channels.indexOf(channel);
      if (channelIndex >= 0 && channelIndex < this.channels.length) {
        const channelObj = this.channels[channelIndex];
        if (channelObj) {
          if (shallowCompareArrays(contrastLimits, channelObj.contrastLimits)) {
            return;
          }
          channelObj.contrastLimits = contrastLimits;
        }
        this.viewer.setChannelProperties(channelIndex, {contrastLimits});
      }
    }
  };

  @action
  changeChannelColor = (channel, color) => {
    if (this.viewer && typeof this.viewer.setChannelProperties === 'function') {
      const channelIndex = typeof channel === 'number' ? channel : this.channels.indexOf(channel);
      if (channelIndex >= 0 && channelIndex < this.channels.length) {
        const channelObj = this.channels[channelIndex];
        if (channelObj) {
          if (shallowCompareArrays(color, channelObj.color)) {
            return;
          }
          channelObj.color = color;
        }
        this.viewer.setChannelProperties(channelIndex, {colors: color});
      }
    }
  };

  @action
  toggleChannelsLock = (channelsLocked) => {
    if (this.viewer) {
      this.channelsLocked = !channelsLocked;
    }
  };

  @action
  changeColorMap = (colorMap) => {
    if (this.viewer && typeof this.viewer.setColorMap === 'function') {
      this.colorMap = colorMap;
      this.viewer.setColorMap(colorMap);
    }
  };

  @action
  changeLensMode = (mode) => {
    if (this.viewer && typeof this.viewer.setLensEnabled === 'function') {
      this.lensEnabled = mode;
      this.viewer.setLensEnabled(mode);
    }
  };

  @action
  changeLensChannel = (channelIndex) => {
    if (
      this.useLens &&
      this.lensEnabled &&
      this.viewer &&
      this.lensChannel !== Number(channelIndex) &&
      typeof this.viewer.setLensChannel === 'function' &&
      Number(channelIndex) >= 0
    ) {
      this.lensChannel = Number(channelIndex);
      this.viewer.setLensChannel(Number(channelIndex));
    }
  };

  @action
  changeGlobalZPosition = (z) => {
    if (this.viewer && typeof this.viewer.setGlobalZPosition === 'function') {
      this.imageZPosition = Number(z);
      this.viewer.setGlobalZPosition(Number(z));
    }
  };
}

export default ViewerState;
