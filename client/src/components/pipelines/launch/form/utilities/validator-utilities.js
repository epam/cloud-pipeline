/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import DataStorageItemSize from '../../../../../models/dataStorage/DataStorageItemSize';
import {getStorageFileAccessInfo} from '../../../../../utils/object-storage';

function logError (parameter, error) {
  const errorText = typeof error === 'object' && error.message
    ? error.message
    : error;
  return console.error(`[${parameter.name}] validation error: ${errorText}`);
}

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

async function getFileContent (path) {
  const info = await getStorageFileAccessInfo(path);
  const {path: pathInfo, objectStorage} = info;
  const content = await objectStorage.getFileContent(pathInfo);
  return content;
}

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
      `File size is too large (maximum file size is ${maxSizeMb}MB`;
      return {[type]: errorMessage};
    }
    return {};
  } catch (error) {
    logError(parameter, error);
    return {warning: `Unable to validate parameter.`};
  }
}

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
    const content = await getFileContent(parameter.value);
    if (!content) {
      return {[type]: 'File is missing or empty'};
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
    return {warning: `Unable to validate parameter.`};
  }
}

export {
  logError,
  getFileSize,
  getFileContent,
  validateItemSize,
  validateCSVHeader
};
