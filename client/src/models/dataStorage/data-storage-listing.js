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

import {action, computed, observable} from 'mobx';
import moment from 'moment-timezone';
import dataStorages from './DataStorages';
import preferences from '../preferences/PreferencesLoad';
import authenticatedUserInfo from '../user/WhoAmI';
import DataStorageRequest from './DataStoragePage';
import DataStorageFilter from './DataStorageFilter';
import roleModel from '../../utils/roleModel';
import MetadataLoad from '../metadata/MetadataLoad';

const DEFAULT_DELIMITER = '/';
const PAGE_SIZE = 40;

const mbToBytes = mb => {
  if (isNaN(mb)) {
    return;
  }
  return Math.round(mb * (1024 ** 2));
};

/**
 * Returns true if user is allowed to download from storage according to the
 * `download.enabled` attribute value
 * @param value
 * @param userGroupsSet
 * @returns {boolean}
 */
export function checkStorageDownloadEnabledAttributeValue (value, userGroupsSet) {
  if (value === undefined) {
    return true;
  }
  try {
    if (/^(true|false)$/i.test(value)) {
      return /^true$/i.test(value);
    }
    const array = value.split(/[,;\s]/g).map((item) => item.trim().toLowerCase());
    if (Array.isArray(array)) {
      return array.some((item) => userGroupsSet.has((item.toLowerCase())));
    }
  } catch (_) {
    // empty
  }
  return true;
}

/**
 * @typedef {Object} CorrectPathOptions
 * @property {string} [delimiter=/]
 * @property {boolean} [trailingSlash=true]
 * @property {boolean} [leadingSlash=true]
 * @property {boolean} [undefinedAsEmpty=false]
 */

/**
 * @param {string} [path]
 * @param {CorrectPathOptions} [options]
 * @returns {string}
 */
function correctPath (path = '', options = {}) {
  const {
    delimiter = DEFAULT_DELIMITER,
    trailingSlash = true,
    leadingSlash = true,
    undefinedAsEmpty = false
  } = options;
  // Removing leading / trailing slashes and double/triple/... slashes
  const parts = path
    .split(delimiter)
    .filter(pathComponent => pathComponent.length);
  // Formatting path to the "/...", ".../" or "/.../" format
  if (parts.length === 0 && (trailingSlash || leadingSlash)) {
    return '/';
  }
  if (parts.length === 0) {
    return undefinedAsEmpty ? undefined : '';
  }
  return [
    ...(leadingSlash ? [''] : []),
    ...parts,
    ...(trailingSlash ? [''] : [])
  ].join(delimiter);
}

/**
 * @typedef {number|string} PageMarker
 */

/**
 * @typedef {Object} PathMarkers
 * @property {number} currentPage - zero-based
 * @property {PageMarker[]} markers
 */

/**
 * @typedef {{[key: string]: PathMarkers}} Markers
 */

/**
 * @param {string} path
 * @param {Markers} markers
 * @param {{delimiter: string?, keepCurrent: boolean?}} [options]
 * @returns {Markers}
 */
function resetMarkersForPath (path = '', markers = {}, options = {}) {
  const {
    delimiter = DEFAULT_DELIMITER,
    keepCurrent = false
  } = options;
  const correctedPath = correctPath(path, {delimiter});
  /**
   * @type {Markers}
   */
  const result = {};
  Object.entries(markers)
    .forEach(([pathKey, markers]) => {
      if (
        (keepCurrent && pathKey === correctedPath) ||
        (pathKey !== correctedPath && !pathKey.startsWith(correctedPath))
      ) {
        result[pathKey] = markers;
      }
    });
  return result;
}

/**
 * @param {string} path
 * @param {Markers} markers
 * @param {{delimiter: string?}} [options]
 * @returns {Markers}
 */
function ensureMarkersForPath (path = '', markers = {}, options = {}) {
  const {
    delimiter = DEFAULT_DELIMITER
  } = options;
  const correctedPath = correctPath(path, {delimiter});
  if (markers[correctedPath]) {
    return markers;
  }
  return {
    ...markers,
    [correctedPath]: {
      currentPage: 0,
      markers: [undefined]
    }
  };
}

/**
 * @param {string} path
 * @param {Markers} markers
 * @returns {PathMarkers}
 */
function getPathMarkers (path, markers = {}) {
  return markers[path] || {
    currentPage: 0,
    markers: [undefined]
  };
}

/**
 * @param {string} path
 * @param {Markers} markers
 * @returns {PageMarker}
 */
function getCurrentPageMarker (path, markers = {}) {
  const {
    currentPage = 0,
    markers: pathMarkers = [undefined]
  } = getPathMarkers(path, markers);
  return pathMarkers[currentPage];
}

/**
 * @param {string} path
 * @param {PageMarker} marker
 * @param {Markers} markers
 * @param {{delimiter: string?}} options
 * @returns {Markers}
 */
function insertNextPageMarker (path, marker, markers = {}, options = {}) {
  const {
    delimiter = DEFAULT_DELIMITER
  } = options;
  const correctedPath = correctPath(path, {delimiter});
  const newMarkers = ensureMarkersForPath(correctedPath, markers);
  const {
    currentPage,
    markers: pathMarkers = [undefined]
  } = newMarkers[correctedPath];
  if (currentPage + 1 <= pathMarkers.length && marker) {
    const newPathMarkers = pathMarkers.slice(0, currentPage + 1).concat(marker);
    return {
      ...markers,
      [correctedPath]: {
        currentPage,
        markers: newPathMarkers
      }
    };
  }
  return markers;
}

/**
 * @param {string} path
 * @param {function} pageFn
 * @param {Markers} markers
 * @param {{delimiter: string?}} options
 * @returns {Markers|undefined} Returns new markers or undefined if nothing changed
 */
function setCurrentPage (path, pageFn, markers = {}, options = {}) {
  if (typeof pageFn !== 'function') {
    return undefined;
  }
  const {
    delimiter = DEFAULT_DELIMITER
  } = options;
  const correctedPath = correctPath(path, {delimiter});
  const newMarkers = ensureMarkersForPath(correctedPath, markers);
  const {
    currentPage = 0,
    markers: pathMarkers = [undefined]
  } = newMarkers[correctedPath];
  const page = pageFn(currentPage);
  if (page >= 0 && page < pathMarkers.length && page !== currentPage) {
    return {
      ...markers,
      [correctedPath]: {
        currentPage: page,
        markers: pathMarkers.slice(0, page + 1)
      }
    };
  }
  return undefined;
}

/**
 * @typedef {Object} DataStoragePagesOptions
 * @property {boolean} [keepPagesHistory=true]
 * @property {number} [pageSize]
 */

class DataStorageListing {
  @observable storageId;
  @observable path;
  @observable showVersions;
  @observable showArchives;
  @observable markers = {};
  /**
   * Storage info (DataStorage request)
   */
  @observable storageRequest;
  @observable pagePending = false;
  @observable pageLoaded = false;
  @observable pageError = undefined;
  @observable pageElements = [];
  /**
   * Current page path. Leading & trailing slashes are removed.
   * "undefined" is returned if current path is root
   */
  @observable pagePath;
  @observable downloadEnabled = false;

  /**
   * Filters info.
   * Request results may be truncated.
   */
  @observable filters = {
    name: undefined,
    sizeGreaterThan: undefined,
    sizeLessThan: undefined,
    dateFilterType: undefined,
    dateAfter: undefined,
    dateBefore: undefined
  };
  @observable resultsTruncated = false;
  @observable filtersApplied = false;

  /**
   * @param {DataStoragePagesOptions} options
   */
  constructor (options = {}) {
    const {
      keepPagesHistory,
      pageSize = PAGE_SIZE
    } = options;
    this.keepPagesHistory = keepPagesHistory;
    this.pageSize = pageSize;
  }

  destroy () {
    this._increaseUniqueToken();
    this.storageRequest = undefined;
    this.markers = undefined;
  }

  @computed
  get infoPending () {
    return this.storageRequest && this.storageRequest.pending;
  }

  @computed
  get pending () {
    return this.infoPending || this.pagePending;
  }

  @computed
  get infoLoaded () {
    return this.storageRequest && this.storageRequest.loaded;
  }

  @computed
  get loaded () {
    return this.infoLoaded && this.pageLoaded;
  }

  @computed
  get infoError () {
    return this.storageRequest
      ? this.storageRequest.error
      : undefined;
  }

  @computed
  get error () {
    return this.infoError || this.pageError;
  }

  @computed
  get info () {
    if (!this.storageRequest || !this.storageRequest.loaded) {
      return undefined;
    }
    return this.storageRequest.value;
  }

  @computed
  get readAllowed () {
    return this.info && roleModel.readAllowed(this.info);
  }

  @computed
  get writeAllowed () {
    return this.info && roleModel.writeAllowed(this.info);
  }

  @computed
  get executeAllowed () {
    return this.info && roleModel.executeAllowed(this.info);
  }

  @computed
  get isOwner () {
    return this.info && roleModel.isOwner(this.info);
  }

  /**
   * @returns {{page: number, first: boolean, next: boolean, previous: boolean}}
   */
  @computed
  get currentPagination () {
    const {
      currentPage = 0,
      markers = [undefined]
    } = getPathMarkers(this.path, this.markers);
    return {
      page: currentPage,
      first: currentPage > 0,
      previous: currentPage > 0,
      next: currentPage + 1 < markers.length
    };
  }

  @computed
  get currentFilter () {
    return this.filters;
  }

  @computed
  get filtersEmpty () {
    if (!this.currentFilter) {
      return true;
    }
    return Object.values(this.currentFilter)
      .every(value => value === undefined);
  }

  @computed
  get resultsFiltered () {
    return this.filtersApplied && !this.filtersEmpty;
  }

  @computed
  get resultsFilteredAndTruncated () {
    return this.resultsFiltered && this.resultsTruncated;
  }

  _increaseUniqueToken = () => {
    this.token = (this.token || 0) + 1;
    return this.token;
  };

  @action
  clearMarkersForPath = (path, including = true) => {
    this.markers = resetMarkersForPath(
      path,
      this.markers,
      {keepCurrent: !including}
    );
  };

  @action
  clearMarkersForCurrentPath = (including = true) => {
    this.clearMarkersForPath(this.path, including);
  };

  @action
  clearMarkers = () => {
    this.markers = resetMarkersForPath();
  };

  @action
  resetFilter = (silent = true) => {
    this.filters = {
      name: undefined,
      sizeGreaterThan: undefined,
      sizeLessThan: undefined,
      dateFilterType: undefined,
      dateAfter: undefined,
      dateBefore: undefined
    };
    if (!silent) {
      this.refreshCurrentPath(true);
    }
  };

  @action
  initialize = (
    storageId,
    path,
    showVersions,
    showArchives
  ) => {
    const storageChanged = this.setStorage(storageId);
    const pathChanged = this.setPath(path);
    const showVersionsChanged = this.setShowVersions(showVersions);
    const showArchivesChanged = this.setShowArchives(showArchives);
    if (
      storageChanged ||
      pathChanged ||
      showVersionsChanged ||
      showArchivesChanged
    ) {
      (this.fetchCurrentPage)();
    }
    return storageChanged ||
    pathChanged ||
    showVersionsChanged ||
    showArchivesChanged;
  };

  @action
  setStorage = (storageId) => {
    if (this.storageId === storageId) {
      return false;
    }
    this._increaseUniqueToken();
    this.pageElements = [];
    this.pagePath = undefined;
    this.pageError = undefined;
    this.pagePending = false;
    this.pageLoaded = false;
    this.storageId = storageId;
    this.path = correctPath('');
    this.clearMarkers();
    this.storageRequest = dataStorages.load(this.storageId);
    this.storageRequest.fetchIfNeededOrWait()
      .catch((error) => console.warn(
        `Error fetching storage #${storageId || '<unknown>'}: ${error.message}`
      ));
    this.initializeDownloadableAttribute();
    return true;
  };

  initializeDownloadableAttribute = () => {
    this.downloadEnabled = false;
    if (this.storageId && this.storageRequest) {
      const metadata = new MetadataLoad(this.storageId, 'DATA_STORAGE');
      Promise.all([
        authenticatedUserInfo.fetchIfNeededOrWait(),
        preferences.fetchIfNeededOrWait(),
        metadata.fetch(),
        this.storageRequest.fetchIfNeededOrWait()
      ])
        .then(() => {
          if (
            authenticatedUserInfo.loaded &&
            preferences.loaded &&
            preferences.storageDownloadAttribute &&
            metadata.loaded
          ) {
            const [currentStorageMetadata] = metadata.value || [];
            if (
              !authenticatedUserInfo.value.admin &&
              !this.isOwner &&
              currentStorageMetadata &&
              currentStorageMetadata.data &&
              currentStorageMetadata.data[preferences.storageDownloadAttribute]
            ) {
              const userGroups = new Set([
                ...(authenticatedUserInfo.value.groups || []).map((group) => group.toLowerCase()),
                ...(authenticatedUserInfo.value.roles || []).map((role) => role.name.toLowerCase())
              ]);
              const value = currentStorageMetadata.data[preferences.storageDownloadAttribute].value;
              return checkStorageDownloadEnabledAttributeValue(
                value,
                userGroups
              );
            }
          }
          return Promise.resolve(true);
        })
        .catch(() => {})
        .then((result) => {
          this.downloadEnabled = result;
        });
    }
  };

  @action
  refreshStorageInfo = (force = true) => {
    if (!this.storageRequest) {
      return Promise.resolve();
    }
    if (force) {
      return this.storageRequest.fetch();
    }
    return this.storageRequest.fetchIfNeededOrWait();
  }

  @action
  setPath = (path = '') => {
    const corrected = correctPath(path);
    if (this.path === corrected) {
      return false;
    }
    this.path = corrected;
    this._increaseUniqueToken();
    if (this.keepPagesHistory) {
      this.markers = ensureMarkersForPath(corrected, this.markers);
    } else {
      this.markers = this.clearMarkers;
    }
    return true;
  };

  @action
  setShowVersions = (showVersions = true) => {
    if (this.showVersions === showVersions) {
      return false;
    }
    this._increaseUniqueToken();
    // We need to clear all markers
    this.clearMarkers();
    this.showVersions = showVersions;
    return true;
  };

  @action
  setShowArchives = (showArchives = false) => {
    if (this.showArchives === showArchives) {
      return false;
    }
    this._increaseUniqueToken();
    // We need to clear all markers
    this.clearMarkers();
    this.showArchives = showArchives;
    return true;
  };

  @action
  changeFilterField = (key, value, applyChanges = true) => {
    this.currentFilter[key] = value;
    if (applyChanges) {
      this.applyFilters();
    }
  };

  @action
  changeFilter = (newFilterObj = {}, applyChanges = true) => {
    Object.keys(newFilterObj).forEach(key => {
      this.changeFilterField(key, newFilterObj[key], false);
    });
    if (applyChanges) {
      this.applyFilters();
    }
  };

  @action
  applyFilters = async () => {
    const pathCorrected = correctPath(
      this.path,
      {
        leadingSlash: false,
        trailingSlash: false,
        undefinedAsEmpty: true
      }
    );
    const formatToUTCString = date => date
      ? moment.utc(date).format('YYYY-MM-DD HH:mm:ss.SSS')
      : undefined;
    try {
      const request = new DataStorageFilter(
        this.storageId,
        pathCorrected ? decodeURIComponent(pathCorrected) : undefined,
        this.showVersions,
        this.showArchives
      );
      let payload = {
        nameFilter: this.currentFilter?.name,
        sizeGreaterThan: mbToBytes(this.currentFilter?.sizeGreaterThan),
        sizeLessThan: mbToBytes(this.currentFilter?.sizeLessThan),
        dateAfter: formatToUTCString(this.currentFilter?.dateAfter),
        dateBefore: formatToUTCString(this.currentFilter?.dateBefore)
      };
      payload = Object.fromEntries(Object.entries(payload)
        .filter(([_, value]) => value !== undefined)
      );
      if (!Object.keys(payload).length) {
        return this.refreshCurrentPath(true);
      }
      await request.send(payload);
      if (request.error) {
        throw new Error(request.error);
      }
      if (!request.loaded) {
        throw new Error('Error loading page');
      }
      const {results = [], nextPageMarker} = request.value || {};
      this.resultsTruncated = !!nextPageMarker;
      this.filtersApplied = true;
      this.pageElements = results;
      this.pageLoaded = true;
      this.pagePath = pathCorrected;
    } catch (error) {
      this.pageElements = [];
      this.pageError = error.message;
      this.pageLoaded = false;
      this.pagePath = pathCorrected;
    } finally {
      this.pagePending = false;
      this.pageLoaded = !this.pageError;
      this.filtersApplied = true;
    }
  };

  @action
  fetchCurrentPage = async () => {
    const token = this._increaseUniqueToken();
    const submitChanges = (fn) => {
      if (token === this.token && typeof fn === 'function') {
        fn();
      }
    };
    const pathCorrected = correctPath(
      this.path,
      {
        leadingSlash: false,
        trailingSlash: false,
        undefinedAsEmpty: true
      }
    );
    try {
      this.pagePending = true;
      this.pageError = undefined;
      const marker = getCurrentPageMarker(this.path, this.markers);
      const request = new DataStorageRequest(
        this.storageId,
        pathCorrected ? decodeURIComponent(pathCorrected) : undefined,
        this.showVersions,
        this.showArchives,
        this.pageSize,
        marker
      );
      await request.fetchPage(marker);
      if (request.error) {
        throw new Error(request.error);
      }
      if (!request.loaded) {
        throw new Error('Error loading page');
      }
      const {
        results = [],
        nextPageMarker
      } = request.value || {};
      submitChanges(() => {
        this.pageElements = results;
        this.pageLoaded = true;
        this.pagePath = pathCorrected;
        this.markers = insertNextPageMarker(this.path, nextPageMarker, this.markers);
        this.filtersApplied = false;
      });
    } catch (error) {
      submitChanges(() => {
        this.pageElements = [];
        this.pageError = error.message;
        this.pageLoaded = false;
        this.pagePath = pathCorrected;
      });
    } finally {
      submitChanges(() => {
        this.pagePending = false;
        this.pageLoaded = !this.pageError;
        this.filtersApplied = false;
      });
    }
  };

  @action
  navigateToNextPage = () => {
    if (this.currentPagination.next) {
      const newMarkers = setCurrentPage(
        this.path,
        (currentPage) => currentPage + 1,
        this.markers
      );
      if (newMarkers) {
        this.markers = newMarkers;
        return this.fetchCurrentPage();
      }
    }
    return Promise.resolve();
  };

  @action
  navigateToPreviousPage = () => {
    if (this.currentPagination.previous) {
      const newMarkers = setCurrentPage(
        this.path,
        (currentPage) => currentPage - 1,
        this.markers
      );
      if (newMarkers) {
        this.markers = newMarkers;
        return this.fetchCurrentPage();
      }
    }
    return Promise.resolve();
  };

  @action
  navigateToFirstPage = () => {
    if (this.currentPagination.first) {
      const newMarkers = setCurrentPage(
        this.path,
        () => 0,
        this.markers
      );
      if (newMarkers) {
        this.markers = newMarkers;
        return this.fetchCurrentPage();
      }
    }
    return Promise.resolve();
  };

  @action
  refreshCurrentPath = (keepCurrentPage = false) => {
    if (!keepCurrentPage) {
      this.clearMarkersForCurrentPath();
    }
    return this.fetchCurrentPage();
  }
}

export default DataStorageListing;
