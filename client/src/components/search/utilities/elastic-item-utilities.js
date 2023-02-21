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

import {SearchItemTypes} from '../../../models/search';

export function filterMatchingItemsFn (item) {
  return function filter (arrayItem) {
    return item &&
      arrayItem &&
      item.id === arrayItem.id &&
      item.parentId === arrayItem.parentId &&
      item.type === arrayItem.type;
  };
}

export function filterNonMatchingItemsFn (item) {
  return function filter (arrayItem) {
    return !item ||
      !arrayItem ||
      item.id !== arrayItem.id ||
      item.parentId !== arrayItem.parentId ||
      item.type !== arrayItem.type;
  };
}

export function itemSharingAvailable (item) {
  return item && (
    item.type === SearchItemTypes.s3File ||
    item.type === SearchItemTypes.azFile ||
    item.type === SearchItemTypes.gsFile
  );
}

function itemFitsMask (item, mask) {
  if (!item) {
    return false;
  }
  if (!mask) {
    return true;
  }
  const {
    path
  } = item;
  return mask.test(path);
}

export function itemIsDownloadable (
  item,
  preferences,
  notDownloadableStorages = []
) {
  if (
    !item ||
    (
      item.type !== SearchItemTypes.NFSFile &&
      item.type !== SearchItemTypes.s3File &&
      item.type !== SearchItemTypes.azFile &&
      item.type !== SearchItemTypes.gsFile
    ) ||
    !preferences ||
    !preferences.loaded
  ) {
    return false;
  }
  if (notDownloadableStorages.includes(Number(item.parentId || item.storageId))) {
    return false;
  }
  const {
    allow = [],
    deny = []
  } = preferences.facetedFilterDownload;
  return (allow.length === 0 || allow.some(mask => itemFitsMask(item, mask))) &&
    (deny.length === 0 || !deny.some(mask => itemFitsMask(item, mask)));
}

export function filterDownloadableItems (items, preferences, notDownloadableStorages = []) {
  return (items || []).filter((item) => itemIsDownloadable(
    item,
    preferences,
    notDownloadableStorages
  ));
}
