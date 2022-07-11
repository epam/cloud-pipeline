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

function measureSingleUrlLatency (url, experiment = 0) {
  const urlWithQuery = url +
    (/\?.+/.test(url) ? '&' : '?') +
    `___e=${experiment || 0}&___r=${Math.floor(Math.random() * 1000000)}`;
  return new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    xhr.timeout = 2000;
    xhr.onload = () => {
      if (performance !== undefined) {
        const resources = performance.getEntriesByType('resource');
        const resourceTiming = resources
          .find(resource => resource.name === (new URL(urlWithQuery)).href);
        if (resourceTiming) {
          const latency = resourceTiming.duration;
          xhr.abort();
          resolve(latency);
        } else {
          xhr.abort();
          resolve(Infinity);
        }
      } else {
        xhr.abort();
        resolve(Infinity);
      }
    };
    xhr.onerror = () => {
      resolve(Infinity);
    };
    xhr.ontimeout = () => {
      resolve(Infinity);
    };
    xhr.open('OPTIONS', urlWithQuery);
    xhr.send();
  });
}

export function clearPerformanceEntries () {
  performance.clearResourceTimings();
}

export default function measureUrlLatency (url, experimentsCount = 5) {
  return new Promise(resolve => {
    measureSingleUrlLatency(url, 0)
      .then(latency => {
        if (latency === Infinity || experimentsCount <= 1) {
          return Promise.resolve([latency]);
        }
        return Promise.all([
          Promise.resolve(latency),
          ...(
            new Array(experimentsCount - 1))
            .fill(true)
            .map((o, index) => measureSingleUrlLatency(url, index + 1))
        ]);
      })
      .then(results => {
        const filtered = results.filter(result => result !== Infinity);
        if (filtered.length === 0) {
          resolve(Infinity);
        } else {
          filtered.sort((a, b) => a - b);
          if (filtered.length >= 3) {
            filtered.pop();
            filtered.shift();
          }
          const sum = filtered.reduce((r, c) => r + c, 0);
          const average = sum / filtered.length;
          resolve(average);
        }
      });
  });
}
