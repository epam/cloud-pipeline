/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import EdgeExternalUrl from '../../models/cluster/EdgeExternalUrl';
import cloudRegionsInfo from '../../models/cloudRegions/CloudRegionsInfo';

function getEdgeUrl (regionId) {
  return new Promise(resolve => {
    const request = new EdgeExternalUrl(regionId);
    request
      .fetch()
      .then(() => {
        if (request.loaded) {
          resolve(request.value);
        } else {
          resolve();
        }
      })
      .catch(() => resolve());
  });
}

function fetchWithTimeout (url, timeoutMS = 2000) {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.withCredentials = true;
    request.onreadystatechange = function () {
      if (request.readyState !== 4) return;
      if (request.status !== 200) {
        const error = request.statusText || `error code: ${request.status}`;
        reject(new Error(error));
      } else {
        resolve();
      }
    };
    request.open('GET', url);
    request.timeout = timeoutMS;
    request.ontimeout = function () {
      reject(new Error('Timeout'));
    };
    request.send();
  });
}

function invalidateToken (edgeUrl) {
  if (!edgeUrl) {
    return Promise.resolve();
  }
  if (edgeUrl.endsWith('/')) {
    edgeUrl = edgeUrl.slice(0, -1);
  }
  const invalidateUrl = edgeUrl.concat('/invalidate');
  return new Promise(async (resolve) => {
    try {
      await fetchWithTimeout(invalidateUrl);
    } catch (e) {
      console.warn(`${invalidateUrl} error: ${e.message}`);
    } finally {
      resolve();
    }
  });
}

function invalidateRegionEdge (regionId) {
  return new Promise((resolve) => {
    getEdgeUrl(regionId)
      .then(url => invalidateToken(url))
      .then(() => resolve());
  });
}

export default function invalidateEdgeTokens () {
  return new Promise((resolve) => {
    cloudRegionsInfo.fetchIfNeededOrWait()
      .then(() => {
        if (cloudRegionsInfo.loaded) {
          const regionIds = (cloudRegionsInfo.value || [])
            .map(region => region.regionId);
          return Promise.all(regionIds.map(invalidateRegionEdge));
        } else {
          throw new Error(cloudRegionsInfo.error);
        }
      })
      .catch(() => {})
      .then(resolve);
  });
};
