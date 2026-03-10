/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import {makeObservable, observable, computed, action} from 'mobx';
import HCSBaseState from './base-state';

class DelayedSliceValue {
  constructor (callback, property, delay = 50) {
    this.delay = delay;
    this.callback = callback;
    this.property = property;
    this.handle = undefined;
  }

  setValue (value) {
    this.value = value;
    if (this.handle === undefined) {
      this.handle = setTimeout(() => {
        this.callback({[this.property]: this.value});
        this.handle = undefined;
      }, this.delay);
    }
  }
}

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
  index = 0;
  identifier = 'Channel';
  name = 'Channel';
  visible = true;
  domain = [];
  contrastLimits = [];
  color = [];
  pixels;

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
    makeObservable(this, {
      index: observable,
      identifier: observable,
      name: observable,
      visible: observable,
      domain: observable,
      contrastLimits: observable,
      color: observable,
      pixels: observable,
      update: action
    });
    this.update(options);
  }

  /**
   * Updates channel state
   * @param {ChannelOptions} options
   */
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
  loader;
  use3D = false;
  volumetricViewerAvailable = false;
  useLens = false;
  useColorMap = false;
  colorMap = '';
  lensEnabled = false;
  lensChannel = 0;
  pending = false;
  isRGB = false;
  xSlice = [0, 0];
  xSliceRange = [0, 0];
  ySlice = [0, 0];
  ySliceRange = [0, 0];
  zSlice = [0, 0];
  zSliceRange = [0, 0];
  xSliceEnabled = false;
  ySliceEnabled = false;
  zSliceEnabled = false;
  selection = [];
  dimensions = [];
  downsamplingModes = [];
  downsamplingMode = 0;
  renderingModes = [];
  renderingMode = 0;
  /**
   * Channels state
   * @type {ChannelState[]}
   */
  channels = [];
  lockedChannels = [];
  imageZPosition = 0;
  fieldID;
  videoPayload;
  listeners = [];

  get allChannelsLocked () {
    const lockedChannels = this.lockedChannels || [];
    return !(this.channels || []).some((channel) => !lockedChannels.includes(channel.name));
  }

  constructor (viewer) {
    super(viewer, 'viewerStateChanged');
    this.xSliceDelayed = new DelayedSliceValue(this.set3D.bind(this), 'xSlice');
    this.ySliceDelayed = new DelayedSliceValue(this.set3D.bind(this), 'ySlice');
    this.zSliceDelayed = new DelayedSliceValue(this.set3D.bind(this), 'zSlice');
    makeObservable(this, {
      loader: observable,
      use3D: observable,
      volumetricViewerAvailable: observable,
      useLens: observable,
      useColorMap: observable,
      colorMap: observable,
      lensEnabled: observable,
      lensChannel: observable,
      pending: observable,
      isRGB: observable,
      xSlice: observable,
      xSliceRange: observable,
      ySlice: observable,
      ySliceRange: observable,
      zSlice: observable,
      zSliceRange: observable,
      xSliceEnabled: observable,
      ySliceEnabled: observable,
      zSliceEnabled: observable,
      selection: observable,
      dimensions: observable,
      downsamplingModes: observable,
      downsamplingMode: observable,
      renderingModes: observable,
      renderingMode: observable,
      channels: observable,
      lockedChannels: observable,
      imageZPosition: observable,
      fieldID: observable,
      videoPayload: observable,
      listeners: observable,
      xSliceDelayed: observable,
      ySliceDelayed: observable,
      zSliceDelayed: observable,
      allChannelsLocked: computed,
      addEventListener: action,
      removeEventListener: action,
      emitOnChange: action,
      onStateChanged: action,
      changeChannelVisibility: action,
      changeChannelContrastLimits: action,
      changeChannelColor: action,
      setChannelLocked: action,
      setChannelsLocked: action,
      changeColorMap: action,
      set3D: action,
      change3dMode: action,
      changeDownsamplingMode: action,
      changeRenderingMode: action,
      changeXSlice: action,
      changeYSlice: action,
      changeZSlice: action,
      changeLensMode: action,
      changeLensChannel: action,
      changeGlobalZPosition: action
    });
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
      xSliceRange = [],
      ySlice = [],
      ySliceRange = [],
      zSlice = [],
      zSliceRange = [],
      xSliceEnabled = false,
      ySliceEnabled = false,
      zSliceEnabled = false,
      use3D = false,
      isRGB,
      pending = false,
      metadata,
      loader3DIndex: downsamplingMode,
      loadersInfo = [],
      renderingModeIdx: renderingMode,
      renderingModes3D = []
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
    this.xSliceRange = xSliceRange;
    this.ySlice = ySlice;
    this.ySliceRange = ySliceRange;
    this.zSlice = zSlice;
    this.zSliceRange = zSliceRange;
    this.xSliceEnabled = xSliceEnabled;
    this.ySliceEnabled = ySliceEnabled;
    this.zSliceEnabled = zSliceEnabled;
    this.selection = globalSelection;
    this.dimensions = globalDimensions;
    this.imageZPosition = globalSelection && globalSelection.z
      ? globalSelection.z
      : 0;
    this.downsamplingMode = downsamplingMode;
    this.downsamplingModes = loadersInfo.filter((dm) => dm.loadable).map((dm) => ({
      id: dm.loaderIdx,
      name: dm.loaderIdx === 0 ? 'No downsampling' : `Downsample ${dm.loaderIdx + 1}x`,
      bytes: dm.bytesPerChannel
    }));
    this.renderingMode = renderingMode;
    this.renderingModes = renderingModes3D;
    this.volumetricViewerAvailable = this.downsamplingModes.length > 0 && this.renderingModes.length > 0;
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

  setChannelsLocked = (locked) => {
    if (this.viewer && typeof this.viewer.setLockChannels === 'function') {
      this.lockedChannels = locked
        ? (this.channels || []).map(c => c.name)
        : [];
      this.viewer.setLockChannels(locked);
    }
  };

  changeColorMap = (colorMap) => {
    if (this.viewer && typeof this.viewer.setColorMap === 'function') {
      this.colorMap = colorMap;
      this.viewer.setColorMap(colorMap);
    }
  };

  set3D = (opts = {}) => {
    if (this.use3D) {
      const slice = (o) => [o[0], o[1]];
      const payload = {
        use3D: true,
        loader3DIndex: this.downsamplingMode,
        renderingModeIdx: this.renderingMode,
        xSlice: this.xSlice.slice(),
        ySlice: this.ySlice.slice(),
        zSlice: this.zSlice.slice(),
        ...opts
      };
      payload.xSlice = slice(payload.xSlice);
      payload.ySlice = slice(payload.ySlice);
      payload.zSlice = slice(payload.zSlice);
      this.viewer.set3D(payload);
    } else {
      this.viewer.set3D(false);
    }
  }

  change3dMode = (enabled) => {
    if (this.viewer) {
      this.use3D = enabled;
      this.viewer.set3D(enabled);
    }
  };

  changeDownsamplingMode = (mode) => {
    if (this.viewer) {
      this.downsamplingMode = mode;
      this.set3D();
    }
  };

  changeRenderingMode = (mode) => {
    if (this.viewer) {
      this.renderingMode = mode;
      this.set3D();
    }
  };

  changeXSlice = (slice) => {
    if (this.viewer) {
      this.xSlice = slice;
      this.xSliceDelayed.setValue(slice);
    }
  };

  changeYSlice = (slice) => {
    if (this.viewer) {
      this.ySlice = slice;
      this.ySliceDelayed.setValue(slice);
    }
  };

  changeZSlice = (slice) => {
    if (this.viewer) {
      this.zSlice = slice;
      this.zSliceDelayed.setValue(slice);
    }
  };

  changeLensMode = (mode) => {
    if (this.viewer && typeof this.viewer.setLensEnabled === 'function') {
      this.lensEnabled = mode;
      this.viewer.setLensEnabled(mode);
    }
  };

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

  changeGlobalZPosition = (z) => {
    if (this.viewer && typeof this.viewer.setGlobalZPosition === 'function') {
      this.imageZPosition = Number(z);
      this.viewer.setGlobalZPosition(Number(z));
    }
  };
}

export default ViewerState;
