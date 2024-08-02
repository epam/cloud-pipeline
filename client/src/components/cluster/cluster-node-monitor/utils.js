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

import moment from 'moment-timezone';

function extractTimelineData (data = {}, measure, hideDatasets = []) {
  if (!Object.keys(data).length || !measure) {
    return [];
  }
  const extractByKey = (data, key) => {
    return data.charts.map(record => {
      const startUnix = moment(record.startTime).unix();
      const endUnix = moment(record.endTime).unix();
      return ({
        record,
        value: (record.gpuUsage[key] || {})[measure],
        date: startUnix + ((endUnix - startUnix) / 2)
      });
    });
  };
  return [{
    data: extractByKey(data, 'activeGpus'),
    color: '#108ee9'
  }, {
    data: extractByKey(data, 'gpuUtilization'),
    color: '#09ab5a'
  }, {
    data: extractByKey(data, 'gpuMemoryUtilization'),
    color: '#f04134'
  }].filter((el, index) => !hideDatasets.includes(index));
};

function extractHeatmapData (data = {}, measure) {
  if (!Object.keys(data).length || !measure) {
    return [];
  }
  const extractHeatMapDataByKey = (
    data,
    key,
    type = 'average',
    mapValue = (v) => v
  ) => {
    const gpuKeys = Object.keys(data.charts[0].gpuDetails);
    return gpuKeys.map(gpuKey => ({
      name: gpuKey,
      records: data.charts.map(record => {
        const details = ((record.gpuDetails || {})[gpuKey] || {})[key] || {};
        const value = details[type];
        return {
          start: record.startTime,
          end: record.endTime,
          value: mapValue(value)
        };
      })
    }));
  };
  return [
    {
      key: 'gpuUtilization',
      data: extractHeatMapDataByKey(data, 'gpuUtilization', measure, (v) => v > 0 ? 1 : 0),
      color: '#108ee9',
      max: 1,
      min: 0
    },
    {
      key: 'gpuUtilization',
      data: extractHeatMapDataByKey(data, 'gpuUtilization', measure),
      color: '#09ab5a',
      max: 100,
      min: 0
    }, {
      key: 'gpuMemoryUtilization',
      data: extractHeatMapDataByKey(data, 'gpuMemoryUtilization', measure),
      color: '#f04134',
      max: 100,
      min: 0
    }
  ];
};

export {extractHeatmapData, extractTimelineData};
