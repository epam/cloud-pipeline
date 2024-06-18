/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import {colors} from '../../../billing/reports/charts';
import {fadeout} from '../../../../themes/utilities/color-utilities';

const STATUSES = {
  PAUSED: 'PAUSED',
  PAUSING: 'PAUSING',
  RESUMING: 'RESUMING',
  RUNNING: 'RUNNING'
};

function getDatasetStyles (key, reportThemes, b) {
  if (key === 'RUNNING') {
    return {
      backgroundColor: reportThemes.lightCurrent,
      flagColor: reportThemes.current
    };
  }
  if (key === 'PAUSED') {
    return {
      backgroundColor: fadeout(reportThemes.lightBlue, 0.65),
      flagColor: reportThemes.lightBlue
    };
  }
  return {
    backgroundColor: fadeout(colors.grey, 0.65),
    flagColor: colors.grey
  };
};

function formatDockerImage (docker = '') {
  // eslint-disable-next-line no-unused-vars
  const [_r, g, iv] = docker.split('/');
  const image = iv.split(':').slice(0, -1).join(':');
  const dockerImage = [g, image].join('/').toLowerCase();
  return dockerImage;
};

function formatDockerImages (images = []) {
  return images.map(formatDockerImage);
};

function extractDatasetByField (field, data = {}) {
  const dataField = data[field];
  if (!dataField) {
    return {
      labels: []
    };
  }
  const categoriesKeys = Object.keys(dataField);
  const labels = [...new Set(Object
    .values(dataField)
    .reduce((acc, cur) => [...acc, ...Object.keys(cur)], [])
  )];
  const dataSets = categoriesKeys.reduce((acc, key) => {
    acc[key] = labels.map(label => (dataField[key] || {})[label] || 0);
    return acc;
  }, {});
  return {
    labels,
    ...dataSets
  };
};

function extractDatasets (data = {}) {
  return {
    owners: extractDatasetByField('owners', data),
    dockerImages: extractDatasetByField('dockerImages', data),
    instanceTypes: extractDatasetByField('instanceTypes', data),
    tags: extractDatasetByField('tags', data)
  };
};

export {
  getDatasetStyles,
  STATUSES,
  formatDockerImages,
  formatDockerImage,
  extractDatasets
};
