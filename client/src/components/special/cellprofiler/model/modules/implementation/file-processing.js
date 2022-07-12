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
/* eslint-disable max-len */

import {isObservableArray} from 'mobx';
import {AnalysisTypes} from '../../common/analysis-types';

const saveImages = {
  name: 'SaveImages',
  group: 'File Processing',
  parameters: [
    'Select the image to save|file|ALIAS imageToSave',
    'Select image name for file prefix|file|ALIAS prefix|COMPUTED|HIDDEN',
    'Saved file format|[png,tiff]|png|COMPUTED|HIDDEN|ALIAS format'
  ]
};

const exportToSpreadsheet = {
  name: 'ExportToSpreadsheet',
  group: 'File Processing',
  parameters: [
    'Calculate the per-image mean values for object measurements?|flag|false|ALIAS perMean',
    'Calculate the per-image median values for object measurements?|flag|false|ALIAS median',
    'Calculate the per-image standard deviation values for object measurements?|flag|false|ALIAS stdDev',
    'File name|string|DATA.csv|ALIAS fileName',
    'Filename prefix|string|MyExpt-|ALIAS prefix',
    'Select the measurements to export|flag|false|ALIAS pick',
    'Measurements|string|PARAMETER "Press button to select measurements"|IF pick==true|ALIAS measurements',
    'Export all measurement types?|flag|true|ALIAS all',
    {
      name: 'objects',
      title: 'Data to export',
      parameterName: 'Data to export',
      type: AnalysisTypes.object,
      multiple: true,
      valueParser: value => (Array.isArray(value) || isObservableArray(value))
        ? value.slice()
        : (value || '').split('|'),
      valueFormatter: (value) => value && value.length > 0 ? value.join('|') : 'Do not use',
      visibilityHandler: (cpModule) => cpModule.getBooleanParameterValue('all') === false
    },
    'Use the object name for the file name?|flag|true|ALIAS objectNameAsFileName'
  ]
};

export default [
  exportToSpreadsheet,
  saveImages
];
