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

import moment from 'moment-timezone';
import whoAmI from '../../models/user/WhoAmI';
import dataStorages from '../../models/dataStorage/DataStorages';
import SendSystemLogs from '../../models/system-logs/send-logs';

const AccessTypes = {
  write: 'WRITE',
  read: 'READ',
  delete: 'DELETE'
};

/**
 * @typedef {Object} StorageItemOptions
 * @property {number|string} [storageId}
 * @property {string} [path]
 * @property {string} [fullPath]
 * @property {string} [reportStorageType]
 */

/**
 * @typedef {Object} LogEntry
 * @property {moment.Moment} timestamp
 * @property {string} accessType
 * @property {StorageItemOptions} object
 */

/**
 * @typedef {Object} PreparedLogEntry
 * @property {moment.Moment} timestamp
 * @property {string} message
 */

const REPORT_DEBOUNCE_MS = 1000;
const DEBOUNCE_ENTRIES_THRESHOLD = 50;

/**
 * Converts number to a string with additional zeros from the left, i.e.
 * 1       -> 000001
 * 123     -> 000123
 * 1234567 -> 1234567
 * @param {number} eventIndex
 * @returns {string}
 */
function formatEventIndex (eventIndex) {
  const shift = 3;
  if (typeof String.prototype.padStart === 'function') {
    return `${eventIndex}`.padStart(shift, '0');
  }
  let str = `${eventIndex}`;
  while (str.length < shift) {
    str = '0'.concat(str);
  }
  return str;
}

/**
 * @param {number} [eventIndex=0]
 * @returns {string}
 */
function getEventIdFromTimestamp (eventIndex = 0) {
  return `${moment.utc().valueOf()}${formatEventIndex(eventIndex)}`;
}

/**
 * @param {LogEntry[]} items
 * @returns {Promise<PreparedLogEntry[]>}
 */
async function prepareAuditItems (items) {
  const storagesIds = items
    .filter((anItem) => anItem.object &&
      anItem.object.fullPath === undefined &&
      anItem.object.storageId !== undefined)
    .map((anItem) => Number(anItem.object.storageId));
  let loadedStorages = (dataStorages.loaded ? (dataStorages.value || []) : []);
  if (storagesIds.some((id) => !loadedStorages.find((aStorage) => aStorage.id === id))) {
    await dataStorages.fetch();
    loadedStorages = (dataStorages.loaded ? (dataStorages.value || []) : []);
  }
  return items
    .filter((anItem) => !!anItem.object)
    .map((anItem) => {
      const {
        object,
        timestamp,
        accessType
      } = anItem;
      const {
        fullPath,
        storageId,
        path,
        reportStorageType
      } = object || {};
      if (fullPath) {
        return {
          timestamp,
          message: `${accessType} ${fullPath}`
        };
      }
      if (!storageId) {
        return undefined;
      }
      const storage = loadedStorages.find((aStorage) => aStorage.id === Number(storageId) &&
        (!reportStorageType || aStorage.type === reportStorageType));
      if (!storage) {
        return undefined;
      }
      const {pathMask} = storage;
      const full = [pathMask, path].filter(Boolean).join('/');
      return {
        timestamp,
        message: `${accessType} ${full}`
      };
    }).filter(Boolean);
}

class AuditStorageAccess {
  /**
   * @type {LogEntry[]}
   */
  logs = [];

  async sendAuditLogs () {
    if (this.sendInProgress) {
      return;
    }
    this.sendInProgress = true;
    if (this.debounceTimer) {
      clearTimeout(this.debounceTimer);
      this.debounceTimer = undefined;
    }
    try {
      await whoAmI.fetchIfNeededOrWait();
      if (!whoAmI.loaded) {
        throw new Error(whoAmI.error || 'Error fetching user info');
      }
      const {
        userName
      } = whoAmI.value || {};
      const pendingEvents = this.logs.slice();
      if (pendingEvents.length === 0) {
        return;
      }
      const prepared = await prepareAuditItems(pendingEvents);
      const payload = prepared.map((log, entryIndex) => ({
        eventId: getEventIdFromTimestamp(entryIndex),
        messageTimestamp: log.timestamp.format('YYYY-MM-DD HH:mm:ss.SSS'),
        message: log.message,
        type: 'audit',
        severity: 'INFO',
        user: userName,
        serviceName: 'gui'
      }));
      if (payload.length > 0) {
        const request = new SendSystemLogs();
        await request.send(payload);
        if (request.error) {
          throw new Error(request.error);
        }
      }
      this.logs = this.logs.filter((log) => !pendingEvents.includes(log));
      this.sendInProgress = false;
      if (this.logs.length > 0) {
        await this.sendAuditLogs();
      }
    } catch (error) {
      console.warn(error.message);
      this.sendInProgress = false;
    }
  }

  sendAuditLogsDebounced = () => {
    if (this.debounceTimer) {
      clearTimeout(this.debounceTimer);
      this.debounceTimer = undefined;
    }
    if (this.logs.length > DEBOUNCE_ENTRIES_THRESHOLD) {
      (this.sendAuditLogs)();
    } else {
      this.debounceTimer = setTimeout(() => this.sendAuditLogs(), REPORT_DEBOUNCE_MS);
    }
  };

  /**
   * @param {string} accessType
   * @param {boolean} debounced
   * @param {StorageItemOptions} object
   */
  reportAccess = (accessType, debounced, ...object) => {
    const time = moment.utc();
    this.logs.push(...object.map((singleObject) => ({
      timestamp: time,
      accessType,
      object: singleObject
    })));
    if (debounced) {
      this.sendAuditLogsDebounced();
    } else {
      (this.sendAuditLogs)();
    }
  };

  /**
   * @param {StorageItemOptions} object
   */
  reportReadAccess = (...object) => {
    this.reportAccess(AccessTypes.read, false, ...object);
  };

  /**
   * @param {StorageItemOptions} object
   */
  reportWriteAccess = (...object) => {
    this.reportAccess(AccessTypes.write, false, ...object);
  };

  /**
   * @param {StorageItemOptions} object
   */
  reportDelete = (...object) => {
    this.reportAccess(AccessTypes.delete, false, ...object);
  };

  reportReadAccessDebounced = (...object) => {
    this.reportAccess(AccessTypes.read, true, ...object);
  };

  /**
   * @param {StorageItemOptions} object
   */
  reportWriteAccessDebounced = (...object) => {
    this.reportAccess(AccessTypes.write, true, ...object);
  };

  /**
   * @param {StorageItemOptions} object
   */
  reportDeleteDebounced = (...object) => {
    this.reportAccess(AccessTypes.delete, true, ...object);
  };
}

const auditStorageAccessManager = new AuditStorageAccess();

export default auditStorageAccessManager;
