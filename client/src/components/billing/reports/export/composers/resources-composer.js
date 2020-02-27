/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import Fields from './fields';

function compose (csv, resources) {
  return new Promise((resolve, reject) => {
    if (!resources.loaded) {
      reject(new Error('Resources data is not available'));
    } else {
      const {value = {}} = resources;
      const getResourceInfo = (parent, name) => {
        if (value.hasOwnProperty(parent) && value[parent].hasOwnProperty(name)) {
          return value[parent][name];
        }
        return {
          value: undefined,
          previous: undefined
        };
      };
      const getResoucesSumm = (a, b) => {
        const {value: aValue, previous: aPrevious} = a;
        const {value: bValue, previous: bPrevious} = b;
        const safeSumm = (o1, o2) => {
          if (o1 === undefined && o2 === undefined) {
            return undefined;
          }
          return (o1 || 0) + (o2 || 0);
        };
        return {
          value: safeSumm(aValue, bValue),
          previous: safeSumm(aPrevious, bPrevious)
        };
      };
      const fileStorages = getResourceInfo('Storage', 'File');
      const objectStorages = getResourceInfo('Storage', 'Object');
      const storages = getResoucesSumm(fileStorages, objectStorages);
      const cpuInstances = getResourceInfo('Compute instances', 'CPU');
      const gpuInstances = getResourceInfo('Compute instances', 'GPU');
      const instances = getResoucesSumm(cpuInstances, gpuInstances);
      const addResourceInfo = (name, info) => {
        csv.setCellValue(
          Fields.summaryPrevious,
          name,
          info.previous
        );
        csv.setCellValue(
          Fields.summaryCurrent,
          name,
          info.value
        );
      };
      addResourceInfo('storages', storages);
      addResourceInfo('file_storages', fileStorages);
      addResourceInfo('object_storages', objectStorages);
      addResourceInfo('instances', instances);
      addResourceInfo('instances_cpu', cpuInstances);
      addResourceInfo('instances_gpu', gpuInstances);
      resolve();
    }
  });
}

export default compose;
