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

import {computed, observable} from 'mobx';
import {getVideoSettings} from '../../cellprofiler/model/analysis/job-utilities';
import AnalysisApi from '../../cellprofiler/model/analysis/analysis-api';
import dataStorageAvailable from '../../../../models/dataStorage/DataStorageAvailable';
import {createObjectStorageWrapper} from '../../../../utils/object-storage';

const DEFAULT_DELAY_MS = 500;

function timePointsArraysAreEqual (arr1, arr2) {
  const sortedArray1 = [...new Set(arr1)].sort((a, b) => Number(a) - Number(b));
  const sortedArray2 = [...new Set(arr2)].sort((a, b) => Number(a) - Number(b));
  if (sortedArray1.length !== sortedArray2.length) {
    return false;
  }
  for (let i = 0; i < sortedArray1.length; i++) {
    if (sortedArray1[i] !== sortedArray2[i]) {
      return false;
    }
  }
  return true;
}

function channelsAreEqual (channelsSet1, channelsSet2) {
  const channels1 = Object.keys(channelsSet1 || {}).sort();
  const channels2 = Object.keys(channelsSet2 || {}).sort();
  if (channels1.length !== channels2.length) {
    return false;
  }
  for (let c = 0; c < channels1.length; c++) {
    const channelName1 = channels1[c];
    const channelName2 = channels2[c];
    const channel1 = channelsSet1[channelName1];
    const channel2 = channelsSet2[channelName2];
    if (
      channelName1 !== channelName2 ||
      channel1.length !== channel2.length
    ) {
      return false;
    }
    for (let i = 0; i < channel1.length; i++) {
      if (channel1[i] !== channel2[i]) {
        return false;
      }
    }
  }
  return true;
}

class HcsVideoSource {
  @observable path;
  @observable pathMask;
  @observable storageId;
  @observable sequenceId;
  @observable timePoints = [];
  @observable wellView = false;
  @observable imageId;
  @observable channels = {};
  @observable videoEndpointAPI;
  @observable videoEndpointAPIError;
  @observable generatedFilePath;
  @observable videoUrl;
  @observable videoError;
  @observable videoPending;
  @observable videoMode = false;
  @observable playbackSpeed = 1;
  @observable crossOrigin = 'anonymous';
  @observable loop = true;
  /**
   * @type {ObjectStorage}
   */
  objectStorage;

  responseToken = 0;

  constructor () {
    (this.initialize)();
  }

  @computed
  get available () {
    return this.imageId &&
      this.storageId &&
      this.pathMask &&
      this.path &&
      this.sequenceId &&
      Object.keys(this.channels || {}).length > 0;
  }

  @computed
  get initialized () {
    return !!this.videoEndpointAPI;
  }

  setVideoMode = (enabled) => {
    if (enabled !== this.videoMode) {
      this.videoMode = enabled;
      (this.generateUrl)();
    }
  }

  setHcsFile = (fileOptions = {}) => {
    const {
      path,
      pathMask,
      storageId
    } = fileOptions;
    if (
      path !== this.path ||
      storageId !== this.storageId ||
      pathMask !== this.pathMask
    ) {
      this.path = path;
      this.pathMask = pathMask;
      this.storageId = storageId;
      this.generateUrlDelayed();
    }
  };

  setPlaybackSpeed = (speed) => {
    if (speed !== this.playbackSpeed) {
      this.playbackSpeed = speed;
      this.generateUrlDelayed();
    }
  }

  setSequenceTimePoints = (sequenceId, timePoints = []) => {
    if (
      sequenceId !== this.sequenceId ||
      !timePointsArraysAreEqual(timePoints, this.timePoints)
    ) {
      this.sequenceId = sequenceId;
      this.timePoints = timePoints;
      this.generateUrlDelayed();
    }
  };

  setWellView = (wellView, imageId) => {
    if (wellView !== this.wellView || imageId !== this.imageId) {
      this.wellView = wellView;
      this.imageId = imageId;
      (this.generateUrl)();
    }
  };

  setChannelsInfo = () => {
    if (this.viewerState) {
      const channels = (this.viewerState.channels || [])
        .filter(channel => channel.visible)
        .map(channel => ({
          [channel.name]: [...channel.color.slice(0, 3), ...channel.contrastLimits]
        }))
        .reduce((r, c) => ({...r, ...c}), {});
      if (!channelsAreEqual(channels, this.channels)) {
        this.channels = {...channels};
        this.generateUrlDelayed();
      }
    }
  };

  attachViewerState = (viewerState) => {
    this.detachViewerState();
    this.viewerState = viewerState;
    if (this.viewerState) {
      this.viewerState.addEventListener(this.setChannelsInfo);
    }
    this.setChannelsInfo();
  };

  detachViewerState = () => {
    this.clearVideoGenerationTimeout();
    if (this.viewerState) {
      this.viewerState.removeEventListener(this.setChannelsInfo);
    }
    this.viewerState = undefined;
  };

  destroy = () => {
    this.clearVideoGenerationTimeout();
    this.detachViewerState();
  };

  clearVideoGenerationTimeout = () => {
    clearTimeout(this.urlGenerationTimer);
    this.urlGenerationTimer = undefined;
  };

  generateUrlDelayed = () => {
    this.clearVideoGenerationTimeout();
    this.urlGenerationTimer = setTimeout(this.generateUrl, DEFAULT_DELAY_MS);
  };

  initializeVideoEndpoint = async () => {
    if (this.videoEndpointAPI || this.videoEndpointAPIError) {
      return;
    }
    try {
      const settings = await getVideoSettings(true);
      const {
        api,
        crossorigin = 'anonymous',
        crossOrigin = crossorigin,
        defaultFPS = 1,
        loop = true
      } = settings;
      if (!api) {
        throw new Error('HCS video endpoint is not specified');
      }
      this.videoEndpointAPI = new AnalysisApi(api);
      this.crossOrigin = crossOrigin;
      this.loop = loop;
      this.playbackSpeed = defaultFPS;
    } catch (e) {
      this.videoEndpointAPIError = e.message;
      this.videoEndpointAPI = undefined;
    }
  };

  initialize = async () => {
    await this.initializeVideoEndpoint();
    await this.generateUrl();
  }

  generateUrl = async () => {
    this.videoPending = true;
    this.clearVideoGenerationTimeout();
    if (!this.videoEndpointAPI) {
      this.videoPending = false;
      return;
    }
    if (!this.videoMode) {
      this.videoPending = false;
      return;
    }
    const payload = {
      channels: this.channels,
      imageId: this.imageId,
      storageId: this.storageId,
      path: this.path,
      pathMask: this.pathMask,
      sequenceId: this.sequenceId,
      timePoints: [...this.timePoints],
      wellView: this.wellView,
      fps: this.playbackSpeed
    };
    const {
      imageId: currentImageId,
      sequenceId: currentSequenceId,
      storageId: currentStorageId,
      path: currentPath,
      pathMask: currentPathMask,
      // timePoints: currentTimePoints = [],
      wellView: currentWellView,
      channels: currentChannels = {},
      fps: currentFPS
    } = this.currentPayload || {};
    this.currentPayload = payload;
    if (
      payload.imageId &&
      payload.storageId &&
      payload.pathMask &&
      payload.path &&
      payload.sequenceId &&
      // payload.timePoints.length > 1 &&
      Object.keys(payload.channels || {}).length > 0 &&
      (
        payload.storageId !== currentStorageId ||
        payload.imageId !== currentImageId ||
        payload.sequenceId !== currentSequenceId ||
        payload.path !== currentPath ||
        payload.pathMask !== currentPathMask ||
        // !timePointsArraysAreEqual(currentTimePoints, payload.timePoints) ||
        !channelsAreEqual(currentChannels, payload.channels || {}) ||
        payload.wellView !== currentWellView ||
        payload.fps !== currentFPS
      )
    ) {
      let path = payload.path;
      if (path.startsWith('/')) {
        path = path.slice(1);
      }
      if (payload.pathMask) {
        path = [payload.pathMask, path].join('/');
      }
      const query = {
        cell: payload.imageId.split(':').pop(),
        byField: payload.wellView ? 0 : 1,
        sequenceId: payload.sequenceId,
        path,
        fps: payload.fps,
        ...(
          Object
            .entries(payload.channels)
            .map(([channelName, configuration]) => ({
              [channelName]: configuration.join(',')
            }))
            .reduce((r, c) => ({...r, ...c}), {})
        )
      };
      const responseToken = this.responseToken = this.responseToken + 1;
      try {
        const result = await this.videoEndpointAPI.generateVideo(query);
        if (this.responseToken === responseToken) {
          this.videoError = undefined;
          const {
            path: videoFilePath
          } = result || {};
          if (!videoFilePath) {
            throw new Error('Error generating video');
          }
          const e = /^\/?cloud-data\/([^/]+)\/(.*)$/i.exec(videoFilePath);
          let resultedPath, storagePath;
          if (e && e.length === 3) {
            storagePath = e[1];
            resultedPath = e[2];
          } else {
            throw new Error('Error generating video: unknown path format');
          }
          resultedPath = resultedPath.replace(/\/\//g, '/');
          if (
            !this.objectStorage ||
            this.objectStorage.path !== storagePath
          ) {
            await dataStorageAvailable.fetchIfNeededOrWait();
            this.objectStorage = await createObjectStorageWrapper(
              dataStorageAvailable,
              storagePath,
              {read: true, write: false}
            );
          }
          if (!this.objectStorage) {
            throw new Error(`Error generating video: unknown storage "${storagePath}"`);
          }
          this.generatedFilePath = resultedPath;
          await this.generateSignedUrl();
        }
      } catch (e) {
        if (this.responseToken === responseToken) {
          console.log(e.message);
          this.videoError = e.message;
        }
      } finally {
        if (this.responseToken === responseToken) {
          this.videoPending = false;
        }
      }
    } else if (this.generatedFilePath && this.objectStorage) {
      await this.generateSignedUrl();
      this.videoPending = false;
    } else {
      this.videoPending = false;
    }
  };

  generateSignedUrl = async () => {
    if (!this.objectStorage || !this.generatedFilePath) {
      return;
    }
    this.videoUrl = await this.objectStorage.generateFileUrl(this.generatedFilePath);
  };
}

export default HcsVideoSource;
