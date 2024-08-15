/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import {parseDate} from '../../special/heat-map-chart/utils';

export const COLORS = {
  activeGpus: '#108ee9',
  gpuUtilization: '#09ab5a',
  gpuMemoryUtilization: '#f04134'
};

function extractTimelineData ({
  metrics = {},
  measure,
  hideDatasets = [],
  from,
  to
}) {
  const extractByKey = (metrics, key) => {
    const data = (metrics.charts || []).map(record => {
      const {unix: startUnix} = parseDate(record.startTime);
      const {unix: endUnix} = parseDate(record.endTime);
      return ({
        record,
        value: (record.gpuUsage[key] || {})[measure],
        date: startUnix + ((endUnix - startUnix) / 2)
      });
    });
    return [
      ...(from ? [{date: from, value: null, record: {}}] : []),
      ...data,
      ...(to ? [{date: to, value: null, record: {}}] : [])
    ];
  };
  return [{
    key: 'gpuActive',
    data: extractByKey(metrics, 'activeGpus'),
    color: COLORS.activeGpus
  }, {
    key: 'gpuUtilization',
    data: extractByKey(metrics, 'gpuUtilization'),
    color: COLORS.gpuUtilization
  }, {
    key: 'gpuMemoryUtilization',
    data: extractByKey(metrics, 'gpuMemoryUtilization'),
    color: COLORS.gpuMemoryUtilization
  }].filter(dataset => !hideDatasets.includes(dataset.key));
};

function extractHeatmapData ({
  metrics = {},
  measure,
  from,
  to,
  hideDatasets = []
}) {
  const extractHeatMapDataByKey = (
    metrics,
    key,
    measure = 'average',
    mapValue = (v) => v
  ) => {
    const gpuDetails = (metrics.charts || [])[0]?.gpuDetails || {};
    const gpuKeys = Object.keys(gpuDetails);
    let prevEndUnix;
    return gpuKeys.map(gpuKey => {
      prevEndUnix = undefined;
      return ({
        name: gpuKey,
        records: [{
          start: from,
          end: from,
          value: undefined,
          hide: true
        }, ...(metrics.charts || []).map(record => {
          const details = ((record.gpuDetails || {})[gpuKey] || {})[key] || {};
          const value = details[measure];
          const {unix: start} = parseDate(record.startTime);
          const {unix: endUnix} = parseDate(record.endTime);
          let startUnix = start;
          if (prevEndUnix && start < prevEndUnix) {
            startUnix = prevEndUnix;
          }
          prevEndUnix = endUnix;
          return {
            start: startUnix,
            end: endUnix,
            value: mapValue(value)
          };
        }), {
          start: to,
          end: to,
          value: undefined,
          hide: true
        }]
      });
    });
  };
  return [
    {
      key: 'gpuActive',
      data: extractHeatMapDataByKey(metrics, 'gpuUtilization', measure, (v) => v > 0 ? 1 : 0),
      color: COLORS.activeGpus,
      max: 1,
      min: 0
    },
    {
      key: 'gpuUtilization',
      data: extractHeatMapDataByKey(metrics, 'gpuUtilization', measure),
      color: COLORS.gpuUtilization,
      max: 100,
      min: 0
    }, {
      key: 'gpuMemoryUtilization',
      data: extractHeatMapDataByKey(metrics, 'gpuMemoryUtilization', measure),
      color: COLORS.gpuMemoryUtilization,
      max: 100,
      min: 0
    }
  ].filter(dataset => !hideDatasets.includes(dataset.key));
};

export {extractHeatmapData, extractTimelineData};
