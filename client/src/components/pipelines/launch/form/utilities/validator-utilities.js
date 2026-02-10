/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import DataStorageAvailable from '../../../../../models/dataStorage/DataStorageAvailable';
import DataStorageItemContent from '../../../../../models/dataStorage/DataStorageItemContent';
import DataStorageItemSize from '../../../../../models/dataStorage/DataStorageItemSize';
import {base64toString} from '../../../../../utils/base64';
import {getStorageLinkInfo} from '../../../../special/data-storage-link/utilities.js';

/**
 * Logs validation error for a parameter
 * @param {Object} parameter - Parameter object with name property
 * @param {Error|string} error - Error object or message
 */
function logError (parameter, error) {
  const errorText = typeof error === 'object' && error.message
    ? error.message
    : error;
  console.error(`[${parameter.name}] validation error: ${errorText}`);
}

/**
 * Fetches the size of a file at the given path
 * @param {string} path - File path
 * @returns {Promise<number>} File size in bytes
 * @throws {Error} If file is not found or size cannot be retrieved
 */
async function getFileSize (path) {
  const sizeRequest = new DataStorageItemSize();
  await sizeRequest.send([path]);
  if (sizeRequest.error) {
    throw new Error(`Error fetching file size: ${sizeRequest.error}`);
  }
  const result = (sizeRequest.value || [])[0];
  if (!result || result.size === undefined) {
    throw new Error(`File not found: ${path}`);
  }
  return result.size;
}

/**
 * Extracts column names from CSV header
 * @param {string} csvString - CSV file content
 * @returns {string[]} Array of column names
 */
function getCSVColumns (csvString = '') {
  if (!csvString || typeof csvString !== 'string') {
    return [];
  }
  const lines = csvString.trim().split('\n');
  if (lines.length === 0) {
    return [];
  }
  const header = lines[0].trim();
  return header.split(',').map((col) => col.trim());
}

/**
 * Validates that file size does not exceed maximum
 * @param {Object} config - Validation configuration
 * @param {Object} config.parameter - Parameter object with value property
 * @param {number} config.maxSizeMb - Maximum allowed file size in MB
 * @param {string} config.type - Type of message ('warning' | 'error')
 * @param {string} config.message - Custom error message
 * @returns {Promise<Object>} Validation result object
 */
async function validateItemSize ({
  parameter,
  options,
  maxSizeMb,
  type = 'warning',
  message = ''
}) {
  try {
    if (!parameter.value) {
      return {};
    }
    const fileSize = await getFileSize(parameter.value);
    const fileSizeMb = fileSize / (1024 * 1024);
    if (fileSizeMb > maxSizeMb) {
      const errorMessage = message ||
        `File size is too large (maximum file size is ${maxSizeMb}MB).`;
      return {[type]: errorMessage};
    }
    return {};
  } catch (error) {
    logError(parameter, error);
    return {warning: 'Unable to validate parameter.'};
  }
}

/**
 * Validates that CSV file contains required columns
 * @param {Object} config - Validation configuration
 * @param {Object} config.parameter - Parameter object with value property
 * @param {string[]} config.columns - Required column names
 * @param {string} config.type - Type of message ('warning' | 'error')
 * @param {string} config.message - Custom error message
 * @returns {Promise<Object>} Validation result object
 */
async function validateCSVHeader ({
  parameter,
  options,
  columns,
  type = 'warning',
  message = ''
}) {
  try {
    if (!parameter.value) {
      return {};
    }
    if (!Array.isArray(columns) || columns.length === 0) {
      return {warning: 'No columns specified for validation.'};
    }
    const {content, truncated} = await getFileContent(parameter.value);
    if (!content) {
      return {[type]: 'File is missing or empty.'};
    }
    if (truncated) {
      return {warning: 'File is too large to validate.'};
    }
    const csvColumns = getCSVColumns(content);
    const missingColumns = columns.filter((col) => !csvColumns.includes(col));
    if (missingColumns.length > 0) {
      const errorMessage = message ||
        `File has missing columns: ${missingColumns.join(', ')}`;
      return {[type]: errorMessage};
    }
    return {};
  } catch (error) {
    logError(parameter, error);
    return {warning: 'Unable to validate parameter.'};
  }
}

/**
 * Fetches file content from storage
 * @param {string} path - File path
 * @returns {Promise<Object>} Object with content, truncated and mayBeBinary flag
 * @throws {Error} If storage or file is not found
 */
async function getFileContent (path) {
  await DataStorageAvailable.fetchIfNeededOrWait();
  const info = getStorageLinkInfo({
    storages: DataStorageAvailable.value,
    path,
    isFolder: false,
    showShared: true
  });
  if (!info?.storageId) {
    throw new Error(`Storage not found for path: ${path}`);
  }
  const request = new DataStorageItemContent(info.storageId, info.relativePath);
  await request.fetch();
  if (request.error) {
    throw new Error(`Error fetching file content: ${request.error}`);
  }
  if (!request?.value) {
    throw new Error(`File not found: ${path}`);
  }
  const {truncated, mayBeBinary, content = ''} = request.value;
  return {
    content: base64toString(content),
    truncated,
    mayBeBinary
  };
}

export {
  logError,
  getFileSize,
  getFileContent,
  getCSVColumns,
  validateItemSize,
  validateCSVHeader
};
