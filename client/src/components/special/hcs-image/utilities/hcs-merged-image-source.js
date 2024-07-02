/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {getHDScreenshotSettings} from '../../cellprofiler/model/analysis/job-utilities';
import AnalysisApi from '../../cellprofiler/model/analysis/analysis-api';
import dataStorageAvailable from '../../../../models/dataStorage/DataStorageAvailable';
import {createObjectStorageWrapper} from '../../../../utils/object-storage';
import auditStorageAccessManager from '../../../../utils/audit-storage-access';
import channelsAreEqual from './channels-are-equal';
import HCSURLsManager from './hcs-urls-manager';

const DEFAULT_IMAGE_FORMAT = 'tiff';

function imageIdsAreEqual (imageIdsA, imageIdsB) {
  const a = [...new Set(imageIdsA || [])].sort();
  const b = [...new Set(imageIdsB || [])].sort();
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i += 1) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

function zPlanesSorter (a, b) {
  return Number(a) - Number(b);
}

function zPlanesAreEqual (zPlanesA, zPlanesB) {
  const a = [...new Set(zPlanesA || [])].sort(zPlanesSorter);
  const b = [...new Set(zPlanesB || [])].sort(zPlanesSorter);
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i += 1) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

class HcsMergedImageSource {
  @observable path;
  @observable pathMask;
  @observable storageId;
  @observable sequenceId;
  @observable timePoint = 0;
  @observable zPlanes = [0];
  @observable mergeZPlanes = false;
  @observable well = undefined;
  @observable imageIds;
  @observable channels = {};
  @observable screenshotEndpointAPI;
  @observable screenshotEndpointAPIError;
  @observable format = DEFAULT_IMAGE_FORMAT;
  @observable omeTiff = undefined;
  @observable urlsManager = new HCSURLsManager(undefined);
  /**
   * @type {ObjectStorage}
   */
  objectStorage;
  listeners = [];

  constructor () {
    (this.initialize)();
  }

  getScreenshotFileName (url, generatedFilePath, format) {
    if (this.path && generatedFilePath && this.well) {
      const name = this.path
        .split('/')
        .pop()
        .split('.')
        .slice(0, -1)
        .join('.')
        .replace(/\s/, '_');
      const format = generatedFilePath.split('.').pop();
      const {
        x,
        y
      } = this.well;
      const col = x + 1;
      const row = y + 1;
      const wellCol = col < 9 ? `0${col}` : col;
      const wellRow = row < 9 ? `0${row}` : row;
      const wellInfo = `r${wellRow}c${wellCol}`;
      const fieldInfo = !this.imageIds || !this.imageIds.length
        ? ''
        : `f${this.imageIds.map((id) => id.split(':').pop()).join(',')}`;
      const zPlanes = this.zPlanes.slice();
      const planeNumber = `p${zPlanes.map((z) => (z + 1)).join(',')}`;
      const timeSeriesNumber = `ts${this.sequenceId || 1}`;
      return `${name}-${wellInfo}${fieldInfo}${planeNumber}${timeSeriesNumber}.${format}`;
    }
    try {
      const urlObject = new URL(url);
      return urlObject.pathname.split('/').pop();
    } catch (_) {
      return `image.${format}`;
    }
  }

  @computed
  get available () {
    return this.imageIds &&
      this.imageIds.length > 0 &&
      this.storageId &&
      this.pathMask &&
      this.path &&
      this.sequenceId &&
      Object.keys(this.channels || {}).length > 0;
  }

  @computed
  get initialized () {
    return !!this.screenshotEndpointAPI;
  }

  @computed
  get omeTiffAvailable () {
    return this.omeTiff &&
      !this.omeTiff.pending &&
      this.omeTiff.storageId &&
      this.omeTiff.path;
  }

  @computed
  get omeTiffPending () {
    return this.omeTiff && this.omeTiff.pending;
  }

  @computed
  get omeTiffStorageId () {
    return this.omeTiff && this.omeTiff.storageId;
  }

  @computed
  get omeTiffPath () {
    return this.omeTiff && this.omeTiff.path;
  }

  @computed
  get omeTiffOffsetsPath () {
    return this.omeTiff && this.omeTiff.offsetsPath;
  }

  @computed
  get omeTiffGenerationError () {
    return this.omeTiff && this.omeTiff.error;
  }

  addOmeTiffGenerationListener = (listener) => {
    this.removeOmeTiffGenerationListener(listener);
    if (typeof listener === 'function') {
      this.listeners.push(listener);
    }
    this.urlsManager.addURLsGeneratedListener(listener);
  };

  removeOmeTiffGenerationListener = (listener) => {
    this.listeners = this.listeners.filter((aListener) => aListener !== listener);
    this.urlsManager.removeURLsGeneratedListener(listener);
  };

  reportOmeTiffGenerated = () => {
    this.listeners.forEach((aListener) => {
      if (typeof aListener === 'function') {
        aListener();
      }
    });
  };

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
      (this.generateOmeTiffIfRequired)();
    }
  };

  setSequenceTimePoints = (sequenceId, timePoint = 0) => {
    if (
      sequenceId !== this.sequenceId ||
      timePoint !== this.timePoint
    ) {
      this.sequenceId = sequenceId;
      this.timePoint = timePoint;
      (this.generateOmeTiffIfRequired)();
    }
  };

  setZPlanes = (zPlanes = [], mergeZPlanes = false) => {
    if (!zPlanesAreEqual(this.zPlanes, zPlanes)) {
      this.zPlanes = zPlanes.slice();
    }
    if (this.mergeZPlanes !== mergeZPlanes) {
      this.mergeZPlanes = mergeZPlanes;
    }
    (this.generateOmeTiffIfRequired)();
  };

  setWell = (well, imageIds) => {
    this.well = well;
    if (
      !imageIdsAreEqual(imageIds, this.imageIds)
    ) {
      this.imageIds = (imageIds || []).slice();
      (this.generateOmeTiffIfRequired)();
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
    if (this.viewerState) {
      this.viewerState.removeEventListener(this.setChannelsInfo);
    }
    this.viewerState = undefined;
  };

  destroy = () => {
    this.detachViewerState();
    this.stopOmeTiffGeneration();
    this.urlsManager.destroy();
    this.listeners = undefined;
  };

  initialize = async () => {
    if (this.screenshotEndpointAPI || this.screenshotEndpointAPIError) {
      return;
    }
    try {
      const settings = await getHDScreenshotSettings(true);
      const {
        api,
        format = DEFAULT_IMAGE_FORMAT,
        pollingIntervalSeconds
      } = settings;
      if (!api) {
        throw new Error('HCS screenshot endpoint is not specified');
      }
      this.screenshotEndpointAPI = new AnalysisApi(api);
      this.defaultFormat = format;
      this.pollingIntervalSeconds = pollingIntervalSeconds;
      (this.generateOmeTiffIfRequired)();
    } catch (e) {
      this.screenshotEndpointAPIError = e.message;
      this.screenshotEndpointAPI = undefined;
    }
  };

  getImageGenerationPayload = (omeTiff = false, format = undefined) => {
    if (!this.screenshotEndpointAPI) {
      throw new Error('Screenshot API is not available');
    }
    if (!this.well) {
      throw new Error('Error generating screenshot: well info is missing');
    }
    if (!this.imageIds || !this.imageIds.length) {
      throw new Error('Error generating screenshot: well fields info is missing');
    }
    if (!this.storageId || !this.path || !this.pathMask) {
      throw new Error('Error generating screenshot: unable to determine .hcs file location');
    }
    if (!this.sequenceId) {
      throw new Error('Error generating screenshot: sequence info is missing');
    }
    if (!this.zPlanes || !this.zPlanes.length) {
      throw new Error('Error generating screenshot: z-planes info is missing');
    }
    if (!omeTiff && Object.keys(this.channels || {}).length === 0) {
      throw new Error('Error generating screenshot: channels info is missing');
    }
    const payload = {
      original: omeTiff && this.imageIds && this.imageIds.length > 1 ? 0 : 1,
      channels: omeTiff ? {} : this.channels,
      imageIds: omeTiff && this.imageIds && this.imageIds.length > 1
        ? [this.well.wellImageId].filter(Boolean)
        : this.imageIds || [],
      storageId: this.storageId,
      path: this.path,
      pathMask: this.pathMask,
      sequenceId: this.sequenceId,
      timePoint: this.timePoint + 1,
      zPlanes: this.mergeZPlanes ? this.zPlanes : this.zPlanes.slice(0, 1),
      well: `${this.well.x + 1}_${this.well.y + 1}`
    };
    let path = payload.path;
    if (path.startsWith('/')) {
      path = path.slice(1);
    }
    if (payload.pathMask) {
      path = [payload.pathMask, path].join('/');
    }
    return {
      original: payload.original,
      well: payload.well,
      cells: payload.imageIds.map((id) => id.split(':').pop()).sort().join(','),
      zPlanes: payload.zPlanes.map((z) => `${z + 1}`).sort().join(','),
      timepoint: payload.timePoint,
      sequenceId: payload.sequenceId,
      path,
      format: omeTiff ? 'ome.tiff' : (format || this.defaultFormat),
      ...(
        Object
          .entries(payload.channels)
          .map(([channelName, configuration]) => ({
            [channelName]: configuration.join(',')
          }))
          .reduce((r, c) => ({...r, ...c}), {})
      )
    };
  };

  increaseOmeTiffGenerationToken = () => {
    this.omeTiffGenerationToken = (this.omeTiffGenerationToken || 0) + 1;
    return this.omeTiffGenerationToken;
  };

  stopOmeTiffGeneration = () => {
    this.increaseOmeTiffGenerationToken();
    if (this.abortSignal) {
      this.abortSignal.aborted = true;
    }
    this.currentPayload = undefined;
    this.omeTiff = undefined;
  };

  parseGeneratedPath = async (generatedPath) => {
    const e = /^\/?cloud-data\/([^/]+)\/(.*)$/i.exec(generatedPath);
    let resultedPath, storagePath;
    if (e && e.length === 3) {
      storagePath = e[1];
      resultedPath = e[2];
    } else {
      throw new Error('Unknown path format');
    }
    resultedPath = resultedPath.replace(/\/\//g, '/');
    await dataStorageAvailable.fetchIfNeededOrWait();
    const objectStorage = await createObjectStorageWrapper(
      dataStorageAvailable,
      storagePath,
      {read: true, write: false}
    );
    if (!objectStorage) {
      throw new Error(`Error generating screenshot: unknown storage "${storagePath}"`);
    }
    return {
      storageId: objectStorage ? objectStorage.id : undefined,
      objectStorage,
      path: resultedPath
    };
  }

  generateOmeTiffIfRequired = async () => {
    const required = this.mergeZPlanes && this.zPlanes && this.zPlanes.length > 1;
    if (!required) {
      this.stopOmeTiffGeneration();
      this.reportOmeTiffGenerated();
      this.urlsManager.changeObjectStorage(undefined);
      return;
    }
    let payload;
    try {
      payload = this.getImageGenerationPayload(true);
      const {
        original: currentOriginal,
        well: currentWell,
        cells: currentCells,
        zPlanes: currentZPlanes,
        timepoint: currentTimepoint,
        sequenceId: currentSequenceId,
        path: currentPath
      } = this.currentPayload || {};
      if (
        currentOriginal === payload.original &&
        currentWell === payload.well &&
        currentCells === payload.cells &&
        currentZPlanes === payload.zPlanes &&
        currentTimepoint === payload.timepoint &&
        currentSequenceId === payload.sequenceId &&
        currentPath === payload.path
      ) {
        payload = undefined;
      }
    } catch (error) {
      console.warn(error.message);
    }
    if (payload) {
      this.stopOmeTiffGeneration();
      this.omeTiff = {
        pending: true,
        error: undefined,
        storageId: undefined,
        path: undefined,
        offsetsPath: undefined
      };
      const token = this.increaseOmeTiffGenerationToken();
      try {
        this.currentPayload = payload;
        this.abortSignal = {
          aborted: false
        };
        const result = await this.screenshotEndpointAPI.generateScreenshot(
          payload,
          this.pollingIntervalSeconds,
          this.abortSignal
        );
        if (token !== this.omeTiffGenerationToken) {
          return;
        }
        const {
          path: omeTiffPath
        } = result || {};
        if (!omeTiffPath) {
          throw new Error('Error generating merged image');
        }
        const {
          objectStorage,
          path: resultedPath
        } = await this.parseGeneratedPath(omeTiffPath);
        let folder = resultedPath || '';
        if (!folder || !folder.length) {
          throw new Error('Error generating merged image');
        }
        if (folder.endsWith('/')) {
          folder = folder.slice(0, -1);
        }
        this.urlsManager.changeObjectStorage(objectStorage);
        this.omeTiff.storageId = objectStorage.id;
        this.omeTiff.path = `${folder}/data.ome.tiff`;
        this.omeTiff.offsetsPath = `${folder}/data.offsets.json`;
      } catch (error) {
        console.warn(error.message);
        if (token === this.omeTiffGenerationToken) {
          this.omeTiff.error = error.message;
        }
      } finally {
        if (token === this.omeTiffGenerationToken && this.omeTiff) {
          this.omeTiff.pending = false;
          this.reportOmeTiffGenerated();
        }
      }
    }
  };

  generateUrl = async (omeTiff = false, format = undefined) => {
    const query = this.getImageGenerationPayload(omeTiff, format);
    const result = await this.screenshotEndpointAPI.generateScreenshot(
      query,
      this.pollingIntervalSeconds
    );
    const {
      path: screenshotFilePath
    } = result || {};
    if (!screenshotFilePath) {
      throw new Error('Error generating video');
    }
    const {
      objectStorage,
      path: resultedPath
    } = await this.parseGeneratedPath(screenshotFilePath);
    this.objectStorage = objectStorage;
    const screenshotURL = await this.objectStorage.generateFileUrl(resultedPath);
    const screenshotAccessCallback = () => auditStorageAccessManager.reportReadAccess({
      storageId: this.objectStorage.id,
      path: resultedPath,
      reportStorageType: 'S3'
    });
    return {
      url: screenshotURL,
      name: this.getScreenshotFileName(screenshotURL, resultedPath, format),
      accessCallback: screenshotAccessCallback
    };
  };
}

export default HcsMergedImageSource;
