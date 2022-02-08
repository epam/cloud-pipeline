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

class ChannelState {
  @observable identifier = 'Channel';
  @observable name = 'Channel';
  @observable visible = true;
  @observable domain = [];
  @observable contrastLimits = [];
  @observable color = [];
  @observable pixels;

  /**
   * @typedef {Object} ChannelOptions
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
      identifier = 'Channel',
      name = 'Channel',
      visible = true,
      domain = [0, 1],
      contrastLimits = [0, 1],
      color = [255, 255, 255],
      pixels
    } = options;
    this.identifier = identifier;
    this.name = name;
    this.visible = visible;
    this.domain = domain;
    this.contrastLimits = contrastLimits;
    this.color = color;
    this.pixels = pixels;
  }
}

class ViewerState {
  @observable use3D = false;
  @observable useLens = false;
  @observable useColorMap = false;
  @observable colorMap = '';
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
      globalSelection = {},
      globalDimensions = [],
      pixelValues = [],
      colors = [],
      domains = [],
      contrastLimits = [],
      useLens = false,
      useColorMap = true,
      colorMap = '',
      xSlice = [],
      ySlice = [],
      zSlice = [],
      use3D = false,
      isRGB,
      pending = false
    } = newState || {};
    this.pending = pending;
    if (pending) {
      return;
    }
    this.use3D = use3D;
    this.useLens = useLens;
    this.useColorMap = useColorMap;
    this.colorMap = colorMap;
    this.isRGB = isRGB;
    this.xSlice = xSlice;
    this.ySlice = ySlice;
    this.zSlice = zSlice;
    this.selection = globalSelection;
    this.dimensions = globalDimensions;
    /**
     * updated channels options
     * @type {ChannelOptions[]}
     */
    const updatedChannels = [];
    for (let c = 0; c < identifiers.length; c++) {
      updatedChannels.push({
        identifier: identifiers[c] || `Channel #${c + 1}`,
        name: channels[c] || `Channel #${c + 1}`,
        visible: channelsVisibility[c],
        domain: domains[c],
        contrastLimits: contrastLimits[c],
        color: colors[c],
        pixels: pixelValues[c]
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
          channelObj.color = color;
        }
        this.viewer.setChannelProperties(channelIndex, {colors: color});
      }
    }
  };

  @action
  changeColorMap = (colorMap) => {
    if (this.viewer && typeof this.viewer.setColorMap === 'function') {
      this.colorMap = colorMap;
      this.viewer.setColorMap(colorMap);
    }
  };
}

export default ViewerState;
