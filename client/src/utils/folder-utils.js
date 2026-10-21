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

function findFolderRecursive (folders, pathSegments) {
  if (!folders || folders.length === 0 || pathSegments.length === 0) {
    return undefined;
  }
  const [currentSegment, ...remainingSegments] = pathSegments;
  const folder = folders.find((folder) => folder.name === currentSegment);
  if (!folder) {
    return undefined;
  }
  if (remainingSegments.length === 0) {
    return folder;
  }
  return findFolderRecursive(folder.childFolders, remainingSegments);
}

export function findFolderFromTree (path, pipelinesLibrary) {
  if (!pipelinesLibrary || !path) {
    return undefined;
  }
  const normalizePath = (path) => {
    const segments = path
      .replace(/^\/+|\/+$/g, '')
      .split('/')
      .filter(Boolean);
    if (segments.length > 0 && segments[0].toLowerCase() === 'library') {
      return segments.slice(1);
    }
    return segments;
  };
  const pathSegments = normalizePath(path);
  if (pathSegments.length === 0) {
    return undefined;
  }
  return findFolderRecursive(pipelinesLibrary.childFolders || [], pathSegments);
}

/**
 * Resolve folderId from library path or return as is if pathOrId is number.
 * @param {string|number} pathOrId
 * @param {Object} pipelinesLibrary
 * @returns {number|undefined}
 */
export function getFolderIdFromTree (pathOrId, pipelinesLibrary) {
  try {
    if (pathOrId === undefined || !pipelinesLibrary) {
      return undefined;
    }
    if (typeof pathOrId === 'number') {
      return pathOrId;
    }
    if (!Number.isNaN(Number(pathOrId))) {
      return Number(pathOrId);
    }
    const folder = findFolderFromTree(pathOrId, pipelinesLibrary);
    return folder?.id;
  } catch (error) {
    console.warn('getFolderIdFromTree - folder is not found:', error);
    return undefined;
  }
}
