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

import {action, computed, observable} from 'mobx';
import HCSBaseState from './base-state';

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

class ViewerState extends HCSBaseState {
  @observable loader;
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
  @observable lockedChannels = [];
  @observable imageZPosition = 0;
  @observable fieldID;
  @observable videoPayload;
  listeners = [];

  @computed
  get allChannelsLocked () {
    const lockedChannels = this.lockedChannels || [];
    return !(this.channels || []).some((channel) => !lockedChannels.includes(channel.name));
  }

  constructor (viewer) {
    super(viewer, 'viewerStateChanged');
  }

  addEventListener = (listener) => {
    this.listeners.push(listener);
  };

  removeEventListener = (listener) => {
    this.listeners = this.listeners.filter(aListener => aListener !== listener);
  };

  emitOnChange = () => {
    this.listeners
      .filter(aListener => typeof aListener === 'function')
      .forEach(aListener => aListener(this));
  };

  @action
  onStateChanged (viewer, newState) {
    const {
      loader,
      identifiers = [],
      channels = [],
      channelsVisibility = [],
      lockChannels,
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
    this.loader = loader;
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
    if (metadata && metadata.Name && /field [\d]+/i.test(metadata.Name)) {
      const e = /field ([\d]+)/i.exec(metadata.Name);
      if (e && e.length) {
        this.fieldID = Number(e[1]);
      } else {
        this.fieldID = undefined;
      }
    } else {
      this.fieldID = undefined;
    }
    /**
     * updated channels options
     * @type {ChannelOptions[]}
     */
    if (lockChannels !== undefined) {
      if (typeof lockChannels === 'boolean') {
        this.lockedChannels = lockChannels ? channels.slice() : [];
      } else if (Array.isArray(lockChannels)) {
        this.lockedChannels = lockChannels.slice();
      }
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
    this.emitOnChange();
  }

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
    this.emitOnChange();
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
    this.emitOnChange();
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
    this.emitOnChange();
  };

  @action
  setChannelLocked = (channel, locked) => {
    if (this.viewer && typeof this.viewer.setLockChannels === 'function') {
      let channelName = channel;
      if (typeof channel === 'object' && typeof channel.name === 'string') {
        channelName = channel.name;
      }
      this.lockedChannels = this.lockedChannels
        .filter(c => c !== channelName)
        .concat(locked ? [channelName] : []);
      this.viewer.setLockChannels(this.lockedChannels.slice());
    }
  };

  @action
  setChannelsLocked = (locked) => {
    if (this.viewer && typeof this.viewer.setLockChannels === 'function') {
      this.lockedChannels = locked
        ? (this.channels || []).map(c => c.name)
        : [];
      this.viewer.setLockChannels(locked);
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
